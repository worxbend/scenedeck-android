# Progress and handoff

## Current checkpoint

- Branch: `feat/operator-workflow`.
- Active slice: **W2 — local aliases and preview freshness**.
- Design/contracts: `aliases-preview-design.md`; alias persistence is being implemented.
- W1a–W1d are implemented and verified. Named decks are ready locally; all W2–W11
  milestones remain incomplete. Start with the first unchecked item in `plan.md`.
- Full feature delivery: **in progress**. This checkpoint completes workspace isolation
  and named decks, not the entire goal. No release or remote publication was performed.
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

## W1c — Named decks in progress

- W1b is committed as `af3d2fa`; this continuation is **progress**, not full-goal
  completion. W1c and all later checkpoints remain incomplete.
- Added an uncommitted Room 4 migration for scoped named decks, members and selection.
  Each deck has independent dense membership/order; profile deletion preserves it.
  Composite joins include profile and collection. Built-in selection defaults to All.
- NamedDeckRepository uses guarded Room transactions; OBS renames update deck members
  in the same scoped transaction as curation. An editor carries its original deck
  snapshot so concurrent edits/renames can reject stale saves.
- Schema and data compilation passed. The first focused test attempt exposed a test
  DAO adapter missing new methods and a SQL line-length violation; both corrected.
  Focused tests are running in `/tmp/scenedeck-named-decks-tests.log` (not a pass yet).
- Still required: production Scenes selector/manager, versioned backup/import with
  preview, UI/migration/concurrency regressions, docs and complete gates/renders.

## W1c — Integrated behavior and verification in progress

- Implemented Scenes selection and expanded Manage decks sheet: create/rename/delete,
  editable membership/order, unavailable references, persisted workspace selection,
  named-only drag reorder and unchanged All/Primary/canonical/widget projection.
  Deferred drag saves retain the original target; old editors reject renamed members.
- Version-1 deck JSON has bounded input, destination preview, per-deck New/Replace
  labels, atomic same-name replacement, stable destination IDs and retained selection.
  File pickers capture origin; Android API 28 bounded reads are covered. Heavy parsing
  and encoding run off the UI thread. No OBS scene-collection writes were introduced.
- Product/architecture/design/agent/privacy docs now describe the implemented worktree.
  Transfer limits and conflict rules are recorded in `decks-design.md`.
- Focused data + live tests/static checks passed before the last regression additions:
  `/tmp/scenedeck-named-decks-renders.log`, 139 tasks, and
  `/tmp/scenedeck-named-decks-renders-final.log`, 134 tasks. Twelve new manager goldens
  cover list/member/import pages in SceneDeck and OBS light/dark, with existing Live
  goldens updated. Reviewed dark/light membership, main compact selector and import
  previews; no clipping in those phone captures. No device/real-OBS claim is made.
- Added ViewModel duplicate-save/failed-draft/context tests, API 28 file-picker tests,
  real-file database reopen, and portable/stale import plus rename regressions.
  The expanded focused run failed before the new ViewModel tests executed because
  their implicit Robolectric SDK attempted inaccessible JDK FileDescriptor internals.
  Pinned those tests to SDK 34, matching existing screen tests; final gate is rerunning.
  The failed evidence is `/tmp/scenedeck-named-decks-final-focused.log`.
- Current full gate is `/tmp/scenedeck-named-decks-quality.log` (session 53153). Source
  is frozen for verification. Next: inspect result, explicit build/tests, update W1c/W1d
  status only with actual evidence and commit. All W2–W11 features remain incomplete.

## W1c/W1d — Verified named-deck checkpoint

- **W1c and W1d are complete.** This goal turn is **progress** toward the full authorized
  scope; W2–W11 remain open. Show rundown/cues/timed advancement remain excluded.
- The first full gate built the app and passed tests but failed one ComposableNaming
  lint rule on the new menu action. Renamed the component to `Action`, without a lint
  suppression. That intermediate log remains `/tmp/scenedeck-named-decks-quality.log`.
- **Full JDK 21/uv quality gate passed**, exit 0: 926 Gradle tasks, formatting,
  complexity, Android lint, build/tests, seven Semgrep rules on 256 source files with
  zero findings, and clean Gitleaks history/working-tree scans. Final evidence:
  `/tmp/scenedeck-named-decks-quality-verified.log`.
- **Explicit assembleDebug + testDebugUnitTest passed**, exit 0, 726 tasks, 8 s:
  `/tmp/scenedeck-named-decks-explicit.log`. Aggregate XML: **659 tests / 116 suites,
  zero failures/errors/skips** (37 tests added over W1b).
- W1d evidence includes actual Room migration from versions 1/2/3, exact-scope joins
  across repeated deck IDs/profile/collection names, independent membership/curation,
  OBS rename conflicts, origin replacement before/during transactions and file pickers,
  stale edits/previews, duplicate saves, failed drafts, and database close/reopen.
  The complete suite also rechecks W1b bulk/Undo/widget/reconnect behavior.
- Twelve new manager goldens cover listing, membership and import in SceneDeck/OBS
  light/dark. Existing Live/Studio/output goldens were updated for the compact selector
  and search icon. Final main, light/dark member and replacement-preview captures
  inspected; final-source golden verification passed in the quality gate. Android API 28
  import is covered by Robolectric; no physical-device, real-OBS or performance claim.
- The original failed/intermediate logs above are historical. No remaining W1c defect
  or product decision blocks the next slice. Continue W2 from the current branch.

## W2 — Implementation started

- Previous goal turn was **progress**, committed as `9e987ed`; authoritative worktree
  was clean before this slice. W1 remains verified; W2–W11 remain incomplete.
- Added planned behavior and preservation rules in `aliases-preview-design.md`.
- Started Room 5 optional alias storage, lossless migration, domain/transfer mapping,
  label-only registry edits and card projection. This is unverified foundation, not
  a finished feature. Next: compile schema, migration/alias tests and actual editor/UI.
- Preview inspection found valid dark images discarded by the existing poller despite
  its documented behavior. W2 will accept them and timestamp successful captures.

## W2 — Integrated behavior; verification in progress

- Added both local-label editors, shared field, alias/raw identity on cards/Inventory/
  deck members, search by either name, and unchanged-role preservation. Failed saves
  retain drafts; the registry mutation checks current scene identity so renamed-scene
  editors cannot recreate an obsolete entry. Room 5 and optional version-1 transfer
  fields preserve earlier curation/decks. Matching product/design/privacy/agent docs
  now describe the implementation, but W2 checkboxes remain open until the full gate.
- Frames now carry monotonic successful-receipt/decode timestamps, with failed captures
  tracked separately. Captions show fresh age, stale (20s or failure), pending and
  unavailable; disconnected Scenes explains its cleared previews. Valid black frames
  replace prior images. A screen-only age clock leaves capture intervals/bounds intact.
- Focused data tests exposed a historical schema-fixture assumption that every entity
  has indices; corrected optional index loading. Core data tests subsequently passed.
  Initial UI/static runs also exposed complexity/long-method issues, now refactored.
  Those intermediate failures remain in `/tmp/scenedeck-alias-preview-focused.log`
  and `/tmp/scenedeck-alias-preview-renders.log`; they are not passing evidence.
- Inventory dialog tests encountered the upstream Robolectric dialog/text-field layout
  loop with explicit window-width qualifiers (robolectric/robolectric#8460). Freezing
  the clock and filling the layout did not solve it; those experiments were removed.
  The actual dialog and failed-save assertions now pass at Robolectric's default 320dp
  width, keeping the height/density qualifier. Evidence: **exit 0, 8s, 131 tasks** in
  `/tmp/scenedeck-alias-dialog-default-width.log`; four theme captures generated, with
  SceneDeck light and OBS dark inspected for visible identity/counter/Save/Cancel.
  This is JVM render evidence, not device evidence.
- Current focused data + Live/Inventory recording + detekt run:
  `/tmp/scenedeck-alias-preview-renders-final.log`. Next: inspect result/screens,
  resolve any failures, run the full JDK 21/uv gate and explicit build/tests, then
  checkpoint W2 and continue W3. The full goal remains active; no rundown work.

## W2 — Verified local labels and preview freshness

- **W2a–c are complete.** This goal turn is **progress** toward the authorized scope;
  W3–W11 remain open. Show rundown/cue lists/timed advancement remain excluded.
- Focused final data + Live/Inventory renders + detekt passed: exit 0, 41s, 190 tasks
  in `/tmp/scenedeck-alias-preview-renders-verified.log`. The prior final-named log
  failed one selector that matched both search input and member text; the assertion
  now targets the member's raw identity, verifies its alias and checks raw-key removal.
- **Full JDK 21/uv quality gate passed**, exit 0: 926 tasks in 2m 8s, formatting,
  complexity, Android lint, debug build/unit tests, seven Semgrep rules with zero
  findings and clean Gitleaks history/working-tree scans. Evidence:
  `/tmp/scenedeck-alias-preview-quality.log`.
- The staged additions also passed the seven-rule Semgrep scan (262 source files,
  zero findings), `/tmp/scenedeck-alias-preview-staged-security.log`.
- **Explicit assembleDebug + testDebugUnitTest passed**, exit 0, 726 tasks, 1s:
  `/tmp/scenedeck-alias-preview-explicit.log`. Aggregate XML: **688 tests / 123 suites,
  zero failures/errors/skips** (29 tests added over W1).
- Coverage includes migrations 1–4 to schema 5, alias round-trip/legacy merge, invalid
  labels, rename conflict/Undo/workspace rejection, raw OBS command targets, original
  unassigned/Archive role retention, failed-save drafts, dual-name search/member edits,
  preview age boundary, failure timestamp retention, valid black captures, bounded
  polling and existing reconnect/context invalidation regressions.
- Sixteen new label-editor/freshness/disconnected theme goldens and an OBS-light
  Inventory capture were generated. Existing Inventory/member/main-deck goldens were
  refreshed and verified by the full gate. Inspected light/dark editors, light aliased
  Inventory and member rows, preview statuses and actual disconnected placeholder.
  Dialog fixture width limitation is documented above. No real-OBS, physical-device
  or measured-performance claim is made.
- Matching FEATURE_SPEC/ARCHITECTURE/DESIGN_SYSTEM/ROADMAP/privacy/AGENTS are updated.
  Next: W3 scoped preflight configuration, bounded independent checks and More entry.
  Keep the full workflow active; no release or publication is authorized here.

## W3 — Preflight implementation in progress

- Previous goal turn was **progress**: W2 committed as `de5acd3`, worktree clean.
  W3–W11 remain unchecked. Scope/decisions are in `preflight-design.md`.
- Added Room 6 scoped configuration, guarded original-value transactional saves,
  manual completion and label-change reset, bounded validation and unknown corrupt-data
  handling. Migration preserves existing aliases/decks. Required scene references stay raw.
- Added finite parallel read checks with independent Pass/Fail/Unknown results, optional
  four-second context-stamped signal observation and a wall-clock report timestamp.
  Read failures never expose exception details. Context/cancel/setup changes clear results.
- Added `:feature:preflight`, setup sheet, checklist and report under More. Start-output
  confirmations offer Open Preflight and dismiss pending consent before navigating;
  output safety remains optional/configurable. No OBS writes or new polling were added.
- Data compilation passed (`/tmp/scenedeck-preflight-foundation.log`, 51s, 35 tasks).
  After resolving complexity/line-length findings without suppressions, core data tests,
  feature compilation and detekt passed (`/tmp/scenedeck-preflight-focused.log`, 18s,
  95 tasks). New tests cover persistence/reopen/scoping/stale saves, migration from 5,
  partial failures, read timeout, cancellation, replacement and signal/unknown distinction.
- First render attempt stopped at an invalid Compose assertion import in the new output
  confirmation test; removed it (the assertion is a member). Added ViewModel tests for
  duplicate runs, clearing old successes, cancellation and config/context invalidation.
  Rendering, final tests, docs and the full gate are still required. Do not mark W3 done.

- Subsequent ViewModel/Compose tests and detekt passed (27s, 185 tasks,
  `/tmp/scenedeck-preflight-renders-verified.log`). The test-store constructor was
  corrected to its existing `forTesting` factory. No production API was loosened.
- Eight new report/setup goldens cover SceneDeck/OBS light/dark. Inspected both surfaces
  in light/dark, including readable unknown/failure states, raw scene identity and form
  actions. An actual expanded-sheet test verifies that Save remains visible while the
  manual editor scrolls. Final report captures use a distinct shared checklist icon and
  fixed test locale/timezone, avoiding machine-dependent timestamp golden differences.
  Final render evidence: exit 0, 26s, 131 tasks,
  `/tmp/scenedeck-preflight-renders-release-check.log` (no release performed).
- Matching product/architecture/design/privacy/AGENTS and the module-map skill are
  updated. Staged source is frozen for the full gate currently running in
  `/tmp/scenedeck-preflight-quality.log`. It also includes a new real-Room regression
  that invalidates workspace identity during SQL insertion and requires rollback.
  Next: inspect full-gate result, explicit build/tests, final audit and W3 checkpoint.

## W3 — Verified Preflight

- **W3a–d are complete.** This goal turn is **progress**; W4–W11 remain open.
  Show rundown/cue lists/timed advancement remain excluded.
- Full JDK 21/uv quality gate passed, exit 0: **988 tasks in 2m 44s**, including
  formatting, complexity, Android lint, debug build/unit tests, seven Semgrep rules
  across 273 source files with zero findings, and clean Gitleaks history/tree scans.
  Evidence: `/tmp/scenedeck-preflight-quality.log`.
- Explicit `assembleDebug testDebugUnitTest` passed: **776 tasks in 6s**,
  `/tmp/scenedeck-preflight-explicit.log`. The resumed process had already exited;
  the terminal log records BUILD SUCCESSFUL. Aggregate XML: **714 tests / 129 suites,
  zero failures, errors or skips**.
- The real-Room regression confirms configuration insertion rolls back if workspace
  identity changes during the write. Eight report/setup light/dark goldens and the
  expanded-sheet/failed-draft/output-confirmation tests passed. No device, real-OBS
  or measured-performance claim is made.
- Product, architecture, design, privacy, roadmap, AGENTS and module-map guidance
  match the implementation. Next: W4 shared-telemetry freshness/identity audit and
  sustained, contextual live alerts. The full workflow remains active.

## W4 — Telemetry identity/freshness foundation in progress

- Previous checkpoint W3 committed as `1f8ee30`; full scope stays active. This turn is
  **progress**, not blocked. W4a–d remain unchecked; no live alert UI is delivered yet.
  `live-alerts-design.md` records the alert plan and the distinction from this foundation.
- Audited shared StatsRepository: it previously accepted any Ready session after reads
  and consumed untagged output events, with no per-reading age. Added origin-bound reads,
  context-event guards, reset on collection replacement, and independent receipt times.
  Events invalidate counter freshness; failed readings have no timestamp. Output polling
  continues even if local collection discovery fails, preserving independent controls.
- `MonotonicClock` is injected via Hilt and uses elapsed realtime including device sleep.
  Tests use virtual time (or JVM monotonic time for the existing real-time Stats fixture).
  No new OBS poller, request type, dependency/version or database migration is involved.
- Initial data/Stats unit+golden/static verification passed: 27s, 132 tasks,
  `/tmp/scenedeck-alert-foundation-focused.log`. The injectable sleep-aware clock and
  additional late-request assertion were added afterward; final verification is pending.
- Five new deterministic regressions cover independently delayed reads, canceled late
  responses after a replacement session, collection transitions, old buffered events,
  and failed/disconnected receipt times. Next: final gate, explicit build/tests and a
  foundation checkpoint, then implement the sustained alert reducer and UI.

## W4 — Verified telemetry foundation

- This goal turn remains **progress**. W4a–d and W5–W11 are still open; this checkpoint
  fixes telemetry ownership/freshness before warning implementation. No alert UI,
  microphone monitoring, release or real-device behavior is claimed.
- First full gate stopped at an unresolved Dagger import in the new clock binding
  (`/tmp/scenedeck-alert-foundation-quality.log`, 8s). Moved the provider to the app's
  Hilt layer; core data retains only the injectable interface. No dependency was added.
- Final full JDK 21/uv quality gate passed, **exit 0, 988 tasks, 2m 59s**:
  `/tmp/scenedeck-alert-foundation-quality-final.log`. Formatting/complexity/lint,
  debug build and unit/Roborazzi verification passed; seven Semgrep rules across
  276 source files found nothing; Gitleaks history (65 commits) and tree scans were clean.
- Explicit `assembleDebug testDebugUnitTest` passed, **exit 0, 776 tasks, 2s**:
  `/tmp/scenedeck-alert-foundation-explicit.log`. Aggregate XML: **719 tests / 130 suites,
  zero failures/errors/skips**. All five new telemetry regressions passed, including
  rejection of further requests from an old canceled batch. No screen UI was changed;
  existing rendered goldens were verified without changing reference images.
- Updated FEATURE_SPEC, ARCHITECTURE, ROADMAP, AGENTS and the OBS project skill for
  the actual telemetry contract. Scope/design/resumption files retain the remaining
  alert work and original Show rundown exclusion. Next: sustained condition reducer,
  contextual inline warnings/navigation/dismissal and explicit speech-monitor consent.

## W4 — Live alerts implemented; full verification pending

- Previous goal turn was **progress**: `43c3f88` committed the telemetry foundation,
  and the worktree was clean. The full scope/exclusion remains intact; W4 is still open.
- Added bounded sustained-warning state machines, streaming counter-delta checks,
  canvas-derived render thresholds, scoped disk minimum and unexpected connection loss.
  Unknown/stale/gapped data cannot prove recovery. Dismissals use 60-second cooldowns
  and occurrence identity. Ready tokens and ActiveConnection generations reset context.
- Added process-local, explicitly armed microphone monitoring with continuous sampled
  quiet detection, brief-signal preservation, invalid/missing-meter unknown states and
  disarming on context/configured-microphone changes. Added no OBS requests or writes.
- `:feature:alerts` provides More/detail navigation, warning cards with readings, direct
  Stop monitoring, consent/error handling, optional foreground-only haptics and a compact
  shell bar above output status. Haptics honor global Settings and do not replay on resume.
  Disk alerts link to Preflight (the disk-check/threshold surface); other alerts link to
  Stats, Mixer or Connections. No sounds or OS alert notifications were added.
- Initial static checks found complexity/return-count/long-fixture issues in core logic
  and long UI strings. Refactored without suppressions; these intermediate failures remain
  in `/tmp/scenedeck-alerts-engine.log` and `/tmp/scenedeck-alerts-ui-compile.log`.
- Engine tests passed (18s, 56 tasks, `/tmp/scenedeck-alerts-engine-final.log`). Real Room/
  repository integration verifies shared polling (seven reads over six virtual seconds),
  synchronous command invalidation and canceled subscriptions; data checks passed (11s,
  56 tasks, `/tmp/scenedeck-alerts-data-final.log`).
- Final focused data tests, app/feature static checks and theme renders passed, **exit 0,
  17s, 135 tasks**, `/tmp/scenedeck-alerts-focused-final.log`. Ten engine tests cover
  sustained/recovery bands, canvas fallback, resets, invalid/stale data, recording-only,
  cooldowns, old dismissal, connection changes, unavailable configuration, speech consent,
  brief signal and missing/wrong-session meters. UI tests cover consent, target replacement,
  rejected arming, direct stop, navigation/dismissal and lifecycle-gated haptics.
- Eight SceneDeck/OBS light/dark page/bar goldens were generated; inspected light page,
  dark page and both light/dark bars for readable states, wrapping and action targets.
  No physical-device, real-OBS, measured-performance or background-delivery claim is made.
- Updated product/architecture/design/privacy/AGENTS and module-map guidance. Next:
  full JDK21/uv gate and explicit build/tests, final audit and W4 checkpoint; W5–W11 remain.

## W4 — Verified live alerts

- **W4a–d are complete.** This goal turn is **progress**; W5–W11 remain open and the
  Show rundown/cue-list/timed-advancement exclusion is unchanged.
- The first full gate (`/tmp/scenedeck-alerts-quality.log`, 1m 28s, 946 tasks) exposed
  an existing Mixer test synchronization race: it renamed a selected scene after the
  selection label appeared but before its asynchronous input discovery completed.
  The test now waits for the populated initial selection and retains its strict final
  rename/input assertion. Mixer production behavior was unchanged. The focused Mixer
  ViewModel regression class passed (34s, 130 tasks,
  `/tmp/scenedeck-alerts-mixer-regression.log`).
- Final JDK 21/uv quality gate passed, **exit 0, 1m 20s, 1,050 tasks**:
  `/tmp/scenedeck-alerts-quality-final.log`. Formatting/complexity, Android lint,
  debug build and unit/Roborazzi checks passed; seven Semgrep rules across 287 source
  files reported zero findings; Gitleaks history (66 commits) and tree scans were clean.
- Explicit `assembleDebug testDebugUnitTest` passed, **exit 0, 19s, 826 tasks**:
  `/tmp/scenedeck-alerts-explicit.log`. Aggregate XML: **742 tests / 136 suites,
  zero failures/errors/skips**, including 10 alert-engine, one repository integration,
  and 12 UI/consent/haptic/render tests. Eight page/bar goldens passed verification.
- Documentation, module-map guidance and the roadmap now reflect the feature. No real
  OBS, physical-device, background-delivery or measured-performance claim is made.
- Next: W5 capability-gated recording controls, local bookmarks and media progress/seek.
  Initial source inspection: ObsVersionInfo carries versions/platform only; inspect its
  GetVersion mapper and preserve advertised capabilities before enabling new recording
  actions. `setMediaInputCursor` already exists in ObsClient and KtobsObsClient, so reuse
  that boundary. The full durable goal remains active; no release/publication performed.

## W5a — Recording tools implementation and focused verification

- Resumed from clean `0e3902f`; this turn is **progress**. W5b–W11 remain open.
- Added typed advertised capabilities and pause/resume/split/chapter request adapters
  without changing pinned dependencies. Added a target-bound recording-tools controller,
  Hilt ViewModel and expanded sheet in Scenes options. All writes use the existing
  deadline/session/cancellation lane, with no automatic retries or new OBS poller.
- Fake-server checks exposed ktobs 0.5.0's non-null recording-event path assumption;
  a local serializable adapter now accepts null/absent paths and maps typed phases.
  Shared telemetry also retains recording-read start time so delayed earlier reads
  cannot falsely confirm a pause/resume write.
- Initial static checks caught complexity/return-count/line-length issues, fixed by
  extraction and simpler control flow, without suppressions. First compile/tests also
  caught cross-module smart casts and incorrect test fixture expectations; corrected.
- Focused JDK21 tests/static checks and five renders passed: **24s, 144 tasks**,
  `/tmp/scenedeck-recording-focused-confirmation.log`. Covers 13 controller regressions,
  five protocol tests, nine recording UI tests and existing Stats regressions. Inspected
  SceneDeck light, OBS dark and compact unknown-outcome images. Full gate is in flight.
- Final audit identified a remaining queued-command issue: observed context/recording
  replacement should cancel admission waits immediately, rather than only suppressing
  completion. Tighten that path and verify before completing W5a.
- No real-OBS/device/profile measurements or release/publication are claimed.

## W5a — Verified recording controls checkpoint

- **W5a is complete; W5b–d and W6–W11 remain open.** This turn made **progress**.
  The durable goal remains active and excludes Show rundown/cues/timed advancement.
- The final audit fix cancels the owning command when observed target/session/recording
  identity changes. This cancels queued admission and suppresses obsolete completion;
  an already-sent action may still have taken effect. Old pending work is not shown as
  an action on a replacement target. The additional regression proves no queued write
  dispatches after an observed recording boundary.
- Final focused controller/UI/static checks passed, **21s, 137 tasks**:
  `/tmp/scenedeck-recording-cancel.log`. New coverage totals **28 tests**: 14 controller,
  five protocol and nine UI tests. Five rendered variants are checked in; no visual
  behavior changed after their initial capture. Full tests also cover shared telemetry.
- First complete JDK21/uv gate passed (2m 5s, 1,050 tasks,
  `/tmp/scenedeck-recording-quality.log`). After the cancellation refinement, the final
  gate passed **exit 0, 2m 13s, 1,050 tasks**:
  `/tmp/scenedeck-recording-quality-final.log`. Android lint, complexity, formatting,
  debug build/unit/Roborazzi checks passed. Semgrep: seven rules over 293 files, zero
  findings. Gitleaks history (67 commits) and tree scans: no leaks.
- Explicit JDK21 `./gradlew assembleDebug testDebugUnitTest` passed **exit 0, 3s,
  826 tasks**, `/tmp/scenedeck-recording-explicit.log`. Aggregate XML: **770 tests /
  139 suites, zero failures/errors/skips**. `git diff --check` is clean.
- Updated FEATURE_SPEC, ARCHITECTURE, DESIGN_SYSTEM, ROADMAP, AGENTS and OBS protocol
  skill guidance. All changes are local; no real-OBS/device verification, measured
  performance, release or publication is claimed.
- Next: **W5b** scoped local bookmarks, independently of OBS chapters. Inspect Room
  schema 6 and scoped repository transaction patterns. Define separate OBS-reported
  stream/recording elapsed timestamps, paused-recording semantics and unknown/gapped
  context, with bounded persistence suited to W6 history/export. Then **W5c** exposes
  bounded media seek/progress using the existing Mixer repository polling boundary.

## W5b — Bookmark implementation (verification in progress)

- Previous turn (`a051c3f`, W5a) was **progress**, with a clean worktree. This turn
  adds local bookmarks; the full goal and Show rundown exclusion are unchanged.
- Added Room schema 7/table/explicit migration, bounded local repository, synchronous
  timestamp capture with separate output counters/sample ages, idempotent retry,
  offline list/edit/delete, and the first `:feature:history` page under More.
- Captures bind to a resolved current workspace, then remain immutable even if saving
  finishes after disconnect. Unknown/inactive/paused/reconnecting states are distinct.
  Insertion sequence (not device wall time) drives the 500-mark retention limit.
- Initial checks caught long SQL/UI strings; fixed formatting without suppressions.
  Core capture (five tests), repository (nine tests), and full historical Room migration
  checks passed in `/tmp/scenedeck-bookmarks-focused-final.log`. That run failed two
  feature tests: a missing Robolectric SDK pin and its known dialog/text-field layout
  loop with explicit window widths. Applied the repository's existing SDK 34/default
  dialog-width test configuration; the actual input/dialog and assertions remain.
- UI renders and ViewModel failed-save/retry regression are running again. No W5b
  checkbox or full feature completion is claimed yet. Full quality/build/preview gates,
  final audit and durable checkpoint remain before handoff.

## W5b — Verified local bookmarks checkpoint

- **W5b is complete; W5c–d and W6–W11 remain open.** This turn made **progress**.
  The durable goal remains active; Show rundown/cues/timed advancement remain excluded.
- Added More → Bookmarks with immediate local capture, separate stream/record elapsed
  values, explicit paused/reconnecting/unknown states, original workspace identity,
  offline label editing/deletion and exact-moment failed-save retry/discard. Room schema
  7 preserves previous data and bounds retention to the newest 500 insertions.
- Focused data/static/UI checks passed **exit 0, 53s, 138 tasks**:
  `/tmp/scenedeck-bookmarks-renders-final.log`. The initial rerun caught a test-only
  invalid `assertDoesNotExist` import; removed it (the API is a member function).
  Final coverage also rejects unsupported persisted payload versions as explicit failure.
- Nine page/editor/compact goldens are checked in. Inspected SceneDeck light, OBS dark,
  compact unknown readings, and both corresponding label dialogs. The established
  Robolectric default-width dialog workaround is test configuration only; actual
  TextField/dialog interactions are exercised without a production bypass.
- Full JDK21/uv `scripts/check-quality.sh` passed **exit 0, 2m 38s, 1,112 tasks**:
  `/tmp/scenedeck-bookmarks-quality.log`. Formatting, complexity, Android lint, debug
  build, unit and Roborazzi verification passed. Semgrep: seven rules over 303 files,
  zero findings. Gitleaks history (68 commits) and worktree scans found no leaks.
- Explicit JDK21 `./gradlew assembleDebug testDebugUnitTest` passed **exit 0, 11s,
  876 tasks**, `/tmp/scenedeck-bookmarks-explicit.log`. Aggregate XML: **799 tests /
  144 suites, zero failures/errors/skips** (29 added tests). Coverage includes capture
  without additional OBS reads, historical migrations, database recreation, scope changes,
  insertion-order retention, idempotency, stale edits, corruption and failed-save recovery.
- Updated FEATURE_SPEC, ARCHITECTURE, DESIGN_SYSTEM, PRIVACY_POLICY, ROADMAP, AGENTS,
  Android skill module map and workflow resumption files. No real-OBS/device verification,
  measured performance, release or publication is claimed. W6 full history/export is
  still separate work, not completed by the bookmark table/page.
- Next: **W5c media progress/remaining time and bounded seeking**. Reuse shared Mixer
  polling and the existing OBS cursor request; add validated/fresh status, read-order
  guards, capability checks, pending/outcome reconciliation and failure-path tests before
  exposing seek controls. See `recording-media-design.md` for concrete source findings.

## W5c — Media progress/seeking implementation (verification in progress)

- Previous turn committed verified W5b as `02aece3`; it made **progress** and left
  a clean worktree. The full goal remains active; W5c–W11 were not completed by W5b.
- Extracted the existing Mixer media poll into a shared controller with per-source
  identity, read ordering, failed-read invalidation, monotonic expiry and focused
  off-program source leases. Added a capability-gated seek controller with bounded
  targets, duration revalidation, one pending seek per input, explicit post-write
  reconciliation and cancellation on source/workspace replacement.
- Added Media tools under channel options, with elapsed/remaining/duration, a single
  release-to-seek slider and explicit unsupported/unknown/pending/outcome feedback.
  Current compact controls remain; their missing status now reads Unknown.
- Initial compile/static checks caught controller size, recursive type inference and
  Compose complexity. Split cache/command/reader responsibilities and extracted the
  slider without adding suppressions. Focused data/UI tests and renders are underway;
  no W5c/d completion or real-OBS/device verification is claimed yet.
- First full JDK21/uv gate passed (2m 7s, 1,112 tasks; 827 tests/146 suites),
  `/tmp/scenedeck-media-quality.log`. Final audit then fixed a focused sheet incorrectly
  treating an off-program/filtered channel as locked. The unfiltered persisted lock set
  now travels in MixerUiState. Its focused regression passed (28s, 131 tasks),
  `/tmp/scenedeck-media-lock.log`, after correcting the feature fake's event-emission API.
  A final full gate is running against that correction; checkboxes remain open until it passes.
- During one earlier focused rerun the formatter had not yet reported terminal when
  the check was launched. Both handles were polled to terminal before the full gates;
  final validation runs with a single Gradle owner and already formatted sources.
- Source checked: official OBS `RequestHandler_MediaInputs.cpp` confirms that time
  values and seeking require playing/paused media and that seek does not enforce the
  duration bound. Existing core OBS adapter/request lane remains unchanged.

## W5c–d — Verified media tools checkpoint

- **W5 is complete; W6–W11 remain open.** This turn made **progress**. The durable
  goal remains active and excludes Show rundown/cues/timed advancement.
- Mixer channel options now open Media tools with actual elapsed/remaining/duration,
  explicit unknown values and a bounded release-to-seek slider. Locks remain accurate
  after a channel leaves program. Six new light/dark/SceneDeck/OBS/pending/compact renders
  are checked in; SceneDeck light, OBS dark and compact unknown-outcome views were inspected.
- Cache, reader and seek command responsibilities are separate, sharing one lock for
  context/read ordering. Failed/stale readings cannot retain prior success. Focused
  off-program channels lease the same reader. Gesture source and duration remain fixed
  from first movement through release, with fresh pre-write validation and post-write
  reconciliation. Queued seeks cancel on observed source/workspace replacement.
- Focused data/static/UI checks passed (48s, 139 tasks,
  `/tmp/scenedeck-media-focused.log`). The additional gesture check caught a missing
  cancellation message, then passed after rendering it (1m 4s, 138 tasks,
  `/tmp/scenedeck-media-gesture.log`). The off-program lock regression passed separately
  (28s, 131 tasks), `/tmp/scenedeck-media-lock.log`.
- Final JDK21/uv `scripts/check-quality.sh` passed **exit 0, 56s, 1,112 tasks**:
  `/tmp/scenedeck-media-quality-final.log`. Formatting, complexity, Android lint,
  debug build, unit and Roborazzi checks passed. Semgrep ran seven rules over 308 files
  with zero findings; Gitleaks history (69 commits) and tree scans found no leaks.
- Explicit JDK21 `./gradlew assembleDebug testDebugUnitTest` passed **exit 0, 1s,
  876 tasks**, `/tmp/scenedeck-media-explicit.log`. Aggregate debug-test XML: **828 tests /
  146 suites, zero failures/errors/skips**. The 29 added tests cover media data/control
  failure paths, bounds, duplicate/canceled/replaced seeks, gesture identity, menu entry,
  source locks, accessibility and renders. Prior recording/bookmark checks also pass,
  completing W5d's combined verification. `git diff --check` is clean.
- Updated FEATURE_SPEC, ARCHITECTURE, DESIGN_SYSTEM, ROADMAP, AGENTS, protocol skill
  guidance and workflow resumption files. No real-OBS/device verification, measured
  performance, release or publication is claimed.
- Next: **W6 local session history/export**. Inspect schema 7 and BookmarksRepository,
  shared Stats/ObsClient context events and LiveAlertsRepository. Define bounded local
  observation sessions, event ordering/deduplication, disconnect/process gaps, retention
  and sanitized export before wiring the History surface. W7 preset-result integration
  must remain explicitly tracked until real preset execution exists; a placeholder event
  type alone is not completed end-to-end history support.

## W6 storage and export foundation — 2026-11-05

- The prior W5 media turn made **progress** and its verified commit is `92824e7`.
  This turn also makes **progress**. W1–W5 stay verified; W6–W11 remain open and
  the whole durable goal stays active. Show rundown/cue lists/timed advancement
  remain excluded. No release or publication is requested.
- Added `history-design.md` with local-observation semantics, explicit gaps/unknown
  ends, bounded persistence, the export allow-list and remaining integration work.
  This is a foundation checkpoint, not completed History capture/UI or full W6.
- Room schema 8 adds sessions, events and preferences, registered in the production
  migration chain. Historical schema fixtures through version 7 preserve previous
  data, including bookmark identity/order/labels. Existing bookmarks are unchanged.
- `HistoryRepository` delegates transaction-bound writes and explicit read states.
  Defaults retain 30 observations (10/30/100 choices), each with the newest 500 events.
  Insertion sequence and ordinals survive backward calendar changes; missing capture
  and retention omissions have separate counts. Replays are idempotent, conflicting
  retained writes fail, and obsolete/pruned events never reinsert.
- Clear/delete invalidate queued starts through preference generation; append/touch
  never recreate a deleted parent. FK cascade removes its events. Deleting an older
  observation leaves a surviving active writer usable. Disable/re-enable changes the
  capture generation and closes previous observations without inventing output stops.
- Recovery closes another process's open observations with `PROCESS_ENDED`, keeping
  the last observed point and an unknown exact end. Heartbeats update that point
  without another event. Owner checks prevent another writer from finishing/touching
  an observation; caller cancellation propagates and storage failures are explicit.
- Export snapshots are consistent, bounded and offline. Versioned JSON uses explicit
  DTOs for observation metadata and typed output/program/warning/gap events. It omits
  process ownership, DB bookkeeping, endpoints, credentials, raw protocol/comments,
  arbitrary errors and output paths. No file chooser/export UI is active yet.
- Added 21 storage tests plus the schema 7→8 preservation regression. Coverage
  includes concurrent duplicate writes, clear/start races, transaction rollback after
  an inserted event, failed-storage retry, process reopen, corrupt/unsupported/oversize
  payloads, unknown states, scope isolation, retention, deletion and JSON escaping.
- The first focused compile exposed new source files placed outside the module source
  directory; moved them into `src/main/java`. The corrected focused run passed all
  25 selected tests. The first full gate found two SQL line lengths and one oversized
  test method; these were fixed without relaxing checks. Final review also preserved
  idempotent start retries for a surviving active observation after deletion of an
  older observation; the regression now asserts both start and append still work.
  Final gates were rerun after this correction, with one Gradle owner at a time.
- Final verification with JDK 21 and uv:
  - `scripts/check-quality.sh`: exit 0; Gradle **1m 45s, 1,112 tasks**. Full debug
    XML totals: **850 tests across 150 suites, zero failures/errors/skips** (22 new
    tests). Existing screenshot regressions passed; no screens changed in this slice.
  - Semgrep: seven rules across 318 files, zero findings. Gitleaks: 70 commits and
    the working tree scanned, no leaks.
  - Explicit `./gradlew assembleDebug testDebugUnitTest`: exit 0, **2s, 876 tasks**.
  - Evidence: `/tmp/scenedeck-history-focused.log`,
    `/tmp/scenedeck-history-quality-final.log`, `/tmp/scenedeck-history-explicit.log`,
    module test XML and `build/reports/security/`. `git diff --check` passed.
- Updated AGENTS, FEATURE_SPEC, ARCHITECTURE, ROADMAP and the durable workflow. No
  new screen/preview, real-OBS/device run, performance measurement or publication
  is claimed. W6a/b/c remain unchecked because integrated capture and user flows are
  not yet implemented; schema/serializer verification is not feature completion.
- Next: implement the context-guarded shared-state recorder, then History list/detail,
  capture/retention controls, clear/delete and SAF export. Resolve bookmark link/edit/
  deletion semantics before integrating saves. Actual preset results depend on W7;
  defining a payload type alone must not check off W6a. Update privacy documentation
  when capture becomes active and render every changed screen in required themes.

## W6 shared capture and History user flow — 2026-11-05

- The prior storage-foundation turn made **progress**, committed as `c9c7908`.
  This turn makes **progress** toward W6. The full durable goal remains active;
  W7–W11 and actual preset-result integration are still required. Show rundown,
  cue lists and timed advancement remain excluded. No release/publication is requested.
- Added one application-scoped HistoryRecorder with shared context/event/telemetry/
  warning feeds and no additional OBS poller. Synchronous origin guards and reducers
  preserve rapid program changes, reject stale reads/events and age evidence to unknown.
  Stream reconnecting is now typed in the protocol adapter; stream/camera read timing
  joins existing recording timing, with atomic revision-aware telemetry merging.
- Capture feeds a bounded 128-command queue and serial Room writer. Known local lost
  entries advance ordinals, explicit interruption/retry starts a new owner/observation,
  and ten-second heartbeats preserve last observation without extra events/requests.
  Failed start/end admission pauses capture. This log does not claim exhaustive upstream
  delivery; known missing counts include failed gap markers, not just raw OBS events.
- Schema 9 links bookmarks atomically to their original persisted observation. Delayed
  saves never retarget or resurrect deleted history. Joined labels/times follow edits,
  deletion and independent bookmark retention. Clear/delete history removes associations
  but preserves bookmarks; all-history exports include unlinked saved marks. Open/unknown
  end detail can include a saved bookmark beyond the last heartbeat.
- Added More → History, offline list/detail, capture/retention settings, explicit retry,
  concrete clear/delete confirmations and a selected/all-history JSON export. Export
  captures its data before the document picker and preserves selection through later
  changes; canceled/expired/duplicate callbacks cannot silently rewrite a file. Failed
  writes are sanitized and explain that the selected document may be incomplete.
- Focused UI/ViewModel/static/app compilation passed **14s, 268 tasks**, including
  snapshot identity, picker cancellation, expired callbacks, write failure, duplicate
  actions and retention/clear semantics (`/tmp/scenedeck-history-ui-tests.log`).
- New recorder/bookmark/schema tests passed with the data suite. Initial visual tests
  needed lazy-list scrolling before locating offscreen rows; fixed without changing
  product layout. A fake-server test passed a JsonObject instead of its required JSON
  string; corrected the fixture. Final focused recording/data/protocol run passed
  **exit 0, 1m 2s, 141 tasks** (`/tmp/scenedeck-history-renders-data.log`).
- Recorded 11 History renders: list/detail in SceneDeck and OBS light/dark, compact
  observation and linked-bookmark views, and failed storage. Inspected SceneDeck list
  light/dark, OBS detail light/dark, both compact views and storage failure. The render
  tests assert reachable actions and readable paused bookmark time.
- Final JDK21/uv `scripts/check-quality.sh` passed **exit 0, 2m 23s, 1,112 tasks**
  (`/tmp/scenedeck-history-capture-quality.log`). Formatting, complexity, Android lint,
  debug build, all unit tests and default Roborazzi verification passed. Semgrep ran
  seven rules over 336 files with zero findings; Gitleaks scanned 71 commits and the
  working tree with no leaks.
- Explicit JDK21 `./gradlew assembleDebug testDebugUnitTest` passed **exit 0, 13s,
  876 tasks** (`/tmp/scenedeck-history-capture-explicit.log`). Final debug XML totals:
  **899 tests across 158 suites, zero failures/errors/skips**, 49 more tests than the
  foundation checkpoint. `git diff --check` is clean. Only one Gradle owner ran at a time.
- Updated AGENTS, FEATURE_SPEC, ARCHITECTURE, DESIGN_SYSTEM, ROADMAP, privacy policy and
  durable workflow files. W6b/c are verified; W6a remains open for actual W7 preset
  outcomes. No real OBS/device run, physical document-provider validation, measured
  performance, release or publication is claimed.
- Next: **W7 controlled multi-action presets**. Inspect the existing guarded control
  repositories, output confirmations, capability/lock checks and workspace rename
  handling. Design a bounded typed local preset store/editor and explicit invocation;
  use target values, preflight missing/unsupported references, stop on failure/unknown/
  replacement, preserve completed steps without rollback and never retry uncertain
  writes automatically. Integrate real results into the original History observation
  before checking W6a. W8–W11 remain in scope; rundown/cue scheduling stays excluded.

## Decisions / limitations

- Include the five additional improvements from the recommendation, alongside the
  six retained main features. No need to request the same scope approval again.
- Presets must work independently of rundown; tablet UI must not introduce cues.
- Keep schemas and tests together; foundation-only work is not workspace completion.
- No release, remote publication, or store upload has been performed.
