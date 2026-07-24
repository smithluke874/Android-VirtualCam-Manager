# Android VirtualCam Manager

**Pure Magisk module + single controller APK**  
**NO LSPosed / NO Xposed required.**

Classic android_virtual_cam (VCAM) paths and flags, controlled by one Magisk module and one APK. Designed for latest rooted Android.

## Components (only these two)

| Component | Role |
|-----------|------|
| **Magisk Module** | Creates `/DCIM/Camera1` at boot, owns Zygisk skeleton for future native frame injection |
| **Manager APK** | Root UI to place `virtual.mp4`, toggle flag files, show status |

No LSPosed. No Xposed. No second framework.

## Status

| Feature | Status |
|---------|--------|
| Magisk module (paths + flags env) | **v1.3.0 ready** |
| Manager APK (Compose + libsu) | **Builds via GitHub Actions** |
| Classic path/flag control | **Working** |
| Zygisk native Camera hooks | Skeleton ready — implementation next |

> **Honest note:** Original VCAM replaced camera frames by **Xposed-hooking** Camera1/Camera2 APIs, then reading `virtual.mp4` from Camera1. Our Magisk module + APK fully own that control plane. Real frame injection without LSPosed requires the Zygisk native libraries (in progress under `magisk-module/zygisk/`).

## Download

### Magisk Module
- Flash `VirtualCam-Manager-Magisk-v1.3.0.zip` (build from `magisk-module/` or project artifacts)

### APK
- **Actions → Build APK → latest green run → Artifacts**
  - `VirtualCam-Manager-debug`
  - `VirtualCam-Manager-release`

## Classic paths (exact)

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

## Install & test

1. Enable **Zygisk** in Magisk settings (needed later for native hooks; harmless now)
2. Flash Magisk module → reboot
3. Install Manager APK → grant root
4. Home tab: confirm Root / Magisk / Module / Camera1 are green
5. Put a video named `virtual.mp4` in **Download**
6. Media tab → **Import from Downloads**
7. Settings tab → toggle flags and verify files under Camera1

## Requirements

- Magisk 27+ (Zygisk available)
- Rooted Android 8 – 16
- **No LSPosed / EdXposed / Xposed**

## Building

See [BUILD.md](BUILD.md). APK builds automatically on every push to `main`.

## Disclaimer

For legitimate privacy research, testing, and development only. Do not use for illegal purposes.
