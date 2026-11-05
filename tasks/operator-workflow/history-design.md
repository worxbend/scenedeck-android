# Local session history — W6 design

Status: shared-state capture, bookmark links, offline History and SAF JSON export are
implemented; verification evidence is in progress.md. **Actual preset outcomes remain
dependent on W7.** W6a stays open until that execution producer is integrated.

## Meaning of an observation

A history session is one continuous period of local observation of a resolved
workspace. It is not an OBS stream, recording file, or show run. The recorder uses a
fresh owner ID per process or explicit retry, and a fresh observation UUID after
connection/collection replacement, process recreation, re-enabling capture or failure
recovery. Session UUIDs are never reused for a new observation.

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
Schema 9 adds bookmark associations with cascading foreign keys to both parents.
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
does not. The recorder reads the current generation for new starts and creates a new
UUID after deletion of its active observation. Clearing history does not disable future
capture; the confirmation explains this.

Read failures/corrupt or unsupported payloads produce explicit Failed states. Missing
detail is distinct from an empty observation. Cancellation propagates. The persistence
layer does not retry failed writes by itself. The recorder pauses on storage/source
failure, offers Retry, and starts a new observation with an explicit gap after recovery.

## Shared capture and failure boundaries

The application starts one HistoryRecorder. SharedHistorySource reuses workspace,
scene, telemetry, warning and context-stamped event flows without another OBS poller.
Events are subscribed before seeding. The synchronous engine guards the current workspace
and reduces typed events before queueing, preserving rapid program transitions.
Program snapshots seed only before a known seed or program event. Output snapshots
require a fresh successful read begun after the last event; default camera false is
never known idle. Event evidence ages to unknown if successful readings stop. Warnings
carry the ActiveConnection/Ready origin; disappearance means cleared or dismissed.

A non-suspending 128-command queue separates capture from serial Room writes. Locally
missed history entries advance ordinals, so the next retained event/gap exposes loss.
Known missed counts include failed gap-entry admission attempts, not just OBS events.
Failure to admit a start/end boundary pauses capture; explicit retry abandons the old
queue epoch and closes its observation with an unknown end and a specific interruption
reason. Normal disconnect/replacement is recorded in the observation end reason, and a
later connection creates another observation. Ten-second heartbeats preserve a last
observed point without adding events or requests. The shared upstream feeds do not have
an exhaustive event sequence contract; this is an observation log, not a complete audit.

## Bookmarks

A mark captures its current persisted observation and monotonic elapsed time before
the existing workspace guard. Initial save stores the bookmark and optional association
in one transaction. A delayed save may link to its original closed observation when
its captured position fits; it never retargets or recreates a deleted parent. Marks made
before capture starts or while disabled remain independent. Existing bookmark observation
IDs are separate identities and are never matched to history using calendar ranges.

History joins current saved labels/times. Label edits, deletion and the independent
500-bookmark retention policy therefore affect later detail/export reads without leaving
duplicate text. Deleting history cascades only associations, preserving bookmarks; they
become unlinked. A saved mark beyond the last heartbeat extends the displayed last-known
point for an open/unknown-ended observation without altering persistent event ordering.

## Export boundary

The repository takes a consistent offline snapshot of all retained observations or
one selected observation in a Room transaction. All-history snapshots also include
independent saved bookmarks; selected snapshots include only their linked marks. An
explicit JSON DTO exports format
`scenedeck-history`, version 1, export time, retention bounds, observation metadata and
typed events. Unknown values, interruption reasons and omitted/missing counts survive
export. JSON handles quotes, newlines and backslashes in OBS names.

The allow-list includes local observation ID, profile ID, collection/scene names,
timestamps, output state/counters, warning categories, gap reasons and bookmark labels/
times. Process owner,
database sequence and deletion generation stay local. There are no endpoint,
credential, raw protocol/comment, arbitrary error, recording-path or media-path fields.
User-supplied OBS names/labels can themselves contain private information; export is
deliberate and user-initiated. Export does not delete the original data.

HistoryFileExport prepares the selected snapshot before opening CreateDocument. Selection,
workspace or saved-data changes while the picker is open cannot retarget it. The snapshot
stays in ViewModel memory, not saved-state Bundles. Cancellation writes nothing; a callback
after process loss is explicitly expired. A file write uses only the chosen document and
propagates cancellation. Failure warns that the selected document may be incomplete;
there is no automatic rewrite. Duplicate writes/settings changes are gated.

## User flow

More → History remains available offline. Capture defaults on, retention defaults to 30
observations (10/30/100 choices), and settings show confirmed saved values. List/detail
distinguish loading, failed, missing and empty data. Retry is explicit. Clear/delete names
the target and preserves independent bookmarks; enabled capture can start a new observation
after clearing. The detail timeline merges typed events and linked marks by elapsed time,
retaining event ordinal ordering. Output readings never imply an observed start, and
unknown ends/omissions remain visible. Shared design components support both themes and
compact height; privacy documentation describes capture, retention and export.

## Remaining integration

Connect actual W7 preset execution outcomes, including partial and unknown results.
W6a is incomplete until this producer exists; a placeholder event type is insufficient.
Keep full workflow verification and real-device/OBS limitations explicit in W11.

Show rundown, cue lists and timed advancement remain excluded. Presets are individually
invoked actions.
