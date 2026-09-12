# Darbak Maps — OSM + Al-Mishari data pipeline

This document defines the private, car-screen map build used by Darbak Maps.

## Goal

Produce one offline Mapsforge file (`darbak-saudi.map`) optimized for the owner's Android 7.x car screen (1024×600), using:

1. **Current OpenStreetMap Saudi Arabia data** as the authoritative base for roads, settlements, services and current names.
2. **Al-Mishari desert data** as the supplemental layer for desert tracks, wadis, faydat, qaa/sabkha, peaks, wells and desert localities that are absent from OSM.

The output is for private use in Darbak Maps and is not a Navitel/NM3 runtime dependency.

## Source policy / precedence

The merge must not blindly stack both maps. When features overlap, use these rules:

| Feature class | Preferred source | Merge rule |
|---|---|---|
| Motorways / paved roads | OSM | OSM wins |
| City streets | OSM | OSM wins |
| Towns / villages | OSM | OSM wins; Mishari only fills missing localities |
| Fuel / public services | OSM | OSM wins |
| Desert tracks | Mishari + OSM | keep both after geometric de-duplication |
| Wadis / seasonal drainage | Mishari + OSM | prefer named/detailed geometry; remove near-duplicates |
| Faydat / qaa / sabkha | Mishari | retain unless a clearly newer OSM equivalent exists |
| Peaks / hills | Mishari + OSM | keep named features, merge near-duplicates by name+distance |
| Wells / springs | Mishari + OSM | keep both after near-duplicate check |
| Protected areas | OSM | OSM wins |

## Al-Mishari extraction input

The preferred source is the **standalone Garmin IMG version of Al-Mishari desert map**, not a combined city map and not `mapV2.nm3`.

One-time extraction workflow on Windows:

1. Open the standalone Al-Mishari `.img` in GPSMapEdit.
2. Save/export it as Polish MP (`mishari.mp`).
3. Keep the original IMG unchanged as the archive/reference copy.
4. Convert the MP features into an intermediate GIS/OSM-compatible dataset.
5. Normalize tags according to the table below before merging with current OSM.

`mapV2.nm3` is retained only as a visual/reference check because NM3 is a final Navitel binary and is not the preferred source for extraction.

## Darbak normalization

Convert Mishari feature classes into standard or Darbak-compatible OSM tags before Mapsforge compilation.

| Desert feature | Normalized tags |
|---|---|
| Main desert road | `highway=track`, `tracktype=grade1` or `grade2` |
| Faint/off-road trail | `highway=track`, `tracktype=grade3..grade5` |
| Foot/path line | `highway=path` |
| Wadi / shaib | `waterway=stream`, `intermittent=yes` (or retained `waterway=wadi` during intermediate processing, then mapped for writer compatibility) |
| Peak | `natural=peak` |
| Hill | `natural=hill` |
| Well | `man_made=water_well` |
| Spring | `natural=spring` |
| Sand | `natural=sand` |
| Dune | `natural=dune` |
| Fayda / basin | `natural=wetland` plus `name=*` and optional `darbak:class=fayda` |
| Qaa / sabkha | `natural=wetland` or `natural=salt_pond`, plus `darbak:class=qaa|sabkha` |
| Desert locality | `place=locality` |
| Camp/reference point | `tourism=camp_site` only when semantically valid; otherwise `place=locality` + `darbak:class=*` |

Original Arabic names are preserved. Do not transliterate or replace an Arabic label merely because an English label exists.

## De-duplication

Use a two-stage de-duplication pass:

1. **Semantic:** normalized Arabic name, feature class, and source identity.
2. **Spatial:** near-identical points/lines/polygons within a class-specific tolerance.

OSM wins for current road/service data. Mishari wins for desert-only content where OSM has no equivalent.

Do not merge two differently named desert features solely because their geometries touch or overlap.

## Mapsforge output

Compile the merged OSM/PBF dataset with Mapsforge Map Writer into:

`darbak-saudi.map`

Recommended map characteristics:

- Saudi Arabia only for the initial private build.
- Preferred language: Arabic (`name:ar`, falling back to `name`).
- Keep highway geometry and desert reference features at useful zoom levels.
- Avoid excessive building detail at low/medium zooms to reduce draw cost on the 1 GB-class head unit.
- Keep POI density conservative: fuel, wells/springs, settlements, desert localities and useful natural features.

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

## QA before replacing the car map

Check at least these areas before making a map package the default:

1. Al-Qassim / Al-Rass and surrounding desert.
2. A dense city area to check label clutter and road hierarchy.
3. A desert area with many named tracks/wadis.
4. A sabkha/qaa/fayda area.
5. Zoom 7–10 for overview performance.
6. Zoom 12–15 while driving for track/name readability.
7. GPS arrow and heading-up rotation while the custom theme is active.

A new map data package must not replace the last known-good package until these checks pass.
