# Security/OBS implementation v1

Date: 2026-10-04. Owner: security_obs. Human-approved plan T3/T4/T5 implemented.
Status: source/test review complete; focused Gradle tests and full gate are queued with
planning_quality, the sole Gradle owner. This report does not claim those checks passed.

## Implemented request lifetime and ownership

`KtobsObsClient.request` captures the original Ready session before semaphore admission.
The 10-second request deadline covers both permit admission and wire-response wait.
Admission and successful completion both compare the captured session identity against
the current runner. Queued commands cannot target a replacement session; completed old
responses cannot be returned after a replacement.

Only the request's own deadline becomes `ObsRequestTimeoutException`. The non-null Result
wrapper prevents a nullable successful return being confused with timeout. Caller
cancellation and an enclosing caller timeout propagate. The implementation adds no retry:
an expired write can have been applied by OBS before the response was lost.

The pinned ktobs dependency's UUID store and late-response handling were inspected from
installed bytecode. Integration regressions inspect the real waiter store, rather than
infer UUID cleanup from semaphore recovery. Production code uses no reflection or new
protocol implementation. Upstream cleanup uses a cancellable mutex in its finally block;
ordinary timeout/cancellation tests cover the normal path, not artificially held upstream
mutex contention. No universal contention-safety claim is made.

## Implemented credential-safe diagnostics

- Connection failure mapping retains Auth/Protocol/Unreachable/Closed categories and
  useful numeric close/RPC codes. Messages are fixed local text, with no server close
  reason, serializer text or transport Throwable cause retained by the OBS boundary.
- Public ConnectionError diagnostic strings redact caller-supplied message/reason/cause.
  This also protects feature connection-test errors constructed outside the OBS runner.
  Existing constructor/property types remain compatible; callers explicitly reading a
  manually supplied raw property remain responsible for that property.
- ObsRequestFailedException preserves requestType/statusCode and constructor compatibility,
  but drops arbitrary response comments and causes. The real request status comes from
  the upstream status enum. Parsing failures after Ready become fixed
  ObsRequestProtocolException without retaining serializer messages or causes.
- The independent peer review caught the post-Ready parsing path after the initial
  implementation. A malformed GetStats field regression and healthy subsequent request
  now cover this boundary in addition to malformed handshake/authentication diagnostics.

## Implemented image allocation guard

ScreenshotRequest defines one shared 2 MiB compressed image budget. Before copying the
base64 substring or allocating decoded bytes, decodeImageData checks encoded character
count and padded/unpadded decoded-byte estimate using Long. Existing data URI and raw
base64 formats remain supported. Exact-boundary payloads are accepted and over-budget
payloads are rejected. The performance owner uses the same constant in the data layer
and checks positive header dimensions and at most 1,048,576 pixels before pixel decode.

These guards bound subsequent image decoding. The WebSocket frame has already been
received/parsed, and aggregate thumbnail memory still scales with current scene count.
Removed scenes are pruned by the data owner. A cache eviction/viewport behavior change
requires measured collection memory and UX review; it was not added speculatively.

## Meaningful regression coverage added

- RequestLifecycleTest: eight unanswered requests release permits and actual UUID entries;
  admission-only timeout sends no write; late responses do not break later requests;
  explicit cancellation and enclosing timeout retain cancellation; queued and completed
  old-session work fail across deterministic replacement; repeated deadlines leave UUID0;
  actual rejected commands strip private comments and keep structured status; malformed
  typed response strips private parser text and permits a later valid request.
- ConnectionErrorSanitizationTest: caller-created model diagnostic strings, malformed
  initial GetVersion, private authentication close reason and unreachable transport cause.
- StudioModeTest: exact compressed-byte boundary, padded/unpadded base64 acceptance and
  over-budget rejection. Existing OBS handshake/reconnect/meter/studio tests stay in scope.

FakeObsServer gained small scripted response/drop/frame/auth-reason hooks, preserving
default existing behavior. Request tests inject an internal semaphore/timeout seam and
pause only caller continuations when proving session replacement races.

## Independent implementation review

performance_data accepted actual deadline/session/base64/server-status changes, then
identified the parsing leak addressed above. security_obs reviewed actual performance
and planner implementations: widget projection/connected-empty behavior, generic Live
transport messages, screenshot batching/header limits, serialized registry deletions and
bulk merges, first-duplicate graph lookups, and confirmed/pending Mixer control ordering.
The review caught a changed registry new-entry sort order; owner restored Int.MAX_VALUE
and added a regression. Mixer token storage is proportional to live caller writes, with
the real OBS 10-second deadline, rather than a hard count cap. Separate generation-based
discovery remains coalesced and direct control events remain prompt.

## Verification and preserved contracts

`git diff --check` passes before queued formatting. Focused tests/full gate are pending;
later verification reports supersede this readiness report. No device latency/frame-time
measurement is claimed. Intentional LAN cleartext support, encrypted password storage,
QR/manual pairing, backup exclusions, module boundaries and local-only curation remain
unchanged. No dependency update or architecture hierarchy was needed.
