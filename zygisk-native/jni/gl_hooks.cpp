/*
 * VirtualCam v2 Phase 2 — GL bind redirect + MediaCodec → RGB → GL texture upload.
 *
 * ShadowHook is loaded at runtime (dlopen) so the .so always links even when
 * the hook engine is absent. Texture upload runs only when an EGL context is
 * current (inside glBindTexture / glDraw* proxies).
 *
 * Still not "done": device must show virtual feed in real camera apps.
 */
#include "gl_hooks.h"

#include <android/log.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>

#include <atomic>
#include <cstdio>
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

#define LOG_TAG "VirtualCamGL"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace vcam {
namespace {

constexpr const char *kHookStatus = "/data/adb/virtualcam/hook_status";
constexpr const char *kVideoPathA = "/storage/emulated/0/DCIM/Camera1/virtual.mp4";
constexpr const char *kVideoPathB = "/data/adb/virtualcam/virtual.mp4";
constexpr GLenum kExternalOes     = 0x8D65;

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

std::mutex g_frame_mu;
std::vector<uint8_t> g_rgb;
int g_rgb_w = 0, g_rgb_h = 0;
std::atomic<int> g_rgb_gen{0};
int g_uploaded_gen = -1;

using glBindTexture_fn = void (*)(GLenum, GLuint);
using glDrawArrays_fn  = void (*)(GLenum, GLint, GLsizei);
glBindTexture_fn orig_glBindTexture = nullptr;
glDrawArrays_fn  orig_glDrawArrays  = nullptr;
void *g_bind_stub = nullptr;
void *g_draw_stub = nullptr;
void *g_shadow_lib = nullptr;

using sh_init_fn = int (*)(int mode, bool debuggable);
using sh_hook_fn = void *(*)(const char *lib, const char *sym, void *new_addr, void **orig);
using sh_unhook_fn = int (*)(void *stub);
using sh_errno_fn = int (*)();
using sh_errmsg_fn = const char *(*)(int);
sh_init_fn  p_sh_init  = nullptr;
sh_hook_fn  p_sh_hook  = nullptr;
sh_unhook_fn p_sh_unhook = nullptr;
sh_errno_fn p_sh_errno = nullptr;
sh_errmsg_fn p_sh_errmsg = nullptr;

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

void yuv420sp_to_rgb(const uint8_t *src, int w, int h, int stride, bool nv21,
                     std::vector<uint8_t> &rgb) {
    if (w <= 0 || h <= 0 || !src) return;
    if (stride <= 0) stride = w;
    rgb.resize((size_t)w * (size_t)h * 3);
    const uint8_t *ys = src;
    const uint8_t *uv = src + (size_t)stride * (size_t)h;
    for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
            int Y = ys[y * stride + x] - 16;
            if (Y < 0) Y = 0;
            int uix = (y / 2) * stride + (x & ~1);
            int U, V;
            if (nv21) {
                V = uv[uix] - 128;
                U = uv[uix + 1] - 128;
            } else {
                U = uv[uix] - 128;
                V = uv[uix + 1] - 128;
            }
            int R = (298 * Y + 409 * V + 128) >> 8;
            int G = (298 * Y - 100 * U - 208 * V + 128) >> 8;
            int B = (298 * Y + 516 * U + 128) >> 8;
            auto clip = [](int v) -> uint8_t {
                if (v < 0) return 0;
                if (v > 255) return 255;
                return (uint8_t)v;
            };
            size_t o = ((size_t)y * (size_t)w + (size_t)x) * 3;
            rgb[o] = clip(R);
            rgb[o + 1] = clip(G);
            rgb[o + 2] = clip(B);
        }
    }
}

void ensure_upload_texture() {
    if (!g_decoder_ready.load()) return;
    if (eglGetCurrentContext() == EGL_NO_CONTEXT) return;

    std::vector<uint8_t> local;
    int w = 0, h = 0, gen = 0;
    {
        std::lock_guard<std::mutex> lock(g_frame_mu);
        if (g_rgb.empty() || g_rgb_w <= 0 || g_rgb_h <= 0) return;
        gen = g_rgb_gen.load();
        if (gen == g_uploaded_gen && g_vtex.load() != 0) return;
        local = g_rgb;
        w = g_rgb_w;
        h = g_rgb_h;
    }

    GLuint tex = g_vtex.load();
    if (tex == 0) {
        glGenTextures(1, &tex);
        if (tex == 0) return;
        g_vtex = tex;
        report("gl_tex_created");
    }

    glBindTexture(GL_TEXTURE_2D, tex);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    if (g_uploaded_gen < 0) {
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, w, h, 0, GL_RGB, GL_UNSIGNED_BYTE, local.data());
    } else {
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, w, h, GL_RGB, GL_UNSIGNED_BYTE, local.data());
    }
    g_uploaded_gen = gen;
}

void hooked_glBindTexture(GLenum target, GLuint texture) {
    if (g_enabled.load() && g_decoder_ready.load()) {
        ensure_upload_texture();
        uint32_t v = g_vtex.load();
        if (v != 0 && (target == kExternalOes || target == GL_TEXTURE_2D)) {
            texture = v;
            static std::atomic<int> hits{0};
            int h = ++hits;
            if (h == 1 || (h % 90) == 0) {
                char buf[80];
                snprintf(buf, sizeof(buf), "gl_bind_redir:%u#%d", (unsigned)v, h);
                report(buf);
            }
        }
    }
    if (orig_glBindTexture)
        orig_glBindTexture(target, texture);
}

void hooked_glDrawArrays(GLenum mode, GLint first, GLsizei count) {
    if (g_enabled.load() && g_decoder_ready.load())
        ensure_upload_texture();
    if (orig_glDrawArrays)
        orig_glDrawArrays(mode, first, count);
}

bool load_shadowhook() {
    static const char *kCandidates[] = {
        "libshadowhook.so",
        "/data/adb/modules/virtualcam_manager/libshadowhook.so",
        "/data/adb/modules/virtualcam_manager/libs/arm64-v8a/libshadowhook.so",
        "/data/adb/modules/virtualcam_manager/libs/armeabi-v7a/libshadowhook.so",
        "/data/adb/modules/virtualcam_manager/libs/x86_64/libshadowhook.so",
        "/data/adb/modules/virtualcam_manager/zygisk/libshadowhook.so",
        nullptr
    };
    for (int i = 0; kCandidates[i]; i++) {
        g_shadow_lib = dlopen(kCandidates[i], RTLD_NOW);
        if (g_shadow_lib) break;
    }
    if (!g_shadow_lib) return false;

    p_sh_init   = (sh_init_fn)dlsym(g_shadow_lib, "shadowhook_init");
    p_sh_hook   = (sh_hook_fn)dlsym(g_shadow_lib, "shadowhook_hook_sym_name");
    p_sh_unhook = (sh_unhook_fn)dlsym(g_shadow_lib, "shadowhook_unhook");
    p_sh_errno  = (sh_errno_fn)dlsym(g_shadow_lib, "shadowhook_get_errno");
    p_sh_errmsg = (sh_errmsg_fn)dlsym(g_shadow_lib, "shadowhook_to_errmsg");
    return p_sh_hook != nullptr;
}

bool install_hooks_runtime() {
    if (!load_shadowhook() || !p_sh_hook) {
        LOGW("ShadowHook not available — hooks not installed");
        report("gl_hook_pending_shadowhook");
        return false;
    }
    if (p_sh_init) {
        int rc = p_sh_init(1, false);
        LOGI("shadowhook_init rc=%d", rc);
    }

    g_bind_stub = p_sh_hook("libGLESv2.so", "glBindTexture",
                            (void *)hooked_glBindTexture, (void **)&orig_glBindTexture);
    if (!g_bind_stub)
        g_bind_stub = p_sh_hook("libGLESv3.so", "glBindTexture",
                                (void *)hooked_glBindTexture, (void **)&orig_glBindTexture);

    g_draw_stub = p_sh_hook("libGLESv2.so", "glDrawArrays",
                            (void *)hooked_glDrawArrays, (void **)&orig_glDrawArrays);

    if (!g_bind_stub) {
        int err = p_sh_errno ? p_sh_errno() : -1;
        const char *msg = p_sh_errmsg ? p_sh_errmsg(err) : "?";
        LOGE("glBindTexture hook failed: %d %s", err, msg);
        report("gl_hook_fail");
        return false;
    }

    LOGI("GL hooks installed (bind=%p draw=%p)", g_bind_stub, g_draw_stub);
    report(g_draw_stub ? "gl_hooked_bind_draw" : "gl_hooked");
    g_hooks_installed = true;
    return true;
}

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
        if (ex) AMediaExtractor_delete(ex);
        report("decoder_extract_fail");
        g_decoder_running = false;
        return;
    }

    int track = -1;
    AMediaFormat *fmt = nullptr;
    const char *mime = nullptr;
    int32_t srcW = 0, srcH = 0;
    for (int i = 0; i < AMediaExtractor_getTrackCount(ex); i++) {
        AMediaFormat *f = AMediaExtractor_getTrackFormat(ex, i);
        const char *m = nullptr;
        if (AMediaFormat_getString(f, AMEDIAFORMAT_KEY_MIME, &m) && m &&
            strncmp(m, "video/", 6) == 0) {
            track = i;
            fmt = f;
            mime = m;
            AMediaFormat_getInt32(f, AMEDIAFORMAT_KEY_WIDTH, &srcW);
            AMediaFormat_getInt32(f, AMEDIAFORMAT_KEY_HEIGHT, &srcH);
            break;
        }
        AMediaFormat_delete(f);
    }
    if (track < 0 || !fmt) {
        AMediaExtractor_delete(ex);
        report("decoder_no_track");
        g_decoder_running = false;
        return;
    }
    AMediaExtractor_selectTrack(ex, track);
    AMediaFormat_setInt32(fmt, AMEDIAFORMAT_KEY_COLOR_FORMAT, 21);

    AMediaCodec *codec = AMediaCodec_createDecoderByType(mime);
    if (!codec || AMediaCodec_configure(codec, fmt, nullptr, nullptr, 0) != AMEDIA_OK) {
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

    int32_t colorFormat = 21;
    int32_t stride = srcW > 0 ? srcW : 640;
    int frames = 0, errs = 0;

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
            if (info.size > 0) {
                size_t outSize = 0;
                uint8_t *outBuf = AMediaCodec_getOutputBuffer(codec, outIdx, &outSize);
                if (outBuf && outSize > 0 && srcW > 0 && srcH > 0) {
                    std::vector<uint8_t> rgb;
                    bool nv21 = (colorFormat == 21 || colorFormat == 0x7F420888 ||
                                 colorFormat == 0x7F000789);
                    yuv420sp_to_rgb(outBuf, srcW, srcH, stride, nv21, rgb);
                    {
                        std::lock_guard<std::mutex> lock(g_frame_mu);
                        g_rgb.swap(rgb);
                        g_rgb_w = srcW;
                        g_rgb_h = srcH;
                        g_rgb_gen++;
                    }
                    frames++;
                    g_frames = frames;
                    if (frames >= 2) {
                        g_decoder_ready = true;
                        if (frames == 2 || (frames % 90) == 0) {
                            char buf[80];
                            snprintf(buf, sizeof(buf), "decoder_frames:%dx%d#%d",
                                     srcW, srcH, frames);
                            report(buf);
                        }
                    }
                }
            }
            AMediaCodec_releaseOutputBuffer(codec, outIdx, false);
            errs = 0;
            usleep(10000);
        } else if (outIdx == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
            AMediaFormat *nf = AMediaCodec_getOutputFormat(codec);
            if (nf) {
                AMediaFormat_getInt32(nf, AMEDIAFORMAT_KEY_COLOR_FORMAT, &colorFormat);
                AMediaFormat_getInt32(nf, AMEDIAFORMAT_KEY_WIDTH, &srcW);
                AMediaFormat_getInt32(nf, AMEDIAFORMAT_KEY_HEIGHT, &srcH);
                int32_t s = 0;
                if (AMediaFormat_getInt32(nf, AMEDIAFORMAT_KEY_STRIDE, &s) && s > 0)
                    stride = s;
                AMediaFormat_delete(nf);
            }
        } else if (outIdx != AMEDIACODEC_INFO_TRY_AGAIN_LATER) {
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
    return install_hooks_runtime();
}

void uninstall_gl_hooks() {
    if (p_sh_unhook) {
        if (g_bind_stub) p_sh_unhook(g_bind_stub);
        if (g_draw_stub) p_sh_unhook(g_draw_stub);
    }
    g_bind_stub = g_draw_stub = nullptr;
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
