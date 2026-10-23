# SceneDeck performance/security/clean-code tasks

Human approved the plan on 2026-10-04. Independent acceptance is recorded in PROPOSAL_REVIEW_v1; application implementation is active. One Gradle verification owner avoids overlapping builds.

## T1: Establish baseline and skill inventory

**Owner:** Planning/QA.

**Description:** Establish baseline and skill inventory in the existing product/architecture contract, using a bounded evidenced slice.

**Acceptance criteria:**
- [x] All required docs/project skills read, all available skill metadata inventoried and applicable guidance recorded.
- [x] Baseline full JDK21/uv gate result and logs recorded; reviewed domain findings have path/evidence/severity.

**Verification:** `scripts/check-quality.sh (baseline PASS; /tmp/scenedeck-quality-baseline.log)`.

**Dependencies:** None.

**Files likely touched:**
- Versioned reports under `tasks/reports/`; plan/task state only.

**Estimated scope:** Small; at most five source/test/doc files per slice.

## T2: Independently challenge proposed fixes

**Owner:** Specialist reviewers.

**Description:** Independently challenge proposed fixes in the existing product/architecture contract, using a bounded evidenced slice.

**Acceptance criteria:**
- [x] Every implementation slice has an independent acceptance rationale and concrete regression plan.
- [x] Speculative changes and measurement limitations are recorded.

**Verification:** `Review reports and acceptance ledger`.

**Dependencies:** T1; human plan approval before application edits.

**Files likely touched:**
- Versioned reports under `tasks/reports/`; plan/task state only.

**Estimated scope:** Small; at most five source/test/doc files per slice.

### Checkpoint after T2

- [x] Required prior reviews/checks recorded; scoped work is reviewable and buildable.

## T3: Bound OBS request lifetime and originating session

**Owner:** security_obs.

**Description:** Bound OBS request lifetime and originating session in the existing product/architecture contract, using a bounded evidenced slice.

**Acceptance criteria:**
- [x] Deadline bounds admission and response wait; eight unanswered requests cannot permanently starve controls.
- [x] Caller cancellation propagates; only local request deadline maps to typed recoverable timeout without retrying writes.
- [x] Queued/late old-session work cannot reach or publish into replacement session; timed-out pending UUID bookkeeping is cleaned.

**Verification:** `./gradlew :core:obs:testDebugUnitTest`.

**Dependencies:** T2.

**Files likely touched:**
- `core/obs/src/main/java/com/scenedeck/android/core/obs/internal/KtobsObsClient.kt`
- `core/obs/src/main/java/com/scenedeck/android/core/obs/ObsClient.kt`
- `core/obs/src/test/java/com/scenedeck/android/core/obs/FakeObsServer.kt`
- `core/obs/src/test/java/com/scenedeck/android/core/obs/RequestLifecycleTest.kt`
- `core/obs/README.md`

**Estimated scope:** Medium; at most five source/test/doc files per slice.

## T3a: Correct verified concurrent ktobs waiter cleanup

**Owner:** security_obs; performance_data and planning_quality independently review.

**Description:** The first fresh focused run exposed seven retained UUID waiters
after eight ordinary concurrent deadlines. Correct the pinned client's cleanup
contention through shared serial protocol execution, preserving eight suspended
in-flight requests and the existing public deadline/session contract.

**Acceptance criteria:**
- [x] Typed request blocks, initial GetVersion and the complete receiver/cleanup
  explicitly share one serial dispatcher; no reliance on Ktor dispatcher inheritance.
- [x] The request deadline includes permit and dispatcher admission; origin-session
  checks and caller cancellation remain intact.
- [x] Repeated concurrent deadline and caller-cancellation batches leave zero actual
  UUID waiters; later healthy requests and existing eight in-flight tests pass.
- [x] Pinned non-suspending critical-section assumption and upgrade recheck are
  documented only after independent acceptance and fresh verification.

**Verification:** `./gradlew :core:obs:testDebugUnitTest`; root runs centrally.

**Dependencies:** T3 and the first fresh focused failure; necessary correction within
the human-approved request lifetime/cleanup acceptance scope.

**Files touched:**
- `core/obs/src/main/java/com/scenedeck/android/core/obs/internal/KtobsObsClient.kt`
- `core/obs/src/main/java/com/scenedeck/android/core/obs/internal/ObsSessionRunner.kt`
- `core/obs/src/test/java/com/scenedeck/android/core/obs/RequestLifecycleTest.kt`
- `core/obs/README.md`

**Estimated scope:** Small; four source/test/doc files.

## T4: Sanitize OBS connection diagnostics

**Owner:** security_obs.

**Description:** Sanitize OBS connection diagnostics in the existing product/architecture contract, using a bounded evidenced slice.

**Acceptance criteria:**
- [x] Private markers from malformed response/close/transport failures do not enter public diagnostic strings or protocol UI message.
- [x] Typed auth/protocol/unreachable categories and useful numeric codes remain; public constructors remain compatible.
- [x] Existing authentication/reconnect tests continue passing.

**Verification:** `./gradlew :core:obs:testDebugUnitTest`.

**Dependencies:** T3 (shared fake server/source ownership).

**Files likely touched:**
- `core/obs/src/main/java/com/scenedeck/android/core/obs/internal/ObsSessionRunner.kt`
- `core/model/src/main/java/com/scenedeck/android/core/model/ConnectionState.kt`
- `core/obs/src/test/java/com/scenedeck/android/core/obs/FakeObsServer.kt`
- `core/obs/src/test/java/com/scenedeck/android/core/obs/HandshakeTest.kt`
- `core/obs/src/test/java/com/scenedeck/android/core/obs/ConnectionErrorSanitizationTest.kt`

**Estimated scope:** Medium; at most five source/test/doc files per slice.

## T5: Bound screenshot encoded payloads

**Owner:** security_obs.

**Description:** Bound screenshot encoded payloads in the existing product/architecture contract, using a bounded evidenced slice.

**Acceptance criteria:**
- [x] Reject oversized encoded/decoded thumbnail payloads before base64 ByteArray allocation.
- [x] Valid requested thumbnails remain accepted; coordinate exact budget with T6 and document receive-frame limitation.

**Verification:** `./gradlew :core:obs:testDebugUnitTest`.

**Dependencies:** T3; agree image budgets with T6 owner.

**Files likely touched:**
- `core/obs/src/main/java/com/scenedeck/android/core/obs/internal/KtobsObsClient.kt`
- `core/obs/src/test/java/com/scenedeck/android/core/obs/StudioModeTest.kt`
- `core/obs/README.md`

**Estimated scope:** Medium; at most five source/test/doc files per slice.

## T6: Bound screenshot work, cache and pixel allocation

**Owner:** performance_data.

**Description:** Bound screenshot work, cache and pixel allocation in the existing product/architecture contract, using a bounded evidenced slice.

**Acceptance criteria:**
- [x] At most two captures are admitted per awaited cycle with no per-scene coroutine backlog; hot scenes retain priority and monotonic dispatch timestamps.
- [x] Only relevant ready/visibility/previews/scenes/program/preview config restarts capture; removed scenes prune frames/throttle cache.
- [x] Header and encoded-size/pixel bounds precede bitmap allocation; cancellation cannot publish obsolete frames and rejection preserves previous valid frame.

**Verification:** `./gradlew :core:data:testDebugUnitTest --tests 'com.scenedeck.android.core.data.ScreenshotRepositoryTest'`.

**Dependencies:** T2; budgets coordinated with T5.

**Files likely touched:**
- `core/data/src/main/java/com/scenedeck/android/core/data/ScreenshotRepository.kt`
- `core/data/src/test/java/com/scenedeck/android/core/data/ScreenshotRepositoryTest.kt`

**Estimated scope:** Small; at most five source/test/doc files per slice.

### Checkpoint after T6

- [x] Required prior reviews/checks recorded; scoped work is reviewable and buildable.

## T7: Protect confirmed mute/volume state from obsolete rollback

**Owner:** performance_data.

**Description:** Reconcile confirmed mute/volume values and pending write tokens so overlapping failures cannot restore an obsolete optimistic predecessor.

**Acceptance criteria:**
- [x] Failure/cancellation removes only its own pending token and exposes the newest remaining pending or last confirmed value; both failure/acknowledgement orders preserve newer writes/events.
- [x] Authoritative discovery reconciles controls without erasing later events/actions; immediate mute and caller cancellation remain.

**Verification:** `./gradlew :core:data:testDebugUnitTest --tests 'com.scenedeck.android.core.data.MixerRepositoryTest' --tests 'com.scenedeck.android.core.data.OptimisticControlTest'`.

**Dependencies:** T2.

**Files likely touched:**
- `core/data/src/main/java/com/scenedeck/android/core/data/MixerRepository.kt`
- `core/data/src/test/java/com/scenedeck/android/core/data/MixerRepositoryTest.kt`
- `core/data/src/main/java/com/scenedeck/android/core/data/OptimisticControl.kt`
- `core/data/src/test/java/com/scenedeck/android/core/data/OptimisticControlTest.kt`

**Estimated scope:** Small; at most five source/test/doc files per slice.

## T8: Keep Mixer event handling responsive during discovery

**Owner:** performance_data.

**Description:** Keep Mixer event handling responsive during discovery in the existing product/architecture contract, using a bounded evidenced slice.

**Acceptance criteria:**
- [x] Refresh triggers coalesce in bounded/conflated separate lane; no launch-per-event backlog.
- [x] Mute/volume events apply while discovery is blocked; later publication cannot erase newer direct event.
- [x] Latest discovery/cancellation wins and obsolete session/selection cannot publish.

**Verification:** `./gradlew :core:data:testDebugUnitTest --tests 'com.scenedeck.android.core.data.MixerRepositoryTest'`.

**Dependencies:** T7 (shared source/test ownership).

**Files likely touched:**
- `core/data/src/main/java/com/scenedeck/android/core/data/MixerRepository.kt`
- `core/data/src/test/java/com/scenedeck/android/core/data/MixerRepositoryTest.kt`
- `core/data/src/main/java/com/scenedeck/android/core/data/OptimisticControl.kt`
- `core/data/src/test/java/com/scenedeck/android/core/data/OptimisticControlTest.kt`

**Estimated scope:** Small; at most five source/test/doc files per slice.

## T9: Serialize registry deletion with mutations

**Owner:** performance_data.

**Description:** Serialize registry deletion with mutations in the existing product/architecture contract, using a bounded evidenced slice.

**Acceptance criteria:**
- [x] remove/deleteStale share current mutex so a prior reorder/import cannot resurrect deleted entities.
- [x] Gated mutation/deletion regressions prove later delete wins and unrelated metadata survives.

**Verification:** `./gradlew :core:data:testDebugUnitTest --tests 'com.scenedeck.android.core.data.RegistryRepositoryTest'`.

**Dependencies:** T2.

**Files likely touched:**
- `core/data/src/main/java/com/scenedeck/android/core/data/RegistryRepository.kt`
- `core/data/src/test/java/com/scenedeck/android/core/data/RegistryRepositoryTest.kt`

**Estimated scope:** Small; at most five source/test/doc files per slice.

## T10: Batch registry reads and writes

**Owner:** performance_data.

**Description:** Batch registry reads and writes in the existing product/architecture contract, using a bounded evidenced slice.

**Acceptance criteria:**
- [x] Mutation performs one indexed snapshot read and one bulk write without per-name DAO queries.
- [x] Preserve duplicate import counts/order, null-field merge semantics, defaults and curation ordering.
- [x] Counting DAO regressions document deterministic before/after database operations.

**Verification:** `./gradlew :core:data:testDebugUnitTest --tests 'com.scenedeck.android.core.data.RegistryRepositoryTest'`.

**Dependencies:** T9 (deletion serialization prerequisite).

**Files likely touched:**
- `core/data/src/main/java/com/scenedeck/android/core/data/RegistryRepository.kt`
- `core/data/src/test/java/com/scenedeck/android/core/data/RegistryRepositoryTest.kt`

**Estimated scope:** Small; at most five source/test/doc files per slice.

### Checkpoint after T10

- [x] Required prior reviews/checks recorded; scoped work is reviewable and buildable.

## T10a: Restore Inventory transfer fixture bulk-write behavior

**Owner:** planning_quality; independent review by performance_data.

**Description:** ImportDao simulated errors/barriers/counts for upsert but treated
upsertAll as a no-op, bypassing the failure/retry and duplicate-command regressions
after bulk import. Route both methods through one shared simulated write.

**Acceptance criteria:**
- [x] Each bulk call counts once and supports the same started/release/failure
  behavior as a single write; production import remains bulk.
- [x] Actual attempted failure is awaited, one failed write retains the preview,
  successful retry clears it with two total writes; duplicate gate/assertions remain.
- [x] Independent actual-diff review accepts preserved product assertions and
  synchronous compareAndSet/ordered completion semantics.
- [x] Inventory/full regression gate passes after the fixture update.

**Verification:** `./gradlew :feature:inventory:testDebugUnitTest`; full quality gate.

**Dependencies:** T10 and the fresh full-gate fixture failure; necessary verification
correction within the approved import/default/error behavior scope.

**Files touched:**
- `feature/inventory/src/test/java/com/scenedeck/android/feature/inventory/RegistryTransferFailureTest.kt`

**Estimated scope:** Small; one test file, no production change.

## T11: Index deck and graph metadata joins

**Owner:** performance_data.

**Description:** Index deck and graph metadata joins in the existing product/architecture contract, using a bounded evidenced slice.

**Acceptance criteria:**
- [x] Each reduction/build indexes metadata once; per-scene/edge role lookup is constant time.
- [x] Preserve Primary defaults, first-duplicate graph entry semantics, sorting, curation and graph verdicts/cycles on representative collection.

**Verification:** `./gradlew :core:data:testDebugUnitTest --tests 'com.scenedeck.android.core.data.ObsStateRepositoryTest' --tests 'com.scenedeck.android.core.data.SceneGraphCycleTest'`.

**Dependencies:** T2.

**Files likely touched:**
- `core/data/src/main/java/com/scenedeck/android/core/data/ObsStateRepository.kt`
- `core/data/src/main/java/com/scenedeck/android/core/data/SceneGraphBuilder.kt`
- `core/data/src/test/java/com/scenedeck/android/core/data/ObsStateRepositoryTest.kt`
- `core/data/src/test/java/com/scenedeck/android/core/data/SceneGraphCycleTest.kt`

**Estimated scope:** Medium; at most five source/test/doc files per slice.

## T12: Deduplicate widget state and clear connected-empty deck

**Owner:** planning_quality.

**Description:** Deduplicate widget state and clear connected-empty deck in the existing product/architecture contract, using a bounded evidenced slice.

**Acceptance criteria:**
- [x] Widget projection ignores unrelated deck metadata/transition emissions while names/program/connection/ready changes update it.
- [x] Ready empty deck clears stale scenes/program actions; unready preserves cached cold-start grid with truthful header.
- [x] poke remains unconditional; updates remain serialized and removed-widget failures isolated.

**Verification:** `./gradlew :app:testDebugUnitTest --tests 'com.scenedeck.android.widget.*Test'`.

**Dependencies:** T2; independently accepted by performance_data.

**Files likely touched:**
- `app/src/main/java/com/scenedeck/android/widget/SceneDeckWidgetUpdater.kt`
- `app/src/main/java/com/scenedeck/android/widget/SceneDeckWidgetState.kt`
- `app/src/test/java/com/scenedeck/android/widget/WidgetUpdateTest.kt`

**Estimated scope:** Medium; at most five source/test/doc files per slice.

## T12a: Sanitize transport failure snackbar text

**Owner:** planning_quality.

**Description:** Stop arbitrary OBS request comments/Throwable messages entering stream/record UI errors; keep useful sanitized category/status feedback.

**Acceptance criteria:**
- [x] Stream/record failure messages exclude private markers from generic and request errors.
- [x] Preserve useful refusal/connection category and local action context; direct error delivery remains and cancellation is not converted to failure.
- [x] Existing transport/output tests and screen goldens pass.

**Verification:** `./gradlew :feature:live:testDebugUnitTest`.

**Dependencies:** T2; independently accepted in PROPOSAL_REVIEW_v1.

**Files likely touched:**
- `feature/live/src/main/java/com/scenedeck/android/feature/live/LiveViewModel.kt`
- `feature/live/src/test/java/com/scenedeck/android/feature/live/TransportErrorMessageTest.kt` (or equivalent focused ViewModel test)

**Estimated scope:** Small; two or three files if message policy is extracted locally for focused testing.

## T13: Synchronize repository skill/design guidance

**Owner:** planning_quality.

**Description:** Synchronize repository skill/design guidance in the existing product/architecture contract, using a bounded evidenced slice.

**Acceptance criteria:**
- [x] Versions/SDK agree with TECH_STACK and catalog; Lucide guidance describes hand-ported catalogue.
- [x] Earlier recording/stream/card/status wording agrees with current shared studio UX; excluded Replay Buffer requests are removed.
- [x] Guidance derives current source-of-truth contract without new UI behavior.

**Verification:** `Source/doc consistency audit; git diff --check`.

**Dependencies:** T2.

**Files likely touched:**
- `.kimi/skills/android-compose/SKILL.md`
- `.kimi/skills/material3-expressive/SKILL.md`
- `.kimi/skills/obs-websocket-v5/SKILL.md`
- `docs/DESIGN_SYSTEM.md`

**Estimated scope:** Medium; at most five source/test/doc files per slice.

## T14: Document implemented guarantees

**Owner:** planning_quality.

**Description:** Document implemented guarantees in the existing product/architecture contract, using a bounded evidenced slice.

**Acceptance criteria:**
- [x] New bounded-request/image/widget/mutation guarantees match reviewed implementation and meaningful tests; synchronize OBS cleanup wording after T3a passes.
- [x] No measured device/frame-time improvement claimed; deterministic work/call-count/resource evidence and compatibility limits stated.

**Verification:** `Review matching source/tests and docs`.

**Dependencies:** T3–T13.

**Files likely touched:**
- `AGENTS.md`
- `docs/ARCHITECTURE.md`
- `docs/FEATURE_SPEC.md`
- `docs/HARDENING.md`
- `docs/ROADMAP.md`

**Estimated scope:** Medium; at most five source/test/doc files per slice.

### Checkpoint after T14

- [x] Required prior reviews/checks recorded; scoped work is reviewable and buildable.

## T15: Final independent review and complete verification

**Owner:** Independent reviewers; root runs central Gradle, security_obs interprets results.

**Description:** Final independent review and complete verification in the existing product/architecture contract, using a bounded evidenced slice.

**Acceptance criteria:**
- [x] Independent final diff review has no unresolved introduced issue; formatting/lint/static security and meaningful tests pass.
- [x] JDK21/uv scripts/check-quality.sh, ./gradlew assembleDebug and ./gradlew testDebugUnitTest succeed.
- [x] Changed screen previews render if applicable; final report distinguishes demonstrated guarantees, deferred findings and environment limitations.

**Verification:** `scripts/check-quality.sh; ./gradlew assembleDebug; ./gradlew testDebugUnitTest; git diff --check`.

**Dependencies:** T3–T14.

**Files likely touched:**
- Versioned reports under `tasks/reports/`; plan/task state only.

**Estimated scope:** Small; at most five source/test/doc files per slice.

### Checkpoint after T15

- [x] Required prior reviews/checks recorded; scoped work is reviewable and buildable.

## Completed verification

All scoped criteria and final checks pass. Root executed final quality and explicit
debug build/unit commands; both exit zero. Independent v4 specialist reports and
FINAL_REVIEW_v1 record 426 passing tests, 111 render tests, scanner target evidence
and the preserved measurement/resource/dependency limitations.
