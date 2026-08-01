/*
 * VirtualCam Manager — Zygisk native module v1.15.0
 * Pure Magisk (NO LSPosed). Companion gate + JNI Camera hooks + ArtHook ART hooks.
 *
 * v1.13.0: ArtHook setPreviewTexture / startPreview / setPreviewDisplay + MediaPlayer
 * v1.15.0: ArtHook setPreviewCallback* + onPreviewFrame NV21 (see nv21_hooks.cpp)
 */
#include <cstdlib>
#include <cstring>
#include <unistd.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <android/log.h>
#include <string>
#include <cstdio>
#include <vector>

#include "zygisk.hpp"
#include <arthook/ArtHook.h>
#include <arthook/Hooked.h>
#include "nv21_hooks.h"

using zygisk::Api;
using zygisk::AppSpecializeArgs;
using zygisk::ServerSpecializeArgs;

#define LOG_TAG "VirtualCamZygisk"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

static constexpr const char *kEnabledPath   = "/data/adb/virtualcam/enabled";
static constexpr const char *kCamera1Video  = "/storage/emulated/0/DCIM/Camera1/virtual.mp4";
static constexpr const char *kDisableFlag   = "/storage/emulated/0/DCIM/Camera1/disable.jpg";
static constexpr const char *kHookStatus    = "/data/adb/virtualcam/hook_status";
static constexpr const char *kLastPkg       = "/data/adb/virtualcam/last_hook_pkg";
static constexpr const char *kModuleVersion = "/data/adb/virtualcam/module_version";
static constexpr const char *kVersionFile   = "/data/adb/virtualcam/version";

struct VcamState {
    bool global_enabled = false;
    bool disable_flag   = false;
    bool has_video      = false;
    bool should_hook    = false;
    char process[256]{};
};

static bool file_exists(const char *path) {
    struct stat st{};
    return stat(path, &st) == 0 && S_ISREG(st.st_mode);
}

static bool read_enabled() {
    int fd = open(kEnabledPath, O_RDONLY | O_CLOEXEC);
    if (fd < 0) return false;
    char buf[8]{};
    ssize_t n = read(fd, buf, sizeof(buf) - 1);
    close(fd);
    return n > 0 && buf[0] == '1';
}

static void write_text(const char *path, const char *text) {
    int fd = open(path, O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0644);
    if (fd < 0) return;
    write(fd, text, strlen(text));
    close(fd);
}

static void report_status(const char *pkg, const char *status) {
    write_text(kHookStatus, status);
    write_text(kLastPkg, pkg);
}

static void write_module_version() {
    write_text(kModuleVersion, "1.15.0");
    write_text(kVersionFile, "1.15.0");
}

class VideoFrameProvider {
public:
    bool open(const char *path) {
        path_ = path;
        if (!file_exists(path)) {
            LOGE("video missing: %s", path);
            opened_ = false;
            return false;
        }
        struct stat st{};
        if (stat(path, &st) == 0 && st.st_size > 0) {
            size_ = st.st_size;
            LOGI("video ready path=%s size=%lld", path, (long long)size_);
            opened_ = true;
            return true;
        }
        opened_ = false;
        return false;
    }
    bool isOpen() const { return opened_; }
    const char *path() const { return path_.c_str(); }
    off_t size() const { return size_; }

    void setOriginalTexture(JNIEnv *env, jobject st) {
        if (orig_st_) { env->DeleteGlobalRef(orig_st_); orig_st_ = nullptr; }
        if (st) { orig_st_ = env->NewGlobalRef(st); LOGI("stored original SurfaceTexture"); }
    }
    jobject originalTexture() const { return orig_st_; }

    void setSurface(JNIEnv *env, jobject surface) {
        if (g_surface_) { env->DeleteGlobalRef(g_surface_); g_surface_ = nullptr; }
        if (surface) { g_surface_ = env->NewGlobalRef(surface); LOGI("stored preview Surface"); }
    }
    jobject surface() const { return g_surface_; }

    bool tryStartMediaPlayer(JNIEnv *env) {
        if (!opened_ || !env) return false;
        if (playing_) return true;

        jobject target_surface = g_surface_;
        jobject local_surface = nullptr;

        if (!target_surface && orig_st_) {
            jclass surfCls = env->FindClass("android/view/Surface");
            if (surfCls) {
                jmethodID ctor = env->GetMethodID(surfCls, "<init>", "(Landroid/graphics/SurfaceTexture;)V");
                if (ctor) {
                    local_surface = env->NewObject(surfCls, ctor, orig_st_);
                    if (local_surface && !env->ExceptionCheck()) {
                        target_surface = local_surface;
                        LOGI("created Surface from original SurfaceTexture");
                    } else {
                        env->ExceptionClear();
                        local_surface = nullptr;
                    }
                }
                env->DeleteLocalRef(surfCls);
            }
        }
        if (!target_surface) {
            LOGE("no Surface/SurfaceTexture for MediaPlayer");
            return false;
        }

        jclass mpCls = env->FindClass("android/media/MediaPlayer");
        if (!mpCls) {
            env->ExceptionClear();
            LOGE("MediaPlayer class not found");
            if (local_surface) env->DeleteLocalRef(local_surface);
            return false;
        }
        jmethodID ctor = env->GetMethodID(mpCls, "<init>", "()V");
        jmethodID setDataSource = env->GetMethodID(mpCls, "setDataSource", "(Ljava/lang/String;)V");
        jmethodID setSurface = env->GetMethodID(mpCls, "setSurface", "(Landroid/view/Surface;)V");
        jmethodID setLooping = env->GetMethodID(mpCls, "setLooping", "(Z)V");
        jmethodID setVolume = env->GetMethodID(mpCls, "setVolume", "(FF)V");
        jmethodID prepare = env->GetMethodID(mpCls, "prepare", "()V");
        jmethodID start = env->GetMethodID(mpCls, "start", "()V");
        if (!ctor || !setDataSource || !setSurface || !setLooping || !prepare || !start) {
            env->ExceptionClear();
            LOGE("MediaPlayer method lookup failed");
            env->DeleteLocalRef(mpCls);
            if (local_surface) env->DeleteLocalRef(local_surface);
            return false;
        }
        jobject mp = env->NewObject(mpCls, ctor);
        if (!mp || env->ExceptionCheck()) {
            env->ExceptionClear();
            LOGE("MediaPlayer construct failed");
            env->DeleteLocalRef(mpCls);
            if (local_surface) env->DeleteLocalRef(local_surface);
            return false;
        }
        jstring jpath = env->NewStringUTF(path_.c_str());
        env->CallVoidMethod(mp, setDataSource, jpath);
        env->DeleteLocalRef(jpath);
        if (env->ExceptionCheck()) {
            env->ExceptionClear(); LOGE("setDataSource failed");
            env->DeleteLocalRef(mp); env->DeleteLocalRef(mpCls);
            if (local_surface) env->DeleteLocalRef(local_surface);
            return false;
        }
        env->CallVoidMethod(mp, setSurface, target_surface);
        if (env->ExceptionCheck()) {
            env->ExceptionClear(); LOGE("setSurface failed");
            env->DeleteLocalRef(mp); env->DeleteLocalRef(mpCls);
            if (local_surface) env->DeleteLocalRef(local_surface);
            return false;
        }
        env->CallVoidMethod(mp, setLooping, JNI_TRUE);
        if (setVolume) env->CallVoidMethod(mp, setVolume, 0.0f, 0.0f);
        env->CallVoidMethod(mp, prepare);
        if (env->ExceptionCheck()) {
            env->ExceptionClear(); LOGE("MediaPlayer.prepare failed");
            env->DeleteLocalRef(mp); env->DeleteLocalRef(mpCls);
            if (local_surface) env->DeleteLocalRef(local_surface);
            return false;
        }
        env->CallVoidMethod(mp, start);
        if (env->ExceptionCheck()) {
            env->ExceptionClear(); LOGE("MediaPlayer.start failed");
            env->DeleteLocalRef(mp); env->DeleteLocalRef(mpCls);
            if (local_surface) env->DeleteLocalRef(local_surface);
            return false;
        }
        if (g_player_) env->DeleteGlobalRef(g_player_);
        g_player_ = env->NewGlobalRef(mp);
        playing_ = true;
        LOGI("MediaPlayer started — virtual.mp4 playing on preview surface");
        env->DeleteLocalRef(mp);
        env->DeleteLocalRef(mpCls);
        if (local_surface) env->DeleteLocalRef(local_surface);
        return true;
    }

    void stop(JNIEnv *env) {
        if (g_player_ && env) {
            jclass mpCls = env->FindClass("android/media/MediaPlayer");
            if (mpCls) {
                jmethodID stopM = env->GetMethodID(mpCls, "stop", "()V");
                jmethodID release = env->GetMethodID(mpCls, "release", "()V");
                if (stopM) env->CallVoidMethod(g_player_, stopM);
                if (release) env->CallVoidMethod(g_player_, release);
                env->ExceptionClear();
                env->DeleteLocalRef(mpCls);
            }
            env->DeleteGlobalRef(g_player_);
            g_player_ = nullptr;
        }
        playing_ = false;
    }
    bool isPlaying() const { return playing_; }

private:
    std::string path_;
    off_t size_ = 0;
    bool opened_ = false;
    bool playing_ = false;
    jobject orig_st_ = nullptr;
    jobject g_surface_ = nullptr;
    jobject g_player_ = nullptr;
};

static VideoFrameProvider g_video;
static Api *g_api = nullptr;
char g_last_pkg[256] = {};

static arthook::Hooked g_hook_setPreviewTexture;
static arthook::Hooked g_hook_startPreview;
static arthook::Hooked g_hook_setPreviewDisplay_java;

extern "C" void Hooked_setPreviewTexture(JNIEnv *env, jobject thiz, jobject st) {
    LOGI("ART hook: setPreviewTexture st=%p video=%d", st, g_video.isOpen() ? 1 : 0);
    if (g_video.isOpen() && st) {
        jclass stCls = env->FindClass("android/graphics/SurfaceTexture");
        if (stCls) {
            jmethodID ctor = env->GetMethodID(stCls, "<init>", "(I)V");
            if (ctor) {
                jobject fake = env->NewObject(stCls, ctor, 10);
                if (fake && !env->ExceptionCheck()) {
                    g_video.setOriginalTexture(env, st);
                    if (g_hook_setPreviewTexture.installed())
                        g_hook_setPreviewTexture.invoke<void>(env, thiz, fake);
                    env->DeleteLocalRef(fake);
                    env->DeleteLocalRef(stCls);
                    report_status(g_last_pkg, "texture_swapped");
                    return;
                }
                env->ExceptionClear();
            }
            env->DeleteLocalRef(stCls);
        }
    }
    if (g_hook_setPreviewTexture.installed())
        g_hook_setPreviewTexture.invoke<void>(env, thiz, st);
}

extern "C" void Hooked_startPreview(JNIEnv *env, jobject thiz) {
    LOGI("ART hook: startPreview video=%s", g_video.isOpen() ? g_video.path() : "none");
    if (g_video.isOpen()) {
        if (g_video.tryStartMediaPlayer(env))
            report_status(g_last_pkg, "surface_playing");
        else
            report_status(g_last_pkg, "inject_attempt_failed");
    }
    if (g_hook_startPreview.installed())
        g_hook_startPreview.invoke<void>(env, thiz);
}

extern "C" void Hooked_setPreviewDisplay_java(JNIEnv *env, jobject thiz, jobject holder) {
    LOGI("ART hook: setPreviewDisplay holder=%p", holder);
    if (holder) {
        jclass shCls = env->GetObjectClass(holder);
        jmethodID getSurface = env->GetMethodID(shCls, "getSurface", "()Landroid/view/Surface;");
        if (getSurface) {
            jobject surf = env->CallObjectMethod(holder, getSurface);
            if (surf && !env->ExceptionCheck()) {
                g_video.setSurface(env, surf);
                env->DeleteLocalRef(surf);
            } else env->ExceptionClear();
        }
        env->DeleteLocalRef(shCls);
    }
    if (g_hook_setPreviewDisplay_java.installed())
        g_hook_setPreviewDisplay_java.invoke<void>(env, thiz, holder);
}

static int install_art_hooks(JNIEnv *env, const char *pkg) {
    auto st = arthook::Initialize(env, false);
    if (st != arthook::Status::kOk) {
        LOGE("ArtHook Initialize failed: %s", arthook::StatusToString(st));
        report_status(pkg, "arthook_init_fail");
        return 0;
    }
    LOGI("ArtHook initialized");
    int n = 0;
    st = g_hook_setPreviewTexture.install(env, "android/hardware/Camera", "setPreviewTexture",
        "(Landroid/graphics/SurfaceTexture;)V", reinterpret_cast<void*>(&Hooked_setPreviewTexture));
    if (st == arthook::Status::kOk) { n++; LOGI("  ART hooked: setPreviewTexture"); }
    else LOGW("  setPreviewTexture: %s", arthook::StatusToString(st));

    st = g_hook_startPreview.install(env, "android/hardware/Camera", "startPreview", "()V",
        reinterpret_cast<void*>(&Hooked_startPreview));
    if (st == arthook::Status::kOk) { n++; LOGI("  ART hooked: startPreview"); }
    else LOGW("  startPreview: %s", arthook::StatusToString(st));

    st = g_hook_setPreviewDisplay_java.install(env, "android/hardware/Camera", "setPreviewDisplay",
        "(Landroid/view/SurfaceHolder;)V", reinterpret_cast<void*>(&Hooked_setPreviewDisplay_java));
    if (st == arthook::Status::kOk) { n++; LOGI("  ART hooked: setPreviewDisplay"); }
    else LOGW("  setPreviewDisplay: %s", arthook::StatusToString(st));

    n += install_preview_callback_hooks(env);

    char buf[64];
    if (n > 0) snprintf(buf, sizeof(buf), "art_hooked:%d", n);
    else snprintf(buf, sizeof(buf), "art_hook_none");
    report_status(pkg, buf);
    LOGI("ArtHook install count: %d", n);
    return n;
}

static void (*orig_native_setup)(JNIEnv*, jobject, jobject) = nullptr;
static void (*orig_native_startPreview)(JNIEnv*, jobject) = nullptr;
static void (*orig_native_stopPreview)(JNIEnv*, jobject) = nullptr;
static void (*orig_native_setPreviewDisplay)(JNIEnv*, jobject, jobject) = nullptr;
static void (*orig_native_setPreviewCallback)(JNIEnv*, jobject, jobject) = nullptr;
static void (*orig_native_takePicture)(JNIEnv*, jobject, jobject, jobject, jobject, jobject) = nullptr;

static void hooked_native_setup(JNIEnv *env, jobject thiz, jobject weak) {
    if (orig_native_setup) orig_native_setup(env, thiz, weak);
}
static void hooked_native_startPreview(JNIEnv *env, jobject thiz) {
    if (orig_native_startPreview) orig_native_startPreview(env, thiz);
}
static void hooked_native_stopPreview(JNIEnv *env, jobject thiz) {
    g_video.stop(env);
    if (orig_native_stopPreview) orig_native_stopPreview(env, thiz);
}
static void hooked_native_setPreviewDisplay(JNIEnv *env, jobject thiz, jobject surface) {
    g_video.setSurface(env, surface);
    if (orig_native_setPreviewDisplay) orig_native_setPreviewDisplay(env, thiz, surface);
}
static void hooked_native_setPreviewCallback(JNIEnv *env, jobject thiz, jobject cb) {
    if (cb) process_callback(env, thiz, cb, "jni");
    if (orig_native_setPreviewCallback) orig_native_setPreviewCallback(env, thiz, cb);
}
static void hooked_native_takePicture(JNIEnv *env, jobject thiz, jobject a, jobject b, jobject c, jobject d) {
    if (orig_native_takePicture) orig_native_takePicture(env, thiz, a, b, c, d);
}

static int install_jni_hooks(JNIEnv *env) {
    if (!g_api || !env) return 0;
    JNINativeMethod methods[] = {
        {"native_setup", "(Ljava/lang/Object;)V", (void*)hooked_native_setup},
        {"native_startPreview", "()V", (void*)hooked_native_startPreview},
        {"native_stopPreview", "()V", (void*)hooked_native_stopPreview},
        {"setPreviewDisplay", "(Landroid/view/Surface;)V", (void*)hooked_native_setPreviewDisplay},
        {"setPreviewCallback", "(Landroid/hardware/Camera$PreviewCallback;)V", (void*)hooked_native_setPreviewCallback},
        {"native_takePicture",
         "(Landroid/hardware/Camera$ShutterCallback;Landroid/hardware/Camera$PictureCallback;"
         "Landroid/hardware/Camera$PictureCallback;Landroid/hardware/Camera$PictureCallback;)V",
         (void*)hooked_native_takePicture},
    };
    g_api->hookJniNativeMethods(env, "android/hardware/Camera", methods, 6);
    int hooked = 0;
    if (methods[0].fnPtr) { orig_native_setup = (decltype(orig_native_setup))methods[0].fnPtr; hooked++; }
    if (methods[1].fnPtr) { orig_native_startPreview = (decltype(orig_native_startPreview))methods[1].fnPtr; hooked++; }
    if (methods[2].fnPtr) { orig_native_stopPreview = (decltype(orig_native_stopPreview))methods[2].fnPtr; hooked++; }
    if (methods[3].fnPtr) { orig_native_setPreviewDisplay = (decltype(orig_native_setPreviewDisplay))methods[3].fnPtr; hooked++; }
    if (methods[4].fnPtr) { orig_native_setPreviewCallback = (decltype(orig_native_setPreviewCallback))methods[4].fnPtr; hooked++; }
    if (methods[5].fnPtr) { orig_native_takePicture = (decltype(orig_native_takePicture))methods[5].fnPtr; hooked++; }
    LOGI("JNI Camera hooks installed: %d / 6", hooked);
    return hooked;
}

static int prepare_method_scaffold(JNIEnv *env, const char *pkg) {
    if (!env) { report_status(pkg, "preparing"); return 0; }
    jclass camera_cls = env->FindClass("android/hardware/Camera");
    if (!camera_cls) {
        env->ExceptionClear();
        report_status(pkg, "preparing:no_camera_cls");
        return 0;
    }
    struct Target { const char *name; const char *sig; };
    static const Target kTargets[] = {
        {"setPreviewTexture", "(Landroid/graphics/SurfaceTexture;)V"},
        {"setPreviewCallback", "(Landroid/hardware/Camera$PreviewCallback;)V"},
        {"setPreviewCallbackWithBuffer", "(Landroid/hardware/Camera$PreviewCallback;)V"},
        {"setOneShotPreviewCallback", "(Landroid/hardware/Camera$PreviewCallback;)V"},
        {"startPreview", "()V"},
        {"setPreviewDisplay", "(Landroid/view/SurfaceHolder;)V"},
    };
    int found = 0;
    for (int i = 0; i < 6; i++) {
        jmethodID mid = env->GetMethodID(camera_cls, kTargets[i].name, kTargets[i].sig);
        if (mid) { found++; LOGI("  method OK: %s", kTargets[i].name); }
        else env->ExceptionClear();
    }
    env->DeleteLocalRef(camera_cls);
    char status_buf[64];
    if (found >= 4) snprintf(status_buf, sizeof(status_buf), "ready_for_arthook:%d/6", found);
    else if (found > 0) snprintf(status_buf, sizeof(status_buf), "preparing:%d/6", found);
    else snprintf(status_buf, sizeof(status_buf), "preparing:0/6");
    report_status(pkg, status_buf);
    return found;
}

class VirtualCamModule : public zygisk::ModuleBase {
public:
    void onLoad(Api *api, JNIEnv *env) override {
        this->api = api; this->env = env; g_api = api;
    }
    void preAppSpecialize(AppSpecializeArgs *args) override {
        const char *process = env->GetStringUTFChars(args->nice_name, nullptr);
        if (process) {
            strncpy(state.process, process, sizeof(state.process) - 1);
            strncpy(g_last_pkg, process, sizeof(g_last_pkg) - 1);
            env->ReleaseStringUTFChars(args->nice_name, process);
        }
        int fd = api->connectCompanion();
        if (fd >= 0) {
            uint8_t reply[4] = {};
            if (read(fd, reply, 3) == 3) {
                state.global_enabled = reply[0] == 1;
                state.disable_flag = reply[1] == 1;
                state.has_video = reply[2] == 1;
                state.should_hook = state.global_enabled && !state.disable_flag && state.has_video;
            }
            close(fd);
        } else {
            state.global_enabled = read_enabled();
            state.disable_flag = file_exists(kDisableFlag);
            state.has_video = file_exists(kCamera1Video);
            state.should_hook = state.global_enabled && !state.disable_flag && state.has_video;
        }
        LOGI("preAppSpecialize pkg=%s enabled=%d disable=%d video=%d hook=%d",
             state.process, state.global_enabled?1:0, state.disable_flag?1:0,
             state.has_video?1:0, state.should_hook?1:0);
        if (!state.should_hook) {
            if (!state.global_enabled || state.disable_flag)
                report_status(state.process, "gate_off");
            api->setOption(zygisk::Option::DLCLOSE_MODULE_LIBRARY);
        }
    }
    void postAppSpecialize(const AppSpecializeArgs *args) override {
        if (!state.should_hook) return;
        write_module_version();
        LOGI("postAppSpecialize: VirtualCam active for %s", state.process);
        if (!g_video.open(kCamera1Video)) {
            report_status(state.process, "no_video");
            return;
        }
        int methods_found = prepare_method_scaffold(env, state.process);
        int art_n = install_art_hooks(env, state.process);
        int jni_n = install_jni_hooks(env);
        char buf[80];
        if (art_n > 0) {
            snprintf(buf, sizeof(buf), "art_hooked:%d+jni:%d", art_n, jni_n);
            report_status(state.process, buf);
        } else if (jni_n > 0 && methods_found >= 4) {
            snprintf(buf, sizeof(buf), "hooked+ready:%d", jni_n);
            report_status(state.process, buf);
        } else if (jni_n > 0) {
            report_status(state.process, "hooked");
        }
        LOGI("status art=%d jni=%d methods=%d pkg=%s", art_n, jni_n, methods_found, state.process);
    }
    void preServerSpecialize(ServerSpecializeArgs *args) override {
        api->setOption(zygisk::Option::DLCLOSE_MODULE_LIBRARY);
    }
private:
    Api *api = nullptr;
    JNIEnv *env = nullptr;
    VcamState state{};
};

static void companion_handler(int fd) {
    uint8_t reply[3];
    reply[0] = read_enabled() ? 1 : 0;
    reply[1] = file_exists(kDisableFlag) ? 1 : 0;
    reply[2] = file_exists(kCamera1Video) ? 1 : 0;
    write(fd, reply, 3);
    LOGD("companion enabled=%u disable=%u video=%u", reply[0], reply[1], reply[2]);
}

REGISTER_ZYGISK_MODULE(VirtualCamModule)
REGISTER_ZYGISK_COMPANION(companion_handler)
