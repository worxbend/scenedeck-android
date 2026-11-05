# Tablet operator layout — W10

Status: **implemented and verified — W10/W11 complete**. Final commands, outcomes and
limitations are recorded in progress.md.
Show rundown, cue lists and timed advancement remain excluded.

## Layout decision

Use available content bounds after the shell's navigation/status/safe insets, not device
identity or orientation. At least 840dp wide and 480dp high gets a two-pane operator view.
Scenes shows a scrollable control pane with larger program/Studio preview context beside
its existing named-deck/filter/grid. Reuse cached thumbnails and their freshness labels;
no extra capture jobs, live video promise or OBS requests. Aliases remain display-only;
raw identities stay visible. Preview is not a command: only existing deck/Take controls act.

Compact wide windows keep the two regions independently reachable without adding the
large context panels. Narrow windows stack the same controls/content, limiting the control
region so the deck/channels remain reachable. Scroll state and feature/confirmation owners
must remain above size-dependent presentation; resizing cannot reconnect or dispatch actions.
Use density tokens, shared semantic program/preview colors and 48dp controls.

Mixer remains a dedicated tab. Wide operator windows place mode/search/group/scene controls
beside a wrapping channel grid; small windows keep horizontally scrollable strips. Shared
meter holders, lock/mute semantics, media tools and originating workspace callbacks remain.
Menus/sheets retain existing bounded widths and the shell retains persistent output status.

## Verification

Render Scenes and Mixer in SceneDeck/OBS light/dark, tablet portrait/landscape, narrow/compact
height and large text. Exercise size changes with state retained, keyboard/focus access,
scene selection/Studio callbacks, channel mute/options and aliases/freshness. Use demo for
emulator resizing/output-continuity checks; report it separately from real-OBS evidence.
Run the full quality gate, explicit build/tests and final normal/F-Droid integration checks.

## Implementation/evidence

StudioWorkspace measures each child once in stable slots and uses relative placement for
layout direction; the measure policy follows the [Compose custom layout contract](https://developer.android.com/develop/ui/compose/layouts/custom).
The same control/content owners stay composed during width/height changes. Scene context is
in DeckContext/SceneMonitor; channel row/grid and shared actions live in MixerChannels.

Sixteen OperatorDeck/OperatorMixer tests cover SceneDeck/OBS light/dark, tablet portrait,
compact height, large text, search/focus retained through width changes, keyboard Enter on
literal scene targets, and unchanged mute/media callbacks. Existing phone renders pass.
Large-font review widened channel cells and moved output status below the scene title;
mode chips wrap. The keyboard fixture explicitly selects keyboard input mode before focus.

The API36 owned demo emulator retained stream time and selected program across phone →
2400×1600 at 240dpi → 1600×800 → restored phone bounds. Studio preview/Transition, Mixer
mute and options worked; banner/status/rail stayed visible. This used the W10 APK before
its final large-font cell-width adjustment; final JVM renders cover that adjustment.
Temporary screenshots/text: `/tmp/scenedeck-tablet-scenes.png`,
`/tmp/scenedeck-tablet-compact.png`, `/tmp/scenedeck-tablet-take.txt`,
`/tmp/scenedeck-tablet-back-phone.txt`. Emulator dimensions were reset, demo exited and the
owned emulator stopped cleanly. No physical tablet/real OBS/performance claim is made.
