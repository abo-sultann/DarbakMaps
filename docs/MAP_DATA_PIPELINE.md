# Darbak Maps — OSM + Al-Mishari data pipeline

This document defines the private, car-screen map build used by Darbak Maps.

## Goal

Produce one offline Mapsforge file (`darbak-saudi.map`) optimized for the owner's Android 7.x car screen (1024×600), using:

1. **Current OpenStreetMap Saudi Arabia data** as the authoritative base for roads, settlements, services and current names.
2. **Al-Mishari standalone Garmin desert data** as the supplemental layer for desert tracks, wadis, peaks, wells and desert localities that are absent from OSM.

The output is for private use in Darbak Maps and is not a Navitel/NM3 runtime dependency.

## Source policy / precedence

The merge must not blindly stack both maps. When features overlap, use these rules:

| Feature class | Preferred source | Merge rule |
|---|---|---|
| Motorways / paved roads | OSM | OSM wins |
| City streets | OSM | OSM wins |
| Towns / villages | OSM | OSM wins; Mishari only fills missing localities |
| Fuel / public services | OSM | OSM wins |
| Desert tracks | Mishari + OSM | preserve Mishari off-road additions; OSM remains authoritative for mapped roads |
| Wadis / seasonal drainage | Mishari + OSM | preserve useful named/detail geometry |
| Peaks / hills | Mishari + OSM | keep named desert reference features |
| Wells / springs | Mishari + OSM | keep high-value desert POIs |
| Protected areas | OSM | OSM wins |

## Al-Mishari extraction input

The source is the **standalone Garmin IMG version of Al-Mishari desert map**, not the combined `mapV2.nm3` Navitel file.

The source archive is kept privately. The build no longer requires GPSMapEdit or a manual Windows conversion. The automated pipeline:

1. opens the private ZIP source and selects the Garmin `.img`;
2. decodes Garmin points, polylines, polygons and labels with the open-source `garmin_img` reader;
3. keeps desert-relevant feature classes and rejects paved-road classes that should come from current OSM;
4. exports the selected features to OSM XML using negative private IDs so they cannot collide with normal OSM IDs;
5. repairs legacy Arabic labels when old Windows Arabic bytes were exposed through a Western code page;
6. sorts the supplemental data to PBF with Osmium;
7. merges it with the latest Saudi Arabia OSM PBF;
8. compiles the merged data into Mapsforge `darbak-saudi.map`.

`mapV2.nm3` remains only a visual/reference check. It is not an input to the final map build.

## Darbak normalization

Mishari feature classes are normalized into standard OSM-compatible tags before Mapsforge compilation.

| Desert feature | Normalized tags |
|---|---|
| Main desert road | `highway=track`, optionally `tracktype=grade1|grade2` |
| Faint/off-road trail | `highway=track`, `tracktype=grade3..grade5` |
| Path line | `highway=path` |
| Wadi / shaib | `waterway=stream`, usually `intermittent=yes` |
| Peak / hill reference | `natural=peak` or `natural=hill` when the Garmin type is known |
| Well | `man_made=water_well` |
| Spring | `natural=spring` |
| Sand / dune | `natural=sand` / `natural=dune` when identified |
| Wet desert basin | `natural=wetland` when identified |
| Desert locality | `place=locality` |
| Camp/reference point | `tourism=camp_site` only when semantically identified; otherwise named custom POIs fall back conservatively to `place=locality` |

Original Arabic names are preserved. Do not transliterate or replace an Arabic label merely because an English label exists.

## De-duplication and safety

The build protects the modern base map by design:

- OSM is the sole authority for normal paved-road Garmin classes.
- Exact repeated Garmin features are removed during decoding.
- Mishari additions use negative IDs, avoiding ID collisions with OSM.
- Unknown unnamed Garmin features are discarded instead of guessed.
- Unknown named line features are retained only as off-road tracks, because the source is the standalone desert map.
- A generated Mapsforge file must pass size/integrity checks before it is published as a build artifact.

Future refinement may add a geometric near-duplicate pass after real-world inspection, but no current OSM road is replaced by Mishari.

## Mapsforge output

The output is:

`darbak-saudi.map`

Build characteristics:

- Saudi Arabia bounds for the private build;
- Arabic and English preferred languages, retaining the default `name`;
- hard-disk Mapsforge writer mode to reduce CI memory pressure;
- custom tag mapping for Darbak desert features;
- current Saudi OSM extract downloaded at build time;
- generated SHA-256 and JSON manifest for each finished map.

The Android app renders this with:

`app/src/main/assets/renderthemes/darbak_desert.xml`

## Visual hierarchy

The Darbak theme is desert-first:

- light sand background;
- paved roads in neutral high-contrast tones;
- desert tracks in brown/gold with grade-sensitive dashes;
- wadis/seasonal drainage in blue;
- peaks/hills and desert locality names visible without overwhelming the screen;
- city landuse/buildings subdued;
- fuel, wells and springs retained as high-value POIs;
- no satellite imagery and no online tile dependency.

## Reproducible build

The map build entry point is:

`tools/map/build_darbak_map.sh`

GitHub Actions uses:

`.github/workflows/map-build.yml`

The private decrypted Mishari ZIP is persisted as a private repository release asset after a successful bootstrap build, so subsequent rebuilds do not depend on MediaFire or manual extraction.

## QA before replacing the car map

Check at least these areas before making a map package the default:

1. Al-Qassim / Al-Rass and surrounding desert.
2. A dense city area to check label clutter and road hierarchy.
3. A desert area with many named tracks/wadis.
4. A sabkha/qaa/fayda area if available in the source.
5. Zoom 7–10 for overview performance.
6. Zoom 12–15 while driving for track/name readability.
7. GPS arrow and heading-up rotation while the custom theme is active.

A new map data package must not replace the last known-good package until these checks pass.
