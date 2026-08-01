#pragma once
#include <jni.h>
#include <atomic>
#include <cstdint>

/**
 * OpenGL / EGL interception layer (v2 pivot).
 *
 * Primary: hook glBindTexture for GL_TEXTURE_EXTERNAL_OES and redirect
 * camera external textures to a virtual texture when the decoder is ready.
 *
 * Status strings written to /data/adb/virtualcam/hook_status — never claim
 * "working feed" until device verification confirms real apps show video.
 */
namespace vcam {

bool install_gl_hooks();
void uninstall_gl_hooks();

void set_enabled(bool on);
bool is_enabled();

void set_video_path(const char *path);
void start_decoder_if_needed();
void stop_decoder();

/** Virtual OES texture id (0 = not ready). */
uint32_t virtual_texture_id();

/** Frame counter from decoder (for status). */
int decoder_frames();

bool decoder_ready();

}  // namespace vcam
