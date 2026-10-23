# Performance/data implementation v1

Date: 2026-10-04. User approved plan and implementation. Status: implementation ready;
focused tests and final gate run by planning_quality, with independent security review.

## Implemented boundaries

- Screenshot poller admits at most two child captures and awaits both before selecting
  more work. It has no per-scene permit-waiter queue. Hot captures get priority, with
  one cold slot when both hot scenes would otherwise repeatedly occupy slow batches.
  Relevant immutable capture configuration is deduplicated; removed names prune
  frames/timestamps. Clock is monotonic. Cancellation is checked before publication.
  Compressed image bytes share core OBS's 2 MiB limit; header dimensions must be
  positive and multiply to at most 1,048,576 pixels using Long before decoding pixels.
  No hard cap is placed on the OBS scene collection or current thumbnail count.
- OptimisticControl separates confirmed OBS values from pending mute/volume requests.
  Failed/cancelled writes remove their own token; newest remaining pending value or
  confirmed value drives display. Successful acknowledgments cannot replace newer
  observed confirmation. Both failure/ack ordering permutations have focused coverage.
  OBS events and discovery observations supersede older tokens without erasing newer
  edits. Cancellation propagates after non-suspending cleanup.
- Mixer discovery requests use conflated StateFlow generations and a 300 ms debounce
  in a separate collectLatest worker. Direct mute/volume events remain immediate while
  discovery waits. Publication checks Ready, generation and cancellation, then merges
  values using the read-start control revision. Unready state clears discovery/control
  caches and invalidates the running generation.
- Registry remove/stale cleanup share the existing mutation mutex. Reorder/import/
  assignment read one indexed snapshot and bulk-write once. Imports retain sequential
  duplicate counting, null-field merge and existing sort-order semantics. Newly assigned
  scenes use Int.MAX_VALUE, retaining the original append/default behavior.
- Deck maps local metadata by name per reduction; graph indexes metadata per build
  retaining the first duplicate entry and Primary defaults.

## Deterministic work evidence

| Path | Before | Implemented bound / assertion |
|---|---|---|
| Slow screenshot dispatch | N children per refresh, repeated duplicate waiter batches | Two admitted children, joined before another batch; 100-scene gated test remains at two calls across 30 virtual seconds |
| Unrelated screenshot settings | Every full settings/deck emission cancels work | Projected configuration equality; haptics edit does not cancel/restart gated captures |
| Registry reorder N names | N byName reads + one bulk write | One snapshot, zero per-name reads, one bulk write |
| Registry import N entries | N byName reads + N writes | One snapshot + one bulk write, duplicates merged sequentially in memory |
| Registry assign N missing names | Snapshot + N reads + N writes | Snapshot + one bulk write, duplicate names deduplicated |
| Lifecycle discovery burst | One full refresh per event, blocking direct events | 100 triggers coalesce to one settled refresh; direct events apply while probe gate remains closed |
| Metadata joins | O(N*M) deck; O((N+E)*M) graph role lookup | O(M) index plus constant-time name lookups, sorting/traversal unchanged |

These are operation/allocation bounds, not measured device latency or frame-time gains.

## Regression files

- ScreenshotRepositoryTest: cadence/subscription, 100-scene slow admission, unrelated
  settings, removed cache entries, invalid/oversized bytes/pixels, accepted-frame
  retention and cold progress with slow program/preview captures.
- MixerRepositoryTest: old mute failure against event/success, cancelled-write cleanup,
  direct events during gated discovery and 100-trigger coalescing, existing volume/media.
- OptimisticControlTest: both failed-write completion orders, older success/newer failure,
  observation/snapshot precedence and newer-success acknowledgment ordering.
- RegistryRepositoryTest: gated deletion/read ordering, 1000-name DAO work counts,
  duplicate/null import preservation, curated/default assignment ordering.
- SceneGraphCycleTest: first duplicate/default role joins plus existing cycle fixtures.

## Independent review and corrections

Security review accepted generic control seam for Boolean/Double with ordering tests,
and coordinated compressed-image budget. It caught an introduced bulk assignment
sortOrder=0 default; implementation now explicitly sets Int.MAX_VALUE and includes a
curated/new-scene regression. Performance review accepted planning_quality's widget
projection and generic transport message changes after reading actual diffs.

Performance review of OBS actual diff accepted total request deadline, before/after
session identity checks, upstream waiter cleanup tests and image bounds. It requested
additional sanitization for response-conversion SerializationException after Ready,
since those errors do not pass the handshake sanitizer; owner is security_obs.

## Verification ownership

No Gradle run by performance_data. planning_quality runs formatting, targeted data
tests and complete JDK21/uv gate serially. Final verification results supersede this
implementation-ready status in a later report.
