# Darbak Edition — Visual Identity Contract

Source of truth: `abo-sultann/Launcher.2026` current main branch.

## Identity
- Product family: Darbak / دربك
- Signature: أبوسلطان
- Do not reuse the legacy Darbak Maps visual identity.
- Preserve upstream OsmAnd licensing/notices where required.
- OsmAnd remains the map engine and offline map foundation.

## Launcher 2026 palette
- CarbonDark: #07111D
- CarbonSurface: #111D2B
- CarbonCard: #101E2C
- CarbonCardBorder: #2D5573
- CyanNeon: #39A9FF
- AmberRacing: #FFC857
- CrimsonSport: #FF647C
- EmeraldSafe: #4CD989
- PurpleNeon: #8F83FF
- DarbakGold: #D7AD55
- TextPrimary: #FFFFFF
- TextSecondary: #D0E2F1
- TextMuted: #8299AA

## Car-screen UI rules
- Target baseline: Android 7.1 / API 25, 1024x600 landscape, Allwinner T3, ~1 GB RAM.
- Arabic-first RTL.
- Large touch targets and high contrast.
- Full-screen automotive layout.
- Settings landing page uses large rounded cards, matching Launcher 2026.
- Settings categories: Map, Display, Tracks & Recording, Saved Places, GPS, Storage, Sound & Alerts, System, About.
- Avoid expensive blur, continuous animation, and GPU-heavy decoration.
- Keep map canvas dominant; controls should not obscure navigation context.
- Sensitive/destructive actions require deliberate interaction.
