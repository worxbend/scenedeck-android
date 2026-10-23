# Final security/OBS and independent QA verification v3

Date: 2026-10-04. This supersedes the pending final-gate status in
SECURITY_OBS_VERIFICATION_v2.md. Root owned all serial Gradle execution;
security_obs independently inspected completed logs, current JUnit XML and source
corrections. No application change remains pending in this review.

**Completion held after this snapshot:** peer review is checking the scanner's
actual inclusion of newly created, unstaged sources. The successful script and
203-target result below describe the observed run; they do not prove broader
coverage. Any corrected source-inclusive gate will be recorded in v4 before final
completion. Application builds and all 426 tests remain passing.

## Completed gates

| Gate | Evidence | Result |
|---|---|---|
| JDK21 + uv `scripts/check-quality.sh` | `/tmp/scenedeck-hardening-quality-pass.log`, process 14110 exit 0 | Pass |
| Gradle qualityCheck: formatting, Detekt, Android lint, unit tests, debug build | Same log: BUILD SUCCESSFUL in 14s; 909 actionable tasks | Pass |
| Semgrep | Seven rules, 203 scanner-reported targets, zero findings; SARIF executionSuccessful=true | Pass |
| Gitleaks history and working tree | 30 commits and ~221.81 MB working tree; both no leaks found | Pass |
| Explicit `assembleDebug testDebugUnitTest` | `/tmp/scenedeck-hardening-build-unit.log`, process 4943 exit 0; BUILD SUCCESSFUL in 3s | Pass |
| Final `git diff --check` | Independently executed after completed gates | Pass |

The explicit command reused valid up-to-date outputs from the immediately preceding
successful quality run. Test counts below are those outputs, not a claim that every
unchanged test executed again in the three-second command.

## Actual test results

Final XML records **426 tests across 85 suites, zero failures, errors or skips**.
The total includes **111 passing Roborazzi render tests in 22 suites**. Preview
coverage includes Connections/editor six tests, Onboarding three and the living
StudioStylebook three. Widget composition and its preference projection also pass.

| Module | Suites | Tests |
|---|---:|---:|
| app | 10 | 53 |
| core:data | 21 | 135 |
| core:designsystem | 17 | 69 |
| core:obs | 9 | 48 |
| feature:connections | 5 | 23 |
| feature:doctor | 1 | 1 |
| feature:graph | 1 | 3 |
| feature:inventory | 4 | 16 |
| feature:live | 6 | 29 |
| feature:mixer | 5 | 25 |
| feature:onboarding | 1 | 3 |
| feature:settings | 2 | 7 |
| feature:stats | 3 | 14 |

Critical regression suites pass: RequestLifecycle 11, ConnectionErrorSanitization
four, ScreenshotRepository eight, MixerRepository 11, OptimisticControl five,
RegistryRepository ten, ObsStateRepository seven, SceneGraphCycle eight,
WidgetUpdate four, TransportErrorMessage two and RegistryTransferFailure two.
The last two Inventory fixtures now exercise the actual bulk DAO write; their
failure/retry and duplicate-confirm assertions are preserved.

## Final accepted behavior and review

Three rounds of eight dropped requests and three rounds of eight caller
cancellations each leave the actual upstream UUID store empty, recover all permits
and permit a healthy follow-up request on the same connection. Tests also prove
late replies, queued old-session commands and completed old-session responses do
not contaminate a replacement session. Parent cancellation remains cancellation;
own request deadlines become typed recoverable failures. Ambiguous writes are
never automatically replayed.

Request continuations, initial GetVersion and the explicitly wrapped complete
receiver share one serial execution lane while eight requests may remain suspended
in flight. Peer review challenged the receiver's Ktor-owned scope and verified the
pinned ktobs 0.5.0 UUID critical sections do not suspend while holding their mutex.
This dependency assumption is documented for rechecking on upgrade. No production
reflection, global UUID clearing or client rewrite was introduced.

The initial handshake and initial GetVersion each already have a separate default
ten-second deadline. GetVersion's deadline encloses permit and dispatcher admission
plus the response, so startup cannot stall indefinitely; sequential phases may take
roughly twenty seconds in total. Existing delayed-version tests cover caller/scope
cancellation and disconnect supersession. This check required no new source change.

Actual remote parser/status/authentication markers remain absent from exposed
connection/request diagnostic strings and Live snackbar messages. A safe coroutine
stacktrace recovery copy may appear in the cause chain; raw parser exceptions and
server comments are not retained by the sanitized boundary.

The final data, widget, Live sanitation and shared-document changes are independently
accepted. Data review covers bounded capture jobs, allocation guards, cancellation
before publication, optimistic confirmed/pending ordering, latest discovery,
serialized deletes and one-snapshot bulk import, preserved new-item sort order,
and first-duplicate graph lookup semantics. Widget review covers distinct visible
projection, connected-empty clearing and preserving cached actions while unready.
Current documentation accurately describes these tested guarantees.

The screenshot guards bound decoded compressed bytes to 2 MiB and bitmap dimensions
to 1,048,576 pixels before pixel allocation. They do not retroactively bound an
already-received WebSocket frame or impose an aggregate bitmap-cache limit; current
scene count still affects cache size. Pending Mixer tokens are proportional to live
writes. No measured device-wide speedup or universal security certification is
claimed. Historical reports retain the initially failing evidence and its fixes;
the completed results above supersede their pending verification rows.
