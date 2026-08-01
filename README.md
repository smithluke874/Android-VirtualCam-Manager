# Android VirtualCam Manager

**Pure Magisk module + single controller APK**  
**NO LSPosed / NO Xposed manager.**

> **v2.0.0-dev architecture pivot:** Native OpenGL/EGL interception (ShadowHook + `glBindTexture` on `GL_TEXTURE_EXTERNAL_OES`) + AMediaCodec. The v1 ArtHook Java Camera1 path is deprecated for modern Android 14–16. **Device verification is required** before claiming a working camera spoof.

## Status (v2.0.0-dev)

| Layer | Status |
|-------|--------|
| Magisk paths + flags | **Working** |
| APK one-tap enable / import | **Working** |
| Zygisk `.so` multi-ABI + **16 KB page size** | **CI builds** |
| ShadowHook `glBindTexture` (EXTERNAL_OES) | **Scaffold** — needs device test |
| AMediaCodec continuous decode | **Scaffold** (frame counter / status) |
| OES texture + SurfaceTexture feed (Phase 2) | **Not started** |
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
| `gl_hooked` / `gl_ready` | `glBindTexture` hooked |
| `gl_partial` / `gl_hook_fail` | Hook incomplete |
| `gl_hook_pending_shadowhook` | Built without ShadowHook |
| `decoder_start` / `decoder_running` | MediaCodec active |
| `decoder_frames:N` | Frames decoded (not proof of on-screen feed) |
| `gl_bind_redir:tex#N` | EXTERNAL_OES bind redirect hit |

## Architecture (v2)

```
APK (root)  --writes-->  /data/adb/virtualcam/*  +  /DCIM/Camera1/*
                              ^
Magisk boot scripts ----------+
                              |
Zygisk .so  --reads-----------+  --writes--> hook_status / last_hook_pkg / version
         |
         +-- ShadowHook: libGLESv2.so!glBindTexture
         +-- Redirect GL_TEXTURE_EXTERNAL_OES when virtual tex ready
         +-- AMediaCodec decoder thread (virtual.mp4)
         +-- Phase 2: OES + SurfaceTexture feed (todo)
         +-- Phase 3: ANativeWindow fallback (todo)
```

## Disclaimer

Legitimate research / testing only. Do not use for illegal purposes.
**Do not treat scaffold status strings as proof the camera is spoofed.**
