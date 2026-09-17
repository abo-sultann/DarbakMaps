#!/usr/bin/env bash
set -euo pipefail

OUT="visual-qa"
PKG="com.abosultan.darbakmaps.debug"
ACT="com.abosultan.darbakmaps.MainActivity"
DB_OUT="$OUT/saved-places.db"
DB_AFTER_DELETE="$OUT/saved-places-after-delete.db"
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

point_for_place_row() {
  local target="$1"
  dump_ui
  python3 - "$OUT/saved-places-window.xml" "$target" <<'PY'
import re,sys,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot(); target=sys.argv[2]
for n in root.iter('node'):
    text=n.attrib.get('text','').strip()
    # Filter chips use the bare category name; a real saved-place row also contains distance/direction.
    if text.startswith(target) and text != target:
        m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', n.attrib.get('bounds',''))
        if m:
            a,b,c,d=map(int,m.groups()); print((a+c)//2,(b+d)//2); raise SystemExit(0)
raise SystemExit(1)
PY
}

tap_desc() { local p; p="$(point_for_desc "$1")" || fail "control missing: $1"; adb shell input tap $p; sleep 2; }
tap_text() { local p; p="$(point_for_text "$1")" || fail "text action missing: $1"; adb shell input tap $p; sleep 2; }
tap_place_row() { local p; p="$(point_for_place_row "$1")" || fail "saved-place row missing: $1"; adb shell input tap $p; sleep 2; }

# UIAutomator can consume most of the 15-second live-fix freshness window on this
# unaccelerated API25 runner. Resolve the control first, then inject a fresh fix
# immediately before the actual tap so the app is tested with a genuinely live fix.
tap_desc_with_fix() {
  local target="$1" lon="$2" lat="$3" p
  p="$(point_for_desc "$target")" || fail "control missing: $target"
  adb emu geo fix "$lon" "$lat" >/dev/null
  sleep 2
  adb shell input tap $p
  sleep 2
}

tap_text_with_fix() {
  local target="$1" lon="$2" lat="$3" p
  p="$(point_for_text "$target")" || fail "text action missing: $target"
  adb emu geo fix "$lon" "$lat" >/dev/null
  sleep 2
  adb shell input tap $p
  sleep 2
}

long_press_place_row() {
  local target="$1" p x y
  p="$(point_for_place_row "$target")" || fail "saved-place row missing for long press: $target"
  read -r x y <<<"$p"
  adb shell input swipe "$x" "$y" "$x" "$y" 900
  sleep 2
}

copy_places_db() {
  local dest="$1"
  adb exec-out run-as "$PKG" cat databases/darbak_places.db > "$dest" || fail "could not copy places database"
  [ -s "$dest" ] || fail "places database is empty"
}

# UIAutomator is slow on the unaccelerated API25 runner; keep controls visible during this gate.
adb shell "run-as $PKG mkdir -p shared_prefs" || true
adb shell "run-as $PKG sh -c 'printf \"%s\\n\" \"<?xml version=\\\"1.0\\\" encoding=\\\"utf-8\\\" standalone=\\\"yes\\\" ?>\" \"<map><boolean name=\\\"auto_hide\\\" value=\\\"false\\\" /></map>\" > shared_prefs/darbak_map_ui.xml'" || true
adb shell am start -W -n "$PKG/$ACT" >/dev/null || fail "could not relaunch Darbak Maps"
sleep 3

# Save an unnamed Summan place. Refresh the same fix again after locating the category row,
# because the slow UI dump must not turn a valid location into a stale one before saveCurrentPlace().
tap_desc_with_fix "حفظ موقع" 46.67530 24.71360
tap_text_with_fix "طير سمان" 46.67530 24.71360

# Save an unnamed water place farther north-east.
tap_desc_with_fix "حفظ موقع" 46.67580 24.71410
tap_text_with_fix "ماء" 46.67580 24.71410

# Prove persistence before relying on the browser UI. This distinguishes a save failure from
# a list-rendering failure and confirms that names are optional.
copy_places_db "$DB_OUT"
python3 - "$DB_OUT" <<'PY'
import sqlite3,sys
path=sys.argv[1]
con=sqlite3.connect(path)
rows=con.execute('SELECT category,name,lat,lon,uuid FROM places ORDER BY id').fetchall()
con.close()
if len(rows) != 2:
    raise SystemExit(f'ERROR: expected exactly 2 saved places before list QA, got {len(rows)}')
if [r[0] for r in rows] != ['summan','water']:
    raise SystemExit('ERROR: saved categories mismatch: '+repr([r[0] for r in rows]))
if any(r[1] not in (None,'') for r in rows):
    raise SystemExit('ERROR: optional place name unexpectedly required/populated')
if any(not r[4] for r in rows):
    raise SystemExit('ERROR: stable UUID missing')
expected=[(24.71360,46.67530),(24.71410,46.67580)]
for row,(lat,lon) in zip(rows,expected):
    if abs(row[2]-lat) > 0.00005 or abs(row[3]-lon) > 0.00005:
        raise SystemExit('ERROR: saved coordinates do not match injected GPS fixes: '+repr(rows))
print('Saved places persisted before list QA.')
PY

# Move east of the water point. Inject only after locating the button so the dialog captures a
# fresh fix; Water must sort ahead of Summan in nearest-first list.
tap_desc_with_fix "المواقع" 46.67625 24.71410
dump_ui
grep -q 'المواقع المحفوظة' "$OUT/saved-places-window.xml" || fail "saved-places dialog did not open"

python3 - "$OUT/saved-places-window.xml" <<'PY'
import re,sys,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
rows=[]
for n in root.iter('node'):
    text=n.attrib.get('text','').strip()
    # Exclude category filter chips. Saved-place rows include category + distance/direction.
    is_water_row=text.startswith('ماء') and text!='ماء'
    is_summan_row=text.startswith('طير سمان') and text!='طير سمان'
    if is_water_row or is_summan_row:
        m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',n.attrib.get('bounds',''))
        if m:
            x1,y1,x2,y2=map(int,m.groups()); rows.append((text,y1))
water=[r for r in rows if r[0].startswith('ماء')]
summan=[r for r in rows if r[0].startswith('طير سمان')]
if not water or not summan:
    raise SystemExit('ERROR: saved-place rows missing from list: '+repr(rows))
if water[0][1] >= summan[0][1]:
    raise SystemExit(f'ERROR: nearest-first order wrong: water_y={water[0][1]} summan_y={summan[0][1]}')
print('Nearest-first UI order verified:', water[0][0], 'before', summan[0][0])
PY

# Open the nearest row and prove the action surface contains live distance + direction.
tap_place_row "ماء"
dump_ui
grep -q 'الموقع المحفوظ' "$OUT/saved-places-window.xml" || fail "saved-place action dialog did not open"
grep -q 'المسافة:' "$OUT/saved-places-window.xml" || fail "saved-place distance missing"
grep -q 'الاتجاه:' "$OUT/saved-places-window.xml" || fail "saved-place direction missing"
tap_text "إغلاق"

# Re-open the browser and exercise the real long-press delete flow end to end.
tap_desc_with_fix "المواقع" 46.67625 24.71410
long_press_place_row "ماء"
dump_ui
grep -q 'حذف الموقع؟' "$OUT/saved-places-window.xml" || fail "delete confirmation did not open on long press"
tap_text "حذف الموقع"
dump_ui

python3 - "$OUT/saved-places-window.xml" <<'PY'
import sys,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
texts=[n.attrib.get('text','').strip() for n in root.iter('node')]
water=[t for t in texts if t.startswith('ماء') and t!='ماء']
summan=[t for t in texts if t.startswith('طير سمان') and t!='طير سمان']
if water:
    raise SystemExit('ERROR: deleted water place still visible in saved-place list: '+repr(water))
if not summan:
    raise SystemExit('ERROR: remaining Summan place disappeared after deleting water')
print('Long-press delete removed only the selected place from the UI.')
PY

copy_places_db "$DB_AFTER_DELETE"
python3 - "$DB_AFTER_DELETE" "$OUT/saved-places-acceptance.txt" <<'PY'
import sqlite3,sys
path,report=sys.argv[1],sys.argv[2]
con=sqlite3.connect(path)
rows=con.execute('SELECT category,name,uuid FROM places ORDER BY id').fetchall()
con.close()
if len(rows) != 1 or rows[0][0] != 'summan':
    raise SystemExit('ERROR: delete persistence mismatch: '+repr(rows))
if rows[0][1] not in (None,'') or not rows[0][2]:
    raise SystemExit('ERROR: remaining saved place metadata corrupted after delete')
with open(report,'w',encoding='utf-8') as f:
    f.write('Darbak Maps saved places gate: PASS\n')
    f.write('saved_count_before_delete=2\n')
    f.write('categories_before_delete=summan,water\n')
    f.write('optional_names=empty\n')
    f.write('nearest_ui=water_before_summan\n')
    f.write('action_dialog=distance_and_direction_present\n')
    f.write('long_press_delete=water_removed\n')
    f.write('saved_count_after_delete=1\n')
    f.write('remaining_category=summan\n')
print('Saved places persistence, nearest/action UI, and long-press delete verified.')
PY

adb shell input keyevent 4 || true
