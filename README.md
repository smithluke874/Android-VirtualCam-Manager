# Android VirtualCam Manager

**Pure Magisk module + single controller APK**  
**NO LSPosed / NO Xposed.**

Automate classic android_virtual_cam paths and flags entirely from the APK. Zygisk native module reads the same control plane for process-level activation (frame injection hooks in progress).

## Only two components

1. **Magisk Module** — boot paths, Zygisk native lib, control dir  
2. **Manager APK** — one-tap enable/disable, import video, flags

## Status (v1.4.0)

| Feature | Status |
|---------|--------|
| Magisk paths + flags env | Working |
| APK one-tap auto-setup / enable | Working |
| Import virtual.mp4 from Downloads | Working |
| Flag toggles (disable, private_dir, …) | Working |
| Zygisk companion + process gate | Source ready (CI builds .so) |
| Camera1/Camera2 frame injection | Stub / next iteration |

## Download

GitHub **Actions** → latest green run → Artifacts:

- `VirtualCam-Manager-debug` / `release`
- `VirtualCam-Manager-Magisk-v1.4.0`

## Install (fully automated after flash)

1. Magisk → enable **Zygisk** → flash module zip → reboot  
2. Install APK → grant root  
3. Home → toggle **ON** (or **Run full auto-setup**)  
4. Media → put `virtual.mp4` in Download → **Import from Downloads**  
5. Optional flags in Settings  

## Control plane (APK ↔ Zygisk)

```
/data/adb/virtualcam/enabled          # 1=on 0=off
/data/adb/virtualcam/config
/storage/emulated/0/DCIM/Camera1/virtual.mp4
/storage/emulated/0/DCIM/Camera1/disable.jpg
...
```

## Requirements

- Magisk 27+ with Zygisk  
- Rooted Android 8–16  
- **No LSPosed**

## Disclaimer

Legitimate research / testing only.
