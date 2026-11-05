# Feature Specification — SceneDeck Android

Derived from a full read of the desktop SceneDeck codebase (`../scenedeck`, Rust/GTK4,
v0.4.0). Sections 1–7 define **parity scope** (what the desktop app does today);
section 8 defines **mobile-first extensions** (unclaimed territory the desktop app
explicitly leaves open).

## 0. Product identity

> **Stream from one machine, control it from another.**

SceneDeck Android is a remote **control surface** for OBS Studio — not a streaming tool.
It connects to the OBS WebSocket server (v5, built into OBS ≥ 28) and focuses on fast,
confident live operation. Core design principle inherited from the desktop app:
**the app never modifies the OBS scene collection** — every curation decision (roles,
colors, icons, ordering) lives in a local registry on the device.

---

## 1. Connection & OBS state

- Connect/disconnect/reconnect to OBS WebSocket: **host, port** (default 4455),
  optional **password** (stored in Android Keystore-backed encrypted storage).
- **Multiple named connection profiles** (mobile improvement over desktop's single
  target): e.g. "Home PC", "Studio rig", with quick-switch and a last-used default.
- Auto-reconnect with backoff; connection state always visible (top bar + status strip).
  The reconnecting screen offers **Retry now**; returning to the foreground or regaining
  network also retries immediately.
- Show OBS version + WebSocket version after connect (`GetVersion`).
- Switch OBS **profiles** and **scene collections** from dropdowns (visible only when
  connected; observe profile/collection change events).
- QR-code / deep-link based pairing (mobile convenience): OBS host shows
  `obsws://host:port` QR, phone scans to add a profile.

## 2. Scenes page (the deck) — hero screen

- Grid of **scene cards**: all OBS scenes by default; an optional **Deck** filter shows
  scenes assigned local role `Primary`. Mixer controls live on their dedicated tab.
- Search matches the OBS name and local label, preserves the selected deck, and disables reorder.
- **My decks** adds local named decks per connection profile + OBS collection. Create,
  rename, delete and select decks; selection is restored when the workspace resolves.
  Membership/order are independent of roles and canonical Inventory order. Explicitly
  selected scenes can have any role. Deleting the selected deck returns to All scenes.
- Manage decks opens a focused sheet. Select/remove members, then use their menus to
  move them; missing OBS scenes remain labeled and removable. The main grid can drag
  named members when search is clear and all members are available. Search retains
  original positions, including gaps from missing scenes. Old editors reject concurrent
  edits and OBS renames instead of restoring stale references.
- Deck backups use version-1 `scenedeck-decks` JSON, separate from registry YAML.
  Import previews the destination and additions/replacements, then replaces same-name
  deck definitions (case-insensitive) including membership/order. Other decks and the
  current selection remain. Export excludes local IDs and connection details. Files
  are bounded to 4 MiB; names to 80 characters, decks to 100, and members to 2,000 per
  deck. Names are trimmed; scene references preserve literal names and first duplicates.
  Picker/preview/confirmation retain the originating workspace. Unknown versions,
  ambiguous duplicate names, stale previews and obsolete sessions are rejected.
- Current **program scene** highlighted (Active state); others show Ready state.
- Tap card → `SetCurrentProgramScene`. Haptic feedback + animated state flip.
- Uniform card tiles use an icon well, numbered positions and separate scene/state
  labels, with no accent rail; grips appear only in explicit reorder mode.
- Per-scene optional **accent color** and **icon** (local metadata).
- Optional **local label**, edited in quick edit or by tapping the Inventory scene name.
  Labels are scoped to the workspace, trimmed, limited to 80 characters, and need not
  be unique; blank restores the OBS name. Scene cards, Inventory and deck membership
  editors show the raw OBS identity alongside a label; TalkBack includes both.
  Commands, membership and ordering still use the literal OBS name. Saving only a
  label preserves the existing role, including unassigned and Archive. Failed saves
  retain the draft; renamed-scene or obsolete-workspace editors reject the save.
  Registry YAML/JSON version 1 includes optional `localAlias`. Old or null fields keep
  destination labels when merging; nonblank fields replace them. Explicit clearing
  uses the editor. Rename, Undo and previous-curation recovery preserve labels.
- All/Primary scene card order = the registry order, shared with Inventory and persisted locally.
  Scenes with a saved position come first; scenes never placed follow OBS's own list
  order (top scene has the highest `sceneIndex`), then name. Any drag (Scenes Reorder
  mode or Inventory) saves the whole arrangement as dense positions. Enable Reorder scenes
  in scene options to expose grips; scene taps do not switch OBS during reordering.
- Long-press card → quick actions (rename local label, change icon/color, remove from deck).
- **Studio mode (extension)**: when enabled, deck switches the *preview* scene;
  a prominent TRANSITION button commits preview → program.

## 3. Audio — mixer strips + dedicated Mixer page

- Audio discovery: global "special" inputs first, then audio-capable inputs of the
  active scene, recursing into enabled nested scenes/groups; deduped; skip sources
  without volume/mute state. Optional allow-list in settings.
- **Mixer-strip cards**: scope badge (global / scene / nested / group), name, live dB
  readout, vertical fader (multiplier ⇄ dB conversion, debounced writes), live
  per-channel **volume meter**, mute toggle, local **slider lock** (UI-only).
- Volume meter semantics match OBS: −60…0 dB scale, green/yellow/red zones at −20/−9 dB,
  one bar per channel, peak fill with fall-off, 20 s peak-hold line, ~300 ms loudness
  notch, pre-fader base square visible even when muted.
- Mixer page modes: **Active** (follows program scene), **Selected** (frozen scene),
  **Pinned**; grouping by scope / scene path / none; session search field.
  Selected/pinned channel snapshots reset on connection/collection replacement.
  The remembered selected scene is restored only for its saved profile + collection.
  Buffered meters, media reads and delayed controls cannot affect a replacement
  workspace. Locks, audio allow-list, mode and grouping remain app-wide preferences.
- **Extensions**: audio balance/pan, sync offset, monitor type (desktop explicitly
  doesn't do these — mobile differentiator, phase M7+).

## 4. Stream & record control

- Persistent Start/Stop **Stream** and Start/Stop **Recording** controls (bottom bar or
  floating toolbar, reachable from every page).
- **Output Safety**: four independent confirmation toggles (confirm start stream /
  stop stream / start recording / stop recording; default: confirm stops only).
- Status strip shows output state + elapsed time; transitional states (Starting /
  Stopping / Reconnecting); recording indicator pulses like a tally light (respects
  reduced-motion settings).
- Compact Stream / Record / VCam row on Scenes. Studio mode and thumbnail preferences
  live in the scene options menu. Replay Buffer is excluded from the Android app.

### Recording tools

Scenes → scene options → **Recording tools** opens pause/resume and advanced recording
controls. Availability comes from the connected OBS server's advertised requests;
unsupported controls are disabled with an explanation. Active recording and fresh
status are required; split and chapter also require recording to be unpaused.

Pause/resume send explicit target-value commands. Buttons remain pending until OBS
acknowledges the request and a subsequent shared status read confirms the target, or
confirmation times out. File split and chapter messages distinguish an accepted
request from an uncertain outcome. OBS can reject these operations for the current
recording format/settings. Chapter names are optional and limited to 120 characters.
They are sent to OBS; no recording path or server error detail is displayed.

Only one tool action runs at a time. Failed, timed-out or canceled writes are never
retried automatically; unknown outcomes ask the operator to check OBS. Connection,
collection and observed recording start/stop changes invalidate old actions/results.
Closing the sheet does not undo an already-sent action. Streaming continues normally
while recording is paused. Existing start/stop safety confirmations are unchanged.

## 5. Stats page (telemetry)

- Poll `GetStats` + `GetStreamStatus` once per second (obs-websocket has no push stats).
- Gauges with amber/red thresholds: FPS, avg frame render time, dropped-frame %
  (warn 1%, crit 5%), network congestion (30%/60%).
- FPS/render thresholds derive from the OBS canvas rate (`GetVideoSettings`, read on
  connect, profile switch and every 10 s): FPS max = target, warn 90 %, crit 75 %;
  render time warn 50 %, crit 90 % of the `1000 / target` ms frame budget. 60 fps is
  only a fallback while the canvas rate is unknown.
- Output chips: STREAM LIVE uses the red program/live tally; REC uses amber recording.
- Compact gauge panels follow bitrate, CPU, memory and output-state summaries.
- Trend charts (FPS, render time), per-sample skipped/missed frame bars, counter cards
  (CPU, memory, bitrate, frame totals); rolling bitrate from consecutive byte counters.
- 2-minute ring-buffer history kept across pages for the current connection/collection
  generation. Disconnect and collection replacement clear prior readings and history;
  obsolete responses/events cannot populate the replacement session. Polling remains
  available independently of local workspace discovery.
- FPS + dropped frames appear in the global status strip on every main page while
  streaming; standby and recording-only sessions retain output status/timers.

## 6. Inventory page (local scene registry)

- Curation is local to the connection profile and OBS scene collection. Same-named
  scenes elsewhere have independent roles, colors, icons and order. Until identity
  and reads finish, show a workspace loading/error state with retry; a failed read
  is never presented as an empty collection. Outputs remain available independently.
- Previous global curation is retained. **Copy previous curation** previews a merge
  into the current collection; confirming never deletes the source. No automatic
  copying to each connected host occurs. Deleting a connection retains its curation
  and disconnects that target if it is still active.

- Lists OBS scenes; assign local **roles**: `Primary` (deck), `Secondary`, `Module`
  (nested reusable), `Raw`, `Debug`, `Archive`.
- Drag-to-reorder (persisted, same order as the Scenes grid; stale entries listed
  last), accent color picker, icon picker, stale-entry detection (registry entry whose
  scene vanished from OBS) with one-tap cleanup; the removal snackbar offers **Undo**,
  which restores role, accent, icon and position (newer curation for the name wins).
  Undo and import confirmation cannot follow a replacement connection/collection;
  reopen the action in the intended workspace after a reconnect.
- Rows keep the scene name readable (up to two lines); stale rows put the stale badge and
  Remove on a second line. Bulk/Export/Import actions wrap instead of clipping.
- Roles are only assigned explicitly: reordering or changing a color/icon keeps a scene
  **Unassigned** (shown as Primary on the deck, counted by Unassigned → Secondary/Doctor).
  Inventory labels such scenes "Unassigned" with an outlined chip, never as a filled
  Primary chip.
- Renames reported by OBS (`SceneNameChanged`) update the deck immediately (card name,
  program/preview tallies, Mixer's selected scene, Graph selection) and move a scene's
  curation and position to the new name in one local transaction (OBS is never
  mutated). If curation already exists for the new name, nothing is migrated: both
  entries are kept, the new name's curation applies and the old entry shows as stale.
  A stale entry applies to any future scene with that name until it is removed in Doctor
  or Inventory.
  A scene-list read that raced the rename cannot bring the old name back.
- **Export/import registry as YAML/JSON** (backup, share between devices). Export carries
  roles (`UNASSIGNED` for none) and positions; import applies the file's order first,
  then local placed scenes the file doesn't mention, so export → import restores the
  same order. Entries without positions keep file order. The destination is captured
  before the file picker opens, and appears in the import preview.

## 7. Graph & Doctor (health tooling)

- **Graph page**: scene dependency tree from nested scene sources; classify edges
  against role rules to spot surprising dependencies before going live. Duplicate scene
  items of the same nested scene form one edge (drawn lines, detail lists and the
  TalkBack count agree). Columns shrink to fit the phone width; wider graphs scroll inside
  the canvas with a "scroll sideways" hint. Role fills use theme accent containers, never
  the program/LIVE red (red marks only forbidden edges and cycles).
- **Doctor page**: diagnostics over OBS inventory + local registry + graph — unassigned
  roles, stale entries, circular references, hierarchy inversions; severity-graded report.
  Inversions are raised only between explicitly assigned roles (never the implicit Primary
  default), except that any production scene nesting a scene explicitly marked Debug/Archive
  is always an error; audio probing visits explicit Primary scenes still in OBS and skips
  video-only inputs. Only scenes removed mid-scan are skipped: timeouts, disconnects and other
  OBS rejections (including during the graph walk) fail the run, so a partial scan never shows
  All clear. The page shows running, all-clear and failed states; fixes wait while a re-run is
  in progress.

## 8. Mobile-first extensions (beyond desktop parity)

| Feature | Notes | Phase |
|---|---|---|
| Multiple connection profiles | named, QR pairing, last-used default | M2 |
| Studio mode (preview/program) + transition control | **SHIPPED M6**: one-tap Studio toggle; deck drives preview (green), TRANSITION + CUT commit to program (red); OBS-side changes from other clients tracked live | M6 ✅ |
| Scene/source **screenshot preview** (`GetSourceScreenshot`) | **SHIPPED M6**: throttled JPEG thumbnails on deck cards (360 px q50; program/preview 2.5 s, others 10 s; only while Live visible + Ready; "Previews" toggle, default ON) | M6 ✅ |
| Transition picker + duration + T-bar | **SHIPPED M6/M7**: bottom-sheet picker + debounced duration slider (M6); full-width T-bar: drag the thumb right (position tracks the finger), release past 80% of the travel to fire, spring-back honors MotionLevel, TalkBack custom action fires it (M7) | M6 ✅ M7 ✅ |
| Virtual camera control | **SHIPPED M7**: direct VCam transport action; active state reads "Stop" on a tertiary fill (labels shrink rather than ellipsize on narrow phones), Starting…/Stopping… with a spinner until OBS confirms (taps ignored meanwhile) | M7 ✅ |
| Scene item visibility toggles | **SHIPPED M7**: "Sources" section in the deck card quick-edit sheet, live via `SceneItemEnableStateChanged` | M7 ✅ |
| Audio balance / sync offset / monitor type | **SHIPPED M7**: per-strip extras sheet (balance 0..1, sync ±950 ms, monitor type), debounced writes | M7 ✅ |
| Widgets & Wear OS tile | **Widget SHIPPED M7**: Glance mini-deck (up to 6 PRIMARY scenes, tally highlight, connect-then-act cold start). Wear OS tile still open | M7 ✅ / M8 |
| Media controls | Play/pause/restart + state chip; channel options → Media tools adds elapsed/remaining/duration and bounded seeking | M7 + W5 |
| Quick-volume notification / foreground service | **SHIPPED M7**: keep-alive foreground service (dataSync, STICKY, timeout handling) + ongoing notification with connection, LIVE/REC output time (never a guessed idle) and program scene + Disconnect action; denied/blocked notification permission is explained with a notification-settings shortcut; `scenedeck://scene/{name}` deep link for automation with sanitized failure toasts (no profile, not connected, rejected switch) | M7 ✅ |

Preview captions show elapsed time since successful receipt/decoding, using a monotonic
clock. A frame becomes **Stale preview** at 20 seconds or when its latest capture fails;
failure preserves the prior frame and timestamp. Before a first result, the caption is
**Preview pending**; a failed first capture is **Preview unavailable**. Disconnection
clears cached images and explains that previews are disconnected. Valid black images
replace old frames. The visible age clock adds no screenshot requests, and disabling
previews hides both images and captions. Existing capture intervals and bounds remain.

Explicitly **out of scope** (same as desktop): input creation/editing, filters,
scene item transforms, scripting, Twitch/StreamElements integration (v1).

### Preflight

More → **Preflight** provides a deliberate, read-only readiness check. Setup is local
to the connection profile and OBS collection: expected collection, required scenes,
microphone input, minimum free recording-disk space, and editable manual checklist.
Defaults use the current collection and 2,048 MB; missing scene/microphone selections
are unknown. Choose detected Mixer inputs or enter their literal OBS name. Scene
selection can display local labels but stores the raw name. Missing references remain
visible and removable. Limits: 100 scenes, 30 manual checks, 120 characters per checklist
label, 4,096 per OBS reference, and 1–1,000,000 MB for disk space.

Run checks produces timestamped **Pass / Fail / Unknown** results independently.
Wrong collection, missing scene, muted microphone and low disk space fail; unreadable,
invalid or timed-out values are unknown. The mute check proves control availability
and mute state, not audible sound. Optionally arm **Check microphone signal this time**
and speak during the four-second window: a valid post-fader peak above -50 dB passes,
observed quiet samples fail the window, and missing/invalid samples remain unknown.
Arming clears after each run; it does not change meter preferences or add polling.

Manual confirmations persist in this workspace until cleared; editing an item's wording
clears its confirmation. Setup/checklist changes invalidate the prior report. Starting
or canceling a run, disconnecting or replacing the workspace clears old results; failed
saves retain the setup draft. Results are a snapshot and should be rerun after changes.
No scan starts an output or changes OBS. Start confirmations optionally link here,
dismissing their pending consent first; starting afterward requires a new deliberate
action. Existing output-confirmation settings remain unchanged, with no mandatory scan.

### Live alerts

More → **Live alerts** shows sustained warnings and current evidence. A compact bar above
persistent output status links to details while warnings or speech monitoring are active.
Warning cards show their reading and link to Stats (frame drops/render), Preflight
(recording-space checks/threshold), Mixer (microphone), or Connections. No alert changes OBS.

- Frame drops: streaming counter deltas, at least 1% for five seconds; recover below
  0.5% for five seconds. Counter resets, invalid counters and zero frame progress are
  unknown and establish a new baseline; lifetime dropped-frame ratios are not alerts.
- Render time: during streaming or recording, at least 90% of the canvas frame budget
  for five seconds; recover below 70% for five seconds. Canvas FPS comes from OBS,
  with 60 fps used only while unknown. Recording-only operation is supported.
- Recording space: below the workspace's Preflight minimum for five seconds while
  recording (including paused recording); recover at least 256 MB or 10% above that
  minimum, whichever is larger, for five seconds. Preflight defaults to 2,048 MB;
  failed/unresolved configuration is unknown, not an assumed lower threshold.
- Unexpected connection loss warns after two seconds and says output state is unknown.
  Deliberate disconnect/profile replacement and collection changes clear the old context.

Stats/output readings older than three seconds, invalid numbers and failed reads are
unknown. Sample gaps interrupt accumulation; repeated samples cannot advance timers.
A warning whose evidence becomes unavailable stays explicitly uncertain until fresh
readings prove recovery. Dismiss hides that occurrence for 60 seconds; a continuing
problem may warn again afterward. Old dismissal callbacks cannot hide newer occurrences.

Speech monitoring is off until the user chooses a microphone in Preflight and explicitly
confirms **Speech expected — arm**. Ten seconds of continuously sampled post-fader peaks
at or below -50 dB warns; detected signal recovers immediately, including brief signal
between UI updates. Missing/invalid meters or gaps over two seconds are unknown, not
silence. Stop monitoring is direct. Reconnect, workspace replacement, loss of workspace
identity, microphone selection changes and process recreation require arming again.
No audio is recorded and no additional OBS requests/subscriptions are introduced.

Alert haptics are off by default and last only for the app process. They also require
the Settings Haptics toggle and run only for new warnings while the activity is resumed;
background warnings are not replayed on return. There are no audible alerts or system
alert notifications. Existing keep-alive output notifications stay unchanged; background
monitoring and delivery depend on Android and the connection, not an alarm guarantee.

### Media progress and seeking

Mixer → channel options → **Media tools** opens an expanded, scrollable sheet for
media-kind inputs. It shows separately reported elapsed, remaining and total time,
with explicit playback state and unknown values. It uses the shared Mixer reader;
opening an off-program source adds it to that reader only while the sheet is open.
Normal polling remains subscribed and sequential, with a two-second delay per batch.
Failed reads clear old values; a local one-second clock expires readings older than
five seconds. The compact transport labels unavailable status **Unknown**.

Seeking requires an advertised cursor request, a fresh playing/paused source, positive
known duration and a valid cursor. Locked channels remain readable but cannot seek.
Dragging previews a target locally and sends one write on release; accessibility
adjustments also commit once. A gesture keeps its original workspace/source identity
and duration. Source replacement or a changed duration discards that gesture. Before
writing, the app re-reads status, checks the duration and clamps the target to 0…duration.
Seeking does not change play/pause state, and another seek on that input is blocked
until the pending command finishes. Compact play/restart controls wait during a seek.

A post-write read supplies the actual displayed position. Feedback distinguishes a
near-requested position, a different position, OBS rejection, changed media and unknown
outcome. Near-requested means within one second, allowing observed read latency while
playing; it is not a frame-accurate editing guarantee. Failed or timed-out writes are
never automatically retried. Workspace replacement, removal/rename, or an observed
playback boundary cancels queued seeks; a dispatched action may already have taken effect.

### Local timestamp bookmarks

More → **Bookmarks** saves moments on this device, independently of OBS chapters or
recording-format support. **Mark now** captures before editing a label: device calendar
time, original connection profile/collection, and separate stream/recording elapsed
times reported by OBS. It sends no OBS request or write. Reads up to three seconds old
are eligible; sample ages are retained. Missing, failed, invalid or stale values are
unknown, while inactive outputs have no invented zero position. Paused recording keeps
its reported position without extrapolation; reconnecting streaming is labeled explicitly.
These are output times, not guaranteed positions in individual files after file splitting.

Saved bookmarks remain readable, editable and deletable while disconnected or after the
connection profile is removed. Labels are local, optional (default “Bookmark”), trimmed
and limited to 120 characters. Failed/stale edits keep their draft; deletion confirms the
specific bookmark. Capturing requires a resolved current workspace, but a captured moment
can finish saving after disconnect without moving to a replacement workspace. Failed saves
retain that exact moment for Retry save or Discard; retry cannot duplicate a committed row.

The app retains the newest 500 bookmarks globally and automatically removes older ones.
Insertion order determines retention, independently of device-clock changes. Captures
have local observation IDs that change after reconnection, collection replacement or app
recreation; they do not imply continuous history through gaps. A mark captured while a
history observation is active links to that original observation, even if saving finishes
after disconnect. Marks captured before history starts or while disabled stay independent.

### Local session history and export

More → **History** shows offline observations and their timelines. An observation is a
period when SceneDeck was watching one connected workspace, not a stream, recording file
or show run. Initial readings describe what was already present; they do not claim that
an output started or a scene switched. Device timestamps and observation elapsed time
remain separate from reported OBS output counters.

Capture is enabled by default and uses shared state/events without an additional OBS
poller. It records output-state changes, program changes, warning categories/uncertainty,
and linked local bookmarks. Connection or collection changes end one observation and
begin a new one when identity is resolved. App interruption preserves the last known
point with an unknown end; disconnected output state is never assumed inactive. Known
capture gaps and retention omissions are displayed separately. This is a bounded local
observation log, not a complete audit of every OBS action.

Capture settings retain the newest **10, 30 (default), or 100 observations**, each with
up to **500 history entries** plus **50 preset run records**. Disabling capture stops new
observations and retains saved data. Clearing history or deleting an observation confirms the action, preserves saved
bookmarks, and allows capture to continue while enabled. Storage/feed failures pause
capture with a sanitized Retry action; retry starts a new observation and marks the gap.

Bookmark labels in history reflect current saved labels. Editing or deleting a bookmark
updates subsequent history reads and exports; labels are not duplicated into permanent
event payloads. Bookmark retention remains independently limited to the newest 500 marks.
Deleting history removes associations without deleting the marks themselves.

**Export observation** exports the selected timeline and its linked bookmarks. **Export
history & bookmarks** includes all retained observations plus independent saved bookmarks.
Both take a consistent snapshot before Android's document picker opens and save versioned
JSON to the chosen document. A canceled picker writes nothing. Later changes do not
retarget a prepared export; failed/expired exports remain explicit. Files include local
profile identity, OBS collection/scene names, bookmark labels, timestamps and typed status
data; they exclude connection endpoints, credentials, raw protocol/comments and output
file paths. Previously exported files are not changed by later edits/deletion in the app.

History can display and export preset run records, including partial and unknown results,
at their original invocation point. More → Presets invokes these runs deliberately.
Show rundown, cue lists and timed advancement remain excluded.

### Controlled multi-action presets

More → **Presets** provides a workspace-scoped list, create/edit/delete, ordered step
editing, review, execution and results. The allow-list supports explicit
program/preview scene targets, mute, volume, media playing/paused/stopped, and stream/
recording active targets. Definitions are bounded to 32 per workspace and 12 ordered
steps each, with revision checks and failed-data states. Preparation checks advertised
read/write support, target availability, input locks/allow-list and Output Safety without
changing OBS. A currently matching value is only a reading; execution must recheck it.

The singleton runner accepts one invocation at a time, requires the concrete output-step
acknowledgements and rejects preparations older than 60 seconds. It checks each target
again, including currently matching values, and waits up to four seconds for a read begun
after write acknowledgement to confirm the result. The whole run is bounded to 90 seconds.
Failures, cancellation, target rename/removal, relevant policy changes or workspace
replacement stop remaining steps; completed actions stay applied and writes are never
retried automatically. An interrupted write may have taken effect and remains unknown.
Manual controls and other OBS clients may interleave; presets are not OBS transactions.

When a History observation was captured at invocation, progress is saved before dispatch
and final results can update it after disconnect. Clearing/pruning History removes these
records permanently. At most 50 runs are captured per observation; further runs can proceed
with an explicit unrecorded result. A storage failure stops remaining steps. Process restart
marks unfinished runs interrupted, preserves completed steps and never resumes execution.
JSON History export version 2 includes these bounded typed records and their limits.

The editor preserves failed-save drafts and their original workspace/revision. Names are
unique within a workspace; steps can be added, edited, removed and moved up/down with
accessible controls. Scene choices show local aliases alongside literal OBS names. Input
choices include off-program sources from a read-only directory; source kind does not prove
control support. Exact names can also be entered manually. Known missing references are
shown explicitly and preparation blocks unavailable/unsupported targets.

Review shows every concrete target and a separate checkbox for each output action that
requires Output Safety consent. Consent is single-use, expires after one minute, and is
cleared when leaving/backgrounding the screen or when the target, policy, definition or
workspace changes. Opening Preflight or Settings also clears it. A run already started
continues across tabs within the current process; return to Presets to stop remaining
steps. Last results retain the original workspace, completed/unknown/unrun steps and
History storage status. Process interruption never resumes a run or confirmation.

Presets are individually invoked; they do not add cue scheduling, delays, loops,
scripting or collection editing.

### Keyboard and compatible foot pedals

More → **Shortcuts** configures up to 32 bindings for the current connection profile +
OBS collection. New bindings have no assigned key. Record a supported chord, choose an
explicit action and save; edit, disable or delete it later. Disabled bindings retain
reserved chords; conflicts keep the draft for correction. Scene aliases are display-only,
and searchable target choices return the exact OBS name. Overlong targets are invalid,
never silently shortened. Concurrent edits and workspace changes retain the original draft.

Supported actions are scene selection (preview in Studio mode, program otherwise), Take
preview, input mute/unmute, and explicit start/stop stream/recording. Supported keys are
A–Z, 0–9, F1–F12 and numpad digits with Ctrl/Alt/Shift/Meta; modifier sides are equivalent,
lock-state bits are ignored, and navigation/editing/system keys remain reserved. Help
explains that a compatible pedal must emit Android keyboard events; device-specific
configuration and universal hardware compatibility are not promised.

Shortcuts run only while foreground and focused on Scenes, Mixer or Stats. Child controls,
text editing (even with the software keyboard hidden), menus and dialogs keep priority.
Holding a key fires once; key-up releases it. There is no global/background interception.
Navigation or backgrounding cancels pending work and consent. The app's own output dialog
may take window focus without cancelling itself. Output Safety uses a concrete, single-use
60-second confirmation; another invocation is rejected without queuing. Fresh Studio,
target, policy and connection checks still apply. Failed or unknown results are visible;
success is quiet. Uncertain writes never retry automatically, and process recreation does
not resume an invocation. Verification evidence and hardware limitations are recorded in
`tasks/operator-workflow/shortcuts-design.md` and `progress.md`.

### Offline demo

**Try offline demo** is available from onboarding and More. Its entry explains that any
real OBS session continues separately. A private Demo task runs a local simulation with
five sample scenes, Studio mode/Take, Mixer controls and synthetic meters, media playback,
output timers and recording tools. It never broadcasts, records files or accesses a camera.
Previews are bounded local test slates; telemetry is synthetic, not device-performance data.

A persistent **DEMO · Local simulation** banner with **Exit demo** covers every page,
including compact windows and More routes. The connection strip says DEMO; output
confirmations and Recording tools repeat the simulation marker. Existing safety checks,
preflight, presets and foreground shortcut rules still apply to this isolated workspace.
Connections/setup/background tools explain the restriction and offer Exit. There are no
real connection tests, credentials, widgets or keep-alive service actions in demo.

Demo uses a separate in-memory database and settings. Its edits, bookmarks, history,
presets and shortcuts cannot overwrite live data. Rotation retains the current visit;
Exit immediately invalidates simulated commands and removes the Demo task. Re-entry
starts idle simulated outputs; temporary edits can remain while Android retains the demo
process. Process recreation starts a fresh sample workspace and never resumes actions or
consent. No live connection is started or stopped by entering or exiting demo. Explicit
exports still save to the user's chosen destination; History filenames start with `demo-`
and their observations carry the DEMO collection identity.

## 9. Settings

- Public privacy-policy link covering local OBS data and Google SDK diagnostics.

- Connection profiles; Output Safety toggles; theme (System/Light/Dark) + theme family
  (see DESIGN_SYSTEM.md); dynamic color (Material You) toggle.
- Appearance uses a focused theme picker sheet; settings toggle rows are tappable.
- Connection profile edit/delete actions use a contextual menu; deletion asks for confirmation.
- Profile forms open fully expanded and scroll with the keyboard; they group identity/server/security, use host/port keyboard navigation, and provide a secure password visibility toggle. Editing endpoint fields invalidates stale connection-test feedback.
- Inventory role choices use a sheet; Doctor reports can filter by severity.
- Motion level: full / reduced / off (respects system animator duration scale).
- UI density (compact / comfortable), haptics toggle. Density is persisted in DataStore
  and drives `SceneDeckTheme.spacing` (page gutter, section/item gaps, row padding); it is
  applied to the page headers, section headers, Inventory, Graph and Settings. Touch
  targets stay ≥ 48 dp. Scenes, Mixer and Stats keep their fixed layouts for now.
- Audio allow-list (Settings → Mixer): a checklist of audio inputs found across every
  OBS scene (same discovery rules, allow-list ignored) plus already saved names. Empty =
  every input; the Mixer re-runs discovery when the list changes. Inputs that only exist
  as disabled scene items are not offered (they can still be kept if already saved).
  Cost: opening the sheet walks every scene and probes each input on the shared serial
  OBS lane, so on large collections it can delay other requests for a few seconds; the
  sheet reloads whenever it appears (also after process death).
- Meter refresh: Smooth (every OBS batch, ~20 Hz), Normal (latest batch per 100 ms),
  Saver (per 250 ms) or Off (meters stop and render empty). Applied client-side to the
  Mixer meter pipeline; OBS keeps sending `InputVolumeMeters` at its fixed ~50 ms cadence
  because changing the event subscription would require re-identifying the session.
- Keep-screen-on while connected.
- Onboarding: first-run welcome + connection wizard (auto-detect OBS on LAN if feasible,
  else manual host/port + QR).

## 10. Non-functional requirements

- **Performance**: deck card tap → OBS request in < 16 ms UI time; meters at ~50 ms
  cadence without jank; baseline profile + R8 for release.
- **Reliability**: survive rotation/process death; auto-reconnect; bounded concurrency
  (≤ 8 parallel OBS reads, mirroring desktop).
- **Offline behavior**: every page shows a designed disconnected placeholder.
- **Accessibility**: content descriptions on all cards/faders, min 48dp touch targets,
  TalkBack-friendly meter alternatives (text dB readouts).
- **Localization-ready**: all strings in resources; ship en first.

## Hardening behavior

- Pairing rejects ambiguous authorities, duplicate parameters, unexpected paths/fragments,
  invalid ports and oversized input. Scene deep links reject ambiguous authorities/NUL names.
- Password drafts intentionally do not survive process recreation; credentials stay encrypted.
- Mixer selection discards obsolete discoveries; Doctor drops prior results on a failed run
  and shows a sanitized error instead.
- Mixer lifecycle discoveries coalesce separately from immediate mute/volume events.
  Confirmed values and pending writes prevent obsolete failures or discovery reads
  from overwriting newer actions/events; cancellation removes its pending edit.
- Onboarding reset cancels the running connection attempt; duplicate attempts are ignored.
  Retries in one onboarding run update a single draft profile (name/host/port/password,
  blank clears the stored password) instead of adding duplicates; only its id is saved state.
- Connected widgets say Connected; their scene list is limited to six items.
- Widget updates ignore unrelated deck metadata. A Ready empty deck clears cached
  scene actions; disconnected/reconnecting states keep the last-known grid for
  connect-then-act. Explicit placement/refresh still retries the current state.
- OBS requests fail after a 10-second admission-and-response deadline and remain
  bound to their originating session and collection revision. New requests are rejected
  while OBS changes scene collections, and queued requests cannot enter another
  collection. A deliberate collection switch can receive its own completion response.
  Timeout or collection replacement does not automatically retry writes;
  failure messages exclude server comments and arbitrary transport/parser details.
  Concurrent deadline/cancellation batches release pending response waiters without
  reducing the eight-request in-flight limit.
- Screenshot previews await at most two capture jobs per batch and prune removed
  scene entries. Each thumbnail is limited to 2 MiB compressed and 1,048,576 pixels
  before bitmap allocation. Cached thumbnail memory scales with eligible scenes;
  the guard does not impose a WebSocket frame-size limit.
- Local registry deletions serialize with other mutations; bulk updates preserve
  import merges, curation ordering and default roles while avoiding per-name reads.
- Stream button reads Stream when idle and Stop when active; active halo respects motion settings.
- Elapsed timers stay visible on phones: green stream, amber recording.
- Newer Android backup/data-transfer rules explicitly exclude all app data.
- Failed profile saves preserve the draft and show a sanitized message; concurrent duplicate writes are blocked.
- Profile/credential operations attempt rollback on storage failure; edited connection tests reuse saved passwords when blank.
- Scenes header shows output status and separate elapsed timers; simultaneous stream/record shows both.
- Stream, Record and VCam are direct controls; Replay Buffer is excluded.

### Persistent broadcast status

The bottom status bar follows the session on every main tab. OBS connection and
broadcast state are separate: OFF AIR when outputs are idle, LIVE with green
elapsed stream time, REC with amber recording time (both when simultaneous).
Stream reconnecting and paused recording have explicit labels. Before the first
output poll it says Checking outputs; a lost connection makes output state unknown.
Performance readings appear on a compact second row only while streaming;
standby and recording-only states hide them.

## Distribution variants

The standard Play/GitHub build includes camera QR pairing. With `-PfdroidBuild=true`,
Connections exposes manual entry only, does not request camera permission, and
excludes the proprietary QR scanner and its dependencies. The empty state explains
manual host/port entry. OBS control, profiles, encrypted credentials, themes, widgets,
and background connection behavior are otherwise shared. Both builds bundle Inter
and JetBrains Mono fonts and work without a Google Play services font provider.
