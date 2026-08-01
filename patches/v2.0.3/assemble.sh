#!/bin/bash
# v2.0.3-probe1 assemble — probe wiring + optional full chunk sources
set -e
cd "$(dirname "$0")/../.."
SRC=patches/v2.0.3
echo "=== v2.0.3-probe1 assemble ==="

# 1) Apply minimal gl_hooks probe wiring (paths + include + call sites)
if [ -f "$SRC/gl_hooks_probe.patch" ] && [ -f zygisk-native/jni/gl_hooks.cpp ]; then
  if ! grep -q 'vcam_probe.inc\|kProbeReqPath' zygisk-native/jni/gl_hooks.cpp 2>/dev/null; then
    patch -p1 -N -r - < "$SRC/gl_hooks_probe.patch" >/dev/null 2>&1 \
      && echo "Applied gl_hooks_probe.patch" \
      || echo "gl_hooks_probe.patch skipped (already applied or conflict)"
  else
    echo "gl_hooks already has probe wiring"
  fi
fi

# 2) Optional full-file chunk reassembly (only if complete + symbol-valid)
assemble_if_complete() {
  local pattern="$1" dest="$2" needle="$3"
  if ls "$SRC"/$pattern 1>/dev/null 2>&1; then
    local tmp; tmp=$(mktemp)
    cat "$SRC"/$pattern > "$tmp"
    if grep -q "$needle" "$tmp" 2>/dev/null; then
      cp "$tmp" "$dest"
      echo "Assembled $dest ($(wc -c < "$dest") bytes)"
    else
      echo "SKIP $dest — chunks incomplete (missing $needle)"
    fi
    rm -f "$tmp"
  fi
}

# 3) Base64-encoded PrerequisiteChecker
if ls "$SRC"/prereq_b64.* 1>/dev/null 2>&1; then
  B64=$(cat "$SRC"/prereq_b64.*)
  if echo "$B64" | base64 -d > /tmp/prereq_decoded.kt 2>/dev/null; then
    if grep -q 'requestProbe' /tmp/prereq_decoded.kt; then
      cp /tmp/prereq_decoded.kt manager-app/app/src/main/java/com/virtualcam/manager/data/PrerequisiteChecker.kt
      echo "Assembled PrerequisiteChecker.kt from b64 ($(wc -c < manager-app/app/src/main/java/com/virtualcam/manager/data/PrerequisiteChecker.kt) bytes)"
    fi
  fi
  rm -f /tmp/prereq_decoded.kt
fi

assemble_if_complete 'homescreen.*' manager-app/app/src/main/java/com/virtualcam/manager/ui/screens/HomeScreen.kt 'requestProbe'
assemble_if_complete 'targetapps.*' manager-app/app/src/main/java/com/virtualcam/manager/ui/screens/TargetAppsScreen.kt 'Roadmap'
assemble_if_complete 'build_yml.*' .github/workflows/build.yml 'v2.0.3-probe1'
assemble_if_complete 'readme.*' README.md 'Failure Doctor'

# Verify
grep -n 'vcam_probe\|kProbeReqPath\|run_probe' zygisk-native/jni/gl_hooks.cpp 2>/dev/null | head -5 || true
test -f zygisk-native/jni/vcam_probe.inc && echo "vcam_probe.inc present" || echo "WARN: vcam_probe.inc missing"
echo "=== v2.0.3 assemble done ==="
