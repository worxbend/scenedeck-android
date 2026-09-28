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
- **Semantic colors are product law**: Program/Live = red tally family; Preview =
  green; Recording = red pulse; meter zones green/yellow/red at −20/−9 dB; stats
  warnings amber→red. These must read correctly in every family.

## Typography

- Inter for UI, JetBrains Mono (tabular figures) for dB readouts, counters, timecodes.
- M3 Expressive type scale; emphasized display styles for the current scene name.

## Shape, elevation, components

- Scene cards: large expressive rounding; transport buttons: stadium (`full`);
  mixer strips: medium. Tonal elevation, not shadows. Active scene card = shape morph
  (MaterialShapes) + accent glow ring; reduced-motion fallback = crossfade.
- Signature custom components (Canvas): `VolumeMeter` (OBS-accurate zones, per-channel
  bars, 20 s peak-hold line, ~300 ms loudness notch, pre-fader base square, smooth
  decay), `StatGauge` (arc with amber/red threshold arcs), `TrendChart`.

## Motion

- `MotionScheme.expressive()` default; springs for card press (scale ~0.96) and faders.
- Motion is semantic: scene activation, tally pulse (1 Hz), meter decay. No decorative
  animation. Honor settings: Full / Reduced / Off + system animator scale.

## Iconography

- Primary: **Lucide** via compose-icons (`radio-tower`, `disc`, `clapperboard`,
  `sliders-vertical`, `activity`, `layers`, `stethoscope`, `waypoints`, …).
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
