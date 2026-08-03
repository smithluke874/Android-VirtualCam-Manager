#!/bin/bash
# v2.0.3-probe1 assemble — probe wiring + UI doctor/probe
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

# 2) PrerequisiteChecker from b64
if ls "$SRC"/prereq_b64.* 1>/dev/null 2>&1; then
  B64=$(cat "$SRC"/prereq_b64.*)
  if echo "$B64" | base64 -d > /tmp/prereq_decoded.kt 2>/dev/null; then
    if grep -q 'requestProbe' /tmp/prereq_decoded.kt; then
      cp /tmp/prereq_decoded.kt manager-app/app/src/main/java/com/virtualcam/manager/data/PrerequisiteChecker.kt
      echo "Assembled PrerequisiteChecker.kt from b64"
    fi
  fi
  rm -f /tmp/prereq_decoded.kt
fi

# 3) FailureDoctorCard from b64
if false && ls "$SRC"/doctor_b64.* 1>/dev/null 2>&1; then
  B64=$(cat "$SRC"/doctor_b64.*)
  if echo "$B64" | base64 -d > /tmp/doctor_decoded.kt 2>/dev/null; then
    if grep -q 'FailureDoctorCard' /tmp/doctor_decoded.kt; then
      cp /tmp/doctor_decoded.kt manager-app/app/src/main/java/com/virtualcam/manager/ui/screens/FailureDoctorCard.kt
      echo "Assembled FailureDoctorCard.kt from b64"
    fi
  fi
  rm -f /tmp/doctor_decoded.kt
fi

# 4) Inject FailureDoctorCard call into HomeScreen if missing
HS=manager-app/app/src/main/java/com/virtualcam/manager/ui/screens/HomeScreen.kt
if false && [ -f "$HS" ] && ! grep -q 'FailureDoctorCard' "$HS" 2>/dev/null; then
  python3 - "$HS" <<'PY'
import sys
p = sys.argv[1]
src = open(p).read()
marker = 'TextButton(onClick = { details = !details }'
if marker not in src or 'FailureDoctorCard' in src:
    print('HomeScreen inject skipped')
    raise SystemExit(0)
inject = '''FailureDoctorCard(\n                status = s,\n                enabled = enabled,\n                live = live,\n                partial = partial,\n                hook = hook,\n                frames = frames,\n                hits = hits,\n                rootOk = rootOk,\n                busy = busy,\n                onProbe = {\n                    scope.launch {\n                        busy = true\n                        msg = "Probe requested\u2026"\n                        msgOk = true\n                        val ok = checker.requestProbe()\n                        if (ok) {\n                            kotlinx.coroutines.delay(1800)\n                            status = checker.check()\n                            msg = status?.diag?.take(80) ?: "Probe written \u2014 open camera app"\n                            msgOk = status?.lastError.isNullOrBlank()\n                        } else {\n                            msg = "Could not write probe_request"\n                            msgOk = false\n                        }\n                        busy = false\n                    }\n                },\n                onRefresh = { refresh() }\n            )\n\n            '''
src = src.replace(marker, inject + marker, 1)
open(p, 'w').write(src)
print('Injected FailureDoctorCard into HomeScreen')
PY
fi

# Verify
grep -n 'vcam_probe\|kProbeReqPath\|run_probe' zygisk-native/jni/gl_hooks.cpp 2>/dev/null | head -4 || true
test -f zygisk-native/jni/vcam_probe.inc && echo "vcam_probe.inc present" || echo "WARN: vcam_probe.inc missing"
grep -q 'requestProbe' manager-app/app/src/main/java/com/virtualcam/manager/data/PrerequisiteChecker.kt 2>/dev/null && echo "PrerequisiteChecker has requestProbe" || true
test -f manager-app/app/src/main/java/com/virtualcam/manager/ui/screens/FailureDoctorCard.kt && echo "FailureDoctorCard present" || true
echo "=== v2.0.3 assemble done ==="
