# Android VirtualCam Manager

**Pure Magisk module + single controller APK**  
**NO LSPosed / NO Xposed manager.**

> **v2.0.1-dev:** Native OpenGL/EGL interception (ShadowHook + `glBindTexture` / `glDraw*` on `GL_TEXTURE_EXTERNAL_OES`) + AMediaCodec YUV→RGB upload + live telemetry (frames / texture / binds). The v1 ArtHook Java Camera1 path is deprecated for modern Android 14–16. **Device verification is required** before claiming a working camera spoof.

## Status (v2.0.1-dev)

| Layer | Status |
|-------|--------|
| Magisk paths + flags | **Working** |
| APK one-tap enable / import | **Working** |
| Dual video path (Camera1 + `/data/adb/virtualcam`) | **Working** |
| Zygisk `.so` multi-ABI + **16 KB page size** | **CI builds** |
| ShadowHook `glBindTexture` + `glDrawArrays` + `glDrawElements` | **Scaffold** — needs device test |
| AMediaCodec continuous decode (loop + FPS pacing) | **Scaffold** (YUV→RGB frames) |
| GL texture upload on bind/draw | **Scaffold** — needs device test |
| Live telemetry (Frames / Tex / Binds on Home) | **Working** |
| Test-pattern bootstrap when decoder cold | **Scaffold** |
| OES SurfaceTexture / EGLImage MediaCodec output | **Not started** (Phase 2.1) |
| ANativeWindow queueBuffer fallback | **Not started** |
| Real apps show virtual feed on device | **Not verified** |

## Download

[Actions ← latest green run ← Artifacts](https://github.com/smithluke874/Android-VirtualCam-Manager/actions)

- `VirtualCam-Manager-debug` / `release`
- `VirtualCam-Manager-Magisk-v2.0.1-dev`

## Install (3 steps)

1. **Magisk** → Settings → **Zygisk ON** → Modules → Install from storage → flash the Magisk zip → **reboot**
2. Install the **Manager APK** → open it → **allow root** when Magisk asks
3. On **Home**:
   - Tap **Pick video or image** (images become a looping video automatically)
   - Flip **VirtualCam ON**
   - Open any camera app — watch **Frames / Binds** rise

The Home screen always shows the next step in plain English. While ON, status and telemetry update automatically. Technical details are behind **Show details**.

## What to expect right now

v2 intercepts OpenGL texture binds and uploads decoded frames as `GL_TEXTURE_2D`. Many Camera2 / CameraX apps sample with `samplerExternalOES` — the preview may stay black or show the real camera until **Phase 2.1** (OES SurfaceTexture / EGLImage). Rising **Frames** and **Binds** numbers mean the native path is alive.

## Hook status values (v2)

| Status | Meaning |
|--------|--------|
| `gate_off` | Enabled flag off or `disable.jpg` present |
| `no_video` | Gate open but video file missing |
| `gl_installing` | Installing native GL hooks |
| `gl_hooked` / `gl_ready` / `gl_hooked_bind_draw` | Hooks installed |
| `gl_partial` / `gl_hook_fail` | Hook incomplete |
| `gl_hook_pending_shadowhook` | Built without ShadowHook |
| `decoder_start` / `decoder_running` / `decoder_frames:WxH#N` | MediaCodec active |
| `gl_bind_redir:tex#N` | Bind redirect hit |
| `gl_tex_created` | Virtual texture allocated on GL thread |

## Architecture

```
Manager APK  →  /data/adb/virtualcam/{enabled,virtual.mp4,hook_status,...}
                     ↓
              Zygisk libvirtualcam.so
                     ↓
         ShadowHook → glBindTexture / glDraw*
                     ↓
         AMediaCodec → YUV→RGB → glTexImage2D
                     ↓
         Redirect EXTERNAL_OES / 2D texture ID
```

Control plane stays under `/data/adb/virtualcam/`. No LSPosed.

## Roadmap (realistic)

**Next few versions (2.1.x – 2.3.x)**  
- Phase 2.1: SurfaceTexture / EGLImageOES so external sampling works  
- Better color conversion + optional libyuv  
- More camera-app smoke tests documented  
- Still honest: “verified on device X with app Y” only after real tests  

**Many versions out (3.x+)**  
- Camera2 / CameraX path coverage on multiple OEMs  
- Optional hybrid NV21 callback for remaining Camera1 apps  
- Optional VirtualDisplay / MediaProjection fallback for stubborn pipelines  
- Polished UX, crash-free long sessions, clear failure modes  

Until a real camera app shows the virtual video on a physical device, status remains **scaffold / needs verification**.
