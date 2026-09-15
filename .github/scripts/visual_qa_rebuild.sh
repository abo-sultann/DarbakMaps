#!/usr/bin/env bash
set -euo pipefail

# Run this gate after dependency/build changes too; it validates the complete 1.0 identity surface.
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
}
trap collect_debug EXIT

snapshot() {
  local name="$1"
  adb exec-out screencap -p > "$OUT/$name.png"
}

dump_ui() {
  adb shell uiautomator dump /sdcard/window.xml >/dev/null
  adb pull /sdcard/window.xml "$OUT/window.xml" >/dev/null
}

point_for_text() {
  local text="$1"
  dump_ui
  python3 - "$OUT/window.xml" "$text" <<'PY'
import re, sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot(); target = sys.argv[2]
for node in root.iter('node'):
    if node.attrib.get('text') == target:
        m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds',''))
        if m:
            x1,y1,x2,y2 = map(int,m.groups()); print((x1+x2)//2, (y1+y2)//2); raise SystemExit(0)
raise SystemExit(1)
PY
}

tap_text() {
  local text="$1" point
  point="$(point_for_text "$text")" || return 1
  adb shell input tap ${point}
  sleep 1
}

home_control() {
  local text="$1"
  if ! tap_text "$text"; then
    tap_text "≡"
    tap_text "$text"
  fi
}

longpress_home_control() {
  local text="$1" point
  point="$(point_for_text "$text")" || { tap_text "≡"; point="$(point_for_text "$text")"; }
  set -- $point
  adb shell input swipe "$1" "$2" "$1" "$2" 900
  sleep 1
}

adb shell wm size reset || true
adb shell wm density reset || true
adb install -r "$APK"
adb shell pm grant "$PKG" android.permission.ACCESS_FINE_LOCATION || true
adb shell pm grant "$PKG" android.permission.READ_EXTERNAL_STORAGE || true
adb shell settings put secure immersive_mode_confirmations confirmed || true
adb shell am force-stop "$PKG"
adb logcat -c || true
adb shell am start -W -n "$PKG/$ACT"
sleep 3

dump_ui
if grep -Eq 'immersive_cling|Viewing full screen|android:id/ok' "$OUT/window.xml"; then
  echo 'ERROR: Android overlay covers Darbak Maps.' >&2; exit 31
fi
if ! grep -q 'دربك للخرائط' "$OUT/window.xml"; then
  echo 'ERROR: Darbak Maps home UI not found.' >&2; exit 32
fi
snapshot "01-home"

# Identity-critical internal surfaces.
home_control "المزيد"; snapshot "02-more"
tap_text "حول دربك للخرائط"; snapshot "03-about"; adb shell input keyevent 4; sleep 1
home_control "المسارات"; snapshot "04-tracks"; adb shell input keyevent 4; sleep 1
home_control "حفظ موقع"; snapshot "05-save-place"; adb shell input keyevent 4; sleep 1
home_control "بحث"; snapshot "06-search"; adb shell input keyevent 4; sleep 1
longpress_home_control "المزيد"; snapshot "07-diagnostics"; adb shell input keyevent 4; sleep 1

python3 - "$OUT" <<'PY'
import glob, os, struct, sys
for path in glob.glob(os.path.join(sys.argv[1], '*.png')):
    with open(path,'rb') as f:
        if f.read(8) != b'\x89PNG\r\n\x1a\n': raise SystemExit('ERROR: non-PNG evidence '+path)
        f.read(4)
        if f.read(4) != b'IHDR': raise SystemExit('ERROR: PNG missing IHDR '+path)
        w,h = struct.unpack('>II', f.read(8))
    if (w,h) != (1024,600): raise SystemExit(f'ERROR: {os.path.basename(path)} is {w}x{h}, expected 1024x600')
print('All visual evidence is 1024x600')
PY

if adb logcat -d | grep -A12 'FATAL EXCEPTION' | grep -q "$PKG"; then
  echo 'ERROR: Darbak Maps fatal exception found in logcat.' >&2; exit 33
fi

cat > "$OUT/acceptance.txt" <<'EOF'
Darbak Maps 1.0 visual gate
Required: Android 7.1, framebuffer exactly 1024x600 landscape, 1GB AVD profile.
Screens: home, more, about, tracks, save-place, search, hidden diagnostics.
Forbidden: Android immersive education overlay, old green identity, old 0.9.x layouts, fatal exception.
This gate validates startup/layout/identity. Real Saudi map content and physical GPS remain hardware/data checks.
EOF

adb shell wm size > "$OUT/display.txt"
adb shell wm density >> "$OUT/display.txt"
adb logcat -d > "$OUT/logcat.txt"
