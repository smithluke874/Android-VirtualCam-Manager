# Android VirtualCam Manager

**Pure Magisk module + single controller APK**  
**NO LSPosed / NO Xposed manager.**

> **v2.0.0-dev architecture pivot:** Native OpenGL/EGL interception (ShadowHook + `glBindTexture` / `glDrawArrays`) + AMediaCodec YUV→RGB→GL texture. The v1 ArtHook Java Camera1 path is deprecated for modern Android 14–16. **Device verification is required** before claiming a working camera spoof.

## Status (v2.0.0-dev)

| Layer | Status |
|-------|--------|
| Magisk paths + flags | **Working** |
| APK one-tap enable / import | **Working** |
| Zygisk `.so` multi-ABI + **16 KB page size** | **CI builds** |
| ShadowHook runtime `dlopen` + bind/draw hooks | **Scaffold** — needs device test |
| AMediaCodec YUV→RGB frames | **Scaffold** |
| GL texture upload on bind/draw (Phase 2) | **Scaffold** — needs device test |
| OES SurfaceTexture MediaCodec output | **Not started** |
| ANativeWindow queueBuffer fallback | **Not started** |
| Real apps show virtual feed on device | **Not verified** |

## Download

[Actions ← latest green run ← Artifacts](https://github.com/smithluke874/Android-VirtualCam-Manager/actions)

- `VirtualCam-Manager-debug` / `release`
- `VirtualCam-Manager-Magisk-v2.0.0-dev` (Zygisk `.so` + optional ShadowHook libs)

## Install (3 steps)

1. **Magisk** → Settings → **Zygisk ON** → Modules → Install from storage → flash `VirtualCam-Manager-Magisk-v2.0.0-dev.zip` → **reboot**
2. Install the **Manager APK** → open it → **allow root** when Magisk asks
3. On **Home**:
   - Tap **Pick video or image** (images become a looping video automatically)
   - Flip **VirtualCam ON**
   - Open any camera app — done

While ON, status updates automatically. Technical details are behind **Show details**.

## Hook status values (v2)

| Status | Meaning |
|--------|--------|
| `gate_off` / `no_video` | Control plane closed |
| `gl_installing` | Installing hooks |
| `gl_hooked` / `gl_hooked_bind_draw` | Hooks installed |
| `gl_hook_pending_shadowhook` | ShadowHook `.so` not loaded |
| `gl_hook_fail` | Hook install failed |
| `decoder_start` / `decoder_running` | MediaCodec active |
| `decoder_frames:WxH#N` | RGB frames produced |
| `gl_tex_created` | Virtual GL texture allocated |
| `gl_bind_redir:tex#N` | Bind redirect hit (not proof of visible feed) |

## Architecture (v2 Phase 2)

```
APK → /data/adb/virtualcam + /DCIM/Camera1/virtual.mp4
Zygisk .so:
  +-- dlopen ShadowHook → hook glBindTexture + glDrawArrays
  +-- AMediaCodec loop → YUV→RGB shared buffer
  +-- On GL thread: upload RGB to GL_TEXTURE_2D, redirect binds
  +-- Next: OES SurfaceTexture + ANativeWindow fallback
```

## Disclaimer

Legitimate research / testing only. Do not use for illegal purposes.
**Scaffold status ≠ camera spoofed.**
