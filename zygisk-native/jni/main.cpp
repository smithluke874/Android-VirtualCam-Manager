/*
 * VirtualCam Manager — Zygisk native module
 * Pure Magisk (NO LSPosed). Reads control plane written by the companion APK:
 *   /data/adb/virtualcam/enabled
 *   /data/adb/virtualcam/config
 *   /storage/emulated/0/DCIM/Camera1/virtual.mp4 + flag .jpg files
 *
 * Lifecycle:
 *  - Companion (root) serves config to app processes
 *  - preAppSpecialize: decide whether this process should activate VCAM
 *  - postAppSpecialize: install JNI / PLT hooks when active
 *
 * Frame injection hooks are stubbed with clear extension points matching
 * original android_virtual_cam (Camera1 PreviewCallback + Camera2 path).
 */

#include <cstdlib>
#include <cstring>
#include <unistd.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <android/log.h>
#include <string>
#include <fstream>
#include <sstream>

#include "zygisk.hpp"

using zygisk::Api;
using zygisk::AppSpecializeArgs;
using zygisk::ServerSpecializeArgs;

#define LOG_TAG "VirtualCamZygisk"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static constexpr const char *kControlDir = "/data/adb/virtualcam";
static constexpr const char *kEnabledPath = "/data/adb/virtualcam/enabled";
static constexpr const char *kCamera1Video = "/storage/emulated/0/DCIM/Camera1/virtual.mp4";
static constexpr const char *kDisableFlag = "/storage/emulated/0/DCIM/Camera1/disable.jpg";

struct VcamState {
    bool global_enabled = false;
    bool disable_flag = false;
    bool has_video = false;
    bool should_hook = false;
    char process[256]{};
};

static bool file_exists(const char *path) {
    struct stat st{};
    return stat(path, &st) == 0;
}

static bool read_enabled() {
    int fd = open(kEnabledPath, O_RDONLY | O_CLOEXEC);
    if (fd < 0) return false;
    char buf[8]{};
    ssize_t n = read(fd, buf, sizeof(buf) - 1);
    close(fd);
    return n > 0 && buf[0] == '1';
}

static void load_state(VcamState &s) {
    s.global_enabled = read_enabled();
    s.disable_flag = file_exists(kDisableFlag);
    s.has_video = file_exists(kCamera1Video);
    s.should_hook = s.global_enabled && !s.disable_flag && s.has_video;
}

class VirtualCamModule : public zygisk::ModuleBase {
public:
    void onLoad(Api *api, JNIEnv *env) override {
        this->api = api;
        this->env = env;
    }

    void preAppSpecialize(AppSpecializeArgs *args) override {
        const char *process = env->GetStringUTFChars(args->nice_name, nullptr);
        if (process) {
            strncpy(state.process, process, sizeof(state.process) - 1);
            env->ReleaseStringUTFChars(args->nice_name, process);
        }

        // Ask companion (root) for authoritative state — works even if
        // app sandbox cannot read /data/adb yet
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
            load_state(state);
        }

        LOGI("preAppSpecialize pkg=%s enabled=%d disable=%d video=%d hook=%d",
             state.process,
             state.global_enabled ? 1 : 0,
             state.disable_flag ? 1 : 0,
             state.has_video ? 1 : 0,
             state.should_hook ? 1 : 0);

        if (!state.should_hook) {
            // Not needed in this process — allow Zygisk to unload us
            api->setOption(zygisk::Option::DLCLOSE_MODULE_LIBRARY);
        }
        // else: stay loaded for postAppSpecialize hooks
    }

    void postAppSpecialize(const AppSpecializeArgs *args) override {
        if (!state.should_hook) return;

        LOGI("postAppSpecialize: activating VirtualCam for %s", state.process);

        /*
         * EXTENSION POINT — real frame injection
         *
         * Original Xposed VCAM hooked (Java):
         *   android.hardware.Camera.setPreviewCallback*
         *   android.hardware.Camera.takePicture
         *   android.graphics.SurfaceTexture
         *   android.hardware.camera2.CameraManager / CameraDevice / CaptureRequest
         *
         * With pure Zygisk we can:
         *   1) api->hookJniNativeMethods(...) for JNI-registered natives
         *   2) api->pltHookRegister(...) on libandroid_runtime / camera libs
         *   3) Decode virtual.mp4 (MediaCodec) and push YUV/RGB into callbacks
         *
         * For now we mark the process as VCAM-active so the control plane is
         * verified end-to-end; frame path lands in subsequent iterations.
         */
        install_hooks();
    }

    void preServerSpecialize(ServerSpecializeArgs *args) override {
        // Do not hook system_server
        api->setOption(zygisk::Option::DLCLOSE_MODULE_LIBRARY);
    }

private:
    Api *api = nullptr;
    JNIEnv *env = nullptr;
    VcamState state{};

    void install_hooks() {
        // Placeholder: register JNI native method hooks when symbols known.
        // Example shape (fill in signatures when implementing):
        //
        // JNINativeMethod methods[] = {
        //   { "native_setup", "(... )V", (void*) hooked_native_setup },
        // };
        // api->hookJniNativeMethods(env, "android/hardware/Camera", methods, 1);
        //
        // PLT example:
        // api->pltHookRegister(dev, ino, "symbol", (void*)my_hook, (void**)&orig);
        // api->pltHookCommit();

        LOGI("install_hooks: control plane OK for %s — frame path pending",
             state.process);
    }
};

/* Root companion: runs outside app sandbox, can always read control files */
static void companion_handler(int fd) {
    uint8_t reply[3];
    reply[0] = read_enabled() ? 1 : 0;
    reply[1] = file_exists(kDisableFlag) ? 1 : 0;
    reply[2] = file_exists(kCamera1Video) ? 1 : 0;
    write(fd, reply, 3);
    LOGD("companion: enabled=%u disable=%u video=%u",
         reply[0], reply[1], reply[2]);
}

REGISTER_ZYGISK_MODULE(VirtualCamModule)
REGISTER_ZYGISK_COMPANION(companion_handler)
