# SECURITY_OBS_v1 — defensive security and OBS lifecycle audit

Date: 2026-10-04. Status: initial audit complete; application implementation not started.

## Scope and method

Read AGENTS.md and FEATURE_SPEC, TECH_STACK, ARCHITECTURE, DESIGN_SYSTEM, ROADMAP. Inspected OBS transport/handshake/events/requests, profile persistence, Keystore crypto, manual/QR parsing, scene automation links, application export/backup policy, connection test state, and existing regression tests. Inspected the locally resolved **ktobs 0.5.0 bytecode** with `javap` to verify behavior at the dependency boundary; no external OBS endpoint was probed.

Applied project android-compose and obs-websocket-v5 skills, Kotlin Coroutines, Kotlin Null Safety, API and Interface Design, defensive local portions of API Security, and the user-supplied AI review workflow. API Security target-probing instructions are not relevant to read-only local source review. No dependency upgrade or production security scanner installation is proposed.

## Findings accepted for a concrete implementation plan

### OBS-R1 — unanswered requests can permanently exhaust the control lane

Severity: **HIGH reliability / performance**.

Evidence: `core/obs/src/main/java/com/scenedeck/android/core/obs/internal/KtobsObsClient.kt:159` acquires `Semaphore(8)` and invokes the ktobs request without a deadline. `connectTimeoutMs` only covers handshake/GetVersion (`ObsSessionRunner.kt:287`). Bytecode for pinned `com.rejeq.ktobs.RequestKt$callMethod$4` and `$callUnitMethod$4` sends the request and awaits its deferred directly; there is no request timeout. StatsRepository and several repositories perform requests without their own deadline.

Trigger: an authenticated OBS-compatible server keeps WebSocket traffic alive but omits eight request responses. All eight permits remain held. Subsequent scene/output commands and telemetry requests wait indefinitely, even though the connection still says Ready.

Minimal fix: add an injectable request deadline covering **permit wait and response wait**. Translate only that deadline into a dedicated recoverable `ObsRequestTimeoutException`; propagate caller `CancellationException` unchanged. Do not retry writes: a timeout does not establish whether OBS applied a command. Keep the eight-permit limit.

Meaningful regression: fake server drops GetStats responses, eight calls occupy the lane, a ninth queued call has a bounded outcome; an ordinary later request works after permits release. Verify caller cancellation propagates, delayed responses cannot poison the receiver, and pending UUID bookkeeping does not grow over repeated timed-out calls. Pinned ktobs UUID removal uses a cancellable mutex in a finally block, so validate cleanup rather than assuming it.

Proposed slice: KtobsObsClient.kt, ObsClient.kt, FakeObsServer.kt, new RequestLifecycleTest.kt, core/obs/README.md (five files). Verification: `./gradlew :core:obs:testDebugUnitTest` plus the final repository gate.

### OBS-R2 — queued work chooses its session after the wait

Severity: **MEDIUM correctness**, especially for output writes during rapid reconnect/profile changes.

Evidence: `KtobsObsClient.kt:160–165` obtains a permit before looking up `sessionRunner.session`. A request's intended session is therefore not retained while queueing. Request completion also does not check session identity. Existing ReconnectTest only verifies maximum in-flight requests and ordinary reconnects, not superseded queued writes.

Trigger: work waits for a permit while a connection is replaced; it can then use the replacement session instead of failing with the old intent. An old read response can similarly complete after session replacement and be applied to current repository state. This is an identified race boundary; it must be demonstrated deterministically in regression coverage rather than described as an observed production incident.

Minimal fix: capture the Ready session **before** queueing, recheck identity and readiness after obtaining the permit, and recheck before returning the response. Fail obsolete work with ObsNotConnectedException. Keep this in the existing request helper; no new architecture layer is needed.

Regression: hold a queued output write deterministically, replace the session, release the wait, and assert no command reaches the replacement server; late old-session read cannot return as current state. Implement in the OBS-R1 slice to avoid parallel edits to the request helper.

### OBS-S1 — connection diagnostics retain raw server/serialization/transport details

Severity: **MEDIUM privacy / hardening contract**.

Evidence: `ObsSessionRunner.kt:299–315` copies authentication/close reasons and `SerializationException.message` into ConnectionError and preserves raw Throwable in Unreachable. `core/model/.../ConnectionState.kt:30–40` uses default data-class diagnostic strings. `feature/connections/.../ProfileEditSheet.kt:348` and onboarding's `connectionErrorMessage` render Protocol.message directly. ConnectionsViewModel's test-handshake catch similarly retains raw exceptions.

Trigger: malformed handshake/GetVersion data or transport failure embeds sensitive diagnostic text in its exception; protocol errors are shown verbatim, while public state `toString()` can include raw exception/server text. The current UI already hides auth/unreachable details, so do not claim all branches show credentials. No actual plaintext credential leak was observed; the concrete defect is failure to enforce the documented sanitized boundary.

Minimal fix: map inbound errors to concise safe messages at the OBS boundary, retain typed error category and numeric close/RPC codes, and make ConnectionError diagnostic strings safe even if callers supply a Throwable or arbitrary server message. Preserve constructor compatibility to avoid broad consumer changes. Public Protocol.message from the OBS client must also be safe, since redacting toString alone does not protect the UI.

Regression: fake response/close reason contains a unique private marker; assert typed authentication/protocol/transport error diagnostic strings do not include it, and the protocol UI-facing message is safe. Verify existing terminal-auth/reconnect semantics.

Proposed slice: ObsSessionRunner.kt, ConnectionState.kt, FakeObsServer.kt, HandshakeTest.kt, new ConnectionErrorSanitizationTest.kt (five files; sequence after R1 because FakeObsServer is shared). Verification: `./gradlew :core:obs:testDebugUnitTest`; connection-screen goldens remain part of the final gate.

### OBS-S2 / DATA-S2 — screenshot payload and bitmap dimensions are unbounded

Severity: **MEDIUM memory safety / performance**.

Evidence: `KtobsObsClient.kt:83–85` decodes arbitrary base64 into another ByteArray; `ScreenshotRepository.kt:127–128` calls BitmapFactory.decodeByteArray without first reading dimensions. Requesting width 360 does not guarantee a trustworthy response or bound a very tall image's pixel count.

Trigger: selected OBS-compatible server returns an oversized encoded image or extreme dimension image. Decoding introduces large extra allocations and may exhaust memory. The current WebSocket input frame is already received before the image helper runs; image caps alone are not a full network frame-limit defense.

Minimal fix: cap encoded/decoded screenshot payload before base64 allocation in core:obs; in core:data read image bounds before allocation and reject unsafe dimensions/pixel counts (using Long arithmetic). Preserve previous good thumbnail on rejection. Agree limits from intended 360 px thumbnail use, document them, and test limits instead of inventing a general image pipeline.

Coordination: performance_data owns bitmap-dimension safeguards; this reviewer can implement core:obs encoded guard as a small separate slice after approval. Focused tests: core OBS decoding boundary plus Robolectric thumbnail decode limits; malformed/rejected capture keeps prior frame.

## Existing protections to preserve

- AES-256/GCM key is in AndroidKeyStore; ciphertext alone enters private preferences. Crypto round-trip, wrong-key and tamper tests already exist. No plaintext Room/DataStore password storage was found.
- Secret drafts/QR target redact passwords from toString and intentionally remain outside saved-state Bundles; existing ProfilePasswordState and model-string tests cover this.
- Backup is disabled and Android data-extraction rules exclude private app storage. Services/notification receivers are unexported.
- LAN cleartext WebSocket is an explicit product contract, not an unsolicited TLS migration candidate.
- QR input is length-bound and rejects user-info, malformed ports, duplicate literal parameters, unexpected paths/fragments and encoded authorities. Camera teardown only unbinds owned use cases and has atomic disposed/reported guards.
- Profile persistence uses noncancellable best-effort rollback and sanitized save/delete UI state. Tests cover common rollback failures. No redesign of this boundary is justified by a style principle alone.
- OBS protocol types stay inside core:obs; the existing client facade, session runner, event dispatcher, repositories and feature UDF boundaries are appropriate. GoF/SOLID are review criteria, not reasons to add layers.

## Deferred or rejected speculative churn

- Do not rewrite the OBS client or add networking layers solely to apply a design pattern.
- Do not restrict connection hosts to public/LAN address ranges: selecting arbitrary hosts is existing remote-control behavior.
- IPv6 support and stricter manual host grammar have UX inconsistencies but are separate feature decisions, not demonstrated security exploits.
- General message/scene-name/card-count limits require compatibility budgets; do not claim image-byte guards bound all WebSocket receive allocations.
- No blanket swallowing of CancellationException, no timeout automatic retry of writes, no blanket detekt suppression, and no dependency pin change without a verified need.

## Peer proposal challenge — performance_data

Reviewed ScreenshotRepository.kt and MixerRepository.kt directly.

1. **Accept screenshot queue/pruning fix.** Current poll loop launches every due scene independently (`ScreenshotRepository.kt:92–97`); its semaphore bounds running capture only. A collection taking longer than the refresh interval accrues additional waiting captures. Removed-scene caches are never pruned while remaining Ready/subscribed. Prefer a bounded worker scheduler with at most one pending/running capture per scene and promptly prioritize hot scenes; avoid spawning one coroutine per scene on every tick. Prune throttle/frame keys with current scene names. Check cancellation before publishing decoded frames so a cancelled prior collection cannot recreate a removed entry. Tests must hold responses across multiple refresh periods and measure calls/queue bounds rather than only checking a semaphore.

2. **Accept mute revision fix.** Old failing mute writes unconditionally restore `previous` (`MixerRepository.kt:244–253`), unlike existing versioned volume rollback. An OBS event or a newer mute action can be overwritten. Add mute-specific revision tracking; OBS events and authoritative discovery invalidate older mutations. Reuse the existing small private pattern, without an externally visible generalized command framework. Regression: delayed first write fails after newer event/action, latest state remains; latest failure still restores its own prior value.

3. **Accept nonblocking discovery event handling, with conditions.** Mixer lifecycle event collector awaits full network discovery (`MixerRepository.kt:130–141`), delaying subsequent mute/volume events. Launching an unbounded discovery job for each event would move the backlog rather than solve it. Coalesce refresh signals in a bounded/conflated lane, keep current event patches fast, and preserve authoritative/later-event ordering using cancellation/version protection. Tests must send a mute event while discovery is blocked and assert prompt application, then release discovery and assert it cannot erase that newer event.

4. **Accept bitmap-bound decoder, with conditions.** Use header bounds before pixel allocation and Long pixel arithmetic. Encoded length and pixel limits are complementary. Tests should use synthetic oversized headers, avoid allocation of oversized fixtures, and preserve previous image on failure. Keep existing dark-frame behavior out of this security/performance slice unless separately accepted.

Independent proposal conclusions sent to performance_data and root. No implementation or focused test execution has occurred in this report; root's QA agent owns build scheduling and has reported baseline quality PASS.

Follow-up peer evidence: `feature/live/.../LiveViewModel.kt:327–331` also renders arbitrary Throwable.message and raw ObsRequestFailedException text for stream/record failures. This path is outside ConnectionError sanitation. Accept a separate small UI-error sanitization slice (ViewModel + transport-error regressions), showing safe category/status and generic fallback rather than server response comments. See PROPOSAL_REVIEW_v1 for the full peer acceptance ledger.

Primary reference checked: [Kotlin cancellation and timeouts](https://kotlinlang.org/docs/cancellation-and-timeouts.html). Dependency behavior conclusions above are based on the installed pinned bytecode, not unverified upstream latest code.
