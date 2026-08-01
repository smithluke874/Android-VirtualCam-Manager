#pragma once
#include <jni.h>
#include <cstring>
#include <vector>
#include <mutex>
#include <atomic>
#include <thread>
#include <string>
#include <android/log.h>
#include <arthook/Hooked.h>

/**
 * Nv21FrameProvider — supplies NV21 frames to the hooked onPreviewFrame.
 *
 * Modes:
 *   1. Video  (primary)  — continuous MediaCodec decode of virtual.mp4 → NV21
 *   2. Pattern (fallback) — solid + moving bar when decoder cannot start
 *
 * Thread-safe. copyInto() is non-blocking (copies latest ready frame).
 * Status written to hook_status: nv21_video:WxH#N or nv21_pattern:WxH#N
 */
class Nv21FrameProvider {
public:
    bool ensure(int w, int h);
    bool copyInto(JNIEnv *env, jbyteArray arr);
    void setVideoPath(const char *path);
    void stop();

    int  width()    const { return w_; }
    int  height()   const { return h_; }
    int  frames()   const { return frame_count_.load(); }
    bool isReady()  const { return ready_; }
    bool isVideo()  const { return video_mode_.load(); }

private:
    void fillPatternLocked();
    bool startDecoderLocked();
    void stopDecoderLocked();
    void decoderLoop();
    void convertToNv21(const uint8_t *src, int srcW, int srcH, int srcStride,
                       int colorFormat, uint8_t *dst, int dstW, int dstH);

    std::mutex mu_;
    std::vector<uint8_t> buf_;
    std::vector<uint8_t> decode_buf_;
    int w_ = 0, h_ = 0;
    int bar_x_ = 0;
    std::atomic<int> frame_count_{0};
    bool ready_ = false;

    std::string video_path_;
    std::atomic<bool> video_mode_{false};
    std::atomic<bool> stop_flag_{false};
    std::atomic<bool> decoder_running_{false};
    std::thread decoder_thread_;
};

extern Nv21FrameProvider g_nv21;
extern char g_last_pkg[256];

void process_callback(JNIEnv *env, jobject camera, jobject callback, const char *which);
int  install_preview_callback_hooks(JNIEnv *env);

extern "C" void Hooked_setPreviewCallback(JNIEnv *env, jobject thiz, jobject cb);
extern "C" void Hooked_setPreviewCallbackWithBuffer(JNIEnv *env, jobject thiz, jobject cb);
extern "C" void Hooked_setOneShotPreviewCallback(JNIEnv *env, jobject thiz, jobject cb);
