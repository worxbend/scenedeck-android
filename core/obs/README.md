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
  `retryNow()` skips only the pending backoff wait (no-op otherwise).
- `Semaphore(8)` around every request (desktop parity).
- The receiver, typed request continuations and initial GetVersion share one
  `Dispatchers.Default.limitedParallelism(1)` execution lane. Suspended requests remain
  concurrent (up to eight); UUID bookkeeping executes serially between suspensions.
  This avoids ktobs 0.5.0's cancelled-mutex cleanup race without retrying requests or
  altering the dependency. Receiver confinement is explicit because ktobs launches it
  in Ktor's WebSocket scope rather than inheriting the caller's dispatcher.
- Request admission and response share a 10 s deadline. An unanswered live socket cannot
  permanently occupy the request lane; the deadline maps to `ObsRequestTimeoutException`,
  while caller cancellation propagates. Requests retain their original session and fail
  if it has been replaced before admission or completion. Timed-out writes are never
  retried automatically: OBS may have applied them before its response was lost.
- Meter events on a dedicated `SharedFlow` (`extraBufferCapacity = 4`,
  `DROP_OLDEST`); all other events on a separate buffered flow.
- ktobs `ObsRequestException` is mapped to our own `ObsRequestFailedException`
  with structured request/status fields and no raw server comment/cause, so no ktobs
  type or private diagnostic leaks through the `ObsClient` API. Connection errors retain
  safe categories/codes; protocol messages and diagnostic strings redact raw details.
  Malformed typed request responses become `ObsRequestProtocolException` without retaining
  parser messages or causes; the session remains usable for subsequent valid responses.
- Screenshot base64 is limited to 2 MiB of compressed image bytes before allocation.
  The data layer separately reads image dimensions before allocating pixels. This bounds
  image-decoder allocations, not the WebSocket frame that has already been received, nor
  the aggregate memory of every thumbnail in a large current scene collection.

Pinned ktobs 0.5.0 removes request UUIDs in a cancellable mutex-protected finally block.
All register/remove/cancel-all critical sections contain no suspension while holding that
mutex. Confining their execution prevents mutex contention, allowing cancelled requests
to finish cleanup through its uncontended fast path. Recheck this assumption when upgrading
ktobs. Regression tests inspect the actual waiter store across repeated batches of eight
timeouts/cancellations, test deadline/permit recovery and delay caller continuations across
session replacement. Unmatched late responses are ignored by the pinned dependency.
