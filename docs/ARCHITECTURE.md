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
  as desktop) for batch reads like audio discovery. A 10-second deadline covers
  permit admission and response waiting. Requests capture the originating session
  before admission and reject a replacement session before dispatch/publication.
  Timeout and protocol failures are typed and sanitized; caller cancellation
  propagates and timed-out writes are not automatically retried. Typed request
  blocks, initial GetVersion and the complete receiver/cleanup explicitly share
  one `Dispatchers.Default.limitedParallelism(1)` execution lane. Up to eight
  requests remain suspended in flight; serial bookkeeping avoids the pinned
  ktobs cancelled-mutex cleanup race. Repeated eight-request deadline/caller-
  cancellation regressions verify zero retained UUID waiters and healthy followups.
  Recheck that UUID critical sections do not suspend when upgrading ktobs; the
  receiver runs in Ktor's scope and requires explicit confinement.
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
Inventory/Settings; Live page reflows grid columns. The shell (`ShellLayout`) switches
compact-height windows (phone landscape, < 480dp) to a navigation rail with a single-row
status strip, applies horizontal safe-drawing insets to all content (end edge only beside
a rail), and re-applies `enableEdgeToEdge` bar styles from the resolved app theme.

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

Mixer lifecycle refreshes feed a conflated, 300 ms debounced discovery lane;
mute/volume events continue reducing immediately while discovery is suspended.
Confirmed control values and pending write tokens reconcile overlapping writes.
Failure/cancellation removes only its own token. Discovery observations preserve
newer events/actions and supply rollback baselines for pending edits.

Thumbnail polling projects only readiness, visibility, preview preference and scene
selection before restarting. Each batch awaits at most two capture child jobs,
including request admission and decode; hot scenes retain priority with a cold-scene
slot to prevent starvation. Removed names prune frame/throttle entries. The OBS
boundary rejects compressed images above 2 MiB before base64 allocation, and the
data layer validates positive dimensions and at most 1,048,576 pixels before bitmap
allocation. These per-image/work bounds do not cap the received WebSocket frame or
aggregate cached frames across the current scene collection.

Registry deletion shares the existing mutation mutex. Bulk mutations read one
snapshot, index it and write a batch while preserving duplicate-import merge/count
semantics and default ordering. Deck/graph reducers index metadata once; the public
graph builder preserves the first duplicate registry entry.

Password drafts stay in memory, outside saved-state Bundles, and secret-bearing
models redact their diagnostic strings. Keystore writes verify synchronous persistence
on IO. Pending OBS connect and socket/client resources close when their owning job is
cancelled. QR camera dismissal releases only its owned camera use cases.

Widget input counts are bounded before allocation; updates serialize, reread current
state and isolate removed-widget failures. A distinct projection of six visible
Primary scenes, program scene, connection label and readiness avoids unrelated
writes. Ready with an empty deck clears cached actions; unready preserves the cached
grid. Explicit placement/refresh `poke()` remains unconditional. Foreground-service work is confined to Main;
failed foreground startup cannot reconnect, and Android15 dataSync timeouts disconnect.

Profile metadata/credential writes use a coordinated persistence boundary with
best-effort noncancellable rollback. Recoverable save/delete errors are sanitized,
observable UI state; failed saves retain the form draft and duplicate writes are
gated. Testing an edited profile reuses its stored credential when the draft is blank.

The persistent status bar consumes the shared session telemetry at app-shell scope,
so changing tabs never resets output state. Output events update the feed promptly;
poll responses are merged only if no newer event superseded the request. Failed
readings become unknown independently. Connection consumers await their own
request outcome rather than accepting a previous terminal connection state.
