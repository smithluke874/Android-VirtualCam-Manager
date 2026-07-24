/*
 * VirtualCam Manager — Zygisk native module v1.5.0
 * Pure Magisk (NO LSPosed).
 *
 * Control plane (APK writes / this module reads):
 *   /data/adb/virtualcam/enabled
 *   /storage/emulated/0/DCIM/Camera1/virtual.mp4
 *   /storage/emulated/0/DCIM/Camera1/disable.jpg
 *
 * Feedback (this module writes / APK reads):
 *   /data/adb/virtualcam/hook_status
 *   /data/adb/virtualcam/last_hook_pkg
 */

#include <cstdlib>
#include <cstring>
#include <unistd.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <android/log.h>
#include <string>
#include <chrono>

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

/*
 * VideoFrameProvider — skeleton for decoding virtual.mp4 frames.
 * Next iteration: MediaCodec / libmediandk to produce NV21/YUV420
 * buffers sized to the active Camera preview request.
 */
class VideoFrameProvider {
public:
    bool open(const char *path) {
        path_ = path;
        if (!file_exists(path)) {
            LOGE("video missing: %s", path);
            return false;
        }
        struct stat st{};
        if (stat(path, &st) == 0) {
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

    // Placeholder: returns false until MediaCodec path is wired
    bool nextNv21(uint8_t * /*out*/, size_t /*capacity*/, int /*w*/, int /*h*/) {
        return false;
    }

private:
    std::string path_;
    off_t size_ = 0;
    bool opened_ = false;
};

static VideoFrameProvider g_video;

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

        install_hooks();
        report_status(state.process, "active");
    }

    void preServerSpecialize(ServerSpecializeArgs *args) override {
        api->setOption(zygisk::Option::DLCLOSE_MODULE_LIBRARY);
    }

private:
    Api *api = nullptr;
    JNIEnv *env = nullptr;
    VcamState state{};

    void install_hooks() {
        /*
         * Frame injection plan (pure Zygisk, no LSPosed):
         *
         * 1. Short term: PLT / JNI native hooks on Camera path
         * 2. Medium: integrate LSPlant (ART method hook) for
         *    Camera.setPreviewCallback / Camera2 ImageReader callbacks
         *    matching original android_virtual_cam HookMain targets
         * 3. Feed NV21 frames from VideoFrameProvider into hooked callbacks
         *
         * Until LSPlant is linked, we still gate processes correctly and
         * report status so the APK can show "Zygisk saw this app".
         */
        LOGI("install_hooks: video=%s size=%lld pkg=%s — frame path pending LSPlant",
             g_video.path(), (long long)g_video.size(), state.process);
    }
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
