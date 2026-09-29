# Roadmap — SceneDeck Android

Phased delivery from empty repo to Play Store. Each milestone is independently
shippable (internal track) and has explicit acceptance criteria. Estimates assume
one focused engineer (or agent-driven development with review).

```
M0 Foundation ─ M1 OBS Core ─ M2 Connections ─ M3 Live Deck ─ M4 Mixer ─ M5 Stats ─
M6 Studio+Preview ─ M7 Power Features ─ M8 Polish & Launch
     │ MVP gate after M5 │           │ Feature-complete gate after M7 │
```

## M0 — Foundation (bootstrap)

**Goal:** building skeleton with CI, theme system, navigation shell.

- Create project: Gradle Kotlin DSL, `libs.versions.toml` per `docs/TECH_STACK.md`,
  all modules from `docs/ARCHITECTURE.md` (empty but wired).
- Hilt, KSP, Compose BOM 2026.09.00, Kotlin 2.4.0, edge-to-edge, splash screen.
- `:core:designsystem`: M3 Expressive theme wrapper, **all theme families**
  (dark+light each), typography (Inter/JetBrains Mono), shape system, Lucide icons
  via compose-icons, `SceneIcons` catalogue enum.
- Nav3 shell with all destinations stubbed + status strip scaffold.
- detekt + ktlint + GitHub Actions (build, lint, unit tests); Roborazzi set up.
- App icon v1 (adaptive + monochrome).
- **Exit:** CI green; stub app runs on phone + tablet; theme switcher demo screen
  cycles all families; Roborazzi goldens committed.

## M1 — OBS WebSocket core (`:core:obs`)

**Goal:** rock-solid protocol lane — the heart of the app.

- **Spike first:** evaluate `ktobs` (io.github.rejeq) for: v5 auth, event coverage,
  `InputVolumeMeters` opt-in, `GetSourceScreenshot`, studio-mode requests. If gaps →
  hand-rolled client on Ktor `ktor-client-okhttp` + kotlinx.serialization behind the
  same `ObsClient` interface.
- `ObsClient` interface + session state machine
  (Disconnected/Connecting/Identifying/Ready/Reconnecting).
- Hello/Identify/auth handshake; typed requests + events (scenes, inputs, outputs,
  profiles, collections, stats); `Semaphore(8)` request concurrency.
- Meter event stream with `DROP_OLDEST` backpressure policy.
- Unit tests with a fake WebSocket server (request/response fixtures recorded from a
  real OBS 31+ session).
- **Exit:** demo screen connects to real OBS, lists scenes, shows version,
  survives Wi-Fi toggle with auto-reconnect.

## M2 — Connections & onboarding

**Goal:** first-run experience worth an award.

- Room-backed **connection profiles** (name/host/port), Keystore-encrypted passwords.
- Connection wizard: manual entry + QR scan (`obsws://` deep link) + (spike) LAN
  auto-discovery; first-run welcome flow.
- Auto-reconnect UI states; designed disconnected placeholders everywhere.
- Settings v1: theme family + mode + dynamic color, motion level, haptics, density,
  keep-screen-on.
- **Exit:** fresh install → wizard → connected to OBS in < 60 s; profile switch works;
  password never in plain storage (verified).

## M3 — Live Deck (hero screen) 🎯

**Goal:** the scene-switching surface — parity with desktop Live page, but tactile.

- `SceneCard` grid (adaptive columns), Active morph + glow + haptic, tap →
  `SetCurrentProgramScene`, drag-to-reorder with persistence.
- Scene roles: `Primary` filter drives the deck (Inventory-lite inside settings until M5).
- Accent colors + scene icons per card; long-press quick-edit sheet.
- TransportBar: start/stop stream + record with **Output Safety** confirmations,
  elapsed counters, pulsing record tally, transitional states.
- Global StatusStrip: connection, FPS, dropped frames, CPU, bitrate.
- Baseline profile; deck scroll + tap latency macrobenchmark budget (<16 ms UI).
- **Exit:** run a real show from the phone: 10+ scene switches, stream start/stop,
  zero jank in Macrobenchmark; TalkBack pass.

## M4 — Mixer

**Goal:** OBS-accurate audio control with the best meters on Android.

- Audio discovery port (special inputs → scene inputs → nested recursion → dedupe).
- `VolumeMeter` custom Canvas component (zones −20/−9 dB, per-channel bars, peak hold,
  loudness notch, pre-fader base, fall-off animation).
- `MixerStrip`: dB-taper vertical fader (debounced writes), mute, lock, scope badges.
- Mixer page modes (Active/Selected/Pinned), grouping, session search.
- Live-page embedded compact mixer row.
- **Exit:** meters visually match OBS's own meters frame-for-frame on the same signal;
  fader drag feels native (60 fps, no event flooding — verify write debounce).

## M5 — Inventory, Graph, Doctor + Stats

**Goal:** curation tooling + telemetry — **MVP feature gate.**

- Inventory: role assignment (Primary/Secondary/Module/Raw/Debug/Archive), reorder,
  colors/icons, stale-entry cleanup, YAML registry export/import (share sheet).
- Graph page: dependency tree of nested scenes with role-rule edge classification.
- Doctor page: diagnostics report (unassigned, stale, cycles, inversions).
- Stats page: 1 Hz polling, gauges with thresholds, trend charts, 2-min ring buffer,
  counter cards.
- **Exit:** MVP complete — full desktop parity surface (except custom CSS/hotkeys,
  replaced by mobile idioms). Ship to internal testers.

## M6 — Studio mode, transitions, previews (beyond desktop)

- Studio mode toggle: deck drives preview, TRANSITION + CUT buttons,
  transition picker + duration slider. (T-bar gesture deferred to M7 stretch.)
- `GetSourceScreenshot` thumbnails on scene cards (throttled, perf-guarded,
  ByteArray decode in :core:data — Coil not needed for raw frames).
- **Exit:** preview/program flow matches OBS studio mode exactly (verified via
  host probes in E2E); thumbnails gated to Live-visible + Ready with a
  settings toggle. (Macrobenchmark overhead check moves to M8 perf pass.)

## M7 — Power features ✅ SHIPPED

- Virtual camera + replay buffer controls; media source play/pause; scene item
  visibility toggles; audio balance/sync-offset/monitor type. **All shipped, E2E-verified
  against live OBS** (replay buffer unavailable on the flatpak test rig — error 604 path
  handled gracefully, fixture-tested).
- T-bar gesture shipped (M6 deferral closed).
- Foreground service (dataSync, STICKY, 6 h budget handling) + ongoing notification with
  live scene/state + Disconnect action; keep-alive toggle via long-press on the status
  strip connection area. Battery audit: no wakelock/alarm leaks, clean stop/restart.
- Glance home-screen mini-deck (6 PRIMARY scenes, tally highlight, connect-then-act),
  `scenedeck://scene/{name}` deep link.
- **Exit:** feature-complete gate; battery/background audit passed. ✅
- Follow-ups for M8: stream/record actions in notification; widget refresh affordance;
  Glance goldens; replay-unavailable snackbar copy; lock glyph icon; ring buffer into
  StatsRepository.

## M8 — Polish & launch

- Motion polish pass, predictive back, tablet/foldable adaptive layouts final.
- Accessibility audit, full Roborazzi matrix (all themes × key screens), l10n prep.
- Play Store listing: feature graphic, screenshots per theme, short video; closed
  beta → production.
- Optional stretch: Wear OS tile (switch scene from watch).

## Cross-cutting (every milestone)

- UDF + immutable UiState; `collectAsStateWithLifecycle()`; design-system components only.
- Unit tests for reducers/repositories; Compose tests for screens; Roborazzi for visuals.
- Keep `libs.versions.toml` and `docs/TECH_STACK.md` in sync; update docs when behavior changes.

## Key risks & mitigations

| Risk | Mitigation |
|---|---|
| ktobs library gaps/bugs | M1 spike; `ObsClient` interface hides the choice; hand-rolled client fallback (~1–2 wks, protocol is well documented) |
| Meter event volume (50 ms) jank | `DROP_OLDEST` flow buffer, Canvas draw-only updates, macrobenchmark gate in M4 |
| M3 Expressive API instability (1.5 alpha) | isolate behind `:core:designsystem`; stable M3 fallbacks for critical components |
| Background WS killed by OEM battery | foreground service (M7) + graceful reconnect everywhere |
