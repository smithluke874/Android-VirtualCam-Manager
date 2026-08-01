#!/bin/bash
set -e
cd "$(dirname "$0")/../.."
echo "=== v1.16.0 assemble ==="

# main.cpp version bump + setVideoPath
if [ -f patches/v1.16.0/main_v116.patch ]; then
  echo "Applying main_v116.patch..."
  patch -p1 < patches/v1.16.0/main_v116.patch && echo "main.cpp patched" || echo "main patch skipped/failed"
fi

# MediaCodec nv21_hooks
SRC=patches/v1.16.0/src
if [ -f "$SRC/p1.00" ] && [ -f "$SRC/p2.00" ]; then
  cat "$SRC"/p1.* "$SRC"/p2.* > zygisk-native/jni/nv21_hooks.cpp
  echo "Assembled MediaCodec nv21_hooks.cpp ($(wc -c < zygisk-native/jni/nv21_hooks.cpp) bytes)"
  if [ -f patches/v1.16.0/nv21_hooks.h ]; then
    cp patches/v1.16.0/nv21_hooks.h zygisk-native/jni/nv21_hooks.h
    echo "Installed MediaCodec nv21_hooks.h"
  fi
else
  echo "MediaCodec parts not staged — keeping pattern NV21 path"
fi

# Simplified HomeScreen (plain-English next step + media pick on Home)
HS=patches/v1.16.0/homescreen
if [ -f "$HS/hs.00" ]; then
  DEST=manager-app/app/src/main/java/com/virtualcam/manager/ui/screens/HomeScreen.kt
  cat "$HS"/hs.* > "$DEST"
  echo "Assembled simplified HomeScreen.kt ($(wc -c < "$DEST") bytes)"
fi

echo "=== Source status ==="
grep -n '1\.16\.0\|1\.15\.0' zygisk-native/jni/main.cpp | head -5 || true
grep -n 'NdkMediaCodec\|fillPatternLocked\|decoderLoop' zygisk-native/jni/nv21_hooks.cpp | head -8 || true
grep -n 'Pick video\|Next step\|VirtualCam is ON' manager-app/app/src/main/java/com/virtualcam/manager/ui/screens/HomeScreen.kt | head -5 || true
