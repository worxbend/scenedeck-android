# Independent performance/data peer review v1

Date: 2026-10-04. Reviewed actual final peer diffs, after implementation approval.
Application verification is owned by security_obs after the QA handoff. This report
records source review acceptance; test/build PASS must come from the QA report.

## OBS request lifetime and diagnostic boundary — accepted

Files: KtobsObsClient.kt, ObsClient.kt, ObsSessionRunner.kt, ConnectionState.kt,
RequestLifecycleTest.kt, ConnectionErrorSanitizationTest.kt, StudioModeTest.kt.

The 10-second default request deadline includes semaphore admission and response.
Capture of the originating session precedes admission; identity checks precede dispatch
and return. withTimeoutOrNull distinguishes local expiry from caller cancellation,
with a non-null Result wrapper permitting nullable result types safely. The timed-out
operation is not retried; a write's remote outcome can remain unknown.

Regression tests examine the pinned upstream library's actual active UUID map after
timeouts/cancellation, then issue a healthy request. Queued replacement-session and
already-completed old-response cases exercise both identity checks. Fake-server waits
have explicit outer deadlines; clients are scoped to test-base cleanup and server use
blocks. No new apparently unbounded fake-server lifecycle was found in source review.

Peer review found response conversion SerializationException still escaped after
Ready. security_obs added ObsRequestProtocolException with a fixed message/no retained
parser cause and a malformed-GetStats regression followed by a healthy request. This
closes the reviewed request parsing diagnostic path without catching cancellation.

Connection boundary maps arbitrary auth/close/parser/transport details to fixed
messages and types; public diagnostic toString does not expand sensitive fields.
Request failure preserves structured type/status while dropping raw comment/cause.
Core compressed-image decode guards base64 and estimated decoded lengths before
allocating a payload byte array, sharing the data repository's 2 MiB image byte limit.
The WebSocket text frame/JSON has already been received before this guard; this does
not impose a transport frame-size limit. No claim of universal bounded networking.

## Widget projection and transport UI messages — accepted

Files: SceneDeckWidgetState.kt, SceneDeckWidgetUpdater.kt, WidgetUpdateTest.kt,
LiveViewModel.kt, TransportErrorMessageTest.kt.

Widget updates compare only six visible scene names, program name, connection label
and readiness, eliminating work on unrelated studio/metadata emissions. Current state
is reread under the existing mutex; poke stays unconditional and widget failures remain
isolated. Connected empty state clears stale scene actions; unready state retains cached
cold-start grid and updates the label. Relevant projection/default/order cases are
covered. A transient Ready/initial-empty scene list may clear cached grid before the
first scene read; the deck model has no initialized-snapshot flag. No broader model
change was introduced in this accepted slice.

Transport errors use a fixed local action-context message, excluding raw OBS comments,
request type/status strings and arbitrary Throwable details. Existing cancellation
handling remains at the caller boundary. No visual layout/theme change was found.

## Data implementation independent review outcome

security_obs independently accepted screenshots, confirmed/pending controls, discovery
generation/publication and batching/index boundaries. It found an introduced bulk role
sortOrder default error, now corrected to Int.MAX_VALUE with a regression preserving
curated ordering/default Primary/new appended Secondary behavior. Snapshot duplicates
retain previous sequential import counts and merge semantics; graph first duplicate
role is retained. Root must use focused/full test results before marking complete.

## Limits and recorded deferrals

- No device profiler, Macrobenchmark or frame-time/latency measurement was run here.
  Work/call-count reduction and queue/allocation guards are the supported claims.
- Thumbnail count remains proportional to the current OBS scene collection; removed
  names are pruned and each decoded frame is bounded. No arbitrary scene limit added.
- Optimistic token storage is proportional to live caller writes, with real client
  request deadlines and completion/cancellation removal; no hard token-count cap added.
- StatsRepository's wall clock and sequential polling cadence remain unchanged. These
  were lower-priority follow-up observations, outside the approved implementation slices.
- No speculative GoF hierarchy, interface-per-repository, protocol rewrite, new dependency,
  production agent scheduler, release/publishing action or UI redesign was introduced.
