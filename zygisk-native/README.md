# Zygisk native module (VirtualCam)

Pure Magisk Zygisk — **no LSPosed**.

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
| `/data/adb/virtualcam/config` | optional key=value |
| `/DCIM/Camera1/virtual.mp4` | source video |
| `/DCIM/Camera1/disable.jpg` | hard off (classic flag) |

## Status

- Companion + enable/disable detection: **done**
- Process selection + stay-loaded when active: **done**
- Camera1/Camera2 frame injection: **next** (hooks stubbed in `install_hooks()`)
