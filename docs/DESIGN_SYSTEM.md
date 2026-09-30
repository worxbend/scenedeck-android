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
| `SceneDeck` (default) | charcoal studio surfaces + azure and cyan accents | new brand |
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

## 11. Mobile navigation refresh (2026-09-30)

Daily operation has three tabs: Scenes, Mixer and Stats. More groups configuration
and diagnostic tools. Page headers use a shared tonal identity panel with titleLarge and compact supporting
context. Scenes prioritize the grid and separate audio into Mixer; advanced output
controls use menus instead of a permanent second transport row. Stats use a 2×2
phone gauge grid and a threshold-based health summary. The default palette defines
all tonal surface containers explicitly for coherent panels in light and dark modes.
Connection readiness uses a green OBS indicator; it must never imply the stream
is on air. The status strip adapts secondary telemetry to available width.

The polish pass adds collapsible scene search, aligned mixer fader baselines and
channel options for lock/advanced settings. Stats gauges sit in tonal panels with
a capped diameter; progress arcs leave numeric readouts clear. More includes
explained destinations and an explicit Session behavior entry. Offline states
remain scrollable on short screens and permit opening connection settings while reconnecting.

## 12. Reference-inspired studio refresh

The default SceneDeck family uses charcoal panels, azure controls and cyan highlights.
Scene tiles form a uniform card grid with a soft diagonal tonal wash, one rounded icon
well and stacked name/state labels. Avoid duplicate oversized background glyphs.
Placeholder cards use theme-aware text in light and dark; thumbnails retain a
scrim. Active borders preserve program red and preview green without expanding
into neighboring tiles. The references guide texture, restraint and hierarchy,
while navigation and actions remain specific to OBS.

Scene cards use compact proportions, a consistent two-line title area, scene-number
labels and state/action captions. Drag handles are shown only in reorder mode,
which suppresses scene-switch taps. Pending shimmer and press scaling respect
Reduced/Off motion settings.

## 13. Studio stylebook

The living Compose reference is `StudioStylebook` in `:core:designsystem/gallery`.
Rendered examples: `core/designsystem/studio_stylebook_dark.png`,
`studio_stylebook_light.png` and `studio_stylebook_obs.png`.

| Element | Shared treatment |
|---|---|
| Page identity | `StudioPageHeader`: 24dp rounded tonal panel, quiet accent wash, 40dp icon well, bold titleLarge and one-line context; actions retain 48dp targets |
| Section label | `StudioSectionHeader`: accent titleSmall and faint divider, 8dp vertical spacing |
| Scene tile | 24dp radius, diagonal tinted surface, 36dp colored icon well, subtle perimeter outline and numbered position; **no left rail**; program/preview retain red/green border and pill |
| Icon well | `StudioIconWell`: 40dp rounded square, 12% tint fill and quiet border; primary/secondary/tertiary decorative tones plus semantic success/warning |
| Buttons | Primary filled for the main action; tonal for supporting actions; outlined for optional actions; stadium shape and 48dp minimum touch targets |
| Button groups | `studioSegmentedButtonColors`: filled accent selection over neutral tonal segments, no competing outlines |
| Filter chips | `studioFilterChipColors`: neutral filled idle, primaryContainer selected; no outline; keep selected semantics |
| Checkbox/switch | Material 3 theme colors and rounded shapes; parent labels expose toggle semantics without duplicate actions |
| Inputs | `studioTextFieldColors`: tonal container, quiet outline, accent focus; rounded shape, leading icon, persistent label, helper/error text |
| Settings | Related controls in rounded tonal panels; varied theme-aware icon wells distinguish appearance, feedback and safety |
| Connection editor | Fully expanded, scrollable editor sheet; identity/server/security sections; host and port helper text; secure password visibility control and keyboard Next/Done actions |

Use the shared components instead of styling a new page independently. Decorative
colors never imply streaming or recording. Card thumbnails keep their contrast
scrim. All families support light/dark modes; High Contrast keeps semantic state
outlines. Goldens cover the stylebook and primary screens.

Main screen status-bar padding sits outside scroll containers, including Mixer, so page headers and scrolled controls stay below system icons and display cutouts.

Stream transport uses a primary blue idle Stream action and a semantic red Stop
button while streaming. The breathing halo runs only with Full motion; Reduced/Off
use the same static red control. Mono timers remain visible in compact layouts:
green for streaming, amber for recording. Light themes
use a darker amber for readable timer text. Shared implementation: BroadcastControls.

The Scenes header owns output status and timers in its trailing area: neutral
Standby when idle, LIVE with green stream time and REC with amber recording time.
Simultaneous outputs show both rows; status dots pulse only with Full motion.
The direct control row contains Stream, Record and VCam. Keep system insets and
accessible status descriptions. Replay Buffer is excluded from Android.

The persistent bottom bar separates connection/output state from performance
metrics into two compact rows while streaming; standby and recording-only states
hide performance metrics. It reuses broadcast tally colors and timer pills
across all main tabs, with explicit OFF AIR, retrying, paused and unknown states.
