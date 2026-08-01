# Android VirtualCam Manager

**Pure Magisk module + single controller APK**  
**NO LSPosed / NO Xposed manager.**

> **v2.0.2-dev Phase 2.1:** Native OpenGL interception (ShadowHook) + AMediaCodec + **AHardwareBuffer → EGLImage → `GL_TEXTURE_EXTERNAL_OES`** (with 2D fallback). Live telemetry on Home (Frames / Tex / Binds / OES|2D). **Device verification required** before claiming a working camera spoof.

## Status (v2.0.2-dev)

| Layer | Status |
|-------|--------|
| Magisk paths + flags | **Working** |
| APK one-tap enable / import | **Working** |
| Dual video path (Camera1 + `/data/adb/virtualcam`) | **Working** |
| Zygisk `.so` multi-ABI + **16 KB page size** | **CI builds** |
| ShadowHook `glBindTexture` + `glDrawArrays` + `glDrawElements` | **Scaffold** — needs device test |
| AMediaCodec continuous decode (loop + FPS pacing) | **Scaffold** |
| GL 2D RGB upload (fallback) | **Scaffold** |
| **OES path: AHardwareBuffer + EGLImageKHR** | **Scaffold (Phase 2.1)** — needs device test |
| Live telemetry (Frames / Tex / Binds / path mode) | **Working** |
| Test-pattern bootstrap | **Scaffold** |
| ANativeWindow / SurfaceTexture MediaCodec output | **Not started** |
| Real apps show virtual feed on device | **Not verified** |

## Download

[Actions ← latest green run ← Artifacts](https://github.com/smithluke874/Android-VirtualCam-Manager/actions)

- `VirtualCam-Manager-debug` / `release`
- `VirtualCam-Manager-Magisk-v2.0.2-dev`

## Install (3 steps)

1. **Magisk** → Settings → **Zygisk ON** → Modules → Install from storage → flash the Magisk zip → **reboot**
2. Install the **Manager APK** → open it → **allow root** when Magisk asks
3. On **Home**:
   - Tap **Pick video or image**
   - Flip **VirtualCam ON**
   - Open any camera app — watch **Frames / Binds** and **OES** or **2D** chip

## What to expect right now

Phase 2.1 prefers a real **EXTERNAL_OES** texture fed by `AHardwareBuffer` + `eglGetNativeClientBufferANDROID` + `eglCreateImageKHR` + `glEGLImageTargetTexture2DOES`. That is what `samplerExternalOES` shaders expect.

If EGL extensions are missing on the device, the module falls back to `GL_TEXTURE_2D` and reports `oes_fallback_2d`. Rising **Frames** and **Binds** mean the native path is alive. A visible virtual feed in a real camera app is still **not claimed** until verified on device.

## Hook / path status values

| Status | Meaning |
|--------|--------|
| `gate_off` / `no_video` | Control plane closed |
| `gl_installing` / `gl_hooked*` / `gl_ready` | Hooks installing or installed |
| `gl_hook_fail` / `gl_hook_pending_shadowhook` | Hook incomplete |
| `decoder_*` / `decoder_frames:WxH#N` | MediaCodec active |
| `gl_tex_created` / `gl_tex_created_oes` | Virtual texture allocated |
| `oes_ready` | OES + EGLImage path in use |
| `oes_fallback_2d` | Fell back to 2D RGB upload |
| `gl_bind_redir:tex#N` | Bind redirect hit |

## Architecture

```
Manager APK → /data/adb/virtualcam/{enabled,virtual.mp4,hook_status,path_mode,...}
                    ↓
             Zygisk libvirtualcam.so
                    ↓
        ShadowHook → glBindTexture / glDraw*
                    ↓
        AMediaCodec → YUV→RGB
                    ↓
   Prefer: AHardwareBuffer → EGLImage → EXTERNAL_OES
   Fallback: glTexImage2D (GL_TEXTURE_2D)
                    ↓
        Redirect EXTERNAL_OES / 2D texture ID
```

## Roadmap (realistic)

**Next few versions (2.1–2.3)**  
- Harden OES path across OEMs (more EGL edge cases)  
- Optional SurfaceTexture / ANativeWindow MediaCodec output  
- Documented smoke tests on real apps  

**Many versions out (3.x+)**  
- Camera2/CameraX coverage on multiple OEMs  
- Optional hybrid NV21 / VirtualDisplay fallbacks  
- Long-session stability and clear failure modes  

Until a real camera app shows the virtual video on a physical device, status remains **scaffold / needs verification**.
