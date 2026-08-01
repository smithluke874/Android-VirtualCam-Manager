# Android VirtualCam Manager

**Pure Magisk module + single controller APK**  
**NO LSPosed / NO Xposed manager.**

Reimplements classic [android_virtual_cam](https://github.com/w2016561536/android_virtual_cam) control plane under Magisk+Zygisk. Frame injection uses the same algorithm (MediaPlayer surface + NV21 callbacks). **ArtHook** provides ART Java method replacement inside the Zygisk `.so` (no LSPosed).

## Status (v1.16.0)

| Layer | Status |
|-------|--------|
| Magisk paths + flags | **Working** |
| APK one-tap enable / import | **Working** |
| Zygisk `.so` (arm64/armv7/x86_64) | **CI builds (CMake + ArtHook)** |
| Process gate + `hook_status` feedback | **Working** |
| Original algorithm documented | **Done** |
| **ArtHook** surface path (`setPreviewTexture` / `startPreview` / `setPreviewDisplay`) | **Done** |
| MediaPlayer surface injection | **Done** — `texture_swapped` → `surface_playing` |
| **ArtHook** `setPreviewCallback*` + dynamic `onPreviewFrame` | **Done** |
| NV21 buffer overwrite (pattern) | **Done** — `nv21_pattern:WxH#N` |
| MediaCodec continuous video→NV21 | **Implemented locally** — landing next (source ready) |
| Camera2 surface redirect | Later |

## Download

[Actions ← latest green run ← Artifacts](https://github.com/smithluke874/Android-VirtualCam-Manager/actions)

- `VirtualCam-Manager-debug` / `release`
- `VirtualCam-Manager-Magisk-v1.16.0`

## Install & verify

1. Magisk → **Zygisk ON** → flash Magisk zip → reboot  
2. Install APK → grant root → Home **ON**  
3. Media → pick image/video → one-tap import  
4. Open a **Camera1** app → return Home → refresh  
   - **Zygisk .so installed** = green  
   - **`texture_swapped` / `surface_playing`** = surface path  
   - **`nv21_cb_hooked` / `nv21_pattern:WxH#N`** = PreviewCallback path  
5. Surface path shows `virtual.mp4` on preview.  
6. Callback path receives patterned NV21 (real video frames next).

## Hook status values

| Status | Meaning |
|--------|--------|
| `gate_off` | Enabled flag off or `disable.jpg` present |
| `no_video` | Gate open but `virtual.mp4` missing/empty |
| `art_hooked:N` / `art_hooked:N+jni:M` | ArtHook installed |
| `texture_swapped` | Fake SurfaceTexture installed |
| `surface_playing` | MediaPlayer playing virtual.mp4 |
| `nv21_cb_hooked:*` | onPreviewFrame hook installed |
| `nv21_pattern:WxH#N` | Pattern NV21 frames delivered |
| `nv21_video:WxH#N` | Real video NV21 (after MediaCodec lands) |

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
         +-- ArtHook: setPreviewCallback* → process_callback
         +-- ArtHook: callback.onPreviewFrame → overwrite NV21 buffer
         +-- JNI native hooks (secondary)
```

## Disclaimer

Legitimate research / testing only. Do not use for illegal purposes.
