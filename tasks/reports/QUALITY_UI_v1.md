# Code review: feature UI, widget integration and repository guidance

Date: 2026-10-04. Reviewer/initial build owner: planning_quality. User approved concrete plan before application edits. This is correctness/hardening work with separately scoped structural cleanup, not a visual redesign.

## Summary

Found two concrete UI-boundary correctness/privacy defects and two supporting maintainability/work issues. Implemented small scoped fixes with independent performance/security review; the targeted UI suite passes. Final repository verification is launched centrally by root, with security_obs interpreting results after the explicit build-ownership handoffs.
Counts: 2 critical, 2 important, 1 nit (deferred).

## Critical findings

- `feature/live/src/main/java/com/scenedeck/android/feature/live/LiveViewModel.kt:327` — trust-boundary diagnostic leak: old transport message policy embedded `Throwable.message` and `ObsRequestFailedException.message` in stream/record snackbar text. Server comments/transport details can contain credentials or private endpoint text. Fix implemented: fixed OBS/action recovery guidance, no raw comment/status/cause. Two semantic marker regressions verify privacy and retained action context.
- `app/src/main/java/com/scenedeck/android/widget/SceneDeckWidgetUpdater.kt:65` — stale actions: old `snapshot.sceneNames.isEmpty()` branch treated a connected empty curated deck as offline and retained previously removed scene actions. Fix implemented: typed readiness controls preservation; Ready empty writes full empty snapshot and clears slots/program; unready keeps cached cold-start grid with truthful header. Persistence regressions cover both branches.

## Important findings

- `app/src/main/java/com/scenedeck/android/widget/SceneDeckWidgetUpdater.kt:38` — excess work in Observer integration: every DeckState emission recomputed/persisted/recomposed widgets, including icon/accent/preview/transition changes invisible to widgets. Fix implemented: project only six visible names, program, connection label and readiness; `distinctUntilChanged()` suppresses duplicate projected emissions. `poke()` remains unconditional; current state is reread inside the existing mutex and per-widget failures remain isolated. Regression asserts unrelated changes emit no update, visible names/order/program/connection changes do. This proves eliminated redundant scheduling, not device-level latency.
- `.kimi/skills/{android-compose,material3-expressive,obs-websocket-v5}/SKILL.md` and early `docs/DESIGN_SYSTEM.md` sections — conflicting duplicated knowledge: older version/SDK, external Lucide-pack, red recording/morph/strip and Replay Buffer wording contradicted pinned stack/current studio contracts. Fix implemented: align with TECH_STACK/AGENTS, hand-ported icons, uniform rail-free tiles, blue Stream/red Stop, green stream/amber record timers, streaming-only persistent metrics and excluded Replay Buffer. No screen color/geometry implementation changed.

## Nits

- `core/data/src/main/java/com/scenedeck/android/core/data/ConnectionProfile.kt:3` — documented exception deferred: compileOnly Compose `@Immutable` annotation is intentional stability metadata, while ARCHITECTURE says no Compose types in data. No runtime Compose dependency is added by this annotation, and removing it or moving models solely for wording is unjustified here. Revisit if domain/presentation coupling expands; no runtime defect claimed.

## Coverage

- Section A (naming & functions): new helpers are cohesive and locally named; broad ViewModel/class rewrites were rejected without a concrete defect.
- Section B (comments & formatting): stale project guidance corrected; shared formatter is the final mechanical authority. Comments explain cached-grid/privacy constraints.
- Section C (SOLID): reviewed feature → core imports, ObsClient facade, UI/data/secret ownership; no ktobs/Ktor import outside core:obs and no new persistence/interface layer. The compileOnly annotation exception is recorded above.
- Section D (DRY/KISS/YAGNI): widget projection reused by flow scheduling and mutex-current reads; no speculative factory/command/framework or dependency addition. Broad checklist duplication was not converted into architecture churn.
- Section E (AI failure modes): privacy boundary/persistence truth fixed with marker/state tests; cancellation helper and existing cleanup/validation retained, no production fixture/fake success or weakened tests introduced.

Build/delivery audit read the root/app Gradle configuration, version catalog,
check-quality/check-secrets scripts, CI/release workflows and signing/Play-infra
helpers. The local gate includes formatting, complexity, all module lint/tests,
Roborazzi verification and debug assembly; CI separates these into bounded jobs.
AGP security constraints stay on buildscript classpath, and signing uses environment
credentials with temporary restricted-permission files and cleanup. Secret setup
uses hidden prompts/stdin, and the infra helper captures the access token into its
child environment rather than printing or persisting it. F-Droid source/dependency
selection remains isolated. No new actionable defect in those reviewed paths was
established; no delivery tool, dependency pin, signing credential or workflow was
modified. Hosted CI/release execution was not performed.

Feature screens use lifecycle-aware collection. Searches found no production
GlobalScope/runBlocking or force-unwrapping in the reviewed feature paths; test
fixtures retain intentional blocking/unit assertions. The existing draw-phase
meter/chart state ownership was retained. Static searches supplement contextual
review and do not establish complete absence of defects.

## Baseline and verification evidence

Initial Git worktree was clean and tasks/plan.md/todo.md absent. Read AGENTS, five required project docs and all three project skills before edits. Workflow plan/tasks were created with exact bounded slices, dependencies, ownership and acceptance; user approval recorded on 2026-10-04.

Baseline command with `JAVA_HOME=/home/worxbend/.sdkman/candidates/java/21.0.8-tem`:
`scripts/check-quality.sh` — PASS. Gradle qualityCheck: 909 actionable tasks, 17 executed / 892 up-to-date, BUILD SUCCESSFUL in 17 s. Existing XML reports at baseline: 80 suites, 383 tests, zero failures/errors/skips; these were largely previously cached/up-to-date results, not claimed as 383 fresh executions. Semgrep: seven checked-in rules, 202 targets, zero findings. Checksum-verified Gitleaks: 30 history commits and working tree, no leaks. Log: `/tmp/scenedeck-quality-baseline.log`.

Focused command with same JDK21 and `--no-configuration-cache`:
`:app:testDebugUnitTest :feature:live:testDebugUnitTest --tests 'com.scenedeck.android.widget.*Test' --tests 'com.scenedeck.android.feature.live.TransportErrorMessageTest' --continue` — PASS, 32 s. XML evidence: WidgetUpdateTest 4, WidgetSnapshotTest 7, SceneDeckWidgetTest 2, TransportErrorMessageTest 2 (15 tests, zero failures/errors). Log: `/tmp/scenedeck-focused-ui.log`.

Initial sandbox execution failed on Gradle wrapper lock in read-only ~/.gradle; required escalation used the existing cache successfully. A later data test escalation call was aborted before launch (no session/log), with no active owned Gradle command; exclusive build ownership transferred to security_obs and then root after the next subagent escalation was aborted before launch. No agents launch overlapping builds. Final commands/results belong in FINAL_REVIEW_v1 and supersede this initial/focused report.

## Independent review and limits

performance_data and security_obs reviewed actual widget/Live diffs and accepted them. Security independently reviewed proposed data/OBS fixes in PROPOSAL_REVIEW_v1. planning_quality reviewed actual screenshot scheduling/cache/decode, registry mutation batching/serialization and metadata index diffs: scoped changes align with existing boundaries, first-duplicate graph semantics are preserved, and no introduced defect was identified in those paths. Full regression outcomes remain required.

No Compose screen visuals intentionally changed. Widget visible content/layout is unchanged except correctly clearing stale actions; existing Glance content tests and full Roborazzi goldens verify retained rendering. No real-device CPU/heap/frame-time benchmark, release publication or hosted CodeQL run was performed in this local review. Ready currently does not carry a separate initial-scene-discovery flag: a transient Ready+empty feed clears cached widgets until scenes arrive; no extra flag/product behavior was invented.

clean-code-guard: clean for the scoped final widget/Live diff; final shared formatting/static checks remain required.

## Shared contract documentation acceptance

AGENTS, ARCHITECTURE, FEATURE_SPEC, HARDENING and ROADMAP now describe the implemented
request/session/privacy, thumbnail, confirmed/pending Mixer, registry/index and widget
guarantees. Earlier feature/roadmap descriptions of accent rails, always-visible metrics,
Kotlin versions, icon delivery and formatting tooling agree with the current source of
truth. The OBS project skill adds tested deadline/privacy/image guidance.

Both security_obs and performance_data independently reviewed these actual documentation
diffs and accepted them. Their precision notes were applied: discovery observations can
supply confirmed rollback baselines while preserving newer pending display values;
normal cancellation/deadline regressions verify ktobs response-store cleanup, while
artificial upstream cleanup-mutex contention remains unverified. `git diff --check`
passed after the documentation changes. Final whole-project results remain pending.

## Fresh central focused failure — supersedes cleanup assumption

The first fresh central OBS/data run failed: OBS 47 tests/two failures and data
135 tests/two failures, zero errors/skips (direct XML inspection), recorded in
`/tmp/scenedeck-hardening-focused-core-first-failure.log`. Eight ordinary concurrent request
deadlines left seven UUID waiters in the pinned ktobs store. This is a real
dependency concurrency defect rather than an artificial mutex-contention caveat.
Security/performance are independently reviewing a serial request/receiver execution
context while preserving eight suspended in-flight requests; concurrent deadline
and caller cancellation cleanup must pass before final acceptance.

Other failures concern coroutine safe-exception copying, LEGACY Robolectric's synthetic
invalid-byte Bitmap and an immediate Mixer mute-state assertion. Privacy assertions
now check private markers; image tests use NATIVE graphics with pixel-cap assertions
retained; Mixer retains an active collector through the read gate. WhileSubscribed
projection expiry remains a hypothesis until rerun. Production data source is unchanged
for these harness fixes. Shared docs will be synchronized after an accepted, verified
cleanup remedy; earlier source/documentation acceptance does not prove this defect fixed.

## Corrected OBS verification and synchronized contract

The corrected central run at `/tmp/scenedeck-hardening-focused-core-rerun.log` passes
OBS nine suites/48 tests, including all 11 lifecycle tests, zero failures/errors/skips
(direct XML inspection). Three batches of eight deadlines and three batches of eight
caller cancellations leave zero actual UUID waiters and permit healthy followups.
Typed requests, initial GetVersion and the full receiver explicitly share one serial
execution lane, with eight suspended requests still in flight; request deadline
includes dispatcher admission. Recheck pinned non-suspending UUID critical sections
and explicit Ktor receiver confinement when upgrading ktobs.

AGENTS/ARCHITECTURE/FEATURE_SPEC/HARDENING/ROADMAP and the OBS project skill now
describe this verified remedy, superseding the earlier artificial-contention caveat.
Data has one remaining invalid-image test assertion among 135 tests; Mixer passes.
The combined command still failed and whole-project completion remains pending.

## Final focused core acceptance and first static gate

The next central run `/tmp/scenedeck-hardening-focused-core-rerun2.log` passes both
modules: 30 suites/183 tests, OBS48/data135, zero failures/errors/skips, direct XML
inspection. The decoder fixtures additionally select SDK 34 because missing library
metadata defaulted to API 23, below the native-shadow threshold; image assertions
were retained and now pass. No production data change was needed for harness fixes.

The first full quality gate failed only on five core Detekt findings. App and Live
Detekt passed; no widget/Live/doc/tooling failure appeared. Owner remedies preserve
semantics: private shared dispatcher, cohesive requestOnProtocol boundary, narrow
documented privacy suppression, multiline JSON and equivalent decoder return.
Planning_quality independently read and accepted those actual edits. The complete
gate/explicit build-test results remain pending; Semgrep/Gitleaks were not reached
by the failed full attempt.

## Inventory integration fixture correction

The next full matrix exposed two RegistryTransferFailureTest failures. ImportDao's
upsertAll was a no-op while only upsert simulated write failures and barriers, so
the newly batched repository bypassed intended test behavior. Both methods now
call a shared write helper once; failure/retry awaits actual write admission and
asserts one failed/two total calls. Duplicate-command barrier assertions are retained.
Production RegistryTransfer/import behavior is unchanged. Its synchronous importing
compareAndSet and ordered completion make the waits valid; no extra source barrier
was introduced. Performance independently reviewed and accepted the actual fixture
diff. Full matrix contained 426 tests/85 suites with only these two failures; final
verification after this correction remains pending.

## Final verification — complete

The complete JDK21/uv quality script and explicit assembleDebug/testDebugUnitTest
commands exit zero. The final matrix is 426 tests/85 suites, zero failures/errors/skips,
including 111 Roborazzi render tests/22 suites. The corrected Inventory assertions,
widget projection and Live privacy tests pass. Both final specialist v4 signoffs
accept actual source and matching documentation; final diff checks pass.

Semgrep actual target-list/JSON inspection includes new untracked OptimisticControl
among 203 production source artifacts, zero findings/errors. Its generic tracked-only
summary caused a provisional false inference; source/target evidence disproved it.
Default test-directory exclusions account for 130 skipped files. No script/policy
change was justified. Gitleaks history/working-tree scans report no leaks. Earlier
pending checkpoints are historical; FINAL_REVIEW_v1 records exact final logs and limits.
