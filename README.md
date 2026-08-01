# Android VirtualCam Manager

**Pure Magisk module + single controller APK**  
**NO LSPosed / NO Xposed manager.**

> **v2.0.0-dev architecture pivot:** Native OpenGL/EGL interception (ShadowHook + `glBindTexture` / `glDraw*` on `GL_TEXTURE_EXTERNAL_OES`) + AMediaCodec YUV→RGB upload. The v1 ArtHook Java Camera1 path is deprecated for modern Android 14–16. **Device verification is required** before claiming a working camera spoof.

## Status (v2.0.0-dev)

| Layer | Status |
|-------|--------|
| Magisk paths + flags | **Working** |
| APK one-tap enable / import | **Working** |
| Zygisk `.so` multi-ABI + **16 KB page size** | **CI builds** |
| ShadowHook `glBindTexture` + `glDrawArrays` + `glDrawElements` | **Scaffold** — needs device test |
| AMediaCodec continuous decode (loop + FPS pacing) | **Scaffold** (YUV→RGB frames) |
| GL texture upload on bind/draw (Phase 2) | **Scaffold** — needs device test |
| Test-pattern bootstrap when decoder cold | **Scaffold** |
| OES SurfaceTexture / EGLImage MediaCodec output | **Not started** (Phase 2.1) |
| ANativeWindow queueBuffer fallback | **Not started** |
| Real apps show virtual feed on device | **Not verified** |

## Download

[Actions ← latest green run ← Artifacts](https://github.com/smithluke874/Android-VirtualCam-Manager/actions)

- `VirtualCam-Manager-debug` / `release`
- `VirtualCam-Manager-Magisk-v2.0.0-dev` (Zygisk `.so` — GL hooks + MediaCodec scaffold)

## Install (3 steps)

1. **Magisk** → Settings → **Zygisk ON** → Modules → Install from storage → flash `VirtualCam-Manager-Magisk-v2.0.0-dev.zip` → **reboot**
2. Install the **Manager APK** → open it → **allow root** when Magisk asks
3. On **Home**:
   - Tap **Pick video or image** (images become a looping video automatically)
   - Flip **VirtualCam ON**
   - Open any camera app — done

The Home screen always shows the next step in plain English. While ON, status updates automatically every few seconds. Technical details are behind **Show details**.

## Hook status values (v2)

| Status | Meaning |
|--------|--------|
| `gate_off` | Enabled flag off or `disable.jpg` present |
| `no_video` | Gate open but video file missing |
| `gl_installing` | Installing native GL hooks |
| `gl_hooked` / `gl_ready` / `gl_hooked_bind_draw` | Hooks installed |
| `gl_partial` / `gl_hook_fail` | Hook incomplete |
| `gl_hook_pending_shadowhook` | Built without ShadowHook libs |
| `gl_tex_created` | Virtual GL texture allocated |
| `gl_bind_redir:tex#N` | Bind redirect hit (not proof of visible feed) |
| `decoder_start` / `decoder_running` | MediaCodec active |
| `decoder_frames:WxH#N` | Frames decoded and uploaded |
| `decoder_exit` | Decoder stopped |

## Architecture (v2)

```
Manager APK  →  /data/adb/virtualcam/enabled + virtual.mp4
                      ↓
Zygisk module (libvirtualcam.so) in target process
                      ↓
ShadowHook  →  glBindTexture / glDrawArrays / glDrawElements
                      ↓
AMediaCodec loop  →  YUV420 → RGB888  →  glTex(Sub)Image2D
                      ↓
Redirect EXTERNAL_OES / 2D texture ID to virtual texture
```

**Limitation (honest):** Many modern camera pipelines sample with `samplerExternalOES`. A `GL_TEXTURE_2D` upload may be ignored by those shaders. Phase 2.1 will feed an OES SurfaceTexture / EGLImage from MediaCodec so external sampling works.

## Control plane files

| Path | Purpose |
|------|---------|
| `/data/adb/virtualcam/enabled` | `1` = ON, `0` = OFF |
| `/data/adb/virtualcam/hook_status` | Live status string |
| `/data/adb/virtualcam/last_hook_pkg` | Last process that loaded hooks |
| `/storage/emulated/0/DCIM/Camera1/virtual.mp4` | Primary media |
| `/storage/emulated/0/DCIM/Camera1/disable.jpg` | Emergency kill switch |

## Build

See [BUILD.md](BUILD.md) and `.github/workflows/build.yml`.  
Native build requires NDK r26+, CMake, multi-ABI, 16 KB page size flags, and optional ShadowHook AAR for runtime libs.

## License / Credits

Magisk + Zygisk module. No LSPosed. Inspired by classic `android_virtual_cam` algorithms, reimplemented natively for modern ART / 16 KB / PAC constraints.
