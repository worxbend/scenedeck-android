# Controlled multi-action presets — W7

Status: scoped storage, read-only preparation, guarded execution and History run records
are implemented; verification is recorded in progress.md. **There is no Presets editor or
invocation UI yet.** W7b/c/d and W6a remain open until that user flow is verified. Foundations are not a completed
operator feature. Show rundown, cue lists and timed advancement remain excluded.

## Stored definition

Room schema 10 adds `action_presets`, keyed by connection profile, collection and local
preset UUID. A normalized name key is unique within that workspace. Definitions survive
profile removal like decks/preflight; no OBS collection data is modified. The repository
exposes explicit loading, ready, failed and unavailable states with origin checks.

Keep at most 32 presets per workspace, each with 1–12 ordered steps and an 80-character
trimmed local name. Repeated controls are retained in order. OBS target names keep their
literal identity, including spaces/case, and have a 1,024-character bound. Versioned JSON
has a 32 KiB character bound; unknown versions, fields or step types fail closed. A preset
cannot contain scripts, delays, conditions, loops, collection edits or arbitrary requests.

The typed allow-list is:

- Set a program scene, or set a preview scene with Studio mode enabled.
- Set input mute on/off.
- Set input volume from zero to unity, matching the Mixer fader range.
- Set media playback to playing, paused or stopped.
- Set stream or recording active/inactive. This does not imply resuming a paused recording.

IDs are captured once per new draft. A retry of the same committed initial save is
idempotent; it cannot overwrite a later revision. Existing editors/deletion compare
the full original definition and revision inside the transaction. Changed-then-reverted
content still invalidates an older editor. Save/delete recheck workspace and cancellation
before commit, and a stale existing editor cannot recreate a deleted preset. Names and
bounds are checked transactionally, including concurrent writers.

## Read-only preparation

`ObsCapabilities.controls` exposes typed groups only when the server advertises the
necessary reads and writes in GetVersion. Wire request names remain in `:core:obs`.
No capability is inferred from a version or a successful connection.

`PresetPreparation` reads the saved revision and a snapshot of input locks, audio
allow-list and Output Safety. It probes at most three steps concurrently, each with
an eight-second deadline, and bounds the whole preparation to 30 seconds. Checks stay
in stored order. Missing targets, unavailable capabilities, locked/excluded inputs,
Studio-off preview, unready media and unknown reads are distinct from eligible controls.
No check invokes an OBS write. Workspace/session guards wrap every read and publication.

The saved preset and relevant settings must still match when preparation finishes.
Failures are sanitized; caller cancellation stops child reads. A failed check leaves
other successful readings intact but blocks Run. A whole-preparation timeout has no
usable plan. `CURRENTLY_MATCHES` means only that the latest read matched: earlier preset
steps or external controls can change it before execution. It must never cause the runner
to pre-skip a later step. Quiet volume matching is relative; zero requires actual zero.
Reconnecting streaming is not a stopped output. Contradictory recording readings are unknown.

`PresetPlan.outputConfirmations` uses the shared OutputSafetyGate for target operations,
including currently matching outputs. This is preparation metadata, not authorization to
write. The executing flow must retain explicit consent to the concrete ordered targets.

## Executing foundation

PresetRunner owns a singleton invocation gate and publishes immutable PresetRunState.
Callers pass the concrete prepared plan and acknowledged output-step indices. Preparations
older than 60 seconds, invalid steps or missing required output acknowledgements send no
OBS requests. The UI must still own fresh, single-use consent and dismiss it on navigation,
selection/definition/context change. The backend API is not a substitute for this UI.

Execution subscribes directly to stamped events before reads, then observes context,
definition, relevant policy and busy media/recording state. Rename/removal of a referenced
target stops the run without retargeting. Every step validates the saved definition and
policy, reads the target, validates again, and only then decides to skip or write. Writes
use explicit values through ObsClient.withSession and the protocol admission guard.
A read started after write acknowledgement must confirm the target within four seconds
before advancing. The whole run is bounded to 90 seconds. No retries or rollback occur.
Manual actions and other clients can interleave; this is not an OBS transaction.

Progress distinguishes pending/checking/writing, confirmed, already matched, rejected,
unknown and not run. Cancellation of a checking step leaves it not run; a possibly
dispatched write becomes unknown. Caller cancellation propagates after a bounded two-second
History finalization. Internal invalidation cancels the run without cancelling its caller.
A run ID scopes Cancel so obsolete actions cannot cancel a replacement invocation.

## History integration

Schema 11 stores at most 50 run records per observation, independently of its 500 events.
Each record holds a snapshot of preset identity/name/revision, ordered typed targets,
step results, typed outcome/reason and timestamps, within a strict 40 KiB character JSON
bound. The original observation/elapsed position is captured synchronously before the
invocation suspends. No association is invented later if History is disabled/unavailable.
A full run-record allowance is explicit and does not prevent the controls from running.

When captured, start and each progress transition save before the next write. Final
updates remain allowed after normal observation closure or capture disable; clear/delete
and pruning cascade and no update recreates a row. Scope, process ownership, immutable
definition and increasing revision are checked transactionally. Storage failure stops
remaining steps, with a bounded final save attempt. A row left unfinished by failed
persistence describes its last saved state, not proof that execution is still active.

LocalProcessIdentity is stable across History capture retries. Startup recovery of older
processes retains completed steps, changes WRITING to UNKNOWN, remaining steps to NOT_RUN,
and marks the outcome interrupted with unknown finish time. It never resumes controls.
Timeline results remain at the original invocation point even if finished after disconnect.
History export version 2 includes the typed records and their retention limit, excluding
process identity, credentials, endpoints, raw errors and recording file paths.

## Remaining UI and verification

1. Add Presets under More with scoped list, named CRUD, reorderable step editor and literal
   target selection. Retain failed-save drafts, show unavailable references, use shared
   Studio components and respect aliases without rewriting OBS target identities.
2. Show a concrete prepared summary and required output confirmations. Opening unrelated
   setup/Preflight dismisses pending consent. A stale/expired/changed selection cannot
   execute. Clear a consumed plan so one dialog action cannot invoke twice.
3. Wire the singleton runner to a deliberate user action, scoped cancellation and persistent
   visible partial/unknown results. Keep the run tied to its original workspace while
   navigating; resolve screen lifecycle deliberately, without duplicate runs on recreation.
4. Exercise editor/run interaction, missing/renamed references, busy media/recording tools,
   policy/context changes during consent/admission, History disabled/full/failure/clear,
   caller cancellation, process interruption, export and required theme/compact renders.
   Backend tests and History result renders alone do not complete W7b/c/d or W6a.

No real OBS/device or performance measurement is established by JVM/fake-server checks.
