# Performance/data final verified review v4

Date: 2026-10-04. This supersedes v1–v3's pending final verification status.
Application source is stable. Performance_data launched no Gradle commands; root
executed the central gates and build. Shared task/doc ownership remains with
planning_quality.

## Final verification — PASS

- JDK 21 + uv `scripts/check-quality.sh` completed with exit 0:
  `/tmp/scenedeck-hardening-quality-pass.log`. Its Gradle `qualityCheck` completed
  successfully, including formatting, applicable Detekt, Android lint and unit tests.
- Independently parsed all debug unit-test XML: **426 tests across 85 suites**, with
  **zero failures, errors or skipped tests**.
- Data XML: **135 tests across 21 suites**, zero failures/errors/skips. Fresh timestamps
  range from `2026-10-04T13:37:49.927Z` to `2026-10-04T13:38:08.909Z`.
- Explicit `assembleDebug testDebugUnitTest` completed successfully:
  `/tmp/scenedeck-hardening-build-unit.log` (`BUILD SUCCESSFUL in 3s`). These tasks
  reuse already-verified outputs where appropriate; this is not a clean-build timing.
- Semgrep 1.156.0: seven configured rules, 203 selected source files, zero findings
  and errors. Actual JSON `paths.scanned` in
  `/tmp/scenedeck-hardening-semgrep-coverage.json` includes the new, untracked
  `OptimisticControl.kt`; selected paths contain no build/generated artifacts.
- Gitleaks history and filesystem passes reported no leaks. Final `git diff --check`
  passed.

The scanner's summary wording suggested a tracked-only limitation. Actual target-list
and JSON evidence disproved the inferred omission of the new production helper.
The existing default scanner policy excludes 130 test-directory files; test static
coverage was not expanded. All new regression tests compile and run in the unit gate.
No scanner script change was needed or made.

## Accepted final source and task criteria

- [x] **T6 screenshots:** at most two child captures per awaited batch, no per-scene
  waiting-job queue; relevant configuration projection, monotonic timestamps,
  pruning, cancelled-publication guard and cold-scene progress. Compressed bytes are
  capped at the shared 2 MiB OBS constant; positive header dimensions and a Long
  product cap of 1,048,576 pixels precede pixel allocation. Native decoder tests cover
  malformed, compressed-byte and pixel limits plus valid-frame retention.
- [x] **T7 Mixer controls:** confirmed values and pending tokens reconcile old/new
  acknowledgments, both failure orders, cancellation, events and discovery baselines.
  Failure/cancellation removes its own token; newer observations prevent obsolete
  rollback. The small generic tracker is shared only by mute and volume.
- [x] **T8 discovery:** conflated/debounced latest discovery runs independently of
  direct control-event reduction. Cancellation, generation, readiness and read-start
  revision guards prevent stale publication and retain newer controls. Gated reads
  and 100-trigger burst regressions pass.
- [x] **T9 registry mutations:** removal and stale cleanup share the mutation mutex;
  gated regressions prevent resurrection by an earlier reorder.
- [x] **T10 registry bulk paths:** one indexed snapshot and one bulk write; duplicate
  import counts, null preservation, curated ordering and missing-scene defaults are
  retained. Independent review found and resolved the introduced sortOrder default
  mismatch; new entries explicitly retain `Int.MAX_VALUE` ordering.
- [x] **T11 metadata lookup:** one name index per deck reduction/graph build replaces
  repeated scans. Graph public-list duplicates retain first-occurrence semantics;
  Primary defaults, sort order and iterative cycle behavior remain covered.

Six directly reviewed data suites account for 49 passing tests: screenshots 8,
Mixer 11, OptimisticControl 5, registry 10, graph 8 and deck state 7. security_obs
independently accepted the final data source and regression semantics.

## Final verification and peer-review follow-ups

The earlier two data failures were harness defects corrected without weakening the
assertions: the blocked-discovery test retains a collector for its WhileSubscribed
projection, and decoder tests select SDK 34 plus native graphics so Robolectric uses
the real decoder. The final focused and full runs pass those semantic assertions.

The only production data adjustment during static verification rewrote decoder
dimension validation into an equivalent conditional return to meet ReturnCount;
the byte/pixel bounds and allocation order are unchanged. security_obs independently
accepted that actual branch and its unchanged tests.

The bulk DAO change exposed an Inventory test double whose `upsertAll` was a no-op.
planning_quality corrected the fixture so single and bulk writes share one failure,
barrier and count path. Independent actual-diff review accepted the change: failure
keeps the preview, retry clears it, and duplicate-write protection remains asserted.
The final full gate passes both corrected tests; production Inventory code was not
changed.

Performance_data independently accepted the final OBS deadline/session, redaction,
payload and protocol-confinement source, its narrow documented privacy suppression,
and its six-parameter client/test admission seam. The real cancelled UUID-cleanup race
found by the first gate was resolved by one explicit shared protocol lane covering
typed requests, initial version discovery and the entire receiver/finally path.
This relies on audited pinned ktobs critical sections containing no suspension; the
dependency-upgrade review condition is documented. Tests assert actual pending-store
cleanup across repeated timeout/cancellation batches and continued healthy requests.

Widget/Live behavior and shared documentation were also independently accepted:
visible widget projection avoids redundant writes, Ready empty clears stale actions,
offline grid behavior remains, current poke writes are serialized, and transport
failures use local sanitized messages. No visual layout was changed by these slices.
No unresolved introduced defect remains in the reviewed, verified state.

## Evidence limits

Verified gains are reduced and bounded work, not measured Android latency or frame
time. A blocked 100-scene capture fixture remains two admitted children rather than
one waiter per scene. Counting DAO regressions verify one snapshot, zero per-name
reads and one bulk write for 1,000-name operations. One settled discovery replaces a
100-trigger burst; direct events remain observable during blocked reads. Metadata
lookup uses one linear index followed by constant-time lookups.

Thumbnail memory still scales with current eligible scenes. Per-image compressed and
pixel limits do not cap an already-received WebSocket text frame. Pending Mixer tokens
scale with live caller writes until completion/deadline and have no hard count cap.
No device benchmark, heap profile, Macrobenchmark, exhaustive security proof or blanket
design-pattern certification is claimed. Stats wall-clock/sequential cadence remains
outside the approved implementation. No dependency upgrade, protocol rewrite, UI
redesign, commit, publication or external message was introduced by this lane.
