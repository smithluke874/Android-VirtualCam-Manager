#pragma once
#include <jni.h>
#include <cstring>
#include <vector>
#include <mutex>
#include <android/log.h>
#include <arthook/Hooked.h>

class Nv21FrameProvider {
public:
    bool ensure(int w, int h) {
        if (w <= 0 || h <= 0) return false;
        std::lock_guard<std::mutex> lock(mu_);
        size_t need = (size_t)w * (size_t)h * 3 / 2;
        if (w_ == w && h_ == h && buf_.size() == need) return true;
        buf_.assign(need, 0);
        w_ = w; h_ = h;
        fillLocked();
        ready_ = true;
        return true;
    }
    bool copyInto(JNIEnv *env, jbyteArray arr) {
        if (!env || !arr || !ready_) return false;
        std::lock_guard<std::mutex> lock(mu_);
        if (buf_.empty()) return false;
        jsize len = env->GetArrayLength(arr);
        if (len <= 0) return false;
        jsize n = (jsize)(buf_.size() < (size_t)len ? buf_.size() : (size_t)len);
        env->SetByteArrayRegion(arr, 0, n, reinterpret_cast<const jbyte*>(buf_.data()));
        frame_count_++;
        if ((frame_count_ % 3) == 0) {
            bar_x_ = (bar_x_ + 8) % (w_ > 0 ? w_ : 1);
            fillLocked();
        }
        return true;
    }
    int width() const { return w_; }
    int height() const { return h_; }
    int frames() const { return frame_count_; }
    bool isReady() const { return ready_; }
private:
    void fillLocked() {
        if (buf_.empty() || w_ <= 0 || h_ <= 0) return;
        size_t ySize = (size_t)w_ * (size_t)h_;
        memset(buf_.data(), 0x80, ySize);
        int barW = w_ / 16; if (barW < 4) barW = 4;
        for (int y = 0; y < h_; y++)
            for (int x = bar_x_; x < bar_x_ + barW && x < w_; x++)
                buf_[(size_t)y * (size_t)w_ + (size_t)x] = (uint8_t)0xE0;
        memset(buf_.data() + ySize, 0x80, buf_.size() - ySize);
    }
    std::mutex mu_;
    std::vector<uint8_t> buf_;
    int w_ = 0, h_ = 0, bar_x_ = 0, frame_count_ = 0;
    bool ready_ = false;
};

extern Nv21FrameProvider g_nv21;
extern char g_last_pkg[256];

void process_callback(JNIEnv *env, jobject camera, jobject callback, const char *which);
int install_preview_callback_hooks(JNIEnv *env);

extern "C" void Hooked_setPreviewCallback(JNIEnv *env, jobject thiz, jobject cb);
extern "C" void Hooked_setPreviewCallbackWithBuffer(JNIEnv *env, jobject thiz, jobject cb);
extern "C" void Hooked_setOneShotPreviewCallback(JNIEnv *env, jobject thiz, jobject cb);
