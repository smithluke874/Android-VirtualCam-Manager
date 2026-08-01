/*
 * NV21 PreviewCallback path (classic VCAM algorithm part B)
 */
#include "nv21_hooks.h"
#include <cstdio>
#include <arthook/ArtHook.h>

#define LOG_TAG "VirtualCamZygisk"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static constexpr const char *kHookStatus = "/data/adb/virtualcam/hook_status";
static constexpr const char *kLastPkg    = "/data/adb/virtualcam/last_hook_pkg";

Nv21FrameProvider g_nv21;

static arthook::Hooked g_hook_setPreviewCallback;
static arthook::Hooked g_hook_setPreviewCallbackWithBuffer;
static arthook::Hooked g_hook_setOneShotPreviewCallback;
static arthook::Hooked g_hook_onPreviewFrame;
static jobject g_tracked_camera = nullptr;
static jclass  g_cb_class = nullptr;

static void report_status(const char *pkg, const char *status) {
    FILE *f = fopen(kHookStatus, "w");
    if (f) { fputs(status, f); fclose(f); }
    f = fopen(kLastPkg, "w");
    if (f) { fputs(pkg, f); fclose(f); }
}

static bool read_preview_size(JNIEnv *env, jobject camera, int *out_w, int *out_h) {
    if (!env || !camera || !out_w || !out_h) return false;
    *out_w = 0; *out_h = 0;
    jclass camCls = env->GetObjectClass(camera);
    if (!camCls) { env->ExceptionClear(); return false; }
    jmethodID getParams = env->GetMethodID(camCls, "getParameters",
        "()Landroid/hardware/Camera$Parameters;");
    if (!getParams) { env->ExceptionClear(); env->DeleteLocalRef(camCls); return false; }
    jobject params = env->CallObjectMethod(camera, getParams);
    env->DeleteLocalRef(camCls);
    if (!params || env->ExceptionCheck()) { env->ExceptionClear(); return false; }
    jclass pCls = env->GetObjectClass(params);
    jmethodID getPreviewSize = env->GetMethodID(pCls, "getPreviewSize",
        "()Landroid/hardware/Camera$Size;");
    if (!getPreviewSize) {
        env->ExceptionClear(); env->DeleteLocalRef(pCls); env->DeleteLocalRef(params); return false;
    }
    jobject sizeObj = env->CallObjectMethod(params, getPreviewSize);
    env->DeleteLocalRef(pCls); env->DeleteLocalRef(params);
    if (!sizeObj || env->ExceptionCheck()) { env->ExceptionClear(); return false; }
    jclass sizeCls = env->GetObjectClass(sizeObj);
    jfieldID wField = env->GetFieldID(sizeCls, "width", "I");
    jfieldID hField = env->GetFieldID(sizeCls, "height", "I");
    if (wField && hField) {
        *out_w = env->GetIntField(sizeObj, wField);
        *out_h = env->GetIntField(sizeObj, hField);
    }
    env->DeleteLocalRef(sizeCls); env->DeleteLocalRef(sizeObj);
    return *out_w > 0 && *out_h > 0;
}

extern "C" void Hooked_onPreviewFrame(JNIEnv *env, jobject thiz, jbyteArray data, jobject camera) {
    if (data && g_nv21.isReady()) {
        g_nv21.copyInto(env, data);
        if ((g_nv21.frames() % 30) == 1) {
            char buf[80];
            snprintf(buf, sizeof(buf), "nv21_pattern:%dx%d#%d",
                     g_nv21.width(), g_nv21.height(), g_nv21.frames());
            report_status(g_last_pkg, buf);
        }
    }
    if (g_hook_onPreviewFrame.installed())
        g_hook_onPreviewFrame.invoke<void>(env, thiz, data, camera);
}

void process_callback(JNIEnv *env, jobject camera, jobject callback, const char *which) {
    if (!env || !callback) return;
    LOGI("process_callback %s cb=%p", which, callback);
    int w = 0, h = 0;
    if (camera && read_preview_size(env, camera, &w, &h)) {
        g_nv21.ensure(w, h);
        LOGI("preview size %dx%d for NV21", w, h);
    } else {
        g_nv21.ensure(640, 480);
        LOGW("preview size unknown — using 640x480");
    }
    if (camera) {
        if (g_tracked_camera) env->DeleteGlobalRef(g_tracked_camera);
        g_tracked_camera = env->NewGlobalRef(camera);
    }
    jclass cbCls = env->GetObjectClass(callback);
    if (!cbCls) { env->ExceptionClear(); LOGE("callback class not found"); return; }
    if (g_cb_class) env->DeleteGlobalRef(g_cb_class);
    g_cb_class = (jclass)env->NewGlobalRef(cbCls);
    if (!g_hook_onPreviewFrame.installed()) {
        auto st = g_hook_onPreviewFrame.install(
            env, g_cb_class, "onPreviewFrame",
            "([BLandroid/hardware/Camera;)V",
            reinterpret_cast<void*>(&Hooked_onPreviewFrame));
        if (st == arthook::Status::kOk) {
            LOGI("  ART hooked: onPreviewFrame");
            char buf[64];
            snprintf(buf, sizeof(buf), "nv21_cb_hooked:%s", which);
            report_status(g_last_pkg, buf);
        } else {
            LOGW("  onPreviewFrame fail: %s", arthook::StatusToString(st));
            char buf[64];
            snprintf(buf, sizeof(buf), "nv21_cb_fail:%s", which);
            report_status(g_last_pkg, buf);
        }
    } else {
        report_status(g_last_pkg, "nv21_cb_reuse");
    }
    env->DeleteLocalRef(cbCls);
}

extern "C" void Hooked_setPreviewCallback(JNIEnv *env, jobject thiz, jobject cb) {
    LOGI("ART hook: setPreviewCallback cb=%p", cb);
    if (cb) process_callback(env, thiz, cb, "cb");
    if (g_hook_setPreviewCallback.installed())
        g_hook_setPreviewCallback.invoke<void>(env, thiz, cb);
}

extern "C" void Hooked_setPreviewCallbackWithBuffer(JNIEnv *env, jobject thiz, jobject cb) {
    LOGI("ART hook: setPreviewCallbackWithBuffer cb=%p", cb);
    if (cb) process_callback(env, thiz, cb, "buf");
    if (g_hook_setPreviewCallbackWithBuffer.installed())
        g_hook_setPreviewCallbackWithBuffer.invoke<void>(env, thiz, cb);
}

extern "C" void Hooked_setOneShotPreviewCallback(JNIEnv *env, jobject thiz, jobject cb) {
    LOGI("ART hook: setOneShotPreviewCallback cb=%p", cb);
    if (cb) process_callback(env, thiz, cb, "oneshot");
    if (g_hook_setOneShotPreviewCallback.installed())
        g_hook_setOneShotPreviewCallback.invoke<void>(env, thiz, cb);
}

int install_preview_callback_hooks(JNIEnv *env) {
    int n = 0;
    auto st = g_hook_setPreviewCallback.install(env, "android/hardware/Camera", "setPreviewCallback",
        "(Landroid/hardware/Camera$PreviewCallback;)V",
        reinterpret_cast<void*>(&Hooked_setPreviewCallback));
    if (st == arthook::Status::kOk) { n++; LOGI("  ART hooked: setPreviewCallback"); }
    else LOGW("  setPreviewCallback: %s", arthook::StatusToString(st));

    st = g_hook_setPreviewCallbackWithBuffer.install(env, "android/hardware/Camera",
        "setPreviewCallbackWithBuffer",
        "(Landroid/hardware/Camera$PreviewCallback;)V",
        reinterpret_cast<void*>(&Hooked_setPreviewCallbackWithBuffer));
    if (st == arthook::Status::kOk) { n++; LOGI("  ART hooked: setPreviewCallbackWithBuffer"); }
    else LOGW("  setPreviewCallbackWithBuffer: %s", arthook::StatusToString(st));

    st = g_hook_setOneShotPreviewCallback.install(env, "android/hardware/Camera",
        "setOneShotPreviewCallback",
        "(Landroid/hardware/Camera$PreviewCallback;)V",
        reinterpret_cast<void*>(&Hooked_setOneShotPreviewCallback));
    if (st == arthook::Status::kOk) { n++; LOGI("  ART hooked: setOneShotPreviewCallback"); }
    else LOGW("  setOneShotPreviewCallback: %s", arthook::StatusToString(st));
    return n;
}
