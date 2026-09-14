# P0 execution plan

- R01: signing and data-transition safety. Do not claim in-place update compatibility without matching certificate. Preserve data first.
- R02: one release update channel; manifest generated only from the exact signed APK.
- R03: publish metadata derived from the build commit/APK, never current moving main.

No release publication until all three have evidence.
