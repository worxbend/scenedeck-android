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
- **Stream transport** — blue idle Stream / red active Stop. **LIVE status** — program
  red; **stream time** — green; **REC status and recording time** — amber. Pulse only with
  Full motion.
- **Meter zones** — green < −20 dB, yellow −20…−9 dB, red > −9 dB (OBS semantics).
- **Warning/Critical thresholds** (stats) — amber at warn, red at crit. FPS and render
  thresholds scale with the OBS canvas frame rate.
- **Stats output chips** — STREAM LIVE uses the program/live red; REC uses the amber
  recording tone (`broadcastWarningTextColor`).

## 4. Typography

- Typeface: **Inter** (UI text) + **JetBrains Mono** (dB readouts, stats counters,
  timecodes — tabular figures). Fonts are bundled as unmodified SIL OFL 1.1 TTFs;
  typography works offline and without Google Play services.
- M3 Expressive type scale; **emphasized display** for the current scene name;
  `LabelLarge`-emphasized for card labels. Numerals always tabular in telemetry.

## 5. Shape & elevation

- Expressive shape scale: scene cards `large` (28dp-ish rounded), mixer strips
  `medium`, buttons `full` (stadium) for transport controls.
- Scene cards retain uniform rounded geometry on activation, with semantic program/preview
  outlines and restrained press scaling (honor Reduced/Off motion).
- Tonal elevation over shadows (dark-first design); glow ring for Active/On-air state.
- Density: `SceneDeckTheme(density = …)` provides `SceneDeckTheme.spacing`. Comfortable =
  20 dp gutter, 16 dp section gap, 8 dp item gap, 12 dp row padding; Compact = 12/10/4/8 dp.
  Use the spacing tokens for new page layouts instead of fixed gutters; never shrink touch
  targets below 48 dp.
- Scene graph role fills: Primary/Secondary/Module = primary/secondary/tertiary
  containers, Raw/Archive = neutral surfaces, Debug = warning amber. Program red is not a
  role color.

## 6. Iconography

- Primary set: hand-ported **Lucide-style ImageVector catalogue** in `:core:designsystem`
  — thin, modern, consistent. The compose-icons Lucide pack is unavailable on Maven
  Central for the pinned stack; no external icon-pack dependency is required.
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
| **SceneCard** | restrained accent wash, icon + label, numbered position (stable under search), program/preview semantic outline and pill, haptic; press = scale 0.96 spring + ripple; no left rail; thumbnails get top and bottom scrims and a dark icon well |
| **VolumeMeter** | custom Canvas: OBS-accurate zones, per-channel bars, peak hold line (20 s), loudness notch (~300 ms), pre-fader base square, butter-smooth 60 fps decay animation |
| **MixerStrip** | vertical fader with dB taper curve, mute with animated strike-through, scope badge chip, lock toggle |
| **TransportBar** | Stream/Record/VCam stadium actions; active Stream breathing halo only with Full motion; green stream and amber record mono timers; active VCam = tertiary "Stop" (labels shrink to fit narrow phones), pending = spinner + Starting…/Stopping… |
| **StatusStrip** | persistent bottom: connection and OFF AIR/LIVE/REC state with timers; performance metrics on a second row only while streaming (one 48dp row in compact-height windows) |
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

- 48dp+ targets, TalkBack labels on cards follow the tap action ("Scene Camera 1, ready,
  double-tap to switch"; studio "double-tap to load in preview"; preview "use Transition or
  Cut"; reorder mode "drag the grip to move"), locked faders announce "locked", meters expose text dB values, sufficient contrast in every theme family
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
explained destinations and an explicit Keep-alive & background entry. Offline states
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
which suppresses scene-switch taps and captions cards "Drag grip to move". The studio T-bar
is a full-width 56dp track under the transition row. Pending shimmer and press scaling respect
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
| Connection editor | Fully expanded, scrollable editor sheet; identity/server/security sections; host and port helper text; secure password visibility control and keyboard Next/Done actions; the onboarding connect step uses the same field treatment and shared `StudioPasswordField` |

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
accessible status descriptions. Phone landscape uses a navigation rail and keeps content
and the rail clear of side system bars and cutouts; snackbars use `StudioSnackbarHost`,
bottom-aligned above the status strip and keyboard. System-bar icons follow the in-app
theme while bars stay transparent with the system's 3-button contrast scrim. Replay Buffer
is excluded from Android.

The persistent bottom bar separates connection/output state from performance
metrics into two compact rows while streaming; standby and recording-only states
hide performance metrics. It reuses broadcast tally colors and timer pills
across all main tabs, with explicit OFF AIR, retrying, paused and unknown states.

### Workspace availability

Use the shared `WorkspaceStatus` inline state while collection identity or its data
is unavailable. Loading has progress; failure has explicit retry and never resembles
an empty scene list. Scenes retains the output row independently. Key scene sheets
and Mixer extras to the displayed workspace so they close when it changes. Inventory's
previous-curation recovery uses a normal confirmation dialog naming the destination
collection and merge counts, with Copy / Cancel actions.

### Named decks

Scenes keeps All scenes and Primary-only Deck chips with a compact My decks selector
and a 48dp search icon. The selected named deck appears in that selector. Manage decks
opens an expanded sheet using StudioPageHeader/StudioSectionHeader, connection and
collection identity, explicit Save/Back and contextual member menus. Membership uses
checkbox rows; missing references say Unavailable in OBS and remain removable. Moves
are disabled during search; the main grid also disables dragging with missing members.
Import shows its destination and replacement counts before confirmation. Failed saves
retain the editor draft; context changes dismiss the obsolete editor.

### Scene labels and preview age

Use shared `SceneAliasField` for local labels, with the literal OBS identity, blank-to-reset
hint and 80-character counter. Quick edit opens expanded; Inventory exposes a focused
label dialog from its 48dp name target. Failed saves retain the input and show an inline
message. Label-only saves preserve the scene's existing role.

Aliased cards have a primary local label and a secondary `OBS · name` line; TalkBack
reads both. Inventory and deck membership rows retain the same identity distinction.
Preview captions appear below the normal card state: age, Stale preview, Preview pending
or Preview unavailable. Leave tally colors for program/preview/output state. The extra
caption adds vertical space and scales with font size; hidden previews hide captions.
Disconnected Scenes uses the shared placeholder with an explicit preview explanation.

### Preflight

Preflight lives under More, with StudioPageHeader naming the connection and collection.
Use explicit Pass / Fail / Unknown text on each result; color supplements the wording.
Keep the timestamp and snapshot reminder visible in the report. Run checks, Setup and
Cancel remain distinct controls; running state clears old findings and shows progress.
The signal checkbox is explicitly armed for one run, with a short speak-now instruction.
Manual confirmations are ordinary accessible checkbox rows with persisted-state copy.

Setup opens expanded with fixed Save/Cancel controls and a scrolling, IME-aware form.
Group expected collection, microphone, disk threshold, required scenes and manual items.
Show raw OBS identity beside aliases and retain unavailable scene references for removal.
Validation and save failure appear inline while retaining the draft. Start-confirmation
links explain that leaving for Preflight cancels that pending start; stop dialogs keep
their focused confirmation actions.


### Live alerts

Use the shared bell icon, StudioPageHeader and StudioSectionHeader under More. The shell
places a compact tonal warning/monitoring bar above persistent output status, without
owning extra system insets. Keep Details a 48dp target and announce status changes politely;
there is no flashing/pulsing warning or motion dependency. Use the shared warning tone
for the icon, with readable theme-native text. The scrollable detail page keeps explicit
unknown/uncertain wording, current readings, source navigation and per-occurrence dismissal.

Microphone monitoring names the literal OBS input and requires a separate speech-expected
confirmation. A changed target dismisses old consent. Stop monitoring stays direct; failed
arming shows an inline explanation. The optional foreground haptics switch is off by
default and explains its session lifetime and global Settings dependency. Keep notification
limitations and cooldown behavior available in the details page, without implying all-clear
from the absence of a warning. Capture page/bar in SceneDeck and OBS light/dark themes.


### Recording tools

Keep advanced recording controls under scene options, leaving the direct Stream / Record /
VCam row compact. The fully expanded sheet uses StudioPageHeader, the shared recording
icon and StudioSectionHeader. It scrolls in compact windows, keeps 48dp controls, explains
unavailable capabilities, and distinguishes active/paused/stopped/unknown state in text.
Pause becomes Resume only from confirmed OBS state. Pending disables all tool writes;
inline results announce politely and retain unknown/rejected wording. Chapter entry is
optional, bounded, and cleared when the target recording context changes. Capture the
sheet in SceneDeck/OBS light/dark and a compact-height uncertain-outcome state.

### Bookmarks

More → Bookmarks uses the shared bookmark glyph, StudioPageHeader and tonal cards. Keep
Mark now near the top and explain that saved OBS times can be up to three seconds old.
Show connection/collection and device time beside each local label. Output times use
JetBrains Mono with explicit paused/reconnecting/inactive/unknown text. Never use a zero
timecode to represent unknown state. The page scrolls below status-bar insets, including
compact-height windows; label/delete actions remain 48dp targets. Label-save failures
retain the draft and deletion names the concrete mark. Unsaved captures offer Retry save
and Discard, with a polite inline message. Display the 500-mark retention limit and that
local bookmarks do not create OBS chapters. Render SceneDeck/OBS light/dark and compact
unknown-state variants.
