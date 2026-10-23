# Independent performance/data peer review v2

Date: 2026-10-04. Supersedes the source-only acceptance in v1 where fresh verification
identified additional conditions. Central Gradle execution belongs to root; no Gradle
command was launched by performance_data. Focused rerun and final gate are pending.

## Fresh focused verification and owned corrections

`/tmp/scenedeck-hardening-focused-core.log` ended with exit 1: 135 data tests had two
failures and 47 OBS tests had two failures. Compilation succeeded. This is a failed
verification checkpoint, not final validation.

- `MixerRepositoryTest.blockedDiscoveryDoesNotDelayOrOverwriteNewerControlEvents`
  failed its immediate mute assertion. Its `first` subscriber had ended before the
  suspended discovery scenario, leaving a `WhileSubscribed` projection subject to
  expiry while real DataStore work and virtual time interleave. The test now holds an
  active collector throughout the blocked read, event assertions and settled discovery.
  Both immediate-event and stale-snapshot assertions remain. This corrects the observed
  projection lifecycle; rerun must confirm the diagnosis. Production code was unchanged.
- `ScreenshotRepositoryTest.rejectsInvalidOversizedBytesAndPixelDimensions` received
  a bitmap for invalid bytes in legacy Robolectric graphics. The rejection and previous
  accepted-frame retention tests now use native graphics, preserving the malformed-byte,
  compressed-size and oversized-pixel assertions. Retention additionally requires three
  accepted frames before corruption, preventing an empty-cache false positive. Production
  decoding code was unchanged. [Robolectric 4.17 graphics modes](https://robolectric.org/javadoc/4.17/org/robolectric/annotation/GraphicsMode.Mode.html)
  distinguish legacy fakes from native Android graphics.

`git diff --check -- core/data` passed after these test fixes. Application test PASS is
not claimed until the central rerun succeeds.

## Independently challenged OBS cleanup remedy — accepted, pending verification

Fresh concurrent timeout tests exposed a real pinned-library cleanup defect: seven
UUID waiters remained after eight cancelled requests. ktobs 0.5.0 acquires its private
mutex from a cancellable `finally`; contention prevents cancelled callers acquiring it.
The original wrapper deadline alone was insufficient to guarantee waiter cleanup.

Reviewed bytecode confirms register/remove/cancel-all critical sections do not suspend
while the private mutex is held. The accepted remedy confines all known UUID access
to one shared `Dispatchers.Default.limitedParallelism(1)` execution lane: typed request
blocks, initial GetVersion and explicitly wrapped `runDefaultReceiver`, including its
cleanup. Eight requests may still suspend concurrently behind the existing semaphore.
The receiver wrapper is essential: ktobs launches the receiver on the Ktor WebSocket
scope rather than inheriting the session caller's dispatcher.

The Kotlin API guarantees serial execution between suspensions for
[limitedParallelism(1)](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-coroutine-dispatcher/limited-parallelism.html),
and an uncontended [Mutex.lock](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.sync/-mutex/lock.html)
does not check cancellation. Confinement therefore makes the pinned cleanup fast path
available without production reflection, clearing unrelated requests or resetting a
replacement session. This assumption must be reviewed when ktobs is upgraded.

Actual source review confirms a session guard after dispatcher admission and before
the typed block, plus the existing final return guard. Total admission/response deadline,
caller cancellation and no automatic write retry remain. Strengthened regressions
inspect actual waiter state across three batches of eight timeouts and three batches
of eight caller cancellations, followed by a healthy request. The old-response test
captures its actual deferred behind a bounded reply barrier while the outer caller stays
paused, retaining coverage after the dispatcher switch.

Protocol sanitization now tests the entire exception cause chain for a private marker.
Coroutine stack-trace recovery may copy an already-safe exception into its cause chain;
requiring null at every boundary was stricter than the privacy contract. The test still
fails for retained parser/server details and still requires a usable subsequent request.

## Shared documentation review — accepted

AGENTS.md and ARCHITECTURE/FEATURE_SPEC/HARDENING/ROADMAP documentation match maximum
two awaited captures, per-image compressed/pixel bounds, proportional total cache memory,
confirmed/pending controls, coalesced discovery, serialized registry deletion, bulk import
and ordering semantics, and first-duplicate graph behavior. Planner refined the discovery
wording to include rollback baselines while preserving newer pending display values.
Final verification wording remains pending the actual gate result.

## Remaining checks and evidence limits

- Central formatter, focused OBS/data rerun and full JDK21/uv quality/build/test gate.
- Keep all T6–T11 verification criteria pending until results are available.
- No device latency/frame-time/heap metrics measured. Deterministic work bounds and
  database operation counts remain the supported performance evidence.
- No transport frame cap, total scene-count cap or hard pending-token count cap added.
