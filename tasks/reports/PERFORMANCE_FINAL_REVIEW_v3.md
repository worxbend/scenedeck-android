# Performance/data verified review v3

Date: 2026-10-04. This supersedes v1/v2's pending focused verification status.
Central root execution of the corrected OBS/data run completed with exit 0 and
`BUILD SUCCESSFUL in 9s`; log: `/tmp/scenedeck-hardening-focused-core-rerun2.log`.
Performance_data launched no Gradle commands. Whole-project gate/build verification
remains owned by root and is a separate final acceptance requirement.

## Fresh data results — PASS

Parsed all fresh `core/data/build/test-results/testDebugUnitTest/TEST-*.xml` files:
**135 tests across 21 suites; zero failures, errors or skipped tests**. Timestamps
range from `2026-10-04T13:28:54.697Z` to `2026-10-04T13:29:00.330Z`.

| Directly reviewed suite | Tests | Reviewed regressions |
|---|---:|---|
| ScreenshotRepositoryTest | 8 | No 100-scene waiting-job fanout across 30 virtual seconds; unrelated settings retain captures; hot/cold fairness; visibility/cadence; pruning; native malformed/byte/pixel rejection; prior valid-frame retention |
| MixerRepositoryTest | 11 | Failed/cancelled mute/volume rollback; older completion against new success/event/discovery; direct events during blocked discovery; 100-trigger coalescing; old discovery cancellation; media behavior |
| OptimisticControlTest | 5 | Both failed-write orders; older success/newer failure in both orders; newer acknowledgment precedence; observation/snapshot precedence; confirmed rollback baseline |
| RegistryRepositoryTest | 10 | Gated remove/stale cleanup after reorder; atomic mutations; 1000-name DAO counts; duplicate/null import semantics; preserved curated/default/new-scene ordering |
| SceneGraphCycleTest | 8 | First duplicate role and Primary defaults; cycle/verdict behavior; large iterative dependency chain |
| ObsStateRepositoryTest | 7 | Indexed curation join, Primary defaults, sorting/reorder, program changes, disconnect and scene-list behavior |

These six reviewed suites account for **49 passing tests** within the full data run.
All assertions remain behavioral; failures were not suppressed or marked ignored.
`git diff --check` passed after the corrected run.

## Verification-driven corrections

The earlier failed core run exposed a real OBS UUID-cleanup race, plus two data test
harness defects. The accepted OBS confinement remedy and its independent bytecode/API
challenge are recorded in `PERFORMANCE_FINAL_REVIEW_v2.md`; fresh OBS validation also
passed according to the coordinated result, with its full suite accounting owned by
security_obs.

The Mixer test now retains an active collector while discovery is gated. Its immutable
UI projection uses `WhileSubscribed`, so a ended `first` collector was insufficient for
later `.value` assertions when real DataStore suspension and virtual time interleaved.
The actual rerun passes both immediate-event and post-discovery assertions with this
lifecycle correction; production Mixer code needed no verification-driven change.

Native graphics alone did not select the real image decoder in the library tests.
Pinned Robolectric 4.17 bytecode shows missing SDK metadata defaults to API 23, whereas
`GraphicsShadowPicker` selects native shadows only at API 26 or higher. The two decoder
tests now explicitly use SDK 34 plus `GraphicsMode.NATIVE`, matching cached Android 14
and existing repository native graphics tests. Fresh malformed-byte, compressed-size,
oversized-pixel and valid-image assertions all pass. Retention first requires three
valid cached frames, then asserts those same frames survive corrupted captures.
Production image guards were unchanged during these test corrections.

## Accepted implementation and task criteria

- [x] T6: At most two capture child jobs per awaited batch; no per-scene coroutine
  queue; monotonic timestamps, relevant projection, pruning, cold-scene progress,
  cancellation guard and compressed/pixel bounds with passing regressions.
- [x] T7: Confirmed values and pending mute/volume tokens reconcile completion order;
  failure/cancellation removes its own token, and newer authoritative observations
  prevent obsolete rollback. Overlapping two-failure cases restore confirmation.
- [x] T8: Conflated/debounced discovery is independent of direct event reduction;
  cancellation/generation checks and read-start revisions preserve current selection
  and newer controls, with blocked-read and lifecycle-burst tests passing.
- [x] T9: Deletes share the mutation mutex; gated regressions prove a later remove
  or stale cleanup cannot be resurrected by an earlier reorder.
- [x] T10: One indexed snapshot plus one batch write for large bulk mutations;
  sequential duplicate import counts, null merges and existing/default ordering pass.
  Independent review's introduced sortOrder defect was corrected to Int.MAX_VALUE.
- [x] T11: One metadata index per deck reduction/graph build; defaults, first duplicate
  graph semantics, sorting, curation and existing cycle/verdict behavior pass.

security_obs independently reviewed the data source and meaningful final regressions.
Performance_data independently accepted actual OBS request/diagnostic/confinement and
widget/Live diffs, plus shared documentation. No unresolved introduced source defect
remains in these reviews. Full static/build/application tests still gate final delivery.

## Supported performance evidence and limits

- Gated screenshot work remains two admitted children instead of one child per scene
  with repeated permit waiters; descendant-count and cancellation regressions pass.
- Registry reorder previously used N individual name reads; import/assignment also
  used per-entry writes. The 1000-name counting DAO now verifies one snapshot, zero
  per-name reads and one bulk write for each supported bulk path.
- One settled discovery replaces a 100-event lifecycle burst; direct control events
  remain observable before blocked reads finish.
- Metadata joins use an O(M) name index and constant-time lookup rather than repeated
  scans; existing sorting and graph traversal remain.

These establish work/call/allocation bounds, not measured device latency, frame-time
or heap improvements. Thumbnail memory still scales with current eligible scenes;
compressed/pixel guards apply per image and do not cap an already-received WebSocket
frame. Pending control tokens scale with live writes until completion/deadline, without
a hard count cap. Stats wall-clock/sequential cadence follow-up remains outside the
approved implementation. No new dependency, protocol rewrite, UI redesign or external
publishing action was introduced.
