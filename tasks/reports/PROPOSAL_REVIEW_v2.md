# Proposal/implementation review v2

Date: 2026-10-04. Independent reviewer: security_obs. Human approved implementation.
This supersedes the proposal-only state of PROPOSAL_REVIEW_v1. Focused/full test results
are pending with planning_quality and will be recorded in final verification.

| Actual implementation | Decision | Evidence / correction / limit |
|---|---|---|
| OBS deadline/origin session | Accept pending focused tests | Deadline spans admission and wire response; non-null Result wrapper; caller cancellation retained; identity checks before admission and after return. Deterministic queued/completed replacement tests and actual UUID-store cleanup assertions. |
| OBS connection/request diagnostics | Accept pending focused tests | Fixed boundary messages/no raw comments/causes; model diagnostic override protects feature-created errors. Peer identified request response-conversion leak; fixed ObsRequestProtocolException and malformed GetStats + later recovery test added. |
| Screenshot work/configuration | Accept pending focused tests | Awaited two-child batches, monotonic dispatch timestamps, relevant immutable projection, no per-scene waiter queue, cooperative yield. Slow two-hot batch reserves a cold slot; cancellation before publication and config joins children. |
| Image allocation limits | Accept pending focused tests | Shared 2 MiB compressed limit before base64 copy/allocation; header positive dimensions and Long pixel multiplication before data-layer decode. Limits exclude receive-frame and aggregate current-scene cache size. |
| Mixer confirmed/pending controls | Accept pending focused tests | Boolean/Double concern justifies narrow generic tracker. Completion removes its own token; revision prevents stale acknowledgment; latest observation invalidates old pending; read-start discovery revision preserves newer edits. Non-suspending finally cleans cancellation. Token count follows live caller writes, not hard count cap; requested multi-token cleanup and discovery/pending integration checks. |
| Mixer separate discovery worker | Accept pending focused tests | StateFlow generations conflate triggers; 300 ms debounce, collectLatest, publication generation/Ready/cancellation checks. Direct mute/volume reducers do not wait for probe network calls. Held-probe and burst tests cover regression trigger. |
| Registry deletion/batching | Accept pending focused tests | Existing mutation mutex includes remove/deleteStale, empty successful-name safeguard preserved. One indexed snapshot/bulk write; ordered duplicate import updates map before next duplicate. Review caught default sortOrder0; restored Int.MAX_VALUE with regression. |
| Deck/graph role indices | Accept pending focused tests | Unique Room names for deck; graph putIfAbsent preserves public first-duplicate semantics. Default Primary and sorting/traversal unchanged. |
| Widget updates/connected-empty | Accept pending focused tests | Projection includes six names/program/label/typed Ready; unrelated fields suppressed. Ready empty writes empty names/program, unready keeps cached grid with truthful label. Current read under mutex, unconditional poke, failure isolation, no collectLatest cancellation mid-write. |
| Live transport errors | Accept pending focused tests | Fixed action/category guidance excludes arbitrary Throwable/comment/status marker. Cancellation flow unchanged; marker tests include generic/request errors. |

No introduced source issue remains from the reviewed actual diffs. Acceptance depends on
required serial formatting, meaningful regressions and full quality/build/unit gates.
Raw hand-edited Kotlin layout will be normalized by the single QA owner. No larger
architecture rewrite, measured device performance claim or speculative TLS/cache policy
was introduced.
