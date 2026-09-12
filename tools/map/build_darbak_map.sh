#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WORK="${1:-$ROOT/build/map-work}"
OUT="${2:-$ROOT/build/map-output}"
MISHARI_PAGE="${MISHARI_PAGE:-https://www.mediafire.com/?pr7b4xb4an3m0np}"
OSM_URL="${OSM_URL:-https://download.openstreetmap.fr/extracts/asia/saudi_arabia-latest.osm.pbf}"
OSMOSIS_VERSION="0.49.2"
MAPSFORGE_VERSION="0.30.0"

rm -rf "$WORK" "$OUT"
mkdir -p "$WORK" "$OUT"

log() { printf '\n==> %s\n' "$*"; }

log "Download Al Mishari Garmin archive"
python3 "$ROOT/tools/map/download_mediafire.py" "$MISHARI_PAGE" "$WORK/almisharIMAP.rar"
sha256sum "$WORK/almisharIMAP.rar" | tee "$OUT/mishari-source.sha256"

log "Extract Al Mishari archive"
mkdir -p "$WORK/mishari"
7z x -y -pTameem65 "$WORK/almisharIMAP.rar" "-o$WORK/mishari" >/tmp/darbak-7z.log
cat /tmp/darbak-7z.log
find "$WORK/mishari" -maxdepth 4 -type f -printf '%s\t%p\n' | sort -nr | tee "$OUT/mishari-files.txt"
IMG="$(find "$WORK/mishari" -type f -iname '*.img' -printf '%s\t%p\n' | sort -nr | head -n1 | cut -f2-)"
if [[ -z "$IMG" || ! -f "$IMG" ]]; then
  echo "No Garmin IMG file found after extraction" >&2
  exit 20
fi
printf 'Selected Garmin IMG: %s (%s bytes)\n' "$IMG" "$(stat -c%s "$IMG")" | tee "$OUT/mishari-img.txt"

log "Decode Garmin IMG into OSM features"
git clone --depth 1 https://github.com/vasilevzhivko/garmin_img.git "$WORK/garmin_img"
cp "$ROOT/tools/map/garmin_to_osm.dart" "$WORK/garmin_img/example/darbak_garmin_to_osm.dart"
(
  cd "$WORK/garmin_img"
  dart pub get
  dart run example/darbak_garmin_to_osm.dart "$IMG" "$WORK/mishari-raw.osm" "$OUT/mishari-report.json"
)

log "Repair legacy Arabic labels"
python3 "$ROOT/tools/map/fix_arabic_labels.py" "$WORK/mishari-raw.osm" "$WORK/mishari.osm"

log "Sort Mishari vector data"
osmium sort "$WORK/mishari.osm" -o "$WORK/mishari.pbf" --overwrite
osmium fileinfo -e "$WORK/mishari.pbf" | tee "$OUT/mishari-osmium-info.txt"

log "Download current Saudi Arabia OSM extract"
curl --fail --location --retry 4 --retry-delay 3 --output "$WORK/saudi-latest.osm.pbf" "$OSM_URL"
sha256sum "$WORK/saudi-latest.osm.pbf" | tee "$OUT/osm-source.sha256"
osmium fileinfo -e "$WORK/saudi-latest.osm.pbf" | tee "$OUT/osm-fileinfo.txt"

log "Merge OSM base with Mishari desert additions"
osmium merge "$WORK/saudi-latest.osm.pbf" "$WORK/mishari.pbf" -o "$WORK/darbak-merged.osm.pbf" --overwrite
osmium fileinfo -e "$WORK/darbak-merged.osm.pbf" | tee "$OUT/merged-osmium-info.txt"

log "Install Osmosis ${OSMOSIS_VERSION} and Mapsforge writer ${MAPSFORGE_VERSION}"
curl --fail --location --retry 4 \
  -o "$WORK/osmosis.zip" \
  "https://github.com/openstreetmap/osmosis/releases/download/${OSMOSIS_VERSION}/osmosis-${OSMOSIS_VERSION}.zip"
unzip -q "$WORK/osmosis.zip" -d "$WORK/osmosis"
OSMOSIS_HOME="$WORK/osmosis"
chmod +x "$OSMOSIS_HOME/bin/osmosis"
mkdir -p "$HOME/.openstreetmap/osmosis/plugins"
curl --fail --location --retry 4 \
  -o "$HOME/.openstreetmap/osmosis/plugins/mapsforge-map-writer-${MAPSFORGE_VERSION}-jar-with-dependencies.jar" \
  "https://github.com/mapsforge/mapsforge/releases/download/${MAPSFORGE_VERSION}/mapsforge-map-writer-${MAPSFORGE_VERSION}-jar-with-dependencies.jar"

test "$(sha256sum "$HOME/.openstreetmap/osmosis/plugins/mapsforge-map-writer-${MAPSFORGE_VERSION}-jar-with-dependencies.jar" | awk '{print $1}')" \
  = "2ed0f79498916259826e0b6ef132ecc427e27999b26736036358733279825626"

log "Prepare Darbak desert tag mapping"
curl --fail --location --retry 4 \
  -o "$WORK/tag-mapping-default.xml" \
  "https://raw.githubusercontent.com/mapsforge/mapsforge/${MAPSFORGE_VERSION}/mapsforge-map-writer/src/main/config/tag-mapping.xml"
python3 "$ROOT/tools/map/prepare_tag_mapping.py" "$WORK/tag-mapping-default.xml" "$WORK/tag-mapping-darbak.xml"

log "Build darbak-saudi.map"
"$OSMOSIS_HOME/bin/osmosis" \
  --rb file="$WORK/darbak-merged.osm.pbf" \
  --mw file="$OUT/darbak-saudi.map" \
  type=hd \
  threads=2 \
  bbox=16.0,34.0,32.6,55.7 \
  map-start-position=24.7136,46.6753 \
  map-start-zoom=7 \
  preferred-languages=ar,en \
  tag-conf-file="$WORK/tag-mapping-darbak.xml" \
  comment="Darbak Maps private: OpenStreetMap + Al Mishari desert additions"

MAP_BYTES="$(stat -c%s "$OUT/darbak-saudi.map")"
if (( MAP_BYTES < 50000000 )); then
  echo "Generated map is unexpectedly small: $MAP_BYTES bytes" >&2
  exit 30
fi
MAP_SHA="$(sha256sum "$OUT/darbak-saudi.map" | awk '{print $1}')"
printf '%s  darbak-saudi.map\n' "$MAP_SHA" > "$OUT/darbak-saudi.map.sha256"

python3 - "$OUT/darbak-map-manifest.json" "$MAP_BYTES" "$MAP_SHA" <<'PY'
import json, sys
from datetime import datetime, timezone
path, size, sha = sys.argv[1], int(sys.argv[2]), sys.argv[3]
data = {
    "name": "darbak-saudi.map",
    "format": "mapsforge-v5",
    "privateUse": True,
    "sizeBytes": size,
    "sha256": sha,
    "builtAtUtc": datetime.now(timezone.utc).isoformat(),
    "sources": [
        "OpenStreetMap Saudi Arabia current extract",
        "Al Mishari standalone desert Garmin map (desert additions only)"
    ],
    "mergePolicy": "OSM authoritative for paved roads/cities/services; Mishari augments off-road tracks, desert hydrology and POIs"
}
with open(path, 'w', encoding='utf-8') as f:
    json.dump(data, f, ensure_ascii=False, indent=2)
PY

log "Map completed"
ls -lh "$OUT/darbak-saudi.map" "$OUT/darbak-map-manifest.json"
cat "$OUT/darbak-saudi.map.sha256"
