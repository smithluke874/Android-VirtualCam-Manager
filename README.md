# Android VirtualCam Manager

**Pure Magisk module + single controller APK**  
**NO LSPosed / NO Xposed.**

## v1.5.0 status

| Component | Status |
|-----------|--------|
| Magisk paths + flags | Working |
| APK one-tap enable / Import video | Working |
| Zygisk native `.so` (arm64 / armv7 / x86_64) | **Built by CI** |
| Process gate + companion + hook_status | Working |
| Camera frame injection | Next (LSPlant ART hooks) |

## Download

[Actions → latest green run → Artifacts](https://github.com/smithluke874/Android-VirtualCam-Manager/actions)

- `VirtualCam-Manager-debug` / `release`
- `VirtualCam-Manager-Magisk-v1.5.0` (includes compiled Zygisk `.so`)

## Install

1. Magisk → **Zygisk ON** → flash Magisk zip → reboot  
2. Install APK → grant root  
3. Home → **ON**  
4. Media → put `virtual.mp4` in Download → **Import**  
5. Open a camera app → return to Home → check **hook status** (`active` = Zygisk gated that app)

## Control plane

```
/data/adb/virtualcam/enabled
/data/adb/virtualcam/hook_status      # idle | active | no_video
/data/adb/virtualcam/last_hook_pkg
/DCIM/Camera1/virtual.mp4
/DCIM/Camera1/disable.jpg
```

## Architecture

- **APK** = full automation UI (root)
- **Magisk** = boot paths + ships Zygisk `.so`
- **Zygisk** = per-app gate + status feedback; frame path uses LSPlant next

## Disclaimer

Legitimate research / testing only.
