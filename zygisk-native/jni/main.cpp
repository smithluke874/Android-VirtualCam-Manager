/*
 * VirtualCam Manager — Zygisk native module v1.8.0
 * Pure Magisk (NO LSPosed manager).
 *
 * v1.8.0:
 *  - Companion control-plane gate (enabled + video + !disable)
 *  - VideoFrameProvider validates virtual.mp4
 *  - hookJniNativeMethods on android.hardware.Camera native methods
 *  - Reports hooked / active / no_video / gate_off to APK via hook_status
 *  - Scaffolding notes for LSPlant ART Java hooks (next milestone)
 *
 * Still needed for full visual spoof on all apps:
 *  - LSPlant ART hooks for Java setPreviewTexture / PreviewCallback
 *  - MediaPlayer surface + NV21 overwrite (docs/ORIGINAL_HOOK_ALGORITHM.md)
 *
 * LSPlant plan (pure Magisk, no LSPosed manager):
 *  1. Add lsplant-standalone headers + static library (or build in CI)
 *  2. Provide InitInfo { inline_hooker, inline_unhooker, art_symbol_resolver }
 *  3. Call lsplant::Init(env, info) in postAppSpecialize after gate opens
 *  4. Hook Java methods via reflection Method objects + callback
 *  5. Keep current JNI hooks as secondary / logging layer
 *  6. Write status "injecting" once frames are actually replaced
 */

#include <cstdlib>
#include <cstring>
#include <unistd.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <android/log.h>
#include <string>
#include <vector>

#include "zygisk.hpp"

using zygisk::Api;
using zygisk::AppSpecializeArgs;
using zygisk::ServerSpecializeArgs;

#define LOG_TAG "VirtualCamZygisk"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static constexpr const char *kEnabledPath  = "/data/adb/virtualcam/enabled";
static constexpr const char *kCamera1Video = "/storage/emulated/0/DCIM/Camera1/virtual.mp4";
static constexpr const char *kDisableFlag  = "/storage/emulated/0/DCIM/Camera1/disable.jpg";
static constexpr const char *kHookStatus   = "/data/adb/virtualcam/hook_status";
static constexpr const char *kLastPkg      = "/data/adb/virtualcam/last_hook_pkg";

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

class VideoFrameProvider {
public:
    bool open(const char *path) {
        path_ = path;
        if (!file_exists(path)) {
            LOGE("video missing: %s", path);
            return false;
        }
        struct stat st{};
        if (stat(path, &st) == 0 && st.st_size > 0) {
            size_ = st.st_size;
            LOGI("video ready path=%s size=%lld", path, (long long)size_);
            opened_ = true;
            return true;
        }
        return false;
    }
    bool isOpen() const { return opened_; }
    const char *path() const { return path_.c_str(); }
    off_t size() const { return size_; }
private:
    std::string path_;
    off_t size_ = 0;
    bool opened_ = false;
};

static VideoFrameProvider g_video;
static Api *g_api = nullptr;

/* Original native method pointers (filled by hookJniNativeMethods) */
static void (*orig_native_setup)(JNIEnv *, jobject, jobject) = nullptr;
static void (*orig_native_startPreview)(JNIEnv *, jobject) = nullptr;
static void (*orig_native_stopPreview)(JNIEnv *, jobject) = nullptr;
static void (*orig_native_setPreviewDisplay)(JNIEnv *, jobject, jobject) = nullptr;
static void (*orig_native_setPreviewCallback)(JNIEnv *, jobject, jobject) = nullptr;
static void (*orig_native_takePicture)(JNIEnv *, jobject, jobject, jobject, jobject, jobject) = nullptr;

static void hooked_native_setup(JNIEnv *env, jobject thiz, jobject weak) {
    LOGI("hook: native_setup (Camera open path)");
    if (orig_native_setup) orig_native_setup(env, thiz, weak);
}

static void hooked_native_startPreview(JNIEnv *env, jobject thiz) {
    LOGI("hook: native_startPreview — video=%s", g_video.isOpen() ? g_video.path() : "none");
    if (orig_native_startPreview) orig_native_startPreview(env, thiz);
}

static void hooked_native_stopPreview(JNIEnv *env, jobject thiz) {
    LOGI("hook: native_stopPreview");
    if (orig_native_stopPreview) orig_native_stopPreview(env, thiz);
}

static void hooked_native_setPreviewDisplay(JNIEnv *env, jobject thiz, jobject surface) {
    LOGI("hook: native_setPreviewDisplay surface=%p", surface);
    /* Full surface swap requires Java-level SurfaceTexture replacement (LSPlant). */
    if (orig_native_setPreviewDisplay) orig_native_setPreviewDisplay(env, thiz, surface);
}

static void hooked_native_setPreviewCallback(JNIEnv *env, jobject thiz, jobject cb) {
    LOGI("hook: native_setPreviewCallback cb=%p", cb);
    if (orig_native_setPreviewCallback) orig_native_setPreviewCallback(env, thiz, cb);
}

static void hooked_native_takePicture(JNIEnv *env, jobject thiz,
                                      jobject sh, jobject raw, jobject post, jobject jpeg) {
    LOGI("hook: native_takePicture");
    if (orig_native_takePicture) orig_native_takePicture(env, thiz, sh, raw, post, jpeg);
}

static int install_jni_hooks(JNIEnv *env) {
    if (!g_api || !env) return 0;

    /*
     * Zygisk can rewrite the JNINativeMethod table for classes that expose
     * native methods. Method names/signatures vary by Android version; we
     * try the common Camera1 set and count successes.
     */
    JNINativeMethod methods[] = {
        {"native_setup",
         "(Ljava/lang/Object;)V",
         (void *) hooked_native_setup},
        {"native_startPreview",
         "()V",
         (void *) hooked_native_startPreview},
        {"native_stopPreview",
         "()V",
         (void *) hooked_native_stopPreview},
        {"native_setPreviewDisplay",
         "(Landroid/view/Surface;)V",
         (void *) hooked_native_setPreviewDisplay},
        {"setPreviewCallback",
         "(Landroid/hardware/Camera$PreviewCallback;)V",
         (void *) hooked_native_setPreviewCallback},
        {"native_takePicture",
         "(Landroid/hardware/Camera$ShutterCallback;"
         "Landroid/hardware/Camera$PictureCallback;"
         "Landroid/hardware/Camera$PictureCallback;"
         "Landroid/hardware/Camera$PictureCallback;)V",
         (void *) hooked_native_takePicture},
    };

    /* Save originals: hookJniNativeMethods replaces fnPtr with original */
    g_api->hookJniNativeMethods(env, "android/hardware/Camera",
                                methods, sizeof(methods) / sizeof(methods[0]));

    int hooked = 0;
    if (methods[0].fnPtr) { orig_native_setup = (decltype(orig_native_setup)) methods[0].fnPtr; hooked++; }
    if (methods[1].fnPtr) { orig_native_startPreview = (decltype(orig_native_startPreview)) methods[1].fnPtr; hooked++; }
    if (methods[2].fnPtr) { orig_native_stopPreview = (decltype(orig_native_stopPreview)) methods[2].fnPtr; hooked++; }
    if (methods[3].fnPtr) { orig_native_setPreviewDisplay = (decltype(orig_native_setPreviewDisplay)) methods[3].fnPtr; hooked++; }
    if (methods[4].fnPtr) { orig_native_setPreviewCallback = (decltype(orig_native_setPreviewCallback)) methods[4].fnPtr; hooked++; }
    if (methods[5].fnPtr) { orig_native_takePicture = (decltype(orig_native_takePicture)) methods[5].fnPtr; hooked++; }

    LOGI("JNI Camera hooks installed: %d / %zu", hooked, sizeof(methods) / sizeof(methods[0]));
    return hooked;
}

class VirtualCamModule : public zygisk::ModuleBase {
public:
    void onLoad(Api *api, JNIEnv *env) override {
        this->api = api;
        this->env = env;
        g_api = api;
    }

    void preAppSpecialize(AppSpecializeArgs *args) override {
        const char *process = env->GetStringUTFChars(args->nice_name, nullptr);
        if (process) {
            strncpy(state.process, process, sizeof(state.process) - 1);
            env->ReleaseStringUTFChars(args->nice_name, process);
        }

        int fd = api->connectCompanion();
        if (fd >= 0) {
            uint8_t reply[4] = {};
            if (read(fd, reply, 3) == 3) {
                state.global_enabled = reply[0] == 1;
                state.disable_flag   = reply[1] == 1;
                state.has_video      = reply[2] == 1;
                state.should_hook = state.global_enabled && !state.disable_flag && state.has_video;
            }
            close(fd);
        } else {
            state.global_enabled = read_enabled();
            state.disable_flag   = file_exists(kDisableFlag);
            state.has_video      = file_exists(kCamera1Video);
            state.should_hook = state.global_enabled && !state.disable_flag && state.has_video;
        }

        LOGI("preAppSpecialize pkg=%s enabled=%d disable=%d video=%d hook=%d",
             state.process,
             state.global_enabled ? 1 : 0,
             state.disable_flag ? 1 : 0,
             state.has_video ? 1 : 0,
             state.should_hook ? 1 : 0);

        if (!state.should_hook) {
            /* Report gate_off so APK can show accurate state */
            if (!state.global_enabled || state.disable_flag) {
                report_status(state.process, "gate_off");
            }
            api->setOption(zygisk::Option::DLCLOSE_MODULE_LIBRARY);
        }
    }

    void postAppSpecialize(const AppSpecializeArgs *args) override {
        if (!state.should_hook) return;

        LOGI("postAppSpecialize: VirtualCam active for %s", state.process);

        if (!g_video.open(kCamera1Video)) {
            report_status(state.process, "no_video");
            return;
        }

        /*
         * Future: LSPlant Init + Java method hooks go here.
         * After successful LSPlant frame injection, report_status(..., "injecting");
         */

        int n = install_jni_hooks(env);
        if (n > 0) {
            report_status(state.process, "hooked");
            LOGI("status=hooked count=%d pkg=%s video=%s",
                 n, state.process, g_video.path());
        } else {
            /* Gate worked, video present, but no matching JNI natives on this ROM */
            report_status(state.process, "active");
            LOGI("status=active (no JNI natives matched — need LSPlant for Java hooks)");
        }
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
