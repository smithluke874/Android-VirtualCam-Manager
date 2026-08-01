#!/bin/bash
set -e
cd "$(dirname "$0")/../.."
echo "=== v1.16.0 assemble ==="

# main.cpp version bump + setVideoPath
if [ -f patches/v1.16.0/main_v116.patch ]; then
  echo "Applying main_v116.patch..."
  patch -p1 -N -r - < patches/v1.16.0/main_v116.patch >/dev/null 2>&1 && echo "main.cpp patched" || echo "main patch skipped/already applied"
fi

# MediaCodec nv21_hooks — assemble from chunks if present and complete
SRC=patches/v1.16.0/src
if [ -f "$SRC/p1.00" ] && [ -f "$SRC/p2.00" ]; then
  cat "$SRC"/p1.* "$SRC"/p2.* > zygisk-native/jni/nv21_hooks.cpp
  echo "Assembled MediaCodec nv21_hooks.cpp ($(wc -c < zygisk-native/jni/nv21_hooks.cpp) bytes)"
  if [ -f patches/v1.16.0/nv21_hooks.h ]; then
    cp patches/v1.16.0/nv21_hooks.h zygisk-native/jni/nv21_hooks.h
    echo "Installed MediaCodec nv21_hooks.h"
  fi
else
  echo "MediaCodec parts not staged — keeping tree NV21 path"
fi

# Sanity: require balanced braces and NdkMediaCodec
if grep -q NdkMediaCodec zygisk-native/jni/nv21_hooks.cpp 2>/dev/null; then
  OPEN=$(grep -o '{' zygisk-native/jni/nv21_hooks.cpp | wc -l)
  CLOSE=$(grep -o '}' zygisk-native/jni/nv21_hooks.cpp | wc -l)
  if [ "$OPEN" != "$CLOSE" ]; then
    echo "ERROR: brace imbalance in nv21_hooks.cpp ($OPEN open / $CLOSE close)"
    exit 1
  fi
  echo "MediaCodec source OK ($OPEN braces balanced)"
else
  echo "WARN: MediaCodec symbols not found — pattern-only mode"
fi

# HomeScreen: prefer tree if already live-status complete; else assemble from hs patches
HS=patches/v1.16.0/homescreen
DEST=manager-app/app/src/main/java/com/virtualcam/manager/ui/screens/HomeScreen.kt
if [ -f "$DEST" ] && grep -q pkgSuffix "$DEST" 2>/dev/null && grep -q 'delay(2000)' "$DEST" 2>/dev/null; then
  echo "Keeping tree HomeScreen (live-status already present, $(wc -c < "$DEST") bytes)"
elif [ -f "$HS/hs.00" ]; then
  cat "$HS"/hs.* > "$DEST"
  echo "Assembled HomeScreen.kt ($(wc -c < "$DEST") bytes)"
fi
if [ -f "$DEST" ] && grep -q pkgSuffix "$DEST" 2>/dev/null; then
  echo "HomeScreen live-status OK"
else
  echo "WARN: HomeScreen may be missing live status"
fi

echo "=== Source status ==="
grep -n '1\.16\.0\|1\.15\.0' zygisk-native/jni/main.cpp | head -5 || true
grep -n 'NdkMediaCodec\|fillPatternLocked\|decoderLoop\|resolve_video' zygisk-native/jni/nv21_hooks.cpp | head -8 || true
grep -n 'Pick video\|Working\|pkgSuffix\|delay(2000)' manager-app/app/src/main/java/com/virtualcam/manager/ui/screens/HomeScreen.kt | head -6 || true
