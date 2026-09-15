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
}
trap collect_debug EXIT

adb shell wm size 1024x600
adb shell wm density 160
adb install -r "$APK"
adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/$ACT"
sleep 4
adb exec-out screencap -p > "$OUT/01-home.png"

cat > "$OUT/acceptance.txt" <<'EOF'
Darbak Maps clean rebuild — visual gate 1
Expected viewport: 1024x600 landscape
Identity: Darbak Interface Reference 2.0 only
Forbidden: old green identity, old 0.9.x layouts, stock white dialogs
This gate validates the first coded shell only; it does NOT claim the real map engine is connected.
EOF
