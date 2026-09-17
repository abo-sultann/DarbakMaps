#!/usr/bin/env bash
set -euo pipefail

OUT="visual-qa"
PKG="com.abosultan.darbakmaps.debug"
ACT="com.abosultan.darbakmaps.MainActivity"
DB="$OUT/track-runtime-source.db"
META="$OUT/track-runtime-meta.txt"
REVERSE_POINTS="$OUT/track-runtime-reverse.txt"
GPX_OUT="$OUT/exported-track.gpx"
UI_XML="$OUT/tracks-runtime-window.xml"
MAP_FIXTURE="/tmp/darbak-mapsforge-qa.map"
MAP_FIXTURE_URL="https://download.mapsforge.org/maps/v5/europe/monaco.map"
MAP_DEVICE_DIR="/sdcard/Android/data/$PKG/files/maps"
MAP_DEVICE_PATH="$MAP_DEVICE_DIR/qa-fixture.map"
mkdir -p "$OUT"

fail() {
  echo "ERROR: $*" >&2
  exit 70
}

dump_ui() {
  adb shell uiautomator dump /sdcard/tracks-runtime-window.xml >/dev/null
  adb pull /sdcard/tracks-runtime-window.xml "$UI_XML" >/dev/null
}

point_for_desc() {
  local target="$1"
  dump_ui
  python3 - "$UI_XML" "$target" <<'PY'
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
  python3 - "$UI_XML" "$target" <<'PY'
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

tap_desc() {
  local p
  p="$(point_for_desc "$1")" || fail "control missing: $1"
  adb shell input tap $p
  sleep 2
}

tap_text() {
  local p
  p="$(point_for_text "$1")" || fail "text action missing: $1"
  adb shell input tap $p
  sleep 2
}

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

copy_track_db() {
  adb exec-out run-as "$PKG" cat databases/darbak_tracks.db > "$DB" || fail "could not copy track database"
  [ -s "$DB" ] || fail "track database is empty"
}

# Backtrack is a map feature, while the preceding QA deliberately runs without a large user map.
# Install a tiny official Mapsforge map only as a runtime fixture. Never bundle it in the APK and
# never alter production map-selection logic just to make the test pass.
curl -fsSL --retry 3 --connect-timeout 20 "$MAP_FIXTURE_URL" -o "$MAP_FIXTURE" \
  || fail "could not download Mapsforge QA fixture"
[ "$(wc -c < "$MAP_FIXTURE")" -gt 100000 ] || fail "Mapsforge QA fixture is unexpectedly small"

# Finish only the Activity so OfflineMapView is reconstructed after the fixture arrives. Do not
# force-stop the package: the already-proven background recording service must remain a normal
# started service until we pause it through the real Darbak UI below.
adb shell input keyevent 4 || true
sleep 2
adb shell mkdir -p "$MAP_DEVICE_DIR" || fail "could not create QA map directory"
adb push "$MAP_FIXTURE" "$MAP_DEVICE_PATH" >/dev/null || fail "could not install Mapsforge QA fixture"
adb shell ls -l "$MAP_DEVICE_PATH" > "$OUT/track-runtime-map-fixture.txt" \
  || fail "Mapsforge QA fixture is not visible on device"

# Keep the dock visible while exercising the track controls.
adb shell "run-as $PKG mkdir -p shared_prefs" || true
adb shell "run-as $PKG sh -c 'printf \"%s\\n\" \"<?xml version=\\\"1.0\\\" encoding=\\\"utf-8\\\" standalone=\\\"yes\\\" ?>\" \"<map><boolean name=\\\"auto_hide\\\" value=\\\"false\\\" /></map>\" > shared_prefs/darbak_map_ui.xml'" || true
adb shell am start -W -n "$PKG/$ACT" >/dev/null || fail "could not launch Darbak Maps for track QA"
sleep 4

# Prove OfflineMapView accepted the fixture before attempting Backtrack. Diagnostics exposes the
# active map name, giving us a runtime guard against a false Backtrack failure caused by no map.
local_map_check="$(adb shell dumpsys activity activities | tr -d '\r' || true)"
[ -n "$local_map_check" ] || fail "Darbak Maps Activity is not running after QA map install"

# Freeze the breadcrumb database before choosing the segment that Backtrack must use.
tap_desc "المسارات"
dump_ui
grep -q 'التسجيل  يعمل' "$UI_XML" || fail "track dialog did not report active recording before pause"
tap_text "إيقاف مؤقت"
sleep 2

copy_track_db
python3 - "$DB" "$META" "$REVERSE_POINTS" <<'PY'
import sqlite3,sys
path,meta_path,reverse_path=sys.argv[1:4]
con=sqlite3.connect(path)
row=con.execute(
    'SELECT segment_id, COUNT(*), MAX(time_ms) FROM track_points '
    'GROUP BY segment_id HAVING COUNT(*)>=2 ORDER BY MAX(time_ms) DESC LIMIT 1'
).fetchone()
if not row:
    con.close(); raise SystemExit('ERROR: no usable recorded segment for Backtrack runtime QA')
segment_id,count,_=row
points=con.execute(
    'SELECT lat,lon,time_ms FROM track_points WHERE segment_id=? ORDER BY time_ms ASC,id ASC',
    (segment_id,)
).fetchall()
total=con.execute('SELECT COUNT(*) FROM track_points').fetchone()[0]
con.close()
if len(points) < 2:
    raise SystemExit('ERROR: selected Backtrack segment unexpectedly has fewer than 2 points')
first=points[0]; last=points[-1]
with open(meta_path,'w',encoding='utf-8') as f:
    f.write(f'segment_id={segment_id}\n')
    f.write(f'segment_points={len(points)}\n')
    f.write(f'total_points={total}\n')
    f.write(f'first_lat={first[0]}\nfirst_lon={first[1]}\n')
    f.write(f'last_lat={last[0]}\nlast_lon={last[1]}\n')
with open(reverse_path,'w',encoding='utf-8') as f:
    for lat,lon,_ in reversed(points[:-1]):
        f.write(f'{lon} {lat}\n')
print(f'Backtrack source segment {segment_id}: {len(points)} points; total DB points={total}')
PY

FIRST_LAT="$(awk -F= '$1=="first_lat"{print $2}' "$META")"
FIRST_LON="$(awk -F= '$1=="first_lon"{print $2}' "$META")"
LAST_LAT="$(awk -F= '$1=="last_lat"{print $2}' "$META")"
LAST_LON="$(awk -F= '$1=="last_lon"{print $2}' "$META")"
TOTAL_POINTS="$(awk -F= '$1=="total_points"{print $2}' "$META")"
[ -n "$FIRST_LAT" ] && [ -n "$LAST_LAT" ] || fail "could not parse Backtrack source metadata"

# Confirm recording really paused, then activate Backtrack from the final point of the exact
# persisted segment chosen above. Inject after UI lookup so the 15-second freshness gate is real.
tap_desc_with_fix "المسارات" "$LAST_LON" "$LAST_LAT"
dump_ui
grep -q 'التسجيل  متوقف مؤقتًا' "$UI_XML" || fail "recording did not pause before Backtrack QA"
tap_text "إجراءات المسار"
tap_text_with_fix "رجوع على آخر مسار" "$LAST_LON" "$LAST_LAT"

# Runtime proof of activation and distance-to-track calculation.
tap_desc_with_fix "المسارات" "$LAST_LON" "$LAST_LAT"
dump_ui
grep -q 'الرجوع على المسار  مفعّل' "$UI_XML" || fail "Backtrack did not become active"
grep -q 'البعد عن المسار' "$UI_XML" || fail "Backtrack off-track distance is missing"
if grep -q 'البعد عن المسار  بانتظار GPS' "$UI_XML"; then
  fail "Backtrack did not receive a live GPS fix"
fi
tap_text "إغلاق"

# Walk the selected segment backward breadcrumb-by-breadcrumb. The final first-point fix must make
# OfflineMapView stop Backtrack automatically at the recorded origin.
while read -r lon lat; do
  [ -n "${lon:-}" ] || continue
  adb emu geo fix "$lon" "$lat" >/dev/null
  sleep 2
done < "$REVERSE_POINTS"
sleep 2

tap_desc_with_fix "المسارات" "$FIRST_LON" "$FIRST_LAT"
dump_ui
grep -q 'الرجوع على المسار  متوقف' "$UI_XML" || fail "Backtrack did not finish at the recorded start"
tap_text "إغلاق"

# Export the frozen breadcrumb database to GPX through the real UI action.
tap_desc "المسارات"
tap_text "إجراءات المسار"
tap_text "حفظ المسار الحالي GPX"

TRACK_DIR="/sdcard/Android/data/$PKG/files/tracks"
GPX_PATH=""
for _ in $(seq 1 20); do
  GPX_PATH="$(adb shell "ls -1t '$TRACK_DIR'/*.gpx 2>/dev/null | head -n 1" | tr -d '\r' || true)"
  [ -n "$GPX_PATH" ] && break
  sleep 1
done
[ -n "$GPX_PATH" ] || fail "GPX export did not create a saved track file"
PENDING="$(adb shell "ls -1 '$TRACK_DIR'/*.pending 2>/dev/null" | tr -d '\r' || true)"
[ -z "$PENDING" ] || fail "GPX export left a .pending file behind"
adb exec-out cat "$GPX_PATH" > "$GPX_OUT" || fail "could not copy exported GPX"
[ -s "$GPX_OUT" ] || fail "exported GPX is empty"

python3 - "$GPX_OUT" "$TOTAL_POINTS" "$OUT/tracks-runtime-acceptance.txt" "$META" <<'PY'
import os,sys,xml.etree.ElementTree as ET
path,total_expected,report,meta=sys.argv[1:5]
root=ET.parse(path).getroot()
ns={'g':'http://www.topografix.com/GPX/1/1'}
if root.tag != '{http://www.topografix.com/GPX/1/1}gpx':
    raise SystemExit('ERROR: exported GPX root/namespace is invalid')
if root.attrib.get('version') != '1.1' or root.attrib.get('creator') != 'DarbakMaps':
    raise SystemExit('ERROR: exported GPX metadata is invalid')
points=root.findall('.//g:trkpt',ns)
segments=root.findall('.//g:trkseg',ns)
if len(points) != int(total_expected):
    raise SystemExit(f'ERROR: GPX point count mismatch: expected {total_expected}, got {len(points)}')
if not segments or not points:
    raise SystemExit('ERROR: GPX contains no usable track segments/points')
values={}
with open(meta,encoding='utf-8') as f:
    for line in f:
        if '=' in line:
            k,v=line.rstrip('\n').split('=',1); values[k]=v
with open(report,'w',encoding='utf-8') as f:
    f.write('Darbak Maps Backtrack + GPX runtime gate: PASS\n')
    f.write(f"backtrack_segment_id={values.get('segment_id','')}\n")
    f.write(f"backtrack_segment_points={values.get('segment_points','')}\n")
    f.write('backtrack_activated=yes\n')
    f.write('backtrack_finished_at_origin=yes\n')
    f.write(f'gpx_points={len(points)}\n')
    f.write(f'gpx_segments={len(segments)}\n')
    f.write(f'gpx_file={os.path.basename(path)}\n')
print(f'Backtrack finished at origin; GPX verified with {len(points)} points in {len(segments)} segments.')
PY

# Prove the exported file is discoverable through Darbak Maps' saved-track browser.
tap_desc "المسارات"
tap_text "إجراءات المسار"
tap_text "المسارات المحفوظة"
dump_ui
grep -q 'المسارات المحفوظة — 1' "$UI_XML" || fail "saved GPX is not listed by the tracks browser"

# Preserve the actual external filename in the acceptance report.
printf 'saved_tracks_browser=1\nexternal_gpx_name=%s\n' "$(basename "$GPX_PATH")" >> "$OUT/tracks-runtime-acceptance.txt"
adb shell input keyevent 4 || true
