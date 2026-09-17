#!/usr/bin/env bash
set -euo pipefail

OUT="visual-qa"
PKG="com.abosultan.darbakmaps.debug"
ACT="com.abosultan.darbakmaps.MainActivity"
DB_BEFORE_REBOOT="$OUT/background-track-before-reboot.db"
DB_AFTER_REBOOT="$OUT/background-track-after-reboot.db"
mkdir -p "$OUT"

fail() {
  echo "ERROR: $*" >&2
  exit 50
}

inject_fix() {
  local lon="$1" lat="$2"
  adb emu geo fix "$lon" "$lat" >/dev/null
  # Recorder pump runs every 2 s. Give API25 software emulation enough margin.
  sleep 3
}

copy_db() {
  local target="$1"
  adb exec-out run-as "$PKG" cat databases/darbak_tracks.db > "$target" \
    || fail "could not copy track database"
  [ -s "$target" ] || fail "track database is empty"
}

wait_for_boot() {
  adb wait-for-device
  local booted=""
  for _ in $(seq 1 90); do
    booted="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
    [ "$booted" = "1" ] && break
    sleep 2
  done
  [ "$booted" = "1" ] || fail "Android did not finish rebooting"

  # The emulator runner unlocks API25 with KEYCODE_MENU after the initial boot. Do the same after
  # our in-test reboot; normal BOOT_COMPLETED for credential-encrypted apps may wait for user unlock.
  adb shell input keyevent 224 >/dev/null 2>&1 || true
  sleep 1
  adb shell input keyevent 82 >/dev/null 2>&1 || true
  sleep 3
}

wait_for_recording_service_after_boot() {
  local service_file="$OUT/background-service-after-reboot.txt"
  for _ in $(seq 1 20); do
    adb shell dumpsys activity services "$PKG" > "$service_file" || true
    if grep -q "TrackRecordingService" "$service_file"; then
      return 0
    fi
    sleep 2
  done

  # Preserve enough evidence to distinguish delayed BOOT_COMPLETED, stopped-package state, and
  # session-state problems without another blind rerun.
  adb shell dumpsys package "$PKG" > "$OUT/background-package-after-reboot.txt" || true
  adb exec-out run-as "$PKG" cat shared_prefs/darbak_map_session.xml \
    > "$OUT/background-session-after-reboot.xml" 2>/dev/null || true
  adb logcat -d > "$OUT/background-reboot-logcat.txt" 2>/dev/null || true
  fail "recording service was not restored by BootReceiver after reboot"
}

# Start from a clean app state so this gate proves fresh persistence, not old breadcrumbs.
adb shell pm clear "$PKG" >/dev/null || fail "could not clear app data"
adb shell pm grant "$PKG" android.permission.ACCESS_FINE_LOCATION || true
adb shell pm grant "$PKG" android.permission.READ_EXTERNAL_STORAGE || true
adb shell am start -W -n "$PKG/$ACT" >/dev/null || fail "could not launch Darbak Maps"
sleep 4

# Recording is enabled by Darbak Maps itself. Verify the private service is alive rather than trying
# to start an exported component from the adb shell (the production service intentionally is private).
adb shell dumpsys activity services "$PKG" > "$OUT/background-service-foreground.txt" || true
grep -q "TrackRecordingService" "$OUT/background-service-foreground.txt" \
  || fail "recording service did not start with Darbak Maps"

# Foreground breadcrumbs: short realistic movement, safely below implied-speed rejection.
inject_fix 46.67530 24.71360
inject_fix 46.67555 24.71385

# Finish the Activity with Back. Do NOT force-stop the package: the recording service must survive
# after the visible Darbak Maps UI is genuinely closed.
adb shell input keyevent 4
sleep 2
adb shell dumpsys activity activities > "$OUT/background-activity.txt" || true
if grep -E "mResumedActivity.*$PKG|ResumedActivity.*$PKG" "$OUT/background-activity.txt" >/dev/null; then
  fail "Darbak Maps Activity is still resumed after Back"
fi
adb shell dumpsys activity services "$PKG" > "$OUT/background-service-closed-ui.txt" || true
grep -q "TrackRecordingService" "$OUT/background-service-closed-ui.txt" \
  || fail "recording service did not remain alive after Activity was closed"

# These fixes are unique to the closed-UI phase and must be persisted by the service.
inject_fix 46.67580 24.71410
inject_fix 46.67605 24.71435
inject_fix 46.67630 24.71460
copy_db "$DB_BEFORE_REBOOT"

# Reboot the Android 7.1 device. This kills the app process and service naturally, without force-stop.
# BootReceiver must restore recording because track_recording remains persisted as true.
adb reboot
wait_for_boot
wait_for_recording_service_after_boot

# New fixes after reboot prove that the restored service owns GPS and resumes persistence with no UI.
inject_fix 46.67655 24.71485
inject_fix 46.67680 24.71510
inject_fix 46.67705 24.71535
copy_db "$DB_AFTER_REBOOT"

python3 - "$DB_BEFORE_REBOOT" "$DB_AFTER_REBOOT" "$OUT/background-track-acceptance.txt" <<'PY'
import math
import sqlite3
import sys

before_path, after_path, report = sys.argv[1], sys.argv[2], sys.argv[3]

def rows(path):
    con = sqlite3.connect(path)
    out = con.execute(
        "SELECT id, segment_id, lat, lon, time_ms, distance_from_prev "
        "FROM track_points ORDER BY id"
    ).fetchall()
    con.close()
    return out

before = rows(before_path)
after = rows(after_path)
if len(before) < 4:
    raise SystemExit(f"ERROR: expected at least 4 breadcrumbs before reboot, got {len(before)}")
if len(after) <= len(before):
    raise SystemExit(
        f"ERROR: recording did not grow after reboot; before={len(before)} after={len(after)}"
    )

def meters(a_lat, a_lon, b_lat, b_lon):
    r = 6371000.0
    p1, p2 = math.radians(a_lat), math.radians(b_lat)
    dp = math.radians(b_lat - a_lat)
    dl = math.radians(b_lon - a_lon)
    h = math.sin(dp/2)**2 + math.cos(p1)*math.cos(p2)*math.sin(dl/2)**2
    return 2*r*math.atan2(math.sqrt(h), math.sqrt(1-h))

def nearest(rows_, targets):
    result = []
    for tlat, tlon in targets:
        result.append(min(meters(lat, lon, tlat, tlon) for _, _, lat, lon, _, _ in rows_))
    return result

closed_targets = [(24.71435, 46.67605), (24.71460, 46.67630)]
reboot_targets = [(24.71510, 46.67680), (24.71535, 46.67705)]
closed_match = nearest(before, closed_targets)
reboot_match = nearest(after, reboot_targets)
if max(closed_match) > 18.0:
    raise SystemExit(
        "ERROR: closed-UI GPS fixes were not persisted; nearest distances="
        + ",".join(f"{x:.1f}m" for x in closed_match)
    )
if max(reboot_match) > 18.0:
    raise SystemExit(
        "ERROR: post-reboot GPS fixes were not persisted; nearest distances="
        + ",".join(f"{x:.1f}m" for x in reboot_match)
    )

segments = len({r[1] for r in after})
retained = sum(float(r[5] or 0.0) for r in after)
with open(report, "w", encoding="utf-8") as f:
    f.write("Darbak Maps background + reboot tracking gate: PASS\n")
    f.write(f"points_before_reboot={len(before)}\n")
    f.write(f"points_after_reboot={len(after)}\n")
    f.write(f"segments_after_reboot={segments}\n")
    f.write(f"retained_distance_m={retained:.1f}\n")
    f.write("closed_ui_target_nearest_m=" + ",".join(f"{x:.1f}" for x in closed_match) + "\n")
    f.write("post_reboot_target_nearest_m=" + ",".join(f"{x:.1f}" for x in reboot_match) + "\n")

print(
    f"Background tracking PASS: {len(before)} points before reboot, "
    f"{len(after)} after reboot; post-boot fixes verified."
)
PY
