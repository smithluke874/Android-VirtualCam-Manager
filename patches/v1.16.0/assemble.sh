#!/bin/bash
set -e
cd "$(dirname "$0")/../.."
echo "=== v1.16.0 MediaCodec assemble ==="

# Apply main.cpp version bump + setVideoPath (safe: pattern header already has no-op setVideoPath)
if [ -f patches/v1.16.0/main_v116.patch ]; then
  echo "Applying main_v116.patch..."
  patch -p1 < patches/v1.16.0/main_v116.patch && echo "main.cpp patched" || echo "main patch skipped/failed"
fi

# Assemble MediaCodec nv21_hooks ONLY when BOTH parts present
if [ -f patches/v1.16.0/nv21_hooks.p1.cpp ] && [ -f patches/v1.16.0/nv21_hooks.p2.cpp ]; then
  cat patches/v1.16.0/nv21_hooks.p1.cpp patches/v1.16.0/nv21_hooks.p2.cpp > zygisk-native/jni/nv21_hooks.cpp
  echo "Assembled MediaCodec nv21_hooks.cpp ($(wc -c < zygisk-native/jni/nv21_hooks.cpp) bytes)"
  if [ -f patches/v1.16.0/nv21_hooks.h ]; then
    cp patches/v1.16.0/nv21_hooks.h zygisk-native/jni/nv21_hooks.h
    echo "Installed MediaCodec nv21_hooks.h"
  fi
else
  echo "MediaCodec parts not yet staged — keeping pattern NV21 path"
fi

echo "=== Source status ==="
grep -n '1\.16\.0\|1\.15\.0' zygisk-native/jni/main.cpp | head -5 || true
grep -n 'NdkMediaCodec\|fillPattern\|fillLocked\|fillPatternLocked' zygisk-native/jni/nv21_hooks.cpp | head -8 || true
grep -n 'setVideoPath\|isVideo\|decoderLoop' zygisk-native/jni/nv21_hooks.h | head -5 || true
