#!/bin/bash
# v2.0.3-probe1 assemble — probe wiring only (Kotlin sources committed, not from b64)
set -e
cd "$(dirname "$0")/../.."
SRC=patches/v2.0.3
echo "=== v2.0.3-probe1 assemble ==="

# 1) gl_hooks probe wiring
if [ -f "$SRC/gl_hooks_probe.patch" ] && [ -f zygisk-native/jni/gl_hooks.cpp ]; then
  if ! grep -q 'vcam_probe.inc\|kProbeReqPath' zygisk-native/jni/gl_hooks.cpp 2>/dev/null; then
    patch -p1 -N -r - < "$SRC/gl_hooks_probe.patch" >/dev/null 2>&1 \
      && echo "Applied gl_hooks_probe.patch" \
      || echo "gl_hooks_probe.patch skipped"
  else
    echo "gl_hooks already has probe wiring"
  fi
fi

# Kotlin sources are committed directly — do not overwrite from b64 during CI
echo "Skipping prereq/doctor b64 assemble (sources committed)"
echo "Skipping HomeScreen inject (sources committed)"

# Verify
grep -n 'vcam_probe\|kProbeReqPath\|run_probe' zygisk-native/jni/gl_hooks.cpp 2>/dev/null | head -4 || true
test -f zygisk-native/jni/vcam_probe.inc && echo "vcam_probe.inc present" || echo "WARN: vcam_probe.inc missing"
test -f manager-app/app/src/main/java/com/virtualcam/manager/data/PrerequisiteChecker.kt && echo "PrerequisiteChecker present" || true
echo "=== v2.0.3 assemble done ==="
