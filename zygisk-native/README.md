# Zygisk native module (VirtualCam)

Pure Magisk Zygisk — **no LSPosed manager**.

## Build (on CI or with NDK)

```bash
# Requires Android NDK
export NDK=$ANDROID_NDK_HOME   # e.g. $ANDROID_HOME/ndk/26.1.10909125
cd zygisk-native
$NDK/ndk-build -j$(nproc)

# Outputs:
#   libs/arm64-v8a/libvirtualcam.so
#   libs/armeabi-v7a/libvirtualcam.so
#   libs/x86_64/libvirtualcam.so

# Package into Magisk module:
cp libs/arm64-v8a/libvirtualcam.so   ../magisk-module/zygisk/arm64-v8a.so
cp libs/armeabi-v7a/libvirtualcam.so ../magisk-module/zygisk/armeabi-v7a.so
cp libs/x86_64/libvirtualcam.so      ../magisk-module/zygisk/x86_64.so
```

`zygisk.hpp` is pulled from [topjohnwu/zygisk-module-sample](https://github.com/topjohnwu/zygisk-module-sample) during CI.

## Control plane (APK writes, Zygisk reads)

| Path | Meaning |
|------|---------|
| `/data/adb/virtualcam/enabled` | `1` = on, `0` = off |
| `/data/adb/virtualcam/hook_status` | live status written by Zygisk |
| `/data/adb/virtualcam/last_hook_pkg` | last package that received hooks |
| `/data/adb/virtualcam/module_version` / `version` | e.g. 1.9.0 |
| `/DCIM/Camera1/virtual.mp4` | source video |
| `/DCIM/Camera1/disable.jpg` | hard off (classic flag) |

## Status values written to hook_status

- `gate_off` – enabled flag is 0 or disable.jpg present
- `no_video` – virtual.mp4 missing or empty
- `active` – gate open + video present, but no matching JNI natives on this ROM
- `hooked` – JNI Camera1 native methods successfully rewritten
- `preparing` – (v1.9+) LSPlant scaffold active, waiting for real Init/Hook
- `injecting` – (future) LSPlant Java hooks + frame replacement active

## Current capability (v1.9.0)

- Companion process gate + enable/disable detection: **done**
- Process selection + DLCLOSE when inactive: **done**
- JNI native hooks on android.hardware.Camera (log + call original): **done**
- Module version written from native + service.sh: **done**
- LSPlant InitInfo shape + target method list documented in source: **done**
- Full visual frame injection (setPreviewTexture / NV21 overwrite): **next**

## Next: real LSPlant integration (pure Magisk, no LSPosed manager)

Target Java methods (from docs/ORIGINAL_HOOK_ALGORITHM.md):

1. `Camera.setPreviewTexture(SurfaceTexture)`
2. `Camera.setPreviewCallback` / `setPreviewCallbackWithBuffer` / `setOneShotPreviewCallback`
3. `Camera.startPreview`
4. `PreviewCallback.onPreviewFrame` for NV21 replacement

Plan:
- Add lsplant-standalone headers + static lib (or build from source in CI with CMake)
- Provide InitInfo with inline_hooker (Dobby or equivalent) + art_symbol_resolver
- Init in postAppSpecialize after gate opens
- Keep existing JNI hooks as secondary / logging layer
- Write richer status so APK can show “injecting”

This keeps the project 100% Magisk module + single companion APK.
