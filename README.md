# Android VirtualCam Manager

**Pure Magisk module + single controller APK**  
**NO LSPosed / NO Xposed manager.**

Reimplements classic [android_virtual_cam](https://github.com/w2016561536/android_virtual_cam) control plane under Magisk+Zygisk. Frame injection uses the same algorithm (MediaPlayer surface + NV21 callbacks) driven by LSPlant ART hooks inside our Zygisk `.so`.

## Status (v1.10.0)

| Layer | Status |
|-------|--------|
| Magisk paths + flags | **Working** |
| APK one-tap enable / import | **Working** |
| Zygisk `.so` (arm64/armv7/x86_64) | **CI builds** |
| Process gate + `hook_status` feedback | **Working** |
| Original algorithm documented | **Done** — `docs/ORIGINAL_HOOK_ALGORITHM.md` |
| LSPlant scaffolding (InitInfo + targets) | **Done** |
| JNI reflection of Camera Java methods | **Done** (v1.10.0) — status `methods_ok:N/6` |
| Real LSPlant ART hooks (frame replace) | **Next** |

## Download

[Actions ← latest green run ← Artifacts](https://github.com/smithluke874/Android-VirtualCam-Manager/actions)

- `VirtualCam-Manager-debug` / `release`
- `VirtualCam-Manager-Magisk-v1.10.0` (includes Zygisk `.so`)

## Install & verify control plane

1. Magisk → **Zygisk ON** → flash Magisk zip → reboot  
2. Install APK → grant root → Home **ON**  
3. Media → pick image/video → one-tap import (encodes image → looping MP4 if needed)  
4. Open a camera app → return Home → refresh  
   - **Zygisk .so installed** = green  
   - **Hook: methods_ok:N/6** or **hooked+methods:N** + last package = gate + reflection work  
5. Until full LSPlant lands, the *image* is still the real camera; gate + paths + JNI hooks + method reflection are live.

## Hook status values (written by Zygisk)

| Status | Meaning |
|--------|---------|
| `gate_off` | Enabled flag off or `disable.jpg` present |
| `no_video` | Gate open but `virtual.mp4` missing/empty |
| `preparing:N/6` | Reflecting Camera methods (partial) |
| `methods_ok:N/6` | Core Camera Java methods visible — ready for LSPlant |
| `hooked` / `hooked+methods:N` | JNI native hooks also installed |
| `active` | Fallback when natives not matched on this ROM |

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
