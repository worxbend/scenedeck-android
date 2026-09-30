# Architecture — SceneDeck Android

Mirrors the desktop app's strict three-lane separation (UI ⇄ controller ⇄ OBS client),
translated to idiomatic Android: **feature modules over core modules, UDF everywhere,
one module owns the wire protocol.**

## Module map

```
:app                        application shell, Hilt app, NavDisplay, theme application
├─ :core:obs                ★ ONLY module that speaks obs-websocket v5 (ktobs/Ktor)
├─ :core:model              pure Kotlin domain models (Scene, Input, OutputStatus, Stats, …)
├─ :core:common             dispatchers, result types, extensions
├─ :core:data               repositories (ObsStateRepository, RegistryRepository,
│                            ProfileRepository, StatsRepository) — Flow APIs only
├─ :core:datastore          Proto DataStore schemas (settings, registry)
├─ :core:database           Room (connection profiles, registry mirror)
├─ :core:designsystem       theme, typography, shapes, motion scheme, SceneDeck icons,
│                            shared atoms (SceneCard, MixerStrip, VolumeMeter, Gauge)
├─ :core:ui                 state holders/helpers shared across features
├─ :feature:live            deck grid + stream/record controls (hero screen)
├─ :feature:mixer           full mixer page
├─ :feature:stats           telemetry dashboards + charts
├─ :feature:inventory       scene registry management (roles/colors/icons/order)
├─ :feature:graph           scene dependency graph
├─ :feature:doctor          diagnostics report
├─ :feature:connections     profile management, QR pairing, connect flow
├─ :feature:settings        settings screens
└─ :feature:onboarding      first-run wizard + help
```

## Layer rules

1. `:core:obs` exposes a single interface, e.g. `ObsClient`:
   `connect(profile)`, `events: SharedFlow<ObsEvent>`, suspend request functions
   (`setCurrentProgramScene(name)`, `setInputMute(…)`, `getSourceScreenshot(…)`, …).
   Nothing else in the app imports ktobs/ktor.
2. `:core:data` repositories combine `ObsClient` events/requests with local storage and
   expose **cold/hot Flows of domain state** (`DeckState`, `MixerState`, `StatsState`,
   `ConnectionState`). No Compose types here.
3. Feature ViewModels consume repository flows, reduce to one immutable
   `data class XxxUiState`, expose `StateFlow`; UI sends events up via lambdas /
   `onEvent(XxxUiEvent)` sealed interfaces.
4. `:core:designsystem` owns **all** visual decisions; feature modules never define
   colors/shapes/typography ad hoc.
5. Secrets never touch repositories: password retrieval is a `SecretsStore` interface
   in `:core:data` implemented with Keystore-backed encryption.

## OBS lane design (`:core:obs`)

- **Connection state machine**: `Disconnected → Connecting → Identifying → Ready →
  Reconnecting(backoff) → Disconnected`. Errors (`ObsAuthException`, socket loss)
  map to typed `ConnectionError`.
- **Event lane**: single `MutableSharedFlow` (buffered, `DROP_OLDEST` for high-volume
  `InputVolumeMeters` ~50 ms cadence — mirrors desktop's `try_send` drop policy).
- **Request lane**: suspend functions with bounded parallelism (`Semaphore(8)`, same
  as desktop) for batch reads like audio discovery.
- **Event subscription**: `ALL | INPUT_VOLUME_METERS` (meters require explicit opt-in).
- **Stats poller**: 1 Hz `GetStats` + `GetStreamStatus` while connected, independent of
  which screen is visible (desktop keeps a 2-min ring buffer for the whole session —
  we do the same in `StatsRepository`).
- **Audio discovery**: special inputs → active-scene audio inputs → recurse into
  enabled nested scenes/groups → dedupe → filter allow-list. Ported 1:1 from desktop.

## State ownership

| State | Owner | Lifetime |
|---|---|---|
| Connection state machine | `:core:obs` ObsSession | connection-scoped service |
| Deck/scenes/audio/events | `ObsStateRepository` | process (survives rotation) |
| Registry (roles/colors/order) | `RegistryRepository` → Room + DataStore | persisted, YAML export |
| Connection profiles | `ProfileRepository` → Room | persisted |
| Passwords | `SecretsStore` → Keystore | persisted |
| Stats ring buffer (2 min) | `StatsRepository` | connection-scoped |
| Screen UI state | feature ViewModels | config-change scoped |

## Process model

- Foreground service (type `dataSync`/media) **only** when "keep alive in background"
  is enabled (M7); otherwise the session is activity-scoped and reconnects on resume.
- Rotation/process death: repositories live in `@Singleton` scope; ViewModels restore
  UI-only state via `SavedStateHandle`.

## Navigation

Navigation 3 with app-owned back stack. Top-level destinations mirror the desktop
workflow: **Scenes, Mixer, Stats**, plus **More** for Inventory, Graph, Doctor,
Connections, Settings and Help. The internal Live route remains serialization-compatible. On tablets/foldables use `material3-adaptive` list-detail for
Inventory/Settings; Live page reflows grid columns.

## Error & offline UX

Every feature screen takes `ConnectionState` as input and renders a designed
disconnected placeholder (illustration + reconnect CTA) instead of crashing or
showing stale controls — same contract as the desktop app's placeholder pages.

## Reliability and secret handling

Cancellation propagates through best-effort OBS operations. Settings transformations
and lock changes are atomic DataStore transactions; StateFlow reducers use atomic
updates. Mixer selection cancels obsolete discovery and only publishes the latest
selection. Doctor refresh replaces previous checks; failed reads retain prior results
rather than classifying every entry as stale. Disconnection clears studio metadata.

Password drafts stay in memory, outside saved-state Bundles, and secret-bearing
models redact their diagnostic strings. Keystore writes verify synchronous persistence
on IO. Pending OBS connect and socket/client resources close when their owning job is
cancelled. QR camera dismissal releases only its owned camera use cases.

Widget input counts are bounded before allocation; updates serialize, reread current
state and isolate removed-widget failures. Foreground-service work is confined to Main;
failed foreground startup cannot reconnect, and Android15 dataSync timeouts disconnect.

Profile metadata/credential writes use a coordinated persistence boundary with
best-effort noncancellable rollback. Recoverable save/delete errors are sanitized,
observable UI state; failed saves retain the form draft and duplicate writes are
gated. Testing an edited profile reuses its stored credential when the draft is blank.
