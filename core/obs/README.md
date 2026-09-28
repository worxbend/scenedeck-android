# :core:obs — OBS WebSocket lane

The only module that speaks obs-websocket v5. Everything is behind `ObsClient`;
nothing else in the app imports ktobs/Ktor (docs/ARCHITECTURE.md rule 1).

## ktobs spike verdict (0.5.0, evaluated 2026-09-29): **USE ktobs**

| Criterion | Result |
|---|---|
| (a) v5 auth (Hello/Identify, `base64(sha256(...))`) | ✅ `Auth.kt` — `authSession()` implements the exact v5 algorithm (SHA256 via org.kotlincrypto, Base64 via kotlin.io.encoding), maps server close 4009 → `ObsAuthException(AuthError.InvalidPassword)`, missing password → `PasswordRequired` |
| (b) `InputVolumeMeters` subscription | ⚠️ Subscription bit exists (`ObsEventSubs.InputVolumeMeters = 1 shl 16`) and works, **but** the typed `InputVolumeMetersEventData` is broken: it decodes `inputs` into `model.Input`, which requires `inputKind`/`unversionedInputKind` — fields the real meters payload does not carry, so `event.get<InputVolumeMetersEventData>()` throws. **Workaround:** we decode `EventOpCode.eventData` (public `JsonObject`) with our own `@Serializable` meter model (`internal/protocol/MeterProtocol.kt`) |
| (c) Raw/custom escape hatch | ✅ `ObsSession.callMethod<Output>(type)` / `callMethod<Output, Input>(type, data)` call any request by name with caller-provided serializers; typed `GetSourceScreenshot` + full studio-mode set (`Get/SetStudioModeEnabled`, `Get/SetCurrentPreviewScene`, `TriggerStudioModeTransition`, transitions/T-bar) also present for M6 |
| (d) Coroutine Flow events | ⚠️ Callback-based (`ObsEventHandler = suspend ObsSession.(EventOpCode) -> Unit`), not a Flow. Trivially adapted: we emit onto our own `SharedFlow`s in `KtobsObsClient` |

Known 0.5.0 quirk we route around: `ObsSessionBuilder.build()` drops the configured
`onEvent` (never passes it to `authSession`). We use `connect {}` instead, which
does wire `onEvent`, and keep the block alive for the session's lifetime.

Dependency fit: requires ktor ≥ 3.2.3, kotlinx-serialization ≥ 1.9.0,
coroutines ≥ 1.10.2 — all satisfied by our pins (3.6.0 / 1.11.0 / 1.11.0).

## Own code on top of ktobs

- Session state machine `Disconnected → Connecting → Identifying → Ready →
  Reconnecting(backoff ≤ 30 s)`; `ObsAuthException` → `Failed(Auth)`, no retry.
- `Semaphore(8)` around every request (desktop parity).
- Meter events on a dedicated `SharedFlow` (`extraBufferCapacity = 4`,
  `DROP_OLDEST`); all other events on a separate buffered flow.
- ktobs `ObsRequestException` is mapped to our own `ObsRequestFailedException`
  so no ktobs type leaks through the `ObsClient` API.
