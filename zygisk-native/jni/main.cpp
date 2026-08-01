/*
 * VirtualCam Manager — Zygisk native module v2.0.3-probe1
 * Pure Magisk (NO LSPosed). Native OpenGL/EGL interception pivot.
 *
 * v1.x: ArtHook Java Camera1 path (legacy, retained in history only)
 * v2.0: ShadowHook + glBindTexture (GL_TEXTURE_EXTERNAL_OES) + AMediaCodec
 * v2.0.3: probe+doctor + Phase 2.1 AHardwareBuffer + EGLImage OES path with 2D fallback
 *
 * Status is honest: report hook/decoder states; do not claim camera spoof
 * until real apps show the virtual feed on device.
 */
#include <cstdlib>
#include <cstring>
#include <unistd.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <android/log.h>
#include <cstdio>

#include "zygisk.hpp"
#include "gl_hooks.h"

using zygisk::Api;
using zygisk::AppSpecializeArgs;
using zygisk::ServerSpecializeArgs;

#define LOG_TAG "VirtualCamZygisk"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

static constexpr const char *kEnabledPath   = "/data/adb/virtualcam/enabled";
static constexpr const char *kCamera1Video  = "/storage/emulated/0/DCIM/Camera1/virtual.mp4";
static constexpr const char *kDisableFlag   = "/storage/emulated/0/DCIM/Camera1/disable.jpg";
static constexpr const char *kHookStatus    = "/data/adb/virtualcam/hook_status";
static constexpr const char *kLastPkg       = "/data/adb/virtualcam/last_hook_pkg";
static constexpr const char *kModuleVersion = "/data/adb/virtualcam/module_version";
static constexpr const char *kVersionFile   = "/data/adb/virtualcam/version";

static bool file_exists(const char *path) {
    struct stat st{};
    return path && stat(path, &st) == 0 && S_ISREG(st.st_mode) && st.st_size > 0;
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
    if (text) write(fd, text, strlen(text));
    close(fd);
}

static void report_status(const char *pkg, const char *status) {
    write_text(kHookStatus, status);
    write_text(kLastPkg, pkg ? pkg : "");
}

static void write_module_version() {
    write_text(kModuleVersion, "2.0.3-probe1");
    write_text(kVersionFile, "2.0.3-probe1");
}

class VirtualCamModule : public zygisk::ModuleBase {
public:
    void onLoad(Api *api, JNIEnv *env) override {
        this->api = api;
        this->env = env;
    }

    void preAppSpecialize(AppSpecializeArgs *args) override {
        write_module_version();

        const char *pkg = nullptr;
        if (args && args->nice_name) {
            pkg = env->GetStringUTFChars(args->nice_name, nullptr);
        }

        bool enabled = read_enabled();
        bool disabled = file_exists(kDisableFlag);
        bool has_video = file_exists(kCamera1Video) ||
                         file_exists("/data/adb/virtualcam/virtual.mp4");
        bool should = enabled && !disabled && has_video;

        LOGI("preAppSpecialize pkg=%s enabled=%d disable=%d video=%d hook=%d",
             pkg ? pkg : "?", enabled ? 1 : 0, disabled ? 1 : 0,
             has_video ? 1 : 0, should ? 1 : 0);

        if (pkg) {
            strncpy(process, pkg, sizeof(process) - 1);
            env->ReleaseStringUTFChars(args->nice_name, pkg);
        }

        if (!should) {
            if (!enabled) report_status(process, "gate_off");
            else if (disabled) report_status(process, "gate_off");
            else report_status(process, "no_video");
            api->setOption(zygisk::Option::DLCLOSE_MODULE_LIBRARY);
            return;
        }

        should_hook = true;
        vcam::set_enabled(true);
        vcam::set_video_path(kCamera1Video);
    }

    void postAppSpecialize(const AppSpecializeArgs *args) override {
        (void)args;
        if (!should_hook) return;

        report_status(process, "gl_installing");
        bool ok = vcam::install_gl_hooks();
        if (!ok) {
            LOGW("GL hooks not fully installed — decoder still starts for status");
        }
        vcam::start_decoder_if_needed();
        report_status(process, ok ? "gl_ready" : "gl_partial");
    }

    void preServerSpecialize(ServerSpecializeArgs *args) override {
        (void)args;
        api->setOption(zygisk::Option::DLCLOSE_MODULE_LIBRARY);
    }

private:
    Api *api = nullptr;
    JNIEnv *env = nullptr;
    bool should_hook = false;
    char process[256]{};
};

REGISTER_ZYGISK_MODULE(VirtualCamModule)
