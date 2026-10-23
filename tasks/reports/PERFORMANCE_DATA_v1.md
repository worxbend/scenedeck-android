# Performance, data and concurrency review v1

Date: 2026-10-04. Status: review complete; implementation awaits approved plan.

## Scope and method

Read AGENTS.md, the five required project documents, repository Android/OBS skills,
Kotlin Coroutines, Clean Code, Architect Review and GoF Patterns (including catalog).
Surveyed repositories, DataStore and Room contracts, stats/history, screenshots,
foreground service and widget lifecycles and their existing regression tests.
Application code remains unchanged; no builds started by this reviewer.

Use coroutine call counts, gated requests, DAO call counts and state assertions as
repeatable measurements. No device/frame-time benchmark is available in this review,
so throughput and jank improvements must not be claimed as measured runtime gains.

## Findings selected for planning

### P1 — Screenshot work queue grows beyond its advertised bound

Evidence: `core/data/src/main/java/com/scenedeck/android/core/data/ScreenshotRepository.kt:87`
launches one child per due scene every tick; its Semaphore is acquired only inside
`capture` at line 105. `lastFetchMs` is set at enqueue time (line 96). With 100 scenes
and one second per capture, only 20 requests finish per 10-second cold interval, but
100 more jobs are enqueued each interval. The two-permit semaphore bounds execution,
not queued jobs. It also schedules a hot scene again while its previous request waits.

Minimal fix: run two structured workers per cycle rather than launching every scene;
workers take due scene names from one finite queue and await captures. Recheck due
timestamps at actual dispatch, stamp on dispatch, use a monotonic clock, and preserve
hot-scene priority. Ensure cancellation reaches every worker, with no launch backlog.
Alternatively keep at most one child per scene plus strictly bounded admission, but
do not mistake per-scene deduplication for a global queue bound.

Regression: gated slow client with 100 scenes, advance multiple refresh intervals,
assert no duplicate in-flight scene and no more than two actively submitted captures;
release captures and assert progress and cancellation on leaving screen.
Files: repository and ScreenshotRepositoryTest (2 files, medium).

### P1 — Screenshot cache and decode memory are unbounded

Evidence: the thumbnail map is only cleared while hidden/disconnected (lines 79–81),
and each capture adds a scene key (118), so removed/renamed scenes remain while Live
stays visible. The throttle map has the same lifetime. `decodeBitmap` (127) trusts
remote image dimensions without a bounds read; requesting width 360 alone does not
bound extreme image height or a malicious response.

Minimal fix: prune thumbnails and throttle timestamps to current scene names on each
capture configuration; inspect decoded bounds before allocation and enforce explicit
encoded-size/pixel budgets. Coordinate encoded response size with the core OBS owner.
Retain the previous accepted frame when an invalid/oversized capture is rejected.

Regression: remove/rename scene while visible and assert both cache outputs and future
calls exclude it; invalid/oversized image dimensions are rejected before pixel allocation;
valid existing JPEG still renders. Keep explicit guards measurable and documented.
Files: repository and ScreenshotRepositoryTest (2 files, medium).

### P2 — Unrelated settings/deck emissions cancel screenshots

Evidence: combine passes whole `settings.settings` and whole DeckState into
collectLatest (64–78), so haptics/theme/output-safety/lock or metadata/transition changes
cancel in-flight captures even though capture inputs did not change. Timestamp written
before cancellation can then suppress a missing frame for ten seconds.

Minimal fix: project to immutable capture configuration (ready/subscribed, previews,
scene names, program/preview names), apply distinctUntilChanged before collectLatest,
and keep throttle bookkeeping aligned with dispatched work. This fits P1 implementation.
Regression: gate capture, change unrelated setting/metadata, assert call is not cancelled
or restarted. Relevant setting, disconnect and scene removal must still cancel.

### P1 — Late failed mute writes undo newer state

Evidence: `MixerRepository.kt:244` records previous mute then always restores it on
failure (250–251). Unlike volume (214–241), mute has no mutation revision. A first
mute-to-true request can hang, a newer OBS event can set true, and the old failure
then writes false over that authoritative state. Discovery publications do not revoke
the old mute rollback either.

Minimal fix: guard mute previews/events/writes/discovery through the same lock and
revision protocol as volume, with separate per-control revisions. Keep immediate
mute behavior and sanitized caller failures; avoid a speculative pattern hierarchy.
Regression: simple failure still rolls back; delayed failure cannot undo newer successful
write, newer OBS event or discovery. Cancellation remains cancellation.
Files: MixerRepository and MixerRepositoryTest (2 files, medium).

### P1 — Registry deletes bypass serialization

Evidence: `RegistryRepository.kt:20` serializes update/reorder/import read-modify-write,
but remove (77) and deleteStale (71–73) do not enter the mutex. A reorder or import
can hold an existing entity, deletion can complete, then the stale upsert resurrects it.

Minimal fix: include both delete operations in the existing mutation boundary. No
database/interface/schema changes required. Do not introduce UnitOfWork machinery.
Regression: gate reorder between read and upsert, issue remove or stale cleanup, release,
assert the later deletion wins and unrelated metadata survives.
Files: RegistryRepository and RegistryRepositoryTest (2 files, small).

### P2 — Registry operations perform one read per scene

Evidence: reorder (57–58) performs N `byName` reads plus one bulk upsert; importMerge
(102–113) performs N reads and N writes; bulk assignment (85–90) first reads all
entities then rereads/writes each missing name.

Minimal fix: use a single snapshot indexed by name and existing `upsertAll` for complete
operation. Preserve duplicate import semantics (last duplicate metadata merges in order;
first missing occurrence increments inserted, later duplicate updated), ordering and
nullable-field merge rules. Existing mutex remains serialization boundary; delete fix
must precede bulk snapshot changes. Avoid a new DAO query when observeAll.first already
provides the required snapshot.
Regression: counting/gated DAO proves one snapshot read, zero per-name reads, one bulk
write; imports with duplicate names, null metadata and existing order match current
behavior. Large fixture verifies exact call counts, without flaky wall-time assertions.
Files: RegistryRepository and RegistryRepositoryTest (2 files, medium).

### P2 — Deck and graph metadata joins are quadratic

Evidence: `ObsStateRepository.kt:100` scans all registry entries for every scene on every
program/preview update. `SceneGraphBuilder.kt:37` scans all entries on every edge/node
role lookup. For N scenes and M entries, deck metadata lookup is O(N*M); edges add
further O(E*M) lookups.

Minimal fix: index registry entries once per reduction/build using associateBy, preserving
the default Primary role and deterministic curation ordering. Consider a separate indexed
registry flow later only if recomputing an index becomes measurable; initial map is enough.
Regression: existing metadata/default/order tests plus representative large collection;
verify lookup counts via a small extracted mapping helper only if needed, not timing.
Files: two production files plus existing tests (≤4 files, small).

## Other evidenced issues for a separate slice

- Mixer event collector awaits full audio discovery (`MixerRepository.kt:130–141`),
  blocking subsequent mute/volume events during sequential network probing. One discovery
  trigger per event can cause a redundant burst and stale temporary publications. A
  conflated refresh trigger separate from direct event reducers is justified, but needs
  cancellation/generation tests and careful publication semantics; do not slip this
  wider concurrency change into the mute fix.
- Widget updates (`app/.../SceneDeckWidgetUpdater.kt:38`) recompute/write/recompose every
  deck emission even when the six names/program/connection label are identical. Project
  to snapshot + distinctUntilChanged while keeping poke() unconditional. Empty connected
  PRIMARY lists are also incorrectly treated as offline at line 65 and preserve removed
  scene actions; only disconnected/unready state should preserve the cached grid.
- Stats `nowMs` and screenshot timestamps use wall clock, so time correction can suppress
  thumbnails or skew bitrate. Monotonic clocks are appropriate for elapsed intervals.
  Stats issues are lower priority than bounded work and control correctness.
- Foreground service, timer/history capacity120, atomic DataStore update/lock operations,
  coroutineResult cancellation propagation and widget count6 bounds already follow
  repository contracts; retain them.

## Architectural assessment and pattern fit

Dependency direction is sound for reviewed modules: features consume repositories,
Room/DataStore stay behind data access, only core OBS imports protocol clients. Hilt
process singleton ownership and Flow Observer style suit one OBS session and rotation.
The injected ObsClient seam is justified by many independent fakes; no extra interfaces
per repository, Command classes for immediately executed calls, Visitor hierarchy or
generic UnitOfWork is warranted. Data classes/copy reducers are idiomatic Prototype
style and preserve immutable state; the connection model is a state machine, not
automatically the GoF State pattern. GRASP information ownership favors the current
repository boundaries; critical fixes are queue/mutation ownership, not class count.

SOLID concerns worth fixing are behavioral (newer state must win, cancellation must
propagate, performance budgets must truly bound allocation), with minimal indirection.

## Peer review of OBS request deadline proposal

Reviewed `KtobsObsClient.kt:159–168` and `ObsSessionRunner`. Agree that requests need a
deadline including semaphore admission. Capture the active session before queueing,
check identity after admission and before returning, and fail stale work rather than
execute it on a replacement session. Map only the request's own deadline to a typed
recoverable error; preserve caller cancellation. `withTimeoutOrNull` around a non-null
result wrapper avoids swallowing outer timeout/cancellation. Verify upstream pending
request registration removes cancelled waiters. Test eight dropped responses, ninth
queued expiration, later successful request, parent cancellation, queued command after
session replacement and late old-session response.

## Verification contract

After approved implementation: focused data regression tests, full assembleDebug,
testDebugUnitTest and scripts/check-quality.sh (one build owner). No screen visuals
are intentionally changed by these proposed data fixes; existing Roborazzi/preview
coverage remains required through the repository gate. Report actual results and
remaining risks, not blanket claims of perfect architecture or measured jank gains.
