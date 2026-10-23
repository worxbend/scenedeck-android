# Implementation Plan: SceneDeck performance, security and maintainability review

## Overview
Review the existing Android application with a coordinated specialist squad, verify findings against the product contract, then implement bounded improvements in performance, credential handling, concurrency and code organization. Preserve the current UX, pinned stack, OBS-only protocol boundary and local-only scene curation. The user has authorized review, proposal discussion, implementation and final validation; the human plan review requested by the planning skill was completed on 2026-10-04 before application edits.

## Architecture decisions
- Keep feature → core dependency direction, Hilt/KSP, immutable StateFlow UI state and lifecycle-aware Compose collection.
- Apply Fowler/Martin, SOLID, GRASP and GoF principles to demonstrated responsibilities and change risks. Keep existing interfaces when they already isolate responsibilities; each proposed abstraction requires concrete benefit.
- Retain OBS protocol logic in `:core:obs`; separate high-volume rendering/read work from control state where evidence warrants it.
- Make only small, reversible implementation slices, each with regression evidence. One agent owns each source area; one agent owns Gradle execution to avoid resource contention.
- Scan all available skill metadata, fully consult applicable skills and record applicability. Use current repository documentation as authority when older skill snapshots disagree.
- Keep version pins unchanged unless a verified security finding requires a coordinated catalog/doc update. Release and external publishing actions are outside this local improvement pass.

## Task list and file ownership

| ID | Concrete slice | Owner | Source/test files | Verification |
|---|---|---|---|---|
| T1 | Baseline, all-skill inventory, UI/build audit | planning_quality | reports only | Full gate PASS; inventory recorded |
| T2 | Independent proposal challenge and acceptance | all specialists | reports only | Evidence review before edits |
| T3 | Deadline includes OBS permit wait; bind queued/late work to originating session | security_obs | KtobsObsClient.kt, ObsClient.kt, FakeObsServer.kt, RequestLifecycleTest.kt, core/obs/README.md | `:core:obs:testDebugUnitTest` |
| T3a | Correct verified ktobs concurrent-cancellation waiter leak through shared serial protocol execution | security_obs | KtobsObsClient.kt, ObsSessionRunner.kt, RequestLifecycleTest.kt, core/obs/README.md | `:core:obs:testDebugUnitTest`; concurrent timeout/cancellation UUID0 and eight in-flight requests |
| T4 | Sanitize OBS connection errors at boundary and diagnostic strings | security_obs | ObsSessionRunner.kt, ConnectionState.kt, FakeObsServer.kt, HandshakeTest.kt, ConnectionErrorSanitizationTest.kt | `:core:obs:testDebugUnitTest` |
| T5 | Bound screenshot base64 input before decoded allocation | security_obs | KtobsObsClient.kt, StudioModeTest.kt, core/obs/README.md | `:core:obs:testDebugUnitTest` |
| T6 | Bound screenshot scheduled work/cache/pixel allocation; avoid unrelated restarts | performance_data | ScreenshotRepository.kt, ScreenshotRepositoryTest.kt | `:core:data:testDebugUnitTest --tests '*ScreenshotRepositoryTest'` |
| T7 | Confirmed/pending mute and volume revisions prevent obsolete rollback | performance_data | MixerRepository.kt, MixerRepositoryTest.kt, OptimisticControl.kt, OptimisticControlTest.kt | `:core:data:testDebugUnitTest --tests '*MixerRepositoryTest' --tests '*OptimisticControlTest'` |
| T8 | Conflate Mixer discovery triggers in separate cancellable lane | performance_data | MixerRepository.kt, MixerRepositoryTest.kt, OptimisticControl.kt, OptimisticControlTest.kt | same targeted Mixer tests |
| T9 | Include registry deletes in existing mutation mutex | performance_data | RegistryRepository.kt, RegistryRepositoryTest.kt | `:core:data:testDebugUnitTest --tests '*RegistryRepositoryTest'` |
| T10 | Index single registry snapshot and bulk-write mutations | performance_data | RegistryRepository.kt, RegistryRepositoryTest.kt | same targeted registry tests + DAO counts |
| T10a | Align Inventory failure/duplicate-write fixture with bulk DAO import | planning_quality | RegistryTransferFailureTest.kt | `:feature:inventory:testDebugUnitTest`; full gate |
| T11 | Index deck/graph metadata joins without changing defaults/order | performance_data | ObsStateRepository.kt, SceneGraphBuilder.kt, ObsStateRepositoryTest.kt, SceneGraphCycleTest.kt | targeted data tests for both classes |
| T12 | Deduplicate widget projection; connected empty deck clears stale actions | planning_quality | SceneDeckWidgetUpdater.kt, SceneDeckWidgetState.kt, WidgetUpdateTest.kt | `:app:testDebugUnitTest --tests '*Widget*Test'` |
| T12a | Sanitize stream/record failure snackbar text | planning_quality | LiveViewModel.kt, TransportErrorMessageTest.kt (or equivalent focused ViewModel test) | `:feature:live:testDebugUnitTest` |
| T13 | Synchronize project skills and design snapshots with actual current contract | planning_quality | three .kimi SKILL.md files, docs/DESIGN_SYSTEM.md | guidance/source consistency; `git diff --check` |
| T14 | Document new guarantees and maintain AGENTS contract | planning_quality | AGENTS.md, docs/ARCHITECTURE.md, docs/FEATURE_SPEC.md, docs/HARDENING.md, docs/ROADMAP.md | reviewed against implemented behavior |
| T15 | Independent final review and full verification | all reviewers; root runs central builds, security_obs interprets logs | reports/plan only | full JDK21/uv gate, explicit assembleDebug/testDebugUnitTest |

All paths in this table are expanded in `tasks/todo.md`. Existing production files remain unchanged before human plan approval and each slice's peer acceptance. T3/T4/T5 are sequential because the OBS source and fake server are shared. T7/T8 and T9/T10 are sequential for the same reason. Independent acceptance is recorded in `tasks/reports/PROPOSAL_REVIEW_v1.md`. T12 was independently accepted by performance_data and security_obs; preserve cached widget grid only while unready, keep `poke()` unconditional. No visual screen design is intentionally changed.

## Acceptance ledger and deferrals

- OBS deadlines/session identity: independently accepted by performance_data; verify dependency cancellation cleanup, never retry timed-out writes.
- Screenshots/mute/Mixer discovery: independently accepted by security_obs, with bounded queue, cancellation and authoritative newer-event regression conditions.
- Registry serialization/bulk and metadata indexing: evidence accepted by planning_quality and security_obs; preserve first-duplicate graph role behavior and duplicate import/null-field/order semantics and default Primary role.
- Live transport failure UI: independently accepted by security_obs; generic/status/category fallback must exclude raw exception/server comment, preserve cancellation and existing error delivery.
- Widget projection/connected-empty fix: independently accepted by performance_data; use ready boolean and preserve explicit retry/placement poke.
- Guidance synchronization: confirmed from TECH_STACK, current DESIGN_SYSTEM/AGENTS and implementation; update stale earlier wording rather than introducing a visual redesign.
- Defer speculative client rewrite, interface-per-repository, GoF ceremony, dependency upgrades without verified need, new backend/platform integrations, recurring scheduling, broad collection/host restrictions and the intentional compileOnly @Immutable architecture wording issue.
- Additional findings are recorded separately; they enter implementation only with exact evidence, independent review, verification and scope compatible with this approved plan.

## Checkpoints
- [x] Human reviewed and approved this plan before application implementation (2026-10-04; explicit message "approved plan and implement").
- [x] Baseline and specialist findings are recorded; each accepted fix has exact file ownership and a reproducible failure or concrete evidence.
- [x] Independent proposal review accepts the approach before implementation.
- [x] Focused regression tests pass after each accepted slice; related docs and AGENTS.md reflect behavior changes.
- [x] Independent final review has no unresolved introduced defect; full JDK21/uv quality gate, assembleDebug and testDebugUnitTest pass.
- [x] Changed Compose screen previews render in required themes, or an explicit environment limitation is recorded.

## Risks and mitigations
| Risk | Impact | Mitigation |
|---|---|---|
| Broad review causes speculative refactoring | High | Evidence-first triage, small slices, preserve contracts, independent proposal review |
| Agents conflict in shared files | High | Exclusive ownership and sequential work on shared APIs/docs |
| Gradle concurrency/resource contention | Medium | Single verification owner and recorded command logs |
| Previously unseen baseline failures | Medium | Record before implementation and distinguish introduced failures |
| Security fixes alter expected OBS behavior | High | Protocol/domain regression tests and documented UX contract checks |
| Skill guidance contains stale snapshots | Medium | Current TECH_STACK/DESIGN_SYSTEM/AGENTS are authoritative; synchronize project skills |

## Open questions
- No product decision is required for read-only audit. Concrete accepted slices are listed above; any further finding requires evidence and peer review within the same bounded scope.
- Human approved the plan on 2026-10-04; bounded implementation is active.
- Build ownership moved from planning_quality to security_obs, then to root for
  central escalation after subagent approval waits aborted without launching a
  build. Security interprets final logs; no agents run overlapping Gradle processes.
- First fresh central OBS/data run failed (47 OBS tests/two failures; 135 data
  tests/two failures). A real pinned ktobs UUID-store leak under concurrent request
  deadlines requires independently accepted request/receiver execution-context
  treatment and timeout/cancellation regressions; final completion remains pending.
- Verified correction: shared serial protocol execution passes repeated concurrent
  timeout/cancellation UUID cleanup while preserving eight suspended requests.
  Native SDK 34 decoder fixtures preserve image assertions. Latest combined focused
  run passes OBS48/data135 (183 total), zero failures/errors/skips. The first full
  gate found five core static-analysis findings; equivalent cleanup is accepted and
  full gate/explicit build-test rerun remains pending.
- Full verification also exposed an Inventory fake whose bulk DAO method was a
  no-op. T10a restores the existing failure/gate/count simulation for bulk imports,
  retaining preview-retry and duplicate-write assertions; independent review accepted
  the one-file test fix, pending final gate.

## Completed verification

Final independent performance/security v4 reviews accept the implemented changes.
Full JDK21/uv quality script and explicit debug build/unit commands exit zero;
426 tests/85 suites and 111 render tests/22 suites have zero failures/errors/skips.
Semgrep actual targets include new production source (203 targets, seven rules,
zero findings/errors); Gitleaks history/working tree and final diff checks pass.
Earlier pending/failed checkpoints above are historical and superseded by this result.
See tasks/reports/FINAL_REVIEW_v1.md for exact evidence and genuine deferred limits.
