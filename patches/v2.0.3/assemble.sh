#!/bin/bash
# v2.0.3-probe1 assemble — reassemble probe/doctor sources from chunks
set -e
cd "$(dirname "$0")/../.."
SRC=patches/v2.0.3
echo "=== v2.0.3-probe1 assemble ==="

if [ -f "$SRC/gl_hooks.00" ]; then
  cat "$SRC"/gl_hooks.* > zygisk-native/jni/gl_hooks.cpp
  echo "Assembled gl_hooks.cpp ($(wc -c < zygisk-native/jni/gl_hooks.cpp) bytes)"
fi
if [ -f "$SRC/homescreen.00" ]; then
  cat "$SRC"/homescreen.* > manager-app/app/src/main/java/com/virtualcam/manager/ui/screens/HomeScreen.kt
  echo "Assembled HomeScreen.kt ($(wc -c < manager-app/app/src/main/java/com/virtualcam/manager/ui/screens/HomeScreen.kt) bytes)"
fi
if [ -f "$SRC/prereq.00" ]; then
  cat "$SRC"/prereq.* > manager-app/app/src/main/java/com/virtualcam/manager/data/PrerequisiteChecker.kt
  echo "Assembled PrerequisiteChecker.kt"
fi
if [ -f "$SRC/targetapps.00" ]; then
  cat "$SRC"/targetapps.* > manager-app/app/src/main/java/com/virtualcam/manager/ui/screens/TargetAppsScreen.kt
  echo "Assembled TargetAppsScreen.kt"
fi
if [ -f "$SRC/build_yml.00" ]; then
  cat "$SRC"/build_yml.* > .github/workflows/build.yml
  echo "Assembled build.yml"
fi
if [ -f "$SRC/readme.00" ]; then
  cat "$SRC"/readme.* > README.md
  echo "Assembled README.md"
fi

# Verify probe symbols
grep -n 'run_probe_if_requested\|write_diag\|probe_request' zygisk-native/jni/gl_hooks.cpp | head -5 || true
grep -n 'requestProbe\|Doctor\|pathMode' manager-app/app/src/main/java/com/virtualcam/manager/ui/screens/HomeScreen.kt | head -5 || true
echo "=== v2.0.3 assemble done ==="
