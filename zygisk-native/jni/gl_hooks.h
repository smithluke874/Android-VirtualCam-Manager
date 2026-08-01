#pragma once
#include <cstdint>

/**
 * OpenGL interception + MediaCodec (v2.0.0-dev Phase 2).
 * Honest status only until device verification.
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

}  // namespace vcam
