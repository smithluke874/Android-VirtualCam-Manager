# VirtualCam Zygisk native (v2.0.0-dev)

## Architecture pivot

v1 used ArtHook on Java `android.hardware.Camera` APIs. That path is fragile on
Android 14–16 (ART inlining, PAC/BTI, 16 KB pages, native camera engines).

v2 intercepts at the **OpenGL ES** layer:

1. ShadowHook on `libGLESv2.so` / `libGLESv3.so`:
   - `glBindTexture`
   - `glDrawArrays`
   - `glDrawElements`
2. When target is `GL_TEXTURE_EXTERNAL_OES` (or 2D) and a virtual texture is ready,
   redirect the texture ID
3. Background `AMediaCodec` loop over `virtual.mp4` → YUV→RGB → `glTex(Sub)Image2D`
4. Test-pattern bootstrap so bind redirects are observable even before the first decoded frame

## Honest status

| Status string | Meaning |
|---------------|---------|
| `gate_off` / `no_video` | Control plane closed |
| `gl_installing` | Installing hooks |
| `gl_hooked` / `gl_ready` / `gl_hooked_bind_draw` | Hooks installed |
| `gl_hook_fail` / `gl_partial` | Hook incomplete |
| `gl_hook_pending_shadowhook` | Built without ShadowHook |
| `gl_tex_created` | Virtual texture allocated on GL thread |
| `gl_bind_redir:tex#N` | Bind redirect hit (not proof of visible feed) |
| `decoder_start` / `decoder_running` / `decoder_frames:WxH#N` | MediaCodec active |
| `decoder_exit` | Decoder stopped |

**Do not claim “camera spoofed” until real apps show the virtual video on device.**

## Known limitation

Many Camera2 / CameraX pipelines sample with `samplerExternalOES`. A `GL_TEXTURE_2D`
upload may be ignored by those shaders. Phase 2.1 will feed an OES SurfaceTexture /
EGLImage from MediaCodec so external sampling works.

## Build

See root `CMakeLists.txt` and CI workflow. Requires NDK, mediandk, GLESv2, EGL.
ShadowHook is optional (runtime dlopen).
