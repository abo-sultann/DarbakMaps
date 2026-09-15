#!/usr/bin/env bash
set -euo pipefail

OUT="visual-qa"
PKG="com.abosultan.darbakmaps.debug"
ACT="com.abosultan.darbakmaps.MainActivity"
APK="app/build/outputs/apk/debug/app-debug.apk"
mkdir -p "$OUT"

collect_debug() {
  adb exec-out screencap -p > "$OUT/final-screen.png" 2>/dev/null || true
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb pull /sdcard/window.xml "$OUT/window.xml" >/dev/null 2>&1 || true
  adb logcat -d > "$OUT/logcat.txt" 2>/dev/null || true
  adb shell wm size > "$OUT/display.txt" 2>/dev/null || true
  adb shell wm density >> "$OUT/display.txt" 2>/dev/null || true
  adb shell dumpsys display | grep -E 'mBaseDisplayInfo|mOverrideDisplayInfo' >> "$OUT/display.txt" 2>/dev/null || true
}
trap collect_debug EXIT

# The AVD hardware itself is configured to 1024x600 before launch. Do not use
# wm-size letterboxing here: the evidence PNG must be the real target size.
adb shell wm size reset || true
adb shell wm density reset || true

adb install -r "$APK"
adb shell pm grant "$PKG" android.permission.ACCESS_FINE_LOCATION || true
# Suppress Android 7's one-time immersive tutorial before opening the app.
adb shell settings put secure immersive_mode_confirmations confirmed || true
adb shell am force-stop "$PKG"
adb logcat -c || true
adb shell am start -W -n "$PKG/$ACT"
sleep 3

# Capture hierarchy first and make the OS tutorial a hard failure, not a false pass.
adb shell uiautomator dump /sdcard/window.xml >/dev/null
adb pull /sdcard/window.xml "$OUT/window.xml" >/dev/null
if grep -Eq 'immersive_cling|Viewing full screen|android:id/ok' "$OUT/window.xml"; then
  echo 'ERROR: Android immersive-mode education UI is covering Darbak Maps.' >&2
  exit 31
fi
if ! grep -q 'دربك للخرائط' "$OUT/window.xml"; then
  echo 'ERROR: Darbak Maps home UI was not found in the active window hierarchy.' >&2
  exit 32
fi

adb exec-out screencap -p > "$OUT/01-home.png"

# Validate the actual framebuffer evidence, not only `wm size` override text.
python3 - "$OUT/01-home.png" <<'PY'
import struct, sys
path = sys.argv[1]
with open(path, 'rb') as handle:
    if handle.read(8) != b'\x89PNG\r\n\x1a\n':
        raise SystemExit('ERROR: visual evidence is not a PNG')
    handle.read(4)
    if handle.read(4) != b'IHDR':
        raise SystemExit('ERROR: PNG has no IHDR header')
    width, height = struct.unpack('>II', handle.read(8))
print(f'Captured framebuffer: {width}x{height}')
if (width, height) != (1024, 600):
    raise SystemExit(f'ERROR: expected real 1024x600 framebuffer, got {width}x{height}')
PY

# Fail on a Darbak Maps fatal exception after launch.
if adb logcat -d | grep -A12 'FATAL EXCEPTION' | grep -q "$PKG"; then
  echo 'ERROR: Darbak Maps fatal exception found in logcat.' >&2
  exit 33
fi

cat > "$OUT/acceptance.txt" <<'EOF'
Darbak Maps clean rebuild — visual gate
Required framebuffer: exactly 1024x600 landscape
Required device RAM profile: 1024 MB
Forbidden: Android immersive education overlay, old green identity, old 0.9.x layouts
The hierarchy must contain the Darbak Maps home UI and logcat must contain no Darbak fatal exception.
This gate validates startup/layout only; real POI/map-content behavior still requires the installed Saudi .map file.
EOF

adb shell wm size > "$OUT/display.txt"
adb shell wm density >> "$OUT/display.txt"
adb shell dumpsys display | grep -E 'mBaseDisplayInfo|mOverrideDisplayInfo' >> "$OUT/display.txt" || true
adb logcat -d > "$OUT/logcat.txt"
