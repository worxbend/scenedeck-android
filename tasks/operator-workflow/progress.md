# Progress and handoff

## Current checkpoint

- Branch: `feat/operator-workflow`.
- Active slice: **W1c — named decks** (next implementation).
- Next action: read `decks-design.md`, add scoped deck/member/selection persistence
  and lossless Room migration, then wire CRUD, membership/order, selection and transfer.
  W1a and W1b are verified. W1c/W1d and all later milestones remain incomplete.
- Full feature delivery: **in progress**. Workspace isolation and explicit previous
  curation recovery are implemented and verified locally; no release was published.
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

## W1b — Activation worktree (in progress)

- Identity foundation committed as `1fec41c`; the preceding goal turn was **progress**:
  final-source gates passed and the checkpoint was committed with a clean worktree.
- RegistryRepository now uses WorkspaceSceneDao and requires an explicit
  WorkspaceSession for every read/mutation. A bound store guards each DAO call before
  and after suspension, retains one mutation mutex, and attaches the captured namespace
  to every write. Loaded-empty, loading, failed, and unavailable metadata are distinct.
- Added WorkspaceContext as the injectable read-only identity boundary. Production
  Hilt binds the existing WorkspaceRepository; legacy curation is read only for recovery.
- ObsStateRepository now uses one scoped scene snapshot, keeps rename logs per scope,
  rejects stale snapshots/events, and combines metadata only from the same session.
  Scene commands use explicit origins. `ObsClient.withSession` carries the caller's
  token into the protocol admission lane, preventing a coroutine delayed before its
  first request from addressing a replacement session.
- Live and Inventory callbacks capture the displayed workspace; sheet state is keyed
  by workspace. Inventory field edits now read/modify inside the registry mutex.
  Imports capture before opening the picker and retain destination through preview;
  Undo carries its removal origin. "Copy previous curation" stages an explicit merge
  preview and keeps the legacy source untouched.
- Graph subscribes to stamped events before any initial read, binds reads to origin,
  and discards stale results. Doctor binds complete scans and stale removals to origin.
  Their unknown/loading states no longer imply an empty collection or All clear.
- Thumbnail frames now carry workspace identity; captures bind their request token,
  reset cache/throttle state at replacement, and Live filters frames by displayed origin.
- Widget cache/actions persist profile + collection, treat unscoped old cache as
  unactionable, and verify the intended collection after cold connection. They never
  select another collection just to satisfy a cached action. Deep links resolve the
  current workspace before switching.
- Added a shared inline WorkspaceStatus component. The Material 3 Expressive skill
  was consulted. Changed screen renders and accessibility checks are still pending.
- `assembleDebug` **passed** for the initial activation production sources (522 tasks):
  `/tmp/scenedeck-workspace-activation-app-compile.log`. A missing Graph → core:common
  dependency was corrected. Subsequent test migration/scene-state guard edits require
  a fresh final build; this is not full W1b verification.
- Core test migration started: RegistryRepositoryTest uses the real scoped Room DAO;
  single-workspace scene/screenshot fixtures have an explicit test-only DAO adapter.
  Additional actual-DAO isolation, queued/stale mutation and recovery tests are still
  required. Current targeted run: `/tmp/scenedeck-workspace-activation-data-tests.log`.
- Outstanding: Mixer discovery/pinned/selected state, meter/media event origins and
  deferred controls; feature/app test constructor and callback migration; new adversarial
  scope tests; failure/cancellation/privacy review; quality/build/tests and renders.
  No W1b checkbox is complete, and named decks plus all later features remain in scope.

## W1b — Mixer and activation verification continuation

- This goal turn is **progress**: migrated Mixer and all remaining feature/app test
  APIs, added regression and rendering evidence, and fixed issues exposed by checks.
  The preceding activation worktree is preserved; no additional feature scope was added.
- Mixer now tags discovery/media snapshots and controls with WorkspaceSession, clears
  selected/pinned lists and meters on replacement, preserves event tokens through
  client-side meter sampling, and binds fader/extras debounce targets before delay.
  Saved selected-scene preferences include profile + collection; legacy unscoped
  choices are not automatically restored. Mode/grouping/locks/allow-list stay app-wide.
- Added AGP native test fixtures in core:data (existing pinned dependencies only),
  sharing the explicit single-workspace adapter and mutable context with feature
  tests. Real multi-workspace registry tests continue using the actual Room DAO.
- Graph's initial workspace watcher is also undispatched: a deterministic test exposed
  it clearing a selection made just after construction. The stamped-event subscription
  still precedes initial graph reads.
- Widget targets are tested against wrong profile/collection and cold starts, and
  legacy unscoped cached actions are rejected. The updater rechecks current identity
  inside each preference transaction after widget discovery can suspend.
- Profile deletion disconnects only its matching current target, with an atomic guard
  against a newer different target. VCam captures the output session before scheduling
  its coroutine. These close two final review gaps.
- Added regression coverage for stale import confirmation; stale Mixer actions/events
  and old write failures; same-named cached frames after reconnect with failed captures;
  and a late scene-list reply/scene action after reconnect. Scoped Mixer selection
  migration remains covered by the existing rename regression with explicit identity.
- Extracted bulk registry operations and screen feedback to keep complexity checks
  satisfied without blanket baselines. Documentation and protocol guidance now describe
  actual worktree activation behavior; W1b is not yet checked complete.
- Focused data/OBS tests passed before Mixer migration:
  `/tmp/scenedeck-workspace-activation-unit.log`. Data+Mixer tests subsequently passed:
  `/tmp/scenedeck-workspace-mixer-tests.log`. The complete app build and unit tests
  passed in `/tmp/scenedeck-workspace-activation-validation.log`, but the combined
  command failed one LiveDeckContent complexity check. Later source changes require
  the final gate, so these are intermediate results, not final-source certification.
- Eight new goldens render workspace loading/failure and recovery confirmation in
  SceneDeck/OBS, light/dark. Recorded final text variants successfully (12 s):
  `/tmp/scenedeck-workspace-renders-final.log`. Inspected initial dark/light captures;
  final copy changes still need visual review. Existing screen goldens passed in the
  broader unit suite. No real device/OBS or measured-performance claim is made.
- A quality attempt overlapped further source cleanup and failed formatting plus
  complexity; it is not a pass (`/tmp/scenedeck-workspace-activation-quality.log`).
  Source was then formatted and frozen. Current final gate is session **15450**:
  `/tmp/scenedeck-workspace-activation-quality-final.log` (JDK 21 + uv).
  Resume by checking that process/log before launching another Gradle run. Then run
  explicit assembleDebug/testDebugUnitTest, inspect final captures, and record outcomes.
- Still required before W1b completion/commit: final gates, final diff review and any
  fixes they expose. W1c named decks and the broader W1d integration checklist remain
  incomplete, along with every later authorized feature. Show rundown stays excluded.

## W1b — Verified activation checkpoint

- W1b is complete. Identity, registry/scene/Mixer/diagnostic consumers, imports/Undo,
  thumbnails, widget targets and matching-profile deletion now use explicit context.
  Named decks are not implemented by this checkpoint.
- The first final-source gate caught only Inventory's Compose ModifierParameter order;
  corrected the signature without suppressing lint. The earlier failed log is retained
  as `/tmp/scenedeck-workspace-activation-quality-final.log`.
- **Full quality gate passed**, exit 0: JDK 21/uv, 926 Gradle tasks, formatting,
  complexity, lint, build/tests, seven Semgrep rules on 244 files with zero findings,
  clean Gitleaks history and working-tree scans. Evidence:
  `/tmp/scenedeck-workspace-activation-quality-verified.log`.
- **Explicit assembleDebug + testDebugUnitTest passed**, exit 0, 726 tasks:
  `/tmp/scenedeck-workspace-activation-explicit.log`. Aggregate XML results (including
  up-to-date suites): **622 tests / 110 suites, zero failures/errors/skips**.
- Eight new goldens cover workspace loading/failure and previous-curation confirmation
  in both SceneDeck and OBS light/dark. Final captures inspected: destination/counts,
  Copy/Cancel, readable theme colors and unchanged surrounding controls. Existing
  screen goldens also passed. No real-OBS/device or measured-performance claim is made.
- Current-checkpoint text and W1b checkbox updated. Historical in-progress entries
  above describe intermediate states, not remaining work. The durable goal stays active
  for W1c–W11; Show rundown remains excluded.

## Decisions / limitations

- Include the five additional improvements from the recommendation, alongside the
  six retained main features. No need to request the same scope approval again.
- Presets must work independently of rundown; tablet UI must not introduce cues.
- Keep schemas and tests together; foundation-only work is not workspace completion.
- No release, remote publication, or store upload has been performed.
