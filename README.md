# Android VirtualCam Manager

**Pure Magisk module + single controller APK**  
**NO LSPosed / NO Xposed required.**

This project takes the classic android_virtual_cam (VCAM) exploit paths and turns them into a clean, modern **Magisk-only + one APK** solution that works on the latest rooted Android versions.

## Goal

Only two components:
1. **Magisk Module** – prepares the exact original VCAM filesystem layout
2. **Single APK** – root-powered controller for `virtual.mp4` + all flag files

No LSPosed. No Xposed. No extra frameworks.

## Current Status

| Component              | Status                          |
|------------------------|---------------------------------|
| Magisk Module          | **v1.2.0 ready**                |
| Controller APK         | Source complete + GitHub Actions builds |
| Zygisk native hooks    | Planned (for real frame injection) |

> **Important**: The Magisk module + APK fully manage the classic paths and flags the original VCAM used. Actual camera frame replacement (Camera1 / Camera2 interception) still requires the native Zygisk component that is under development. Until then, the environment is 100% prepared for any future hook or for apps that respect the classic file-based control.

## Download

### Magisk Module
- Latest: `VirtualCam-Manager-Magisk-v1.2.0.zip` (see Releases or `magisk-module/`)

### APK
GitHub Actions builds the APK on every push to `main`.  
Go to **Actions → Build APK → latest successful run → Artifacts** and download:
- `VirtualCam-Manager-debug`
- `VirtualCam-Manager-release`

## Classic paths preserved exactly

```
/storage/emulated/0/DCIM/Camera1/virtual.mp4
/storage/emulated/0/DCIM/Camera1/disable.jpg
/storage/emulated/0/DCIM/Camera1/private_dir.jpg
/storage/emulated/0/DCIM/Camera1/no-silent.jpg
/storage/emulated/0/DCIM/Camera1/no_toast.jpg
/storage/emulated/0/DCIM/Camera1/force_show.jpg
```

Private mode (when `private_dir.jpg` exists):
`/storage/emulated/0/Android/data/<package>/files/Camera1/`

## Requirements

- Magisk 27+ (or Magisk Alpha / Kitsune)
- Rooted Android 8.0 – 16 (target latest)
- **No LSPosed / EdXposed / Xposed**

## How to use

1. Flash the Magisk module and reboot
2. Install the VirtualCam Manager APK (from Actions artifacts)
3. Grant root to the APK
4. Use Media tab → prepare directory / place your `virtual.mp4`
5. Use Settings tab → toggle the classic flags

## Building locally

See [BUILD.md](BUILD.md).

## Disclaimer

For legitimate privacy research, testing, and development only. Do not use for any illegal purpose.
