#!/usr/bin/env bash
set -euo pipefail

OUT="visual-qa"
PKG="com.abosultan.darbakmaps.debug"
ACT="com.abosultan.darbakmaps.MainActivity"
SERVICE="com.abosultan.darbakmaps.core.TrackRecordingService"
RESUME_ACTION="com.abosultan.darbakmaps.action.RESUME_TRACK"
PAUSE_ACTION="com.abosultan.darbakmaps.action.PAUSE_TRACK"
DB_OUT="$OUT/background-track.db"
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

# Start from a clean app state so this gate proves fresh persistence, not old breadcrumbs.
adb shell pm clear "$PKG" >/dev/null || fail "could not clear app data"
adb shell pm grant "$PKG" android.permission.ACCESS_FINE_LOCATION || true
adb shell pm grant "$PKG" android.permission.READ_EXTERNAL_STORAGE || true
adb shell am start -W -n "$PKG/$ACT" >/dev/null || fail "could not launch Darbak Maps"
sleep 3

# Explicitly enable automatic recording in case defaults change later.
adb shell am startservice \
  -n "$PKG/$SERVICE" \
  -a "$RESUME_ACTION" >/dev/null || fail "could not resume recording service"
sleep 1

# Foreground breadcrumbs: short realistic movement, safely below implied-speed rejection.
inject_fix 46.67530 24.71360
inject_fix 46.67555 24.71385

# Send only the UI to background. Do NOT force-stop the package: the service must stay alive.
adb shell input keyevent 3
sleep 2
adb shell dumpsys activity services "$PKG" > "$OUT/background-service.txt" || true
grep -q "TrackRecordingService" "$OUT/background-service.txt" \
  || fail "recording service did not remain alive after UI went to background"

# These fixes are unique to the background phase and must be persisted by the service.
inject_fix 46.67580 24.71410
inject_fix 46.67605 24.71435
inject_fix 46.67630 24.71460

# Pause recording so SQLite closes cleanly before copying the database out of the app sandbox.
adb shell am startservice \
  -n "$PKG/$SERVICE" \
  -a "$PAUSE_ACTION" >/dev/null || fail "could not pause recording service"
sleep 2

adb exec-out run-as "$PKG" cat databases/darbak_tracks.db > "$DB_OUT" \
  || fail "could not copy track database"
[ -s "$DB_OUT" ] || fail "track database is empty"

python3 - "$DB_OUT" "$OUT/background-track-acceptance.txt" <<'PY'
import math
import sqlite3
import sys

path, report = sys.argv[1], sys.argv[2]
con = sqlite3.connect(path)
rows = con.execute(
    "SELECT id, segment_id, lat, lon, time_ms, distance_from_prev "
    "FROM track_points ORDER BY id"
).fetchall()
con.close()

if len(rows) < 4:
    raise SystemExit(f"ERROR: expected at least 4 persisted breadcrumbs, got {len(rows)}")

# Final two coordinates only occur after HOME was pressed. A near match proves background writes.
targets = [(24.71435, 46.67605), (24.71460, 46.67630)]

def meters(a_lat, a_lon, b_lat, b_lon):
    r = 6371000.0
    p1, p2 = math.radians(a_lat), math.radians(b_lat)
    dp = math.radians(b_lat - a_lat)
    dl = math.radians(b_lon - a_lon)
    h = math.sin(dp/2)**2 + math.cos(p1)*math.cos(p2)*math.sin(dl/2)**2
    return 2*r*math.atan2(math.sqrt(h), math.sqrt(1-h))

matched = []
for tlat, tlon in targets:
    best = min(meters(lat, lon, tlat, tlon) for _, _, lat, lon, _, _ in rows)
    matched.append(best)

if max(matched) > 18.0:
    raise SystemExit(
        "ERROR: background GPS fixes were not persisted; nearest distances="
        + ",".join(f"{x:.1f}m" for x in matched)
    )

segments = len({r[1] for r in rows})
retained = sum(float(r[5] or 0.0) for r in rows)
with open(report, "w", encoding="utf-8") as f:
    f.write("Darbak Maps background tracking gate: PASS\n")
    f.write(f"persisted_points={len(rows)}\n")
    f.write(f"segments={segments}\n")
    f.write(f"retained_distance_m={retained:.1f}\n")
    f.write("background_target_nearest_m=" + ",".join(f"{x:.1f}" for x in matched) + "\n")

print(f"Background tracking persisted {len(rows)} points; background fixes verified.")
PY
