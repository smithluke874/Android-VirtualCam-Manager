# Android VirtualCam Manager

**Pure Magisk module + single controller APK**  
**NO LSPosed / NO Xposed manager.**

Reimplements classic [android_virtual_cam](https://github.com/w2016561536/android_virtual_cam) control plane under Magisk+Zygisk. Frame injection uses the same algorithm (MediaPlayer surface + NV21 callbacks) driven by LSPlant ART hooks inside our Zygisk `.so`.

## Status (v1.9.1)

| Layer | Status |
|-------|--------|
| Magisk paths + flags | **Working** |
| APK one-tap enable / import | **Working** |
| Zygisk `.so` (arm64/armv7/x86_64) | **CI builds** |
| Process gate + `hook_status` feedback | **Working** |
| Original algorithm documented | **Done** — `docs/ORIGINAL_HOOK_ALGORITHM.md` |
| LSPlant scaffolding (InitInfo + targets) | **Done** |
| Status reports **preparing** | **Done** (v1.9.1) |
| Real LSPlant ART hooks (frame replace) | **Next** |

## Download

[Actions ← latest green run ← Artifacts](https://github.com/smithluke874/Android-VirtualCam-Manager/actions)

- `VirtualCam-Manager-debug` / `release`
- `VirtualCam-Manager-Magisk-v1.9.1` (includes Zygisk `.so`)

## Install & verify control plane

1. Magisk → **Zygisk ON** → flash Magisk zip → reboot  
2. Install APK → grant root → Home **ON**  
3. Media → pick image/video → one-tap import (encodes image → looping MP4 if needed)  
4. Open a camera app → return Home → refresh  
   - **Zygisk .so installed** = green  
   - **hook status: hooked / active / preparing** + last package = gate works  
5. Until full LSPlant lands, the *image* is still the real camera; gate + paths + JNI hooks are live.

## Architecture

```
APK (root)  --writes-->  /data/adb/virtualcam/*  +  /DCIM/Camera1/*
                              ^
Magisk boot scripts ----------+
                              |
Zygisk .so  --reads-----------+  --writes--> hook_status / last_hook_pkg / version
         |
         +-- (next) LSPlant hooks Camera.* per original HookMain
```

## Disclaimer

Legitimate research / testing only. Do not use for illegal purposes.
