# Android VirtualCam Manager

**Pure Magisk module + single controller APK**  
**NO LSPosed / NO Xposed manager.**

Reimplements classic [android_virtual_cam](https://github.com/w2016561536/android_virtual_cam) control plane under Magisk+Zygisk. Frame injection uses the same algorithm (MediaPlayer surface + NV21 callbacks). **ArtHook** provides ART Java method replacement inside the Zygisk `.so` (no LSPosed).

## Status (v1.13.0)

| Layer | Status |
|-------|--------|
| Magisk paths + flags | **Working** |
| APK one-tap enable / import | **Working** |
| Zygisk `.so` (arm64/armv7/x86_64) | **CI builds (CMake + ArtHook)** |
| Process gate + `hook_status` feedback | **Working** |
| Original algorithm documented | **Done** — `docs/ORIGINAL_HOOK_ALGORITHM.md` |
| Method reflection scaffold | **Done** — `ready_for_arthook:N/6` |
| **ArtHook ART hooks** (`setPreviewTexture` / `startPreview` / `setPreviewDisplay`) | **Done** (v1.13.0) |
| MediaPlayer surface injection | **Done** — status `texture_swapped` → `surface_playing` |
| NV21 PreviewCallback overwrite | **Next** (MediaCodec frame provider) |
| Camera2 surface redirect | Later |

## Download

[Actions ← latest green run ← Artifacts](https://github.com/smithluke874/Android-VirtualCam-Manager/actions)

- `VirtualCam-Manager-debug` / `release`
- `VirtualCam-Manager-Magisk-v1.13.0` (includes Zygisk `.so` with ArtHook)

## Install & verify

1. Magisk → **Zygisk ON** → flash Magisk zip → reboot  
2. Install APK → grant root → Home **ON**  
3. Media → pick image/video → one-tap import (encodes image → looping MP4 if needed)  
4. Open a **Camera1** app → return Home → refresh  
   - **Zygisk .so installed** = green  
   - **Hook: `art_hooked:N+jni:M`** or **`texture_swapped` / `surface_playing`** = ART + MediaPlayer path active  
5. Apps that use `Camera.setPreviewTexture` + `startPreview` should show `virtual.mp4` on the preview surface.

## Hook status values (written by Zygisk)

| Status | Meaning |
|--------|--------|
| `gate_off` | Enabled flag off or `disable.jpg` present |
| `no_video` | Gate open but `virtual.mp4` missing/empty |
| `preparing:N/6` | Reflecting Camera methods (partial) |
| `ready_for_arthook:N/6` | Core Camera Java methods visible |
| `art_hooked:N` / `art_hooked:N+jni:M` | ArtHook installed on N methods |
| `texture_swapped` | `setPreviewTexture` replaced arg with fake ST |
| `surface_playing` | MediaPlayer started on original surface/texture |
| `inject_attempt_failed` | MediaPlayer prepare/start failed |
| `arthook_init_fail` | ArtHook layout discovery failed on this ROM |
| `hooked` / `hooked+ready:N` | JNI native hooks only (fallback) |

## Architecture

```
APK (root)  --writes-->  /data/adb/virtualcam/*  +  /DCIM/Camera1/*
                              ^
Magisk boot scripts ----------+
                              |
Zygisk .so  --reads-----------+  --writes--> hook_status / last_hook_pkg / version
         |
         +-- ArtHook: Camera.setPreviewTexture → fake ST
         +-- ArtHook: Camera.startPreview → MediaPlayer(virtual.mp4)
         +-- JNI native hooks (secondary)
```

## Disclaimer

Legitimate research / testing only. Do not use for illegal purposes.
