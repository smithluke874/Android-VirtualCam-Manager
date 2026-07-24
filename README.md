# Android VirtualCam Manager

**Pure Magisk module + single controller APK**  
**NO LSPosed / NO Xposed manager.**

Reimplements classic [android_virtual_cam](https://github.com/w2016561536/android_virtual_cam) control plane under Magisk+Zygisk. Frame injection uses the same algorithm (MediaPlayer surface + NV21 callbacks) driven by LSPlant ART hooks inside our Zygisk `.so`.

## Status

| Layer | Status |
|-------|--------|
| Magisk paths + flags | **Working** |
| APK one-tap enable / import | **Working** |
| Zygisk `.so` (arm64/armv7/x86_64) | **CI builds** |
| Process gate + `hook_status` feedback | **Working** |
| Original algorithm documented | **Done** — `docs/ORIGINAL_HOOK_ALGORITHM.md` |
| LSPlant ART hooks (frame replace) | **Next** |

## Download

[Actions ← latest green run ← Artifacts](https://github.com/smithluke874/Android-VirtualCam-Manager/actions)

- `VirtualCam-Manager-debug` / `release`
- `VirtualCam-Manager-Magisk-v1.5.0` (includes Zygisk `.so`)

## Install & verify control plane

1. Magisk → **Zygisk ON** → flash Magisk zip → reboot  
2. Install APK → grant root → Home **ON**  
3. Media → put `virtual.mp4` in Download → **Import**  
4. Open a camera app → return Home → refresh  
   - **Zygisk .so installed** = green  
   - **hook status: active** + last package = gate works  
5. Until LSPlant lands, the *image* is still the real camera; gate+paths are live.

## Architecture

```
APK (root)  --writes-->  /data/adb/virtualcam/*  +  /DCIM/Camera1/*
                              ^
Magisk boot scripts ----------+
                              |
Zygisk .so  --reads-----------+  --writes--> hook_status / last_hook_pkg
         |
         +-- (next) LSPlant hooks Camera.* per original HookMain
```

## Disclaimer

Legitimate research / testing only. Do not use for illegal purposes.
