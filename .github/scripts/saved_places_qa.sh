#!/usr/bin/env bash
set -euo pipefail

OUT="visual-qa"
PKG="com.abosultan.darbakmaps.debug"
ACT="com.abosultan.darbakmaps.MainActivity"
DB_OUT="$OUT/saved-places.db"
mkdir -p "$OUT"

fail() {
  echo "ERROR: $*" >&2
  exit 60
}

dump_ui() {
  adb shell uiautomator dump /sdcard/saved-places-window.xml >/dev/null
  adb pull /sdcard/saved-places-window.xml "$OUT/saved-places-window.xml" >/dev/null
}

point_for_desc() {
  local target="$1"
  dump_ui
  python3 - "$OUT/saved-places-window.xml" "$target" <<'PY'
import re,sys,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot(); target=sys.argv[2]
for n in root.iter('node'):
    if n.attrib.get('content-desc') == target:
        m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', n.attrib.get('bounds',''))
        if m:
            a,b,c,d=map(int,m.groups()); print((a+c)//2,(b+d)//2); raise SystemExit(0)
raise SystemExit(1)
PY
}

point_for_text() {
  local target="$1"
  dump_ui
  python3 - "$OUT/saved-places-window.xml" "$target" <<'PY'
import re,sys,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot(); target=sys.argv[2]
for n in root.iter('node'):
    if n.attrib.get('text') == target:
        m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', n.attrib.get('bounds',''))
        if m:
            a,b,c,d=map(int,m.groups()); print((a+c)//2,(b+d)//2); raise SystemExit(0)
raise SystemExit(1)
PY
}

point_for_prefix() {
  local target="$1"
  dump_ui
  python3 - "$OUT/saved-places-window.xml" "$target" <<'PY'
import re,sys,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot(); target=sys.argv[2]
for n in root.iter('node'):
    if n.attrib.get('text','').strip().startswith(target):
        m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', n.attrib.get('bounds',''))
        if m:
            a,b,c,d=map(int,m.groups()); print((a+c)//2,(b+d)//2); raise SystemExit(0)
raise SystemExit(1)
PY
}

tap_desc() { local p; p="$(point_for_desc "$1")" || fail "control missing: $1"; adb shell input tap $p; sleep 2; }
tap_text() { local p; p="$(point_for_text "$1")" || fail "text action missing: $1"; adb shell input tap $p; sleep 2; }
tap_prefix() { local p; p="$(point_for_prefix "$1")" || fail "row missing: $1"; adb shell input tap $p; sleep 2; }
inject_fix() { adb emu geo fix "$1" "$2" >/dev/null; sleep 3; }

# UIAutomator is slow on the unaccelerated API25 runner; keep controls visible during this gate.
adb shell "run-as $PKG mkdir -p shared_prefs" || true
adb shell "run-as $PKG sh -c 'printf \"%s\\n\" \"<?xml version=\\\"1.0\\\" encoding=\\\"utf-8\\\" standalone=\\\"yes\\\" ?>\" \"<map><boolean name=\\\"auto_hide\\\" value=\\\"false\\\" /></map>\" > shared_prefs/darbak_map_ui.xml'" || true
adb shell am start -W -n "$PKG/$ACT" >/dev/null || fail "could not relaunch Darbak Maps"
sleep 3

# Save an unnamed Summan place from the live GPS fix.
inject_fix 46.67530 24.71360
tap_desc "حفظ موقع"
tap_text "طير سمان"

# Save an unnamed water place farther north-east.
inject_fix 46.67580 24.71410
tap_desc "حفظ موقع"
tap_text "ماء"

# Move east of the water point. Water must now sort ahead of Summan in nearest-first list.
inject_fix 46.67625 24.71410
tap_desc "المواقع"
dump_ui

python3 - "$OUT/saved-places-window.xml" <<'PY'
import re,sys,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
rows=[]
for n in root.iter('node'):
    text=n.attrib.get('text','').strip()
    if text.startswith('ماء') or text.startswith('طير سمان'):
        m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',n.attrib.get('bounds',''))
        if m:
            x1,y1,x2,y2=map(int,m.groups()); rows.append((text,y1))
water=[r for r in rows if r[0].startswith('ماء')]
summan=[r for r in rows if r[0].startswith('طير سمان')]
if not water or not summan:
    raise SystemExit('ERROR: saved-place rows missing from list')
if water[0][1] >= summan[0][1]:
    raise SystemExit(f'ERROR: nearest-first order wrong: water_y={water[0][1]} summan_y={summan[0][1]}')
print('Nearest-first UI order verified:', water[0][0], 'before', summan[0][0])
PY

# Open the nearest row and prove the action surface contains live distance + direction.
tap_prefix "ماء"
dump_ui
grep -q 'الموقع المحفوظ' "$OUT/saved-places-window.xml" || fail "saved-place action dialog did not open"
grep -q 'المسافة:' "$OUT/saved-places-window.xml" || fail "saved-place distance missing"
grep -q 'الاتجاه:' "$OUT/saved-places-window.xml" || fail "saved-place direction missing"

# Copy and inspect persistence: category-only saves must not require a name.
adb exec-out run-as "$PKG" cat databases/darbak_places.db > "$DB_OUT" || fail "could not copy places database"
[ -s "$DB_OUT" ] || fail "places database is empty"
python3 - "$DB_OUT" "$OUT/saved-places-acceptance.txt" <<'PY'
import sqlite3,sys
path,report=sys.argv[1],sys.argv[2]
con=sqlite3.connect(path)
rows=con.execute('SELECT category,name,lat,lon,uuid FROM places ORDER BY id').fetchall()
con.close()
if len(rows) != 2:
    raise SystemExit(f'ERROR: expected exactly 2 saved places, got {len(rows)}')
if [r[0] for r in rows] != ['summan','water']:
    raise SystemExit('ERROR: saved categories mismatch: '+repr([r[0] for r in rows]))
if any(r[1] not in (None,'') for r in rows):
    raise SystemExit('ERROR: optional place name unexpectedly required/populated')
if any(not r[4] for r in rows):
    raise SystemExit('ERROR: stable UUID missing')
with open(report,'w',encoding='utf-8') as f:
    f.write('Darbak Maps saved places gate: PASS\n')
    f.write('saved_count=2\n')
    f.write('categories=summan,water\n')
    f.write('optional_names=empty\n')
    f.write('nearest_ui=water_before_summan\n')
    f.write('action_dialog=distance_and_direction_present\n')
print('Saved places persistence and nearest/action UI verified.')
PY

adb shell input keyevent 4 || true
