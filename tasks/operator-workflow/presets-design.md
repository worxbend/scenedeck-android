# Controlled multi-action presets — W7

Status: scoped storage and read-only preparation implemented; verification is recorded
in progress.md. **There is no Presets editor, invocation or executing runner in the app
yet.** W7b/c/d and actual History integration remain open. Foundations are not a completed
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

## Remaining execution and UI work

1. Add Presets under More with a scoped list, named CRUD, reorderable step editor and
   literal target selection. Use the shared Studio headers/components, retain failed-save
   drafts, show unavailable references, and respect local aliases without changing targets.
   References renamed/removed in OBS must be resolved explicitly or reported unavailable;
   do not silently retarget an executing sequence.
2. Show a concrete prepared step summary with per-step availability and output safety.
   Opening unrelated setup/Preflight must dismiss pending consent. A stale selection,
   changed definition/workspace or expired preparation cannot execute from an old dialog.
3. Build one bounded invocation at a time with immutable progress/results. Re-read each
   target immediately before writing, including locks/capability/target guards, and
   reconcile a read begun after acknowledgement before advancing. Use explicit values,
   preserve completed steps, and stop on failure, unknown, cancellation or replacement.
   No automatic retry and no rollback of completed actions. Manual OBS/other-client
   actions may interleave; this is not an atomic transaction on OBS.
4. Keep the invocation bound to its original context across suspension, including queued
   request admission. Cancel obsolete targets on observed rename/removal or context loss.
   A write already dispatched when canceled may have taken effect; mark it unknown and
   never label all steps rolled back or unsent. Handle media/recording tools already busy.
5. Persist actual partial/unknown results in the original History observation when capture
   is enabled. Capture that association at invocation, not completion. A disconnect can
   close the observation before a result arrives; the integration must preserve that
   result without retargeting or resurrecting deleted history. The current generic history
   append rejects closed observations, so do not ignore that failure or assume it suffices.
6. Verify executor races/failures, consent, process interruption, History/export outcomes,
   and editor/run interaction and theme/compact renders. Update privacy documentation
   when the user flow starts saving preset definitions/results. Only then check W7b/c/d
   and W6a.

No real OBS/device or performance measurement is established by JVM/fake-server checks.
