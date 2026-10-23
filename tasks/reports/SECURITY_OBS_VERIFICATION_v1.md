# Security/OBS verification v1

Date: 2026-10-04. Independent implementation review: performance_data. Execution:
root owns all Gradle processes; security_obs inspected actual logs and JUnit XML.

## Fresh focused results

`/tmp/scenedeck-hardening-focused-core-rerun.log` executed the post-correction OBS
suite. Actual XML contains **9 suites, 48 tests, zero failures, errors or skips**.
The combined command failed solely on one remaining data graphics-fixture test;
the OBS task itself completed successfully. Data owner is correcting native SDK
selection; data/final whole-project verification remains pending.

RequestLifecycleTest: **11/11 passed**. These include three batches of eight
concurrent timeouts and three batches of eight explicit caller cancellations,
each checking the pinned dependency's actual UUID store returns to zero. Healthy
requests after those batches passed on the same session, so the remedy releases
bookkeeping without forcing reconnects or retrying expired writes. Admission
deadlines, parent timeout/cancellation, unmatched late replies, queued replacement
commands and completed-old-response guards also passed.

ConnectionErrorSanitizationTest: **4/4 passed**. Malformed initial response,
authentication close reason, unreachable transport cause and caller-created model
diagnostic strings preserve categories while excluding private markers. Request
rejection/private comment and malformed post-Ready parser cause-chain tests passed.
StudioMode's compressed-byte padded/unpadded boundary test passed. Existing
handshake, reconnect, bounded concurrent request, event, meter and power/studio
regressions remained passing.

## Corrected dependency interaction

The first fresh run measured seven retained UUID waiters after eight concurrent
timeouts, despite permits being released. That failure was corrected, not deferred.
Typed requests, the initial direct GetVersion, and the entire runDefaultReceiver
body explicitly share one limitedParallelism(1) execution view. Receiver wrapping
is necessary because ktobs launches it in the Ktor WebSocket scope. Installed
ktobs 0.5.0 bytecode shows no suspension while its UUID mutex critical sections
hold the lock. Sequential execution prevents cleanup contention while permitting
eight suspended requests. Recheck that assumption on a ktobs upgrade.

The official coroutines 1.11 documentation confirms sequential execution between
suspensions for [limitedParallelism(1)](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-coroutine-dispatcher/limited-parallelism.html)
and cancellation checks only when [Mutex.lock suspends](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.sync/-mutex/lock.html).
The pinned dependency bytecode plus the actual multi-request regressions establish
the application-specific remedy; no production reflection or dependency fork is used.

Coroutine stack-trace recovery legitimately copied the fixed protocol exception
into its own safe cause chain. The corrected test checks private-marker exclusion
throughout that chain rather than demanding a null cause. Raw parser causes/comments
are still discarded at the boundary.

## Current source and remaining gate

One pre-existing Kotlin warning was also corrected after focused PASS: the
getSourceScreenshot override now uses the interface's `request` parameter name
and qualifies the private helper as `this.request`. This changes no request
behavior; the final gate will compile/test that naming alignment.

All sources are frozen pending final formatting/quality/build/unit verification.
`git diff --check` passes. No source boundary or credential/LAN/backup policy changed.
The screenshot compressed-byte guard does not cap already received WebSocket frames
or total current-collection bitmap memory. No device frame-time/latency gain is claimed.
Final whole-project evidence will supersede this focused-only verification status.
