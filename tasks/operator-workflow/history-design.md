# Local session history — W6 design and storage foundation

Status: storage/export foundation verified. **Capture, History UI, file
export flow, bookmark integration and preset results are not shipped.** W6 remains
open; a stored payload type is not evidence of an integrated feature.

## Meaning of an observation

A history session is one continuous period of local observation of a resolved
workspace. It is not an OBS stream, recording file, or show run. A recorder instance
will use a fresh process owner ID and a fresh observation UUID after connection or
collection replacement, process recreation, re-enabling capture or recovery from a
storage failure. Session UUIDs must never be reused for a new observation.

The initial state is marked `initial`; seeing an active output on connect does not
claim that SceneDeck saw it start. Device calendar timestamps are separate from
monotonic elapsed time within an observation. Calendar time may move backward.
Insertion sequence orders observations; increasing ordinals order events. A
heartbeat can update the last-observed point without creating event rows.

On process recovery, old owners' open observations are closed with `PROCESS_ENDED`
and an unknown end timestamp. The last-observed point remains available. Disabling
capture similarly closes open observations with an unknown exact end. Neither action
invents an output stop. Normal recorder closures can supply the observed interruption
time and reason. Warning disappearance means “cleared or dismissed,” not proof of
recovery. Unknown output state stays explicit.

## Persistence and bounds

Room schema 8 adds session, event and preference tables, preserving all prior tables.
Default retention is 30 observations, with supported limits of 10, 30 or 100; each
observation retains its newest 500 events. These are implementation bounds, not
measured device performance. List/detail/export reads are bounded by those limits.

Transactions serialize starts, appends, settings, deletion and retention. Events have
a composite session/ordinal identity and a cascading parent foreign key. A matching
retained replay is idempotent; conflicting retained data fails. Replays of pruned or
skipped ordinals are obsolete and cannot reinsert history. Ordinal jumps persist both
the event's missing-before count and the observation's total missing count. Pruning
increments a separate omitted count, preserving the distinction from missed capture.

Deletion (one observation or all) increments a preference generation. A start captured
under an older generation cannot create a parent, and append/touch never create a
missing parent. A retry of a surviving, already-started observation can still succeed.
Deleting an older observation does not stop writes to a surviving active observation.
Disabling/re-enabling capture also changes generation, while changing retention alone
does not. The future recorder must read the current generation for new starts and
create a new UUID after deletion of its active observation. Clearing history does not
disable future capture; its UI must explain this.

Read failures/corrupt or unsupported payloads produce explicit Failed states. Missing
detail is distinct from an empty observation. Cancellation propagates. The persistence
layer does not retry failed writes by itself. The future recorder must pause on storage
failure, offer Retry, and start a new observation with an explicit gap after recovery.

## Export boundary

The repository takes a consistent offline snapshot of all retained observations or
one selected observation in a Room transaction. An explicit JSON DTO exports format
`scenedeck-history`, version 1, export time, retention bounds, observation metadata and
typed events. Unknown values, interruption reasons and omitted/missing counts survive
export. JSON handles quotes, newlines and backslashes in OBS names.

The allow-list includes local observation ID, profile ID, collection/scene names,
timestamps, output state/counters, warning categories and gap reasons. Process owner,
database sequence and deletion generation stay local. There are no endpoint,
credential, raw protocol/comment, arbitrary error, recording-path or media-path fields.
User-supplied OBS names can themselves contain private information; the future export
flow must be deliberate and user-initiated. Export does not delete the original data.

## Remaining integration

1. Add one process-scoped recorder fed by shared state and context-stamped typed
   events, without another OBS poller. Subscribe before seeding snapshots; guard
   against late reads overwriting events or recording under a replacement workspace.
   Use a bounded queue; ordinal gaps must expose overflow rather than block protocol
   reception. Do not use the default virtual-camera false value as a known initial
   state without a successful read or typed event.
2. Bind warnings to their originating context. Distinguish dismissed/unknown/reset
   states without inventing recovery. Persist only meaningful transitions, not each
   telemetry sample. Decide heartbeat ordering with queued events explicitly.
3. Integrate bookmarks atomically with the original persisted observation. Delayed
   saves must not retarget or recreate deleted observations. Define label edit/delete
   and independent bookmark retention/export semantics before duplicating label data.
   Existing bookmark observation IDs are independent and must not be equated to new
   history session IDs or matched by wall-clock ranges.
4. Add offline History list/detail, retention/capture controls, clear/delete and a
   concrete SAF JSON export flow under More. Include sanitized storage errors/retry,
   interruption/unknown/omitted details and required theme/compact renders. Update the
   privacy policy when capture becomes active.
5. Connect actual W7 preset execution outcomes, including partial and unknown results.
   W6a is incomplete until this producer exists; a placeholder event type is insufficient.
6. Verify context races, capture deduplication, queue overflow, process recreation,
   failure/retry, bookmark integration and the full user export flow. Storage tests
   alone do not satisfy these recorder/UI checkpoints.

Show rundown, cue lists and timed advancement remain excluded. Presets are individually
invoked actions.
