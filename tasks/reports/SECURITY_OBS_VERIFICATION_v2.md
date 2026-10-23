# Security/OBS/data verification v2

Date: 2026-10-04. This supersedes the focused status in
SECURITY_OBS_VERIFICATION_v1. Root executed the serial Gradle commands; security_obs
independently read fresh logs/XML and reviewed the corrections.

`/tmp/scenedeck-hardening-focused-core-rerun2.log`: **BUILD SUCCESSFUL in 9s**.
Both OBS/data test tasks executed. Actual JUnit XML: **30 suites, 183 tests,
zero failures, errors or skips**.

| Focused module/suite | Tests | Result |
|---|---:|---|
| core:obs, 9 suites | 48 | Pass |
| RequestLifecycleTest | 11 | Pass |
| ConnectionErrorSanitizationTest | 4 | Pass |
| core:data, 21 suites | 135 | Pass |
| ScreenshotRepositoryTest | 8 | Pass |
| MixerRepositoryTest | 11 | Pass |
| OptimisticControlTest | 5 | Pass |
| RegistryRepositoryTest | 10 | Pass |
| ObsStateRepositoryTest | 7 | Pass |
| SceneGraphCycleTest | 8 | Pass |

The observed first-run seven-waiter leak is resolved in the meaningful concurrent
regressions: three eight-request timeout rounds and three eight-request cancellation
rounds each leave the actual UUID store empty, and healthy follow-up requests succeed
without reconnect/reset. Queued/completed old-session work, parent cancellation,
late replies and marked server/parser diagnostics remain covered and passing.

The data graphics fixture required both native mode and an explicit SDK34. Pinned
Robolectric's absent-manifest default selected SDK23, while native graphics selection
requires at least SDK26. Native SDK34 now verifies invalid compressed bytes, oversized
byte/pixel rejection and retention of a prior accepted frame. No decoder assertion was
weakened and no production data code changed to accommodate the test. The Mixer test
retains a subscriber while probing real IO, matching its WhileSubscribed contract.

The pre-existing getSourceScreenshot override parameter warning is corrected and its
source compiled/tested in this fresh passing run. No Kotlin warning/error lines remain
in this log. Expected native decoder messages for malformed input are not test failures.

Sources are frozen and `git diff --check` passed before the fresh formatting/rerun.
Final full quality/build/unit checks are still owned by root and pending; focused
success does not substitute for those gates. Earlier reports preserve the first failures,
accepted confinement mechanism and resource/measurement limits.
