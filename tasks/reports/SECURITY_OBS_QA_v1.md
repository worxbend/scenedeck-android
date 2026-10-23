# Security/OBS QA correction v1

Date: 2026-10-04. Status: first focused run failed; reviewed corrections are ready
for a fresh serial run owned by root. This supersedes the assumption in
SECURITY_OBS_IMPLEMENTATION_v1 that UUID contention was merely unproved.

## First actual post-implementation evidence

Formatter passed (`BUILD SUCCESSFUL in 3s`) and `git diff --check` passed.
The focused OBS/data run compiled and executed the changed tests, then failed in
12 seconds: OBS 47 tests / 2 failures; data 135 tests / 2 failures. Preserved log:
`/tmp/scenedeck-hardening-focused-core-first-failure.log`.

OBS `missingResponsesReleaseEveryPermitAndPendingRequest` measured **seven retained
UUID entries** after eight simultaneous deadlines. Permits recovered but the actual
dependency waiter store did not. This demonstrates routine cleanup contention,
not just an artificial lock-holder scenario. The test correctly blocked delivery.

OBS malformed-response sanitation produced a safe ObsRequestProtocolException but
coroutine stack-trace recovery copied that safe exception into its own cause chain.
The failure was the overstrict `cause == null` assertion, not retained raw parser
data. The test now checks that the private response marker is absent throughout
the cause chain, which is the actual privacy contract.

Data failures were assigned to performance_data: a transient WhileSubscribed
projection lost its active subscriber during real IO, and Robolectric legacy
graphics returned a Bitmap for invalid bytes. The owner retained a Mixer subscriber
and used native graphics for actual BitmapFactory decoder behavior. Fresh results
are still required; this report does not assume those hypotheses passed.

## Independently accepted UUID cleanup remedy

One shared `Dispatchers.Default.limitedParallelism(1)` dispatcher confines:

- typed request blocks, including UUID registration and removal;
- the initial direct GetVersion handshake request;
- the complete runDefaultReceiver body, including cancelAllUuids in its finally block.

The receiver wrapper is explicit: pinned ktobs launches its receiver in Ktor's
WebSocket scope rather than inheriting the runner job's dispatcher. Installed
bytecode verified that all UUID mutex critical sections contain no suspension
while holding the mutex. Confinement serializes those sections between suspensions,
so cancelled cleanup uses the uncontended mutex fast path. Suspended requests
remain concurrent behind the existing eight-permit limit.

The outer deadline includes permit and dispatcher admission and response waiting.
Session identity is checked after permit admission, again inside the dispatcher
immediately before the typed call, and in the caller context after it returns.
Caller cancellation still propagates. There is no production reflection, dependency
fork, synthetic response, broad waiter clear, connection reset or write retry.

performance_data independently challenged dispatcher inheritance, then accepted
the explicit receiver confinement and inspected the actual final patch. Recheck
this mechanism against ktobs bytecode/source when changing its pinned version.

## Strengthened regression coverage

- Three rounds of eight concurrent dropped replies each assert typed deadlines,
  actual UUID store size zero, followed by a healthy request on the same session.
- Three rounds of eight concurrent caller cancellations each retain cancellation,
  join all request jobs, assert UUID zero, and perform a healthy request afterward.
- The completed-old-response test holds the real server reply with a latch, captures
  its actual Deferred, then releases it while the caller continuation remains paused.
  This preserves the originating-session race test after the dispatcher change.
- The malformed response test checks the full safe cause chain for a private marker,
  verifies UUID cleanup, and performs another valid request.

Sources and regression design are peer-approved; fresh focused/full verification
results will supersede this ready-to-rerun report.
