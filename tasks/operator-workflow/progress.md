# Progress and handoff

## Current checkpoint

- Branch: `feat/operator-workflow`.
- Active slice: **W1b — authoritative workspace integration**.
- Next action: follow `workspace-design.md` for session identity, origin-bound
  mutations, scoped UI/widget/cache state, and legacy recovery. W1a is verified.
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

## Decisions / limitations

- Include the five additional improvements from the recommendation, alongside the
  six retained main features. No need to request the same scope approval again.
- Presets must work independently of rundown; tablet UI must not introduce cues.
- Keep schemas and tests together; foundation-only work is not workspace completion.
- No release, remote publication, or store upload has been performed.
