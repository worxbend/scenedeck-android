---
name: material3-expressive
description: Material 3 Expressive theming, motion, typography, iconography and award-level UI craft for SceneDeck Android. Use for any UI, theme, color, motion, icon, or asset work.
---

# Material 3 Expressive design skill

SceneDeck Android's visual bar: **award-winning tactile broadcast surface** —
dark-first, studio-native, glowing tally energy, disciplined by M3 Expressive.
Full spec: `docs/DESIGN_SYSTEM.md`. Follow it; this file is the working checklist.

## Theming

- All themes go through the `SceneDeckTheme` wrapper in `:core:designsystem`:
  `MaterialExpressiveTheme(colorScheme = family.colorScheme(dark),
  motionScheme = motionSchemeFor(userMotionLevel), typography = SceneDeckTypography)`.
- Theme families (see DESIGN_SYSTEM.md §2): SceneDeck (default), OBS, Obsidian, Nord,
  Dracula, Solarized Dark, Stream Red, Studio Purple, Sunset Coral, Mint Control,
  High Contrast, Material You (dynamic color, Android 12+). Each = light + dark
  `ColorScheme`; never hardcode a color in a feature module.
- **Semantic colors are product law**: Program scene = red tally family; Preview =
  green. Stream transport = blue idle Stream / red active Stop; elapsed stream time
  and LIVE status = green, recording time and REC status = amber. Meter zones are
  green/yellow/red at −20/−9 dB; stats warnings amber→red. Keep text readable in light
  themes with shared `BroadcastControls` colors.

## Typography

- Inter for UI, JetBrains Mono (tabular figures) for dB readouts, counters, timecodes.
- M3 Expressive type scale; emphasized display styles for the current scene name.

## Shape, elevation, components

- Scene cards: uniform 24dp rounded tiles, numbered positions, icon wells and subtle
  outlines; no left accent rail. Program/preview borders and pills retain their semantic
  colors. Drag grips appear only in explicit reorder mode. Transport buttons: stadium
  (`full`); mixer strips: medium. Tonal elevation, not shadows.
- Signature custom components (Canvas): `VolumeMeter` (OBS-accurate zones, per-channel
  bars, 20 s peak-hold line, ~300 ms loudness notch, pre-fader base square, smooth
  decay), `StatGauge` (arc with amber/red threshold arcs), `TrendChart`.

## Motion

- `MotionScheme.expressive()` default; springs for card press (scale ~0.96) and faders.
- Motion is semantic: scene activation, tally pulse, meter decay. The active Stream
  breathing halo and output-status pulses run only with Full motion. Honor Full /
  Reduced / Off and system animator scale; use static controls for Reduced/Off.

## Iconography

- Primary: hand-ported **Lucide-style ImageVector** catalogue in `:core:designsystem`
  (`radio-tower`, `disc`, `clapperboard`, `sliders-vertical`, `activity`, `layers`,
  `stethoscope`, `waypoints`, …). The compose-icons Lucide pack is unavailable on
  Maven Central for the pinned stack; do not add it as a dependency.
  Secondary: Material Symbols Rounded for gaps.
- User-assignable scene icon catalogue lives in `:core:designsystem/icons/SceneIcons.kt`.

## Craft checklist (run before finishing any UI work)

1. Previewed in dark + light + at least SceneDeck and OBS families
   (`@PreviewLightDark` / multi-preview), and Roborazzi goldens updated.
2. Edge-to-edge insets handled (status/nav bars, IME).
3. 48dp+ touch targets; TalkBack content descriptions (meters expose text dB values).
4. Press states + haptics (`HapticFeedbackType`) on all deck controls.
5. Reduced-motion path verified; recording tally pulse disabled when reduced.
6. High-frequency values (meters, counters) read in draw phase, not composition.
7. Contrast verified per theme family (High Contrast family must pass AAA for states).

## Shared studio components

- Use `StudioPageHeader`, `StudioSectionHeader`, `StudioIconWell` and `StudioChrome`
  control colors; consult the living `StudioStylebook` before adding a new treatment.
- Keep status-bar insets outside main-screen scroll containers. Connection forms open
  fully expanded, group identity/server/security and keep password drafts out of saved state.
- Persistent status distinguishes connection from OFF AIR / LIVE / REC; output state
  is unknown when disconnected, and performance metrics appear only while streaming.
