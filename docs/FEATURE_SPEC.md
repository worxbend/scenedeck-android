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
- Show OBS version + WebSocket version after connect (`GetVersion`).
- Switch OBS **profiles** and **scene collections** from dropdowns (visible only when
  connected; observe profile/collection change events).
- QR-code / deep-link based pairing (mobile convenience): OBS host shows
  `obsws://host:port` QR, phone scans to add a profile.

## 2. Live page (the deck) — hero screen

- Grid of **scene cards**: only scenes assigned local role `Primary`.
- Current **program scene** highlighted (Active state); others show Ready state.
- Tap card → `SetCurrentProgramScene`. Haptic feedback + animated state flip.
- Per-scene optional **accent color** and **icon** (local metadata).
- Scene card order = user-defined drag order, persisted locally.
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
- **Extensions**: virtual camera toggle, replay buffer toggle + save (desktop leaves
  both unimplemented).

## 5. Stats page (telemetry)

- Poll `GetStats` + `GetStreamStatus` once per second (obs-websocket has no push stats).
- Gauges with amber/red thresholds: FPS, avg frame render time, dropped-frame %
  (warn 1%, crit 5%), network congestion (30%/60%).
- Trend charts (FPS, render time), per-sample skipped/missed frame bars, counter cards
  (CPU, memory, bitrate, frame totals); rolling bitrate from consecutive byte counters.
- 2-minute ring-buffer history kept for the whole connection, not just while page open.
- FPS + dropped frames always visible in the global status strip on every page.

## 6. Inventory page (local scene registry)

- Lists OBS scenes; assign local **roles**: `Primary` (deck), `Secondary`, `Module`
  (nested reusable), `Raw`, `Debug`, `Archive`.
- Drag-to-reorder (persisted), accent color picker, icon picker, stale-entry detection
  (registry entry whose scene vanished from OBS) with one-tap cleanup.
- **Export/import registry as YAML/JSON** (backup, share between devices).

## 7. Graph & Doctor (health tooling)

- **Graph page**: scene dependency tree from nested scene sources; classify edges
  against role rules to spot surprising dependencies before going live.
- **Doctor page**: diagnostics over OBS inventory + local registry + graph — unassigned
  roles, stale entries, circular references, hierarchy inversions; severity-graded report.

## 8. Mobile-first extensions (beyond desktop parity)

| Feature | Notes | Phase |
|---|---|---|
| Multiple connection profiles | named, QR pairing, last-used default | M2 |
| Studio mode (preview/program) + transition control | **SHIPPED M6**: one-tap Studio toggle; deck drives preview (green), TRANSITION + CUT commit to program (red); OBS-side changes from other clients tracked live | M6 ✅ |
| Scene/source **screenshot preview** (`GetSourceScreenshot`) | **SHIPPED M6**: throttled JPEG thumbnails on deck cards (360 px q50; program/preview 2.5 s, others 10 s; only while Live visible + Ready; "Previews" toggle, default ON) | M6 ✅ |
| Transition picker + duration | **SHIPPED M6**: bottom-sheet picker + debounced duration slider. T-bar gesture **deferred to M7 stretch** | M6 ✅ (T-bar M7) |
| Virtual camera & replay buffer controls | desktop excludes | M7 |
| Scene item visibility toggles | quick "hide camera" style actions | M7 |
| Audio balance / sync offset / monitor type | desktop excludes | M7 |
| Widgets & Wear OS tile | scene switch from home screen / watch | M8 |
| Media controls (play/pause media sources) | `TriggerMediaInputAction` | M7 |
| Quick-volume notification / foreground service | keep meters alive, control from shade | M7 |

Explicitly **out of scope** (same as desktop): input creation/editing, filters,
scene item transforms, scripting, Twitch/StreamElements integration (v1).

## 9. Settings

- Connection profiles; Output Safety toggles; theme (System/Light/Dark) + theme family
  (see DESIGN_SYSTEM.md); dynamic color (Material You) toggle.
- Motion level: full / reduced / off (respects system animator duration scale).
- UI density (compact / comfortable), haptics toggle.
- Audio allow-list; meter refresh behavior; keep-screen-on while connected.
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
