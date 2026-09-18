#!/usr/bin/env bash
set -euo pipefail
OUT="visual-qa"; PKG="com.abosultan.darbakmaps.debug"; ACT="com.abosultan.darbakmaps.MainActivity"; APK="app/build/outputs/apk/debug/app-debug.apk"; mkdir -p "$OUT"
collect_debug(){ adb exec-out screencap -p > "$OUT/final-screen.png" 2>/dev/null||true; adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1||true; adb pull /sdcard/window.xml "$OUT/window.xml" >/dev/null 2>&1||true; adb logcat -d > "$OUT/logcat.txt" 2>/dev/null||true; adb shell wm size > "$OUT/display.txt" 2>/dev/null||true; adb shell wm density >> "$OUT/display.txt" 2>/dev/null||true; }; trap collect_debug EXIT
snapshot(){ adb exec-out screencap -p > "$OUT/$1.png"; }; dump_ui(){ adb shell uiautomator dump /sdcard/window.xml >/dev/null; adb pull /sdcard/window.xml "$OUT/window.xml" >/dev/null; }
point_for_desc(){ local d="$1"; dump_ui; python3 - "$OUT/window.xml" "$d" <<'PY'
import re,sys,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot(); target=sys.argv[2]
for n in root.iter('node'):
 if n.attrib.get('content-desc')==target:
  m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',n.attrib.get('bounds',''))
  if m:
   a,b,c,d=map(int,m.groups()); print((a+c)//2,(b+d)//2); raise SystemExit(0)
raise SystemExit(1)
PY
}
tap_desc(){ local p; p="$(point_for_desc "$1")"||return 1; adb shell input tap $p; sleep 2; }
long_desc(){ local p; p="$(point_for_desc "$1")"||return 1; set -- $p; adb shell input swipe "$1" "$2" "$1" "$2" 900; sleep 2; }
point_for_text(){ local t="$1"; dump_ui; python3 - "$OUT/window.xml" "$t" <<'PY'
import re,sys,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot(); target=sys.argv[2]
for n in root.iter('node'):
 if n.attrib.get('text')==target:
  m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',n.attrib.get('bounds',''))
  if m:
   a,b,c,d=map(int,m.groups()); print((a+c)//2,(b+d)//2); raise SystemExit(0)
raise SystemExit(1)
PY
}
tap_text(){ local p; p="$(point_for_text "$1")"||return 1; adb shell input tap $p; sleep 2; }
# Software-only API25 emulation can make Launcher3 ANR while Darbak itself is healthy.
# Remove only that unrelated system overlay, then relaunch Darbak before assertions.
dismiss_launcher_anr(){
 for _ in 1 2 3; do
  dump_ui || true
  if grep -q "Launcher3 isn't responding" "$OUT/window.xml"; then
   local p; p="$(point_for_text "Close app")" || true
   if [ -n "${p:-}" ]; then adb shell input tap $p; sleep 2; fi
   adb shell am start -W -n "$PKG/$ACT" >/dev/null || true; sleep 2
  else return 0; fi
 done
 dump_ui
 if grep -q "Launcher3 isn't responding" "$OUT/window.xml"; then echo 'ERROR: emulator Launcher3 ANR still covers Darbak Maps.' >&2; exit 30; fi
}

adb shell wm size reset||true; adb shell wm density reset||true; adb install -r "$APK"; adb shell pm grant "$PKG" android.permission.ACCESS_FINE_LOCATION||true; adb shell pm grant "$PKG" android.permission.READ_EXTERNAL_STORAGE||true; adb shell settings put secure immersive_mode_confirmations confirmed||true
# UIAutomator on the unaccelerated API25 runner can take >10s per dump. Disable only
# auto-hide in this test process so accessibility capture does not race the 6.5s UX timer.
adb shell "run-as $PKG mkdir -p shared_prefs" || true
adb shell "run-as $PKG sh -c 'printf \"%s\\n\" \"<?xml version=\\\"1.0\\\" encoding=\\\"utf-8\\\" standalone=\\\"yes\\\" ?>\" \"<map><boolean name=\\\"auto_hide\\\" value=\\\"false\\\" /></map>\" > shared_prefs/darbak_map_ui.xml'" || true
adb shell am force-stop "$PKG"; adb logcat -c||true; adb shell am start -W -n "$PKG/$ACT"; sleep 3
dismiss_launcher_anr
dump_ui
if grep -Eq 'immersive_cling|Viewing full screen|android:id/ok' "$OUT/window.xml"; then echo 'ERROR: Android overlay covers Darbak Maps.' >&2; exit 31; fi
# New home intentionally has no title/top bar. Validate stable semantic controls instead.
grep -q 'content-desc="بحث"' "$OUT/window.xml" || { echo 'ERROR: search control missing.' >&2; exit 32; }
grep -q 'content-desc="حفظ موقع"' "$OUT/window.xml" || { echo 'ERROR: save control missing.' >&2; exit 32; }
grep -q 'content-desc="المزيد"' "$OUT/window.xml" || { echo 'ERROR: more control missing.' >&2; exit 32; }
snapshot 01-home

tap_desc "المزيد"; dump_ui; grep -q 'فتح الإعدادات' "$OUT/window.xml" || { echo 'ERROR: Status dialog did not open.' >&2; exit 34; }; snapshot 02-more
tap_text "فتح الإعدادات"; snapshot 03-settings; adb shell input keyevent 4; sleep 1
tap_desc "المزيد"; tap_text "عن التطبيق"; snapshot 04-about; adb shell input keyevent 4; sleep 1
tap_desc "المسارات"; snapshot 05-tracks; adb shell input keyevent 4; sleep 1
tap_desc "حفظ موقع"; snapshot 06-save-place; adb shell input keyevent 4; sleep 1
tap_desc "بحث"; snapshot 07-search; adb shell input keyevent 4; sleep 1
long_desc "المزيد"; snapshot 08-diagnostics; adb shell input keyevent 4; sleep 1

# Confirm diagnostics really returned to home before testing map gestures.
dump_ui
grep -q 'content-desc="بحث"' "$OUT/window.xml" || { echo 'ERROR: home did not return after diagnostics.' >&2; exit 35; }

# Short tap on empty map: transient controls must disappear, no delayed long-press dialog may open.
adb shell input tap 700 250; sleep 1; dump_ui
if grep -q 'content-desc="بحث"' "$OUT/window.xml"; then echo 'ERROR: map tap did not hide controls.' >&2; exit 36; fi
if grep -q 'حفظ هذه النقطة' "$OUT/window.xml"; then echo 'ERROR: short map tap incorrectly triggered long-press save.' >&2; exit 37; fi
grep -q 'كم/س' "$OUT/window.xml" || { echo 'ERROR: speed readout disappeared with transient controls.' >&2; exit 38; }
snapshot 09-clean-map

# Second short tap restores the controls.
adb shell input tap 700 250; sleep 1; dump_ui
grep -q 'content-desc="بحث"' "$OUT/window.xml" || { echo 'ERROR: second map tap did not restore controls.' >&2; exit 39; }

# Deliberate long press on empty map must open the typed place picker.
adb shell input swipe 700 250 700 250 900; sleep 1; dump_ui
grep -q 'حفظ هذه النقطة' "$OUT/window.xml" || { echo 'ERROR: map long press did not open place picker.' >&2; exit 40; }
snapshot 10-long-press-save
adb shell input keyevent 4; sleep 1

python3 - "$OUT" <<'PY'
import glob,os,struct,sys
for p in glob.glob(os.path.join(sys.argv[1],'*.png')):
 with open(p,'rb') as f:
  if f.read(8)!=b'\x89PNG\r\n\x1a\n': raise SystemExit('ERROR: non-PNG '+p)
  f.read(4)
  if f.read(4)!=b'IHDR': raise SystemExit('ERROR: bad PNG '+p)
  w,h=struct.unpack('>II',f.read(8))
 if (w,h)!=(1024,600): raise SystemExit(f'ERROR: {os.path.basename(p)} is {w}x{h}')
print('All visual evidence is 1024x600')
PY
if adb logcat -d|grep -A12 'FATAL EXCEPTION'|grep -q "$PKG"; then echo 'ERROR: fatal exception.' >&2; exit 33; fi
printf '%s\n' 'Darbak Maps visual gate: API25 / 1024x600 / 1GB; icon-only home; Darbak settings; short-tap hide/restore; map long-press save picker.' > "$OUT/acceptance.txt"
adb shell wm size > "$OUT/display.txt"; adb shell wm density >> "$OUT/display.txt"; adb logcat -d > "$OUT/logcat.txt"
