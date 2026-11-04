# Progress and handoff

## Current checkpoint

- Branch: `feat/operator-workflow`.
- Active slice: **W1b — authoritative workspace integration**.
- Next action: bind the registry and all consumers to the verified workspace context
  using `workspace-design.md`. W1a and the W1b identity foundation are verified.
- Full feature delivery: **in progress**. No new user-facing feature is marked shipped.
- Blocking product questions: none. Show rundown is expressly excluded.

## 2026-11-05 — Workflow started

- Created durable conversation goal and local resumption/plan/progress files.
- Preserved the completed performance/security review in existing task files.
- Reviewed project contracts and all three project skills.
- Verified clean starting worktree on `main`; created the implementation branch.
- Evidence: `SceneRegistryEntity.sceneName` is the global primary key; registry
  consumers share one unscoped DAO. Migration must retain existing metadata.
- Evidence: media status contains duration/cursor and MixerRepository exposes seek,
  while UI only presents play/pause/restart. Recording pause/resume is not exposed.
- Environment: uv is installed. System Java is 25; JDK 21 exists at the plan's path.
- Fresh baseline: online `assembleDebug testDebugUnitTest` spent five minutes resolving
  dependencies and was cancelled before compilation. Offline retry failed because
  `kotlinx-serialization-core-jvm:1.7.3` is absent from cache. Logs:
  `/tmp/scenedeck-operator-baseline.log` and
  `/tmp/scenedeck-operator-baseline-offline.log`. This is not a passing baseline.
- Direct Maven Central HEAD for the missing jar returned HTTP 200. Targeted checks
  are retrying with IPv4 and bounded network timeouts, without configuration cache.

## W1a — Persistence implemented and quality gate passed

- Room version 3 adds `connectionProfileId` and `sceneCollectionName` to the registry
  primary key. Legacy rows retain scope `(0, "")` and all original fields.
- Legacy DAO reads/deletions are limited to legacy scope. A new WorkspaceSceneDao
  makes scope explicit for reads, cleanup, and transactional rename.
- Migrations now live in core/database and are registered by the production builder.
- Added migration tests based on the committed v1/v2 schemas plus same-name isolation,
  scoped cleanup/rename, conflict preservation, and legacy-operation isolation tests.
- App workspace activation and named decks are not implemented yet (W1b/W1c).
- Targeted verification running: `/tmp/scenedeck-workspace-tests.log`.
- Room generated schema 3 and compiled production/test sources successfully.
- First targeted result: four storage-isolation tests passed; two migration tests
  could not load the historical JSON fixtures. Corrected resource wiring through
  `variant.hostTests` / `sources.resources` after inspecting the pinned AGP API.
  The earlier source-set and Test-classpath approaches were not retained.
- Formatting passed (`/tmp/scenedeck-workspace-format.log`). The complete
  `:core:data:testDebugUnitTest` suite now passes, including both historical migration
  tests and all four isolation tests (`/tmp/scenedeck-workspace-data-tests.log`).
  No dependency pins changed.
- W1b follow-up evidence: widgets currently carry only a scene name and reconnect to
  the last-used profile, so their cached actions must also be bound to a workspace.
  Staged Inventory imports and Undo need origin scope, not the scope at confirmation.
- Full JDK 21/uv quality gate **passed**, exit 0:
  `/tmp/scenedeck-workspace-quality.log`. Gradle qualityCheck passed (909 tasks);
  Semgrep ran seven rules on 228 tracked files with zero findings; Gitleaks history
  and working-tree scans both reported no leaks.
- Current aggregate unit-test outputs: **582 tests / 106 suites**, zero
  failures/errors/skips (includes up-to-date suites reused by Gradle).
- Explicit `assembleDebug testDebugUnitTest --no-configuration-cache` confirmation
  **passed**, exit 0 (717 tasks, 715 up-to-date):
  `/tmp/scenedeck-workspace-explicit-verification.log`.
- No Compose screens changed in W1a. Existing preview tests pass through the full
  suite; no real-OBS or physical-device performance claim is made for this slice.
- Complete data suite: **181 tests / 25 suites, zero failures/errors/skips**.
- W1b design/evidence and legacy recovery decision recorded in
  [workspace-design.md](workspace-design.md). Legacy ownership cannot be inferred,
  so the integration will offer explicit scoped recovery rather than guessing a host.
- Durable plan committed as `fbd9fa8` (`docs: start durable operator feature workflow`).

## W1b — Session and context foundation

- W1a committed as `548bc6c` (`feat: prepare scoped scene registry persistence`).
- Ready now identifies each client session and collection revision. Production
  context event/meter streams stamp the origin before buffering; legacy untagged
  streams remain while consumers migrate. Meter publication pauses during collection
  changes. New requests reject Changing; queued/in-flight results reject revision
  replacement. A dispatched deliberate collection switch can finish through its
  own events, but still rejects a changed origin before dispatch. No write retry added.
- Collection changes during the initial GetVersion are retained in the first Ready
  state. All receiver/version/request bookkeeping remains on the serial protocol lane;
  admission deadlines and eight suspended requests remain intact.
- Checked official OBS generated protocol and pinned ktobs 0.5.0 event types before
  exposing CurrentSceneCollectionChanging. No dependency versions changed.
- Added ActiveConnection to ObsSessionHolder: synchronous invalidation before any
  preparation suspension, matching-attempt confirmation, and generation-guarded
  failure/cancellation cleanup, including cancellation before async execution.
- Added WorkspaceRepository for confirmed profile + discovered collection identity.
  It exposes unknown/resolving/failed states, coalesces explicit retry, retains the
  target across reconnect, and guards stale discovery/results synchronously.
- Added eight deterministic workspace tests and six fake-WebSocket protocol tests;
  expanded Holder tests for invalidation/cancellation and immediate reconnect after
  initial connection success. Existing onboarding test construction updated for DI.
- Targeted OBS/data tests, ktfmtFormat and detekt passed before the final immediate-
  reconnect refinement: `/tmp/scenedeck-workspace-identity-checks.log`. Initial
  complexity findings were fixed with small helpers and event-publisher ownership;
  no baselines or suppressions added. Onboarding/data also passed in
  `/tmp/scenedeck-workspace-context.log`.
- Initial full quality run found a Graph refresh timeout. Investigation reproduced
  an existing subscription race deterministically: `onStart` triggered graph discovery
  before registering the SharedFlow subscription, losing events emitted during the
  read. New regression test failed against the prior code. Replaced with
  `onSubscription` before filtering, retaining the event while initial discovery runs.
  Reproduction: `/tmp/scenedeck-graph-subscription-reproduction.log`. The documented
  coroutine operator semantics were checked in the official Kotlin API reference.
- Final-source verification will cover the immediate-reconnect refinement, meter
  context test, queued collection-switch rejection, and Graph fix. Initial full-gate
  log: `/tmp/scenedeck-workspace-identity-quality.log`.
- The next full gate passed: 909 Gradle tasks; Semgrep seven rules / 232 files / zero
  findings; Gitleaks history/working-tree scans clean. Explicit assembleDebug and
  testDebugUnitTest also passed (717 tasks). Aggregate at that point: 599 tests in
  108 suites, zero failures/errors/skips. Logs:
  `/tmp/scenedeck-workspace-identity-final-quality.log` and
  `/tmp/scenedeck-workspace-identity-explicit-verification.log`.
- Final review then reproduced a late canceled credential failure poisoning the
  replacement connection's outcome. A non-cancellable fake credential provider made
  the failure deterministic. Holder now clears obsolete failures after joining the
  previous preparation, before executing the new command. Red-test evidence:
  `/tmp/scenedeck-workspace-superseded-failure-reproduction.log`. Verification is being
  repeated for this final source change; the preceding pass does not cover it.
- Previous goal turn classification: **progress** (implemented identity and guarded
  connection preparation, added deterministic regressions, corrected Graph startup).
  On resume the final quality process handle was missing, its log ended mid-build,
  and no Gradle/quality process remained. That interrupted run is not a pass.
  Restarted the final-source gate in `/tmp/scenedeck-workspace-identity-resumed-quality.log`;
  its Gradle qualityCheck has passed (909 tasks); security scans are finishing.
- Final-source verification **passed**, both processes exited 0. The resumed full
  quality gate includes 909 Gradle tasks, seven Semgrep rules on 232 tracked files
  with zero findings, and clean Gitleaks history/working-tree scans. Explicit
  `assembleDebug testDebugUnitTest --no-configuration-cache` also passed (717 tasks):
  `/tmp/scenedeck-workspace-identity-resumed-explicit.log`.
- Final aggregate: **600 tests / 108 suites, zero failures/errors/skips** (including
  Gradle up-to-date results). Data: 192 tests; OBS: 60 tests; Graph: 8 tests. No Compose
  screen changed; existing rendering tests ran through the suite. No real-OBS/device
  or measured-performance claim is made.
- Still incomplete: scoped registry activation, stamped event consumers, UI/cache/
  widget scoping, origin-bound imports/Undo, legacy recovery, and named decks. The
  app intentionally still reads the legacy registry until those consumers migrate.
  This foundation does not check off W1b or ship a workspace user flow.

## Decisions / limitations

- Include the five additional improvements from the recommendation, alongside the
  six retained main features. No need to request the same scope approval again.
- Presets must work independently of rundown; tablet UI must not introduce cues.
- Keep schemas and tests together; foundation-only work is not workspace completion.
- No release, remote publication, or store upload has been performed.
