#pragma once
#include <jni.h>
#include <cstring>
#include <vector>
#include <mutex>
#include <atomic>
#include <android/log.h>
#include <arthook/Hooked.h>

/**
 * Nv21FrameProvider — supplies NV21 frames to the hooked onPreviewFrame.
 * Pattern mode (moving bar) is active; MediaCodec continuous video is staged for land.
 */
class Nv21FrameProvider {
public:
    bool ensure(int w, int h);
    bool copyInto(JNIEnv *env, jbyteArray arr);
    void setVideoPath(const char *path) { (void)path; } // no-op until MediaCodec land
    void stop() {}

    int  width()    const { return w_; }
    int  height()   const { return h_; }
    int  frames()   const { return frame_count_.load(); }
    bool isReady()  const { return ready_; }
    bool isVideo()  const { return false; }

private:
    void fillLocked();

    std::mutex mu_;
    std::vector<uint8_t> buf_;
    int w_ = 0, h_ = 0;
    int bar_x_ = 0;
    std::atomic<int> frame_count_{0};
    bool ready_ = false;
};

extern Nv21FrameProvider g_nv21;
extern char g_last_pkg[256];

void process_callback(JNIEnv *env, jobject camera, jobject callback, const char *which);
int  install_preview_callback_hooks(JNIEnv *env);

extern "C" void Hooked_setPreviewCallback(JNIEnv *env, jobject thiz, jobject cb);
extern "C" void Hooked_setPreviewCallbackWithBuffer(JNIEnv *env, jobject thiz, jobject cb);
extern "C" void Hooked_setOneShotPreviewCallback(JNIEnv *env, jobject thiz, jobject cb);
