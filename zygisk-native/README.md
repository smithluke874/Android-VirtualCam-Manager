# VirtualCam Zygisk native (v2.0.0-dev)

## Architecture pivot

v1 used ArtHook on Java `android.hardware.Camera` APIs. That path is fragile on
Android 14–16 (ART inlining, PAC/BTI, 16 KB pages, native camera engines).

v2 intercepts at the **OpenGL ES** layer:

1. ShadowHook on `libGLESv2.so!glBindTexture`
2. When target is `GL_TEXTURE_EXTERNAL_OES` and a virtual texture is ready,
   redirect the texture ID
3. Background `AMediaCodec` loop over `virtual.mp4`

## Honest status

| Status string | Meaning |
|---------------|---------|
| `gate_off` / `no_video` | Control plane closed |
| `gl_installing` | Installing hooks |
| `gl_hooked` / `gl_ready` | glBindTexture hooked |
| `gl_hook_fail` / `gl_partial` | Hook incomplete |
| `gl_hook_pending_shadowhook` | Built without ShadowHook |
| `decoder_start` / `decoder_running` / `decoder_frames:N` | MediaCodec active |
| `gl_bind_redir:tex#N` | Bind redirect hit (not proof of visible feed) |

**Do not claim “camera spoofed” until real apps show the virtual video on device.**

## Build

- NDK r26+ recommended
- Link flags include `-Wl,-z,max-page-size=16384`
- Optional: place ShadowHook under `third_party/shadowhook/`

## Next hardening

1. OES texture + SurfaceTexture fed by MediaCodec (Phase 2)
2. Hook `ASurfaceTexture_updateTexImage` / draw paths
3. `ANativeWindow_queueBuffer` fallback for non-GL apps
4. Device matrix testing
