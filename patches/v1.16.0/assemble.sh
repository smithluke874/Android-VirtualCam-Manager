#!/bin/bash
# v1.16.0 / v2 assemble guard — NEVER clobber a complete v2 HomeScreen
set -e
cd "$(dirname "$0")/../.."
echo "=== assemble (v2-aware) ==="

# main.cpp version bump (legacy patch — skip if already v2)
if [ -f patches/v1.16.0/main_v116.patch ]; then
  if grep -q '2\.0\.' zygisk-native/jni/main.cpp 2>/dev/null; then
    echo "main.cpp is v2 — skip v1.16 main patch"
  else
    patch -p1 -N -r - < patches/v1.16.0/main_v116.patch >/dev/null 2>&1 && echo "main.cpp patched" || echo "main patch skipped"
  fi
fi

# MediaCodec nv21_hooks — only if v2 gl_hooks is NOT primary
if [ -f zygisk-native/jni/gl_hooks.cpp ] && grep -q 'upload_oes\|install_gl_hooks' zygisk-native/jni/gl_hooks.cpp 2>/dev/null; then
  echo "v2 gl_hooks present — skip legacy nv21_hooks assembly"
else
  SRC=patches/v1.16.0/src
  if [ -f "$SRC/p1.00" ] && [ -f "$SRC/p2.00" ]; then
    cat "$SRC"/p1.* "$SRC"/p2.* > zygisk-native/jni/nv21_hooks.cpp
    echo "Assembled MediaCodec nv21_hooks.cpp"
  fi
fi

# HomeScreen: NEVER overwrite v2 UI (pathMode / oes_ready / Frames / delay(1200))
DEST=manager-app/app/src/main/java/com/virtualcam/manager/ui/screens/HomeScreen.kt
HS=patches/v1.16.0/homescreen
if [ -f "$DEST" ] && grep -qE 'pathMode|oes_ready|Frames |delay\(1200\)|decoderFrames' "$DEST" 2>/dev/null; then
  echo "Keeping tree HomeScreen (v2 telemetry UI, $(wc -c < "$DEST") bytes)"
elif [ -f "$DEST" ] && grep -q pkgSuffix "$DEST" 2>/dev/null && grep -qE 'delay\([0-9]+\)' "$DEST" 2>/dev/null; then
  echo "Keeping tree HomeScreen (live-status present, $(wc -c < "$DEST") bytes)"
elif [ -f "$HS/hs.00" ]; then
  cat "$HS"/hs.* > "$DEST"
  echo "Assembled legacy HomeScreen.kt ($(wc -c < "$DEST") bytes)"
else
  echo "HomeScreen left as-is"
fi

echo "=== Source status ==="
grep -n '2\.0\.\|install_gl_hooks\|upload_oes' zygisk-native/jni/main.cpp zygisk-native/jni/gl_hooks.cpp 2>/dev/null | head -8 || true
grep -n 'pathMode\|oes_ready\|Frames\|Pick video' "$DEST" 2>/dev/null | head -8 || true
echo "=== assemble done ==="
