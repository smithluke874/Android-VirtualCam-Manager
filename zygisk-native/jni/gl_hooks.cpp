/*
 * VirtualCam v2 — native OpenGL interception + MediaCodec decoder scaffold.
 *
 * Goal: replace camera frames at GL_TEXTURE_EXTERNAL_OES bind time.
 * Honest status only: report gl_hooked / decoder states; do not claim
 * end-to-end camera spoof until verified on device.
 */
#include "gl_hooks.h"

#include <android/log.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>

#include <cstring>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>

#include <media/NdkMediaCodec.h>
#include <media/NdkMediaExtractor.h>
#include <media/NdkMediaFormat.h>

/* ShadowHook — provided by CI-fetched third_party or system. */
#if defined(VCAM_HAS_SHADOWHOOK)
#include "shadowhook.h"
#endif

#define LOG_TAG "VirtualCamGL"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace vcam {
namespace {

constexpr const char *kHookStatus = "/data/adb/virtualcam/hook_status";
constexpr const char *kLastPkg    = "/data/adb/virtualcam/last_hook_pkg";
constexpr const char *kVideoPathA = "/storage/emulated/0/DCIM/Camera1/virtual.mp4";
constexpr const char *kVideoPathB = "/data/adb/virtualcam/virtual.mp4";
constexpr GLenum kExternalOes     = 0x8D65; /* GL_TEXTURE_EXTERNAL_OES */

std::atomic<bool> g_enabled{false};
std::atomic<bool> g_hooks_installed{false};
std::atomic<bool> g_decoder_running{false};
std::atomic<bool> g_decoder_ready{false};
std::atomic<bool> g_stop{false};
std::atomic<int>  g_frames{0};
std::atomic<uint32_t> g_vtex{0};

std::mutex g_path_mu;
std::string g_video_path;
std::thread g_decoder_thread;

using glBindTexture_fn = void (*)(GLenum, GLuint);
glBindTexture_fn orig_glBindTexture = nullptr;
void *g_bind_stub = nullptr;

void report(const char *status) {
    int fd = open(kHookStatus, O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0644);
    if (fd >= 0) {
        if (status) write(fd, status, strlen(status));
        close(fd);
    }
}

bool file_ok(const char *path) {
    struct stat st{};
    return path && path[0] && stat(path, &st) == 0 && S_ISREG(st.st_mode) && st.st_size > 0;
}

const char *resolve_video() {
    std::lock_guard<std::mutex> lock(g_path_mu);
    if (!g_video_path.empty() && file_ok(g_video_path.c_str()))
        return g_video_path.c_str();
    if (file_ok(kVideoPathA)) return kVideoPathA;
    if (file_ok(kVideoPathB)) return kVideoPathB;
    return nullptr;
}

/*
 * glBindTexture proxy: when enabled and target is EXTERNAL_OES, and we have a
 * virtual texture, redirect the bind. This is the minimal GL pivot path.
 *
 * NOTE: Many camera pipelines re-attach buffers via SurfaceTexture.updateTexImage
 * after bind; pure ID swap is not sufficient for all apps. This is the first
 * layer — device testing will drive the next hooks (updateTexImage / draw / queueBuffer).
 */
void hooked_glBindTexture(GLenum target, GLuint texture) {
    if (g_enabled.load() && target == kExternalOes) {
        uint32_t v = g_vtex.load();
        if (v != 0 && g_decoder_ready.load()) {
            texture = v;
            static std::atomic<int> hits{0};
            int h = ++hits;
            if (h == 1 || (h % 120) == 0) {
                char buf[64];
                snprintf(buf, sizeof(buf), "gl_bind_redir:%u#%d", v, h);
                report(buf);
            }
        }
    }
    if (orig_glBindTexture)
        orig_glBindTexture(target, texture);
}

#if defined(VCAM_HAS_SHADOWHOOK)
bool install_shadowhook_gl() {
    int rc = shadowhook_init(SHADOWHOOK_MODE_UNIQUE, false);
    if (rc != 0) {
        LOGW("shadowhook_init rc=%d", rc);
    }

    g_bind_stub = shadowhook_hook_sym_name(
        "libGLESv2.so",
        "glBindTexture",
        (void *)hooked_glBindTexture,
        (void **)&orig_glBindTexture);

    if (!g_bind_stub) {
        g_bind_stub = shadowhook_hook_sym_name(
            "libGLESv3.so",
            "glBindTexture",
            (void *)hooked_glBindTexture,
            (void **)&orig_glBindTexture);
    }

    if (!g_bind_stub) {
        int err = shadowhook_get_errno();
        LOGE("glBindTexture hook failed: %d %s", err, shadowhook_to_errmsg(err));
        report("gl_hook_fail");
        return false;
    }

    LOGI("glBindTexture hooked via ShadowHook");
    report("gl_hooked");
    g_hooks_installed = true;
    return true;
}
#else
bool install_dlsym_gl() {
    void *gles = dlopen("libGLESv2.so", RTLD_NOW);
    if (!gles) {
        report("gl_dlopen_fail");
        return false;
    }
    void *sym = dlsym(gles, "glBindTexture");
    if (!sym) {
        report("gl_dlsym_fail");
        return false;
    }
    orig_glBindTexture = (glBindTexture_fn)sym;
    LOGW("ShadowHook not linked — glBindTexture not rewritten (scaffold only)");
    report("gl_hook_pending_shadowhook");
    return false;
}
#endif

/*
 * Decoder scaffold: loops virtual.mp4 with AMediaCodec.
 * Phase 1: decode frames and report counts (texture feed is Phase 2).
 * Until Phase 2 lands and device tests pass, do not claim on-screen spoof.
 */
void decoder_loop() {
    const char *path = resolve_video();
    if (!path) {
        report("no_video");
        g_decoder_running = false;
        return;
    }

    report("decoder_start");
    AMediaExtractor *ex = AMediaExtractor_new();
    if (!ex || AMediaExtractor_setDataSource(ex, path) != AMEDIA_OK) {
        LOGE("extractor fail");
        if (ex) AMediaExtractor_delete(ex);
        report("decoder_extract_fail");
        g_decoder_running = false;
        return;
    }

    int track = -1;
    AMediaFormat *fmt = nullptr;
    const char *mime = nullptr;
    for (int i = 0; i < AMediaExtractor_getTrackCount(ex); i++) {
        AMediaFormat *f = AMediaExtractor_getTrackFormat(ex, i);
        const char *m = nullptr;
        if (AMediaFormat_getString(f, AMEDIAFORMAT_KEY_MIME, &m) && m &&
            strncmp(m, "video/", 6) == 0) {
            track = i;
            fmt = f;
            mime = m;
            break;
        }
        AMediaFormat_delete(f);
    }
    if (track < 0 || !fmt) {
        LOGE("no video track");
        AMediaExtractor_delete(ex);
        report("decoder_no_track");
        g_decoder_running = false;
        return;
    }
    AMediaExtractor_selectTrack(ex, track);

    AMediaCodec *codec = AMediaCodec_createDecoderByType(mime);
    if (!codec || AMediaCodec_configure(codec, fmt, nullptr, nullptr, 0) != AMEDIA_OK) {
        LOGE("codec configure fail");
        if (codec) AMediaCodec_delete(codec);
        AMediaFormat_delete(fmt);
        AMediaExtractor_delete(ex);
        report("decoder_codec_fail");
        g_decoder_running = false;
        return;
    }
    AMediaFormat_delete(fmt);
    AMediaCodec_start(codec);
    report("decoder_running");

    int frames = 0;
    int errs = 0;
    while (!g_stop.load()) {
        ssize_t inIdx = AMediaCodec_dequeueInputBuffer(codec, 2000);
        if (inIdx >= 0) {
            size_t sz = 0;
            uint8_t *ib = AMediaCodec_getInputBuffer(codec, inIdx, &sz);
            ssize_t sample = ib ? AMediaExtractor_readSampleData(ex, ib, sz) : -1;
            if (sample < 0) {
                AMediaExtractor_seekTo(ex, 0, AMEDIAEXTRACTOR_SEEK_CLOSEST_SYNC);
                sample = ib ? AMediaExtractor_readSampleData(ex, ib, sz) : -1;
            }
            if (sample >= 0) {
                int64_t pts = AMediaExtractor_getSampleTime(ex);
                AMediaCodec_queueInputBuffer(codec, inIdx, 0, (size_t)sample, pts, 0);
                AMediaExtractor_advance(ex);
            } else {
                AMediaCodec_queueInputBuffer(codec, inIdx, 0, 0, 0, 0);
                usleep(20000);
            }
        }

        AMediaCodecBufferInfo info{};
        ssize_t outIdx = AMediaCodec_dequeueOutputBuffer(codec, &info, 2000);
        if (outIdx >= 0) {
            AMediaCodec_releaseOutputBuffer(codec, outIdx, false);
            if (info.size > 0) {
                frames++;
                g_frames = frames;
                if (frames >= 3) {
                    g_decoder_ready = true;
                    if (frames == 3 || (frames % 90) == 0) {
                        char buf[72];
                        snprintf(buf, sizeof(buf), "decoder_frames:%d", frames);
                        report(buf);
                    }
                }
            }
            errs = 0;
            usleep(8000);
        } else if (outIdx != AMEDIACODEC_INFO_TRY_AGAIN_LATER &&
                   outIdx != AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
            if (++errs > 80) break;
        }
    }

    AMediaCodec_stop(codec);
    AMediaCodec_delete(codec);
    AMediaExtractor_delete(ex);
    g_decoder_ready = false;
    g_decoder_running = false;
    report("decoder_exit");
    LOGI("decoder exited after %d frames", frames);
}

}  // namespace

bool install_gl_hooks() {
#if defined(VCAM_HAS_SHADOWHOOK)
    return install_shadowhook_gl();
#else
    return install_dlsym_gl();
#endif
}

void uninstall_gl_hooks() {
#if defined(VCAM_HAS_SHADOWHOOK)
    if (g_bind_stub) {
        shadowhook_unhook(g_bind_stub);
        g_bind_stub = nullptr;
    }
#endif
    g_hooks_installed = false;
}

void set_enabled(bool on) { g_enabled = on; }
bool is_enabled() { return g_enabled.load(); }

void set_video_path(const char *path) {
    std::lock_guard<std::mutex> lock(g_path_mu);
    g_video_path = path ? path : "";
}

void start_decoder_if_needed() {
    if (g_decoder_running.load()) return;
    if (!resolve_video()) {
        report("no_video");
        return;
    }
    g_stop = false;
    g_decoder_running = true;
    g_decoder_thread = std::thread([] { decoder_loop(); });
    g_decoder_thread.detach();
}

void stop_decoder() {
    g_stop = true;
    g_decoder_ready = false;
}

uint32_t virtual_texture_id() { return g_vtex.load(); }
int decoder_frames() { return g_frames.load(); }
bool decoder_ready() { return g_decoder_ready.load(); }

}  // namespace vcam
