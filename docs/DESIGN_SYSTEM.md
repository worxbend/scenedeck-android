# Design System — SceneDeck Android

Goal: an **award-winning, tactile broadcast surface**. The app should feel like
professional studio hardware translated to glass: confident, dark-first, glowing with
tally-light energy, but disciplined by Material 3 Expressive.

## 1. Design principles

1. **Tactile deck, not a settings app.** Big cards, physical-feeling presses
   (haptics + press-state scale/glow), instant feedback.
2. **Live state is sacred.** Active scene, live stream, recording — always visible,
   always color-coded, never ambiguous.
3. **Dark-first, broadcast-native.** Studio environments are dark; light theme is a
   fully-designed first-class citizen, not an afterthought.
4. **Motion with meaning.** State changes animate (scene flip, meter fall-off, tally
   pulse); decoration doesn't.
5. **Material 3 Expressive** as the vocabulary: bold type, expressive shapes,
   springy motion physics, dynamic color where the user wants it.

## 2. Theming architecture (core feature)

`MaterialExpressiveTheme` wrapper with a `SceneDeckThemeFamily` enum. Users pick:
**System / Light / Dark** + one theme family + optional **dynamic color** (Material You).

Built-in theme families (port of the desktop's 12 themes + new mobile exclusives):

| Family | Character | Source |
|---|---|---|
| `SceneDeck` (default) | signature deep-space indigo + electric violet accent | new brand |
| `OBS` | faithful to OBS Studio's dark UI (grey-blue panels, teal accent) | desktop port |
| `Obsidian` | near-black, high-contrast white/violet | desktop port |
| `Nord` | arctic blue-grey palette | desktop port |
| `Dracula` | classic dracula purple/pink | desktop port |
| `Solarized Dark` | solarized base tones | desktop port |
| `Stream Red` | broadcast red tally accents | desktop port |
| `Studio Purple` | rich purple production vibe | desktop port |
| `Sunset Coral` | warm coral/amber, light-friendly | new |
| `Mint Control` | fresh green-teal, light-friendly | new |
| `High Contrast` | accessibility-max | desktop port |
| `Material You` | dynamic color from wallpaper | Android 12+ |

Implementation: each family = `ColorScheme` (light+dark) + optional shape/typography
tweaks, defined in `:core:designsystem/theme/families/`. Preview every component
against every family via multi-preview annotations; Roborazzi goldens per family.

## 3. Color semantics (fixed across themes)

These hues carry product meaning and adapt per theme but keep their roles:

- **Program / Live** — red family (tally). Used ONLY for on-air state.
- **Preview** — green family (studio mode).
- **Ready / idle** — neutral surface tones.
- **Stream active** — theme accent; **Recording** — red pulse (respect reduced motion).
- **Meter zones** — green < −20 dB, yellow −20…−9 dB, red > −9 dB (OBS semantics).
- **Warning/Critical thresholds** (stats) — amber at warn, red at crit.

## 4. Typography

- Typeface: **Inter** (UI text) + **JetBrains Mono** (dB readouts, stats counters,
  timecodes — tabular figures).
- M3 Expressive type scale; **emphasized display** for the current scene name;
  `LabelLarge`-emphasized for card labels. Numerals always tabular in telemetry.

## 5. Shape & elevation

- Expressive shape scale: scene cards `large` (28dp-ish rounded), mixer strips
  `medium`, buttons `full` (stadium) for transport controls.
- MaterialShapes (shape-morphing) for the Active-scene card: morphs subtly on
  activation (with reduced-motion fallback = crossfade).
- Tonal elevation over shadows (dark-first design); glow ring for Active/On-air state.

## 6. Iconography

- Primary set: **Lucide** (via compose-icons) — thin, modern, consistent.
  Examples: `radio-tower` stream, `circle-dot`/`disc` record, `clapperboard` scenes,
  `sliders-vertical` mixer, `activity` stats, `layers` inventory, `stethoscope` doctor,
  `waypoints`/`git-branch` graph.
- Secondary: **Material Symbols Rounded** where Lucide lacks a glyph.
- **Scene icon catalogue** (user-assignable to scene cards, parity with desktop's ~30
  curated glyphs): camera, mic, gamepad, chat, music, desktop/monitor, guests/users,
  presentation, image, video, globe, heart, star, bolt, coffee, … (see
  `:core:designsystem/icons/SceneIcons.kt` catalog enum).
- App icon: adaptive icon + monochrome themed icon; logo = stylized stacked
  "deck" tiles with a tally-red live tile (SVG master in `assets/`).

## 7. Signature components (the "wow" layer)

| Component | Craft details |
|---|---|
| **SceneCard** | accent-tinted surface, icon + label, hotkey digit badge, Active = morph + glow ring + haptic; press = scale 0.96 spring + ripple |
| **VolumeMeter** | custom Canvas: OBS-accurate zones, per-channel bars, peak hold line (20 s), loudness notch (~300 ms), pre-fader base square, butter-smooth 60 fps decay animation |
| **MixerStrip** | vertical fader with dB taper curve, mute with animated strike-through, scope badge chip, lock toggle |
| **TransportBar** | stream/record stadium buttons; recording = pulsing tally dot; elapsed-time counter in mono |
| **StatusStrip** | persistent bottom: connection dot, FPS, dropped frames (highlights only when dropping), CPU, bitrate |
| **StatGauge** | arc gauge with amber/red threshold arcs (FPS, render time, congestion, dropped %) |
| **TrendChart** | custom Canvas line/area chart, 2-min window, theme-aware grid |
| **EmptyState** | disconnected placeholders with Lucide illustration + reconnect CTA |

## 8. Motion

- `MotionScheme.expressive()` default; springs for cards/faders; `MotionScheme.standard()`
  fallback when user picks reduced motion or system animator scale = 0.
- Screen transitions: shared-element-ish container transforms (Nav3 predictive back).
- Recording tally: 1 Hz pulse; disabled under reduced motion.
- All motion levels configurable: Full / Reduced / Off (settings).

## 9. Assets to produce

- `assets/brand/`: logo SVG + exports, adaptive icon layers, monochrome icon,
  splash logo, Play Store feature graphic + screenshots (M8).
- `assets/illustrations/`: 3–4 empty-state illustrations (disconnected, no scenes,
  no audio inputs, first-run).
- Onboarding imagery: connection wizard art, QR pairing illustration.

## 10. Accessibility

- 48dp+ targets, TalkBack labels on cards ("Scene Camera 1, ready, double-tap to
  switch"), meters expose text dB values, sufficient contrast in every theme family
  (verify with Roborazzi contrast goldens), reduced-motion respected everywhere.
