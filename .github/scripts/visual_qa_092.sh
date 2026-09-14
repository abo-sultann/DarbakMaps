#!/usr/bin/env bash
set -euo pipefail

PKG='com.abosultan.darbakmaps.debug'
ACTIVITY='com.abosultan.darbakmaps.MainActivity'
APK='app/build/outputs/apk/debug/app-debug.apk'
OUT="$GITHUB_WORKSPACE/visual-qa"
mkdir -p "$OUT"

adb install -r "$APK"
adb shell wm size 1024x600
adb shell wm density 160
adb shell settings put system accelerometer_rotation 0 || true
adb shell settings put system user_rotation 1 || true
adb shell settings put secure location_providers_allowed +gps || true
adb shell pm grant "$PKG" android.permission.ACCESS_FINE_LOCATION || true
adb shell pm grant "$PKG" android.permission.ACCESS_COARSE_LOCATION || true

# Seed realistic app-private QA data before first launch.
cat > /tmp/darbak_places.xml <<'EOF'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
  <string name="places">[{"id":"qa-quail","name":"QA Camp","lat":26.3500,"lon":43.9700,"createdAt":1789430400000,"icon":"quail","category":"سمان","note":"موقع تجريبي للمراجعة البصرية"},{"id":"qa-tree","name":"شجرة الطلح","lat":26.3610,"lon":43.9850,"createdAt":1789430460000,"icon":"tree","category":"شجرة","note":"ظل وموقع محفوظ"},{"id":"qa-water","name":"مورد الماء","lat":26.3380,"lon":43.9520,"createdAt":1789430520000,"icon":"water","category":"ماء","note":"موقع قريب"}]</string>
</map>
EOF
APP_DIR="$(adb shell run-as "$PKG" pwd | tr -d '\r')"
test -n "$APP_DIR"
adb shell run-as "$PKG" mkdir -p "$APP_DIR/shared_prefs"
adb push /tmp/darbak_places.xml /data/local/tmp/darbak_places.xml >/dev/null
adb shell chmod 644 /data/local/tmp/darbak_places.xml
adb shell run-as "$PKG" cp /data/local/tmp/darbak_places.xml "$APP_DIR/shared_prefs/darbak_places.xml"
adb shell rm -f /data/local/tmp/darbak_places.xml

# Qassim-like sample position so distance/save UI can render realistically.
adb emu geo fix 43.9700 26.3500 || true
adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/$ACTIVITY"
sleep 5
adb emu geo fix 43.9700 26.3500 || true
sleep 3

capture() {
  local name="$1"
  adb exec-out screencap -p > "$OUT/$name.png"
  test -s "$OUT/$name.png"
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb pull /sdcard/window.xml "$OUT/$name.xml" >/dev/null 2>&1 || true
}

tap_node() {
  local mode="$1"
  local needle="$2"
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1
  adb pull /sdcard/window.xml /tmp/window.xml >/dev/null 2>&1
  local xy
  xy="$(python3 - "$mode" "$needle" <<'PY'
import re, sys, xml.etree.ElementTree as ET
mode, needle = sys.argv[1], sys.argv[2]
root = ET.parse('/tmp/window.xml').getroot()
match = None
for node in root.iter('node'):
    text = node.attrib.get('text', '')
    rid = node.attrib.get('resource-id', '')
    desc = node.attrib.get('content-desc', '')
    ok = False
    if mode == 'text':
        ok = text == needle or needle in text
    elif mode == 'id':
        ok = rid.endswith('/' + needle) or rid.endswith(':id/' + needle)
    elif mode == 'desc':
        ok = desc == needle or needle in desc
    if ok and node.attrib.get('bounds'):
        match = node
        break
if match is None:
    print('')
    sys.exit(0)
nums = list(map(int, re.findall(r'\d+', match.attrib['bounds'])))
if len(nums) != 4:
    print('')
    sys.exit(0)
x1, y1, x2, y2 = nums
print(f'{(x1+x2)//2} {(y1+y2)//2}')
PY
  )"
  if [ -z "$xy" ]; then
    echo "::error::UI node not found: $mode=$needle"
    cat /tmp/window.xml
    return 1
  fi
  adb shell input tap $xy
  sleep 1
}

capture '01-main'

tap_node text 'القائمة'
capture '02-more'

tap_node text 'الإعدادات'
capture '03-settings'
adb shell input keyevent 4
sleep 1

# Actual PointEditor, fed by emulator GPS.
tap_node text 'حفظ موقع'
capture '04-save-place'
adb shell input keyevent 4
sleep 1

tap_node text 'المحفوظات'
capture '05-saved-places'
adb shell input keyevent 4
sleep 1

# Search real saved-place data; no 181 MB map download is needed for this UI gate.
tap_node id 'search_input'
adb shell input text 'QA'
adb shell input keyevent 66
sleep 4
capture '06-search-results'

tap_node text 'QA Camp'
capture '07-search-actions'
adb shell input keyevent 4
sleep 1

adb shell input keyevent 4 || true
tap_node text 'القائمة'
tap_node text 'حول دربك'
capture '08-about'

{
  echo 'source_sha='"$GITHUB_SHA"
  echo 'package='"$PKG"
  echo 'api=25'
  echo 'viewport=1024x600'
  echo 'screenshots=8'
  adb shell wm size | tr '\r' ' '
  adb shell wm density | tr '\r' ' '
} > "$OUT/visual-qa.txt"
