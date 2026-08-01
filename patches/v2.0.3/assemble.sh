#!/bin/bash
# v2.0.3-probe1 assemble — reassemble probe/doctor sources from chunks
# Only overwrites tree files when reconstructed content has required symbols.
set -e
cd "$(dirname "$0")/../.."
SRC=patches/v2.0.3
echo "=== v2.0.3-probe1 assemble ==="

assemble_if_complete() {
  local pattern="$1" dest="$2" needle="$3"
  if ls "$SRC"/$pattern 1>/dev/null 2>&1; then
    local tmp
    tmp=$(mktemp)
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

assemble_if_complete 'gl_hooks.*' zygisk-native/jni/gl_hooks.cpp 'run_probe_if_requested'
assemble_if_complete 'homescreen.*' manager-app/app/src/main/java/com/virtualcam/manager/ui/screens/HomeScreen.kt 'requestProbe'
assemble_if_complete 'prereq.*' manager-app/app/src/main/java/com/virtualcam/manager/data/PrerequisiteChecker.kt 'requestProbe'
assemble_if_complete 'targetapps.*' manager-app/app/src/main/java/com/virtualcam/manager/ui/screens/TargetAppsScreen.kt 'Roadmap'
assemble_if_complete 'build_yml.*' .github/workflows/build.yml 'v2.0.3-probe1'
assemble_if_complete 'readme.*' README.md 'Failure Doctor'

echo "=== v2.0.3 assemble done ==="
