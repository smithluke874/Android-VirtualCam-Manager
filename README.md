# Android VirtualCam Manager

**Pure Magisk module + single controller APK**  
**NO LSPosed / NO Xposed manager.**

Reimplements classic [android_virtual_cam](https://github.com/w2016561536/android_virtual_cam) control plane under Magisk+Zygisk. Frame injection uses the same algorithm (MediaPlayer surface + NV21 callbacks). JNI native hooks already attempt MediaPlayer playback onto the preview Surface; full `setPreviewTexture` swap + `onPreviewFrame` NV21 overwrite will be driven by ArtHook / LSPlant ART hooks inside our Zygisk `.so`.

## Status (v1.12.0)

| Layer | Status |
|-------|--------|
| Magisk paths + flags | **Working** |
| APK one-tap enable / import | **Working** |
| Zygisk `.so` (arm64/armv7/x86_64) | **CI builds** |
| Process gate + `hook_status` feedback | **Working** |
| Original algorithm documented | **Done** — `docs/ORIGINAL_HOOK_ALGORITHM.md` |
| LSPlant scaffolding (targets + reflection) | **Done** |
| JNI reflection of Camera Java methods | **Done** — status `ready_for_lsplant:N/6` |
| Frame provider + MediaPlayer surface attempt | **Done** (v1.12.0) — status can become `surface_playing` |
| Real ART Java hooks (setPreviewTexture / NV21) | **Next** (ArtHook or LSPlant) |

## Download

[Actions ← latest green run ← Artifacts](https://github.com/smithluke874/Android-VirtualCam-Manager/actions)

- `VirtualCam-Manager-debug` / `release`
- `VirtualCam-Manager-Magisk-v1.12.0` (includes Zygisk `.so`)

## Install & verify control plane

1. Magisk → **Zygisk ON** → flash Magisk zip → reboot  
2. Install APK → grant root → Home **ON**  
3. Media → pick image/video → one-tap import (encodes image → looping MP4 if needed)  
4. Open a camera app that uses Camera1 `setPreviewDisplay` / `startPreview` → return Home → refresh  
   - **Zygisk .so installed** = green  
   - **Hook: ready_for_lsplant:N/6** or **hooked+ready:N** + last package = gate + reflection work  
   - **surface_playing** = MediaPlayer successfully started on the preview Surface (visual may still be mixed with real camera until ART hooks land)
5. Full visual spoof (real camera pipeline replaced) still needs the ART Java method hooks.

## Hook status values (written by Zygisk)

| Status | Meaning |
|--------|---------|
| `gate_off` | Enabled flag off or `disable.jpg` present |
| `no_video` | Gate open but `virtual.mp4` missing/empty |
| `preparing:N/6` | Reflecting Camera methods (partial) |
| `ready_for_lsplant:N/6` | Core Camera Java methods visible — ready for real ART hooks |
| `hooked` / `hooked+ready:N` | JNI native hooks also installed |
| `surface_playing` | MediaPlayer started on preview Surface (v1.12.0) |
| `inject_attempt_failed` | MediaPlayer prepare/start failed |
| `active` | Fallback when natives not matched on this ROM |

## Architecture

```
APK (root)  --writes-->  /data/adb/virtualcam/*  +  /DCIM/Camera1/*
                              ^
Magisk boot scripts ----------+
                              |
Zygisk .so  --reads-----------+  --writes--> hook_status / last_hook_pkg / version
         |
         +-- JNI native hooks: store Surface, start MediaPlayer(virtual.mp4)
         +-- (next) ArtHook / LSPlant: setPreviewTexture swap + onPreviewFrame NV21
```

## Disclaimer

Legitimate research / testing only. Do not use for illegal purposes.
