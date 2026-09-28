---
name: obs-websocket-v5
description: obs-websocket v5 protocol expertise for SceneDeck Android — handshake/auth, requests, events, volume meters, stats polling, and the ktobs/Ktor client in :core:obs. Use for any OBS protocol, networking, or OBS state work.
---

# obs-websocket v5 skill

All protocol code lives in **`:core:obs`** behind the `ObsClient` interface. Nothing
else imports ktobs/Ktor. Requires OBS ≥ 28 (WebSocket server built in, default
port 4455).

## Client choice

Primary: **`io.github.rejeq:ktobs-core` + `ktobs-ktor`** (KMP client on Maven Central,
built on Ktor `ktor-client-*` + kotlinx.serialization; see
https://github.com/Rejeq/ktobs). Wrap it behind `ObsClient` so a hand-rolled client
(Ktor `WebSockets` + `KotlinxWebsocketSerializationConverter(Json)`) can replace it
without touching the rest of the app. Verify at integration time: `InputVolumeMeters`
subscription, `GetSourceScreenshot`, studio-mode request set.

## Protocol essentials

- Flow: WebSocket connect → server `Hello` (op 0, may contain `authentication`
  challenge) → client `Identify` (op 1, with `eventSubscriptions` bitmask) →
  server `Identified` (op 2). Requests op 6 / responses op 7, events op 5.
- **Auth**: `base64(sha256(base64(sha256(password + salt)) + challenge))` using the
  salt+challenge from `Hello`. Password from Keystore-backed storage only.
- **Event subscriptions**: `All | InputVolumeMeters` — meters are NOT included in
  `All`; they stream at ~50 ms and must be buffered with `DROP_OLDEST` (mirror the
  desktop's drop policy; never backpressure the UI).
- **Stats are poll-only**: `GetStats` + `GetStreamStatus` at 1 Hz for the whole
  connection (2-min ring buffer), not per-screen.
- **Concurrency**: bound parallel request batches with `Semaphore(8)` (desktop parity).

## Requests the app uses

| Category | Requests |
|---|---|
| General | `GetVersion`, `GetStats` |
| Scenes | `GetSceneList`, `GetCurrentProgramScene`, `SetCurrentProgramScene`, `GetSceneItemList` (groups incl.), `GetSceneItemEnabled`, `SetSceneItemEnabled` (M7) |
| Studio (M6) | `GetStudioModeEnabled`, `SetStudioModeEnabled`, `Get/SetCurrentPreviewScene`, `TriggerStudioModeTransition`, `Get/SetCurrentSceneTransition`, `SetCurrentSceneTransitionDuration` |
| Config | `GetProfileList`, `SetCurrentProfile`, `GetSceneCollectionList`, `SetCurrentSceneCollection` |
| Outputs | `Get/Start/StopStream( Status)`, `Get/Start/StopRecord( Status)`, `ToggleVirtualCam` (M7), `ToggleReplayBuffer`, `SaveReplayBuffer` (M7) |
| Audio | `GetSpecialInputs`, `Get/SetInputMute`, `Get/SetInputVolume` (multiplier), `SetInputAudioBalance/SyncOffset/MonitorType` (M7) |
| Media (M7) | `GetMediaInputStatus`, `TriggerMediaInputAction` |
| Sources (M6) | `GetSourceScreenshot` (JPEG, throttled) |

## Events handled

Stream/record state (+ record file), `CurrentProgramSceneChanged`, input
mute/volume changed, `InputVolumeMeters`, input created/removed/renamed, scene item
created/removed/reindexed/enabled, profile + profile-list changed, collection +
collection-list changed, scene list changed. Events either update state directly or
trigger re-reads.

## Audio rules (port from desktop, do not improvise)

- Discovery order: special inputs → active-scene audio inputs → recurse into
  **enabled** nested scenes/groups → dedupe → skip sources without volume/mute state
  → apply optional allow-list.
- Volume = multiplier ⇄ dB conversion; writes debounced while fader drags.
- Meter semantics: −60…0 dB, zones at −20/−9 dB, per-channel bars from
  `inputLevelsMul` (magnitude/peak per channel), 20 s peak-hold, ~300 ms loudness
  notch, pre-fader base level shown even when muted.

## Session state machine

`Disconnected → Connecting → Identifying → Ready → Reconnecting(backoff)`; map
`ObsAuthException` / socket failures to typed `ConnectionError`; auto-reconnect with
exponential backoff; emit everything on one `SharedFlow<ObsEvent>`.
