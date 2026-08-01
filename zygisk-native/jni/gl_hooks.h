#pragma once
#include <cstdint>

/**
 * OpenGL interception + MediaCodec (v2.0.2-dev Phase 2.1).
 * Honest status only until device verification of visible feed.
 *
 * Phase 2:   ShadowHook glBindTexture / glDraw* + YUV→RGB → GL_TEXTURE_2D
 * Phase 2.1: Prefer GL_TEXTURE_EXTERNAL_OES fed by AHardwareBuffer + EGLImageKHR
 *            (samplerExternalOES-compatible). Falls back to 2D when extensions
 *            or buffers are unavailable.
 */
namespace vcam {

bool install_gl_hooks();
void uninstall_gl_hooks();

void set_enabled(bool on);
bool is_enabled();

void set_video_path(const char *path);
void start_decoder_if_needed();
void stop_decoder();

uint32_t virtual_texture_id();
int decoder_frames();
bool decoder_ready();
bool oes_path_active();

}  // namespace vcam
