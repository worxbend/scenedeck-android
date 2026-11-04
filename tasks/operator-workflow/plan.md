# Implementation plan

## Product and architecture decisions

- Keep Scenes / Mixer / Stats and More. Audio stays on Mixer. Advanced recording,
  presets, preflight configuration, and history use focused menus/sheets or More.
- Keep runtime OBS actions within the existing control-surface boundary. All
  organization, aliases, checklists, presets, and history are stored locally.
- Retain Hilt, Room, DataStore, immutable StateFlow state, shared design components,
  and the existing pinned dependencies. Wire additions remain inside `:core:obs`.
- Preserve confirmation policy, session ownership, cancellation, request deadlines,
  stale-read rejection, explicit unknown states, and bounded work.
- Reuse shared telemetry and event flows instead of adding screen-owned pollers.
- Both normal and F-Droid variants receive the features without proprietary additions.
- Scope local scene identity by connection profile and OBS scene collection before
  building decks/presets on it. A same-named scene on another scope is independent.
- Keep legacy global curation recoverable through migration; do not silently copy it
  to every new host or delete it. Decide and document its activation/import behavior
  in W1b after inspecting session ownership and import flows.
- Named deck membership/order is independent from diagnostic scene roles. Preserve
  the existing All and Primary-only Deck views and canonical Inventory order.
- New OBS features are capability-gated from advertised requests, with sanitized
  unsupported-state handling. Never infer recording-format support from version alone.

## Delivery checkpoints

Checkboxes mean implemented and verified, not merely designed. The order permits
small useful improvements while establishing shared state before automation.

### W0 — Durable setup

- [x] Record authorized scope and exclusion, create branch and resumption entry point.
- [x] Review current docs, project skills, existing task history, and relevant source.
- [x] Record fresh verification baseline/environment (dependency resolution failed;
  no build/test pass is claimed; see progress).

### W1 — Workspace isolation and named decks

- [x] W1a: Add scoped persistence and lossless Room migration with real-database tests.
- [ ] W1b: Bind registry/Inventory/Graph/Doctor/widget projection to an authoritative
  profile + collection context; handle connecting, collection changes, reconnection,
  profile deletion, migration, and stale asynchronous work.
- [ ] W1c: Add named deck CRUD, independent membership and ordering, selection
  persistence, and import/export. Keep built-in All and Deck behavior.
- [ ] W1d: Verify same-name isolation, migration, rename conflicts, bulk operations,
  Undo/import scoping, widget actions, process restoration, and screen renders.

### W2 — Local aliases and preview freshness

- [ ] W2a: Store/edit a local alias; show OBS identity where needed; search both names;
  preserve scene addressing, ordering, TalkBack, rename, and export/import behavior.
- [ ] W2b: Timestamp successful screenshots using monotonic age; distinguish fresh,
  stale, unavailable, and disconnected images without increasing polling frequency.
- [ ] W2c: Verify alias round-trips and stale/failed/reconnected preview rendering.

### W3 — Preflight

- [ ] W3a: Persist scoped configuration: expected collection, required scenes,
  selected microphone, disk threshold, and editable manual checks.
- [ ] W3b: Run bounded checks with independent pass/fail/unknown states and timestamp.
  Separate mute/control availability from optional signal detection. A failed scan
  must not display old success; a context change invalidates the run.
- [ ] W3c: Add preflight under More and an optional entry from output confirmation;
  preserve output safety and avoid a mandatory scan on every start.
- [ ] W3d: Verify partial failures, cancellation, unknown readings, manual checklist
  persistence, context changes, accessibility, and dark/light previews.

### W4 — Live alerts

- [ ] W4a: Reduce shared telemetry into sustained dropped-frame, render-load,
  recording-space, and connection-loss warnings with hysteresis and cooldowns.
- [ ] W4b: Add contextual inline warnings and navigation to affected readings;
  optional haptics, accessible descriptions, dismissal/recovery behavior.
- [ ] W4c: Add explicitly armed microphone silence detection only when speech is
  expected; unavailable meters are unknown, not silence. No audible default alert.
- [ ] W4d: Verify thresholds against canvas FPS, counter resets, stale samples,
  recording-only behavior, disconnection, rearming, and notification limitations.

### W5 — Recording and media tools

- [ ] W5a: Retain available request capabilities; add recording pause/resume and
  advanced split/chapter actions when supported, with confirmed/pending state.
- [ ] W5b: Add local timestamp bookmarks independent of OBS chapter availability;
  define recording/stream time bases and paused-recording behavior explicitly.
- [ ] W5c: Expose media progress, remaining time, and bounded seek in Mixer options;
  disable seeking when duration is unknown and reconcile subsequent OBS state.
- [ ] W5d: Verify unsupported requests/formats, cancellation/timeouts, duplicate taps,
  recording state changes, seek bounds, and compact/light/dark previews.

### W6 — Local session history and export

- [ ] W6a: Persist bounded local sessions/events from shared state: output changes,
  program changes, reconnect gaps, warnings, bookmarks, and preset results.
- [ ] W6b: Add history/detail, clear/delete, retention controls, and user-initiated
  export. Exclude credentials, raw protocol payloads, arbitrary server comments,
  and private paths; mark gaps and unknown outcomes honestly.
- [ ] W6c: Verify event deduplication, retention, process recreation, context changes,
  export escaping, and storage failure; update privacy documentation.

### W7 — Controlled multi-action presets

- [ ] W7a: Persist scoped named presets using a small typed allow-list of existing
  runtime controls (scene, mute/volume, media, and confirmed output operations).
- [ ] W7b: Build step editor and explicit invocation with a concrete step summary;
  share output safety and advertise unsupported or missing targets before execution.
- [ ] W7c: Execute one bounded sequence in its originating context; use target values,
  stop on failure/unknown result/disconnect, record completed steps, and never retry
  uncertain writes automatically. Cancellation is not rollback of completed actions.
- [ ] W7d: Verify concurrent invocation, mid-sequence external changes, cancellation,
  session replacement, partial success, stale references, and screen rendering.

### W8 — Keyboard and compatible foot pedals

- [ ] W8a: Add scoped configurable key bindings to supported actions, conflict
  reporting, clear/edit, and discoverable shortcut help.
- [ ] W8b: Respect text-entry focus, modifiers, key-up/repeat semantics, Studio mode,
  output confirmations, and connection state. Pedals are devices that emit key events;
  do not claim universal hardware support or global background interception.
- [ ] W8c: Verify focus, repeat suppression, bindings after rename/context change,
  and accessibility; record any physical-device checks separately.

### W9 — Demo mode

- [ ] W9a: Add a clearly labeled local simulated session with scenes, Mixer,
  telemetry, Studio mode, and controllable outputs through the existing boundary.
- [ ] W9b: Ensure demo cannot open a real OBS connection, use real credentials,
  send real actions, overwrite real curation, or imply an actual broadcast.
- [ ] W9c: Provide entry from onboarding/More and a clear exit; verify process
  recreation, transition back to real profiles, and normal/F-Droid behavior.

### W10 — Tablet operator layout

- [ ] W10a: Add an adaptive Scenes arrangement with larger program/preview context
  and deck controls; no rundown/cue panel. Keep Mixer a dedicated surface.
- [ ] W10b: Adapt Mixer space and menus for large/resizable windows, retaining phone
  landscape behavior, cutout insets, 48dp targets, and persistent output status.
- [ ] W10c: Render phone/tablet/compact-height/font-scale/theme variants and verify
  keyboard/focus navigation and window resizing without resetting session state.

### W11 — Integration and delivery verification

- [ ] Reconcile FEATURE_SPEC, ARCHITECTURE, DESIGN_SYSTEM, ROADMAP, privacy docs,
  project skills when relevant, and AGENTS with implemented behavior.
- [ ] Run full JDK 21/uv quality gate, explicit debug build/unit tests, and required
  preview renders; record commands and results with no fabricated device claims.
- [ ] Verify F-Droid compilation/feature parity and normal QR onboarding remains intact.
- [ ] Review final diff for regressions, accidental scope expansion, and secrets.
- [ ] Mark the durable goal complete only after every included feature is delivered.

## Validation approach

Use migration/in-memory Room tests for persisted identity and round trips; deterministic
coroutine tests for stale work, context changes, and alert timing; fake OBS server tests
for capabilities/new requests and uncertain write outcomes; Compose semantics and
Roborazzi renders for changed flows. Avoid tests that merely restate implementation.
One Gradle process owner runs checks sequentially. Use existing dependencies first.

Current available JDK 21:
`/home/worxbend/.sdkman/candidates/java/21.0.8-tem`.
System Java defaults to 25, so prefix Gradle/quality commands with this `JAVA_HOME`.
