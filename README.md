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
| MediaCodec continuous video→NV21 | **Done** (v1.16.0) — `nv21_video:WxH#N` |
| Camera2 surface redirect | Later |

## Download

[Actions ← latest green run ← Artifacts](https://github.com/smithluke874/Android-VirtualCam-Manager/actions)

- `VirtualCam-Manager-debug` / `release`
- `VirtualCam-Manager-Magisk-v1.16.0` (includes Zygisk `.so` with ArtHook + MediaCodec)

## Install (3 steps)

1. **Magisk** → Settings → **Zygisk ON** → Modules → Install from storage → flash `VirtualCam-Manager-Magisk-v1.16.0.zip` → **reboot**
2. Install the **Manager APK** → open it → **allow root** when Magisk asks
3. On **Home**:
   - Tap **Pick video or image** (images become a looping video automatically)
   - Flip **VirtualCam ON**
   - Open any camera app — done

The Home screen always shows the next step in plain English. While ON, status updates automatically every few seconds. Technical details are behind **Show details**.

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
| `nv21_cb_hooked:cb/buf/oneshot` | Dynamic onPreviewFrame hook installed |
| `nv21_pattern:WxH#N` | Pattern NV21 frames delivered to callback |
| `nv21_video:WxH#N` | Real MediaCodec frames from virtual.mp4 delivered to callback |
| `nv21_decoder_start` / `nv21_video_ready` | Decoder thread starting / ready |
| `nv21_decoder_fail` | Decoder could not start — pattern fallback |
| `nv21_cb_fail:*` | onPreviewFrame ArtHook install failed |
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
         +-- ArtHook: setPreviewCallback* → process_callback
         +-- ArtHook: callback.onPreviewFrame → overwrite NV21 buffer
         +-- MediaCodec decoder thread → continuous video→NV21
         +-- JNI native hooks (secondary)
```

## Disclaimer

Legitimate research / testing only. Do not use for illegal purposes.
