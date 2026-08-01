#!/bin/bash
set -e
cd "$(dirname "$0")/../.."
echo "=== v1.16.0 MediaCodec assemble ==="

# Apply main.cpp version bump + setVideoPath
if [ -f patches/v1.16.0/main_v116.patch ]; then
  echo "Applying main_v116.patch..."
  patch -p1 < patches/v1.16.0/main_v116.patch && echo "main.cpp patched" || echo "main patch skipped/failed"
fi

# Assemble from src chunks if present
SRC=patches/v1.16.0/src
if [ -f "$SRC/p1.00" ] && [ -f "$SRC/p2.00" ]; then
  cat "$SRC"/p1.* "$SRC"/p2.* > zygisk-native/jni/nv21_hooks.cpp
  echo "Assembled MediaCodec nv21_hooks.cpp ($(wc -c < zygisk-native/jni/nv21_hooks.cpp) bytes)"
  if [ -f patches/v1.16.0/nv21_hooks.h ]; then
    cp patches/v1.16.0/nv21_hooks.h zygisk-native/jni/nv21_hooks.h
    echo "Installed MediaCodec nv21_hooks.h"
  fi
elif [ -f patches/v1.16.0/nv21_hooks.p1.cpp ] && [ -f patches/v1.16.0/nv21_hooks.p2.cpp ]; then
  cat patches/v1.16.0/nv21_hooks.p1.cpp patches/v1.16.0/nv21_hooks.p2.cpp > zygisk-native/jni/nv21_hooks.cpp
  echo "Assembled from p1/p2 ($(wc -c < zygisk-native/jni/nv21_hooks.cpp) bytes)"
  if [ -f patches/v1.16.0/nv21_hooks.h ]; then
    cp patches/v1.16.0/nv21_hooks.h zygisk-native/jni/nv21_hooks.h
  fi
else
  echo "MediaCodec parts not staged — keeping pattern NV21 path"
fi

echo "=== Source status ==="
grep -n '1\.16\.0\|1\.15\.0' zygisk-native/jni/main.cpp | head -5 || true
grep -n 'NdkMediaCodec\|fillPatternLocked\|decoderLoop' zygisk-native/jni/nv21_hooks.cpp | head -8 || true
grep -n 'setVideoPath\|isVideo\|decoderLoop' zygisk-native/jni/nv21_hooks.h | head -5 || true
