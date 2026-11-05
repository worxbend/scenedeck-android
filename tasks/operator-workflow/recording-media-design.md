# W5 recording and media tools

Status: active. W5a–b are implemented and verified; W5c–d are incomplete. No rundown,
cue sequence, timed advancement or Replay Buffer behavior is added.

## Recording controls

- Retain a typed optional-capability projection of `GetVersion.availableRequests`.
  Unknown/unadvertised requests default unavailable; OBS version numbers are not a
  substitute. The pinned ktobs 0.5.0 already has the four recording requests.
- Scenes → scene options → Recording tools opens a fully expanded, scrollable
  sheet. Keep the three direct transport controls and their existing confirmations.
- Pause and resume are target-value writes, never toggles. One pending command
  blocks all recording-tool actions. Check fresh shared telemetry and re-read status
  before dispatch. No separate screen poller is introduced.
- A successful pause/resume reply is followed by up to 3.5 seconds waiting for a
  subsequent shared recording sample whose read started after the acknowledgement to confirm the target. Otherwise the outcome
  stays unknown. Transport failures/timeouts/cancellation never retry the write.
- Split and chapter use successful acknowledgements, with wording that OBS accepted
  the action. Format support is determined by OBS; advertised capability alone is
  not proof. Rejections have a fixed message without server comments or file paths.
- Advanced actions require active, unpaused recording. Chapter names are optional,
  trimmed, and bounded to 120 characters. They are sent deliberately to OBS.
- Actions retain the authoritative target generation, Ready token and observed
  recording lifecycle generation. Reconnection, collection changes, deliberate target
  replacement and observed start/stop boundaries invalidate old intent/results and
  cancel queued work. A canceled command releases the shared pending slot; an
  already-dispatched action still has an uncertain outcome.
  Cross-client changes after dispatch cannot be rolled back or made atomic by this app.
- Controls require a recording sample no older than three seconds. Event receipt
  invalidates previous counter evidence; unknown status disables writes. A one-second
  local freshness clock does not send OBS requests.

## Remaining W5 decisions/work

Media seeking (W5c) will use the existing shared Mixer media polling boundary.
Validate duration/cursor, clamp seeks to the known duration, show elapsed/remaining,
disable unknown-duration seeks, and reconcile actual OBS status after each write.
The protocol itself does not bound the cursor against media duration.

## Sources checked 2026-11-05

Official [OBS WebSocket protocol](https://github.com/obsproject/obs-websocket/blob/master/docs/generated/protocol.md):
GetVersion, PauseRecord, ResumeRecord, SplitRecordFile, CreateRecordChapter,
GetMediaInputStatus and SetMediaInputCursor. The protocol warns of recording-format
constraints for chapters; do not turn its historical format example into a current
format allow-list. Local pinned ktobs jar APIs were inspected; no dependency change.

## Implementation findings

A fake-server lifecycle test exposed ktobs 0.5.0's non-null `outputPath` assumption.
OBS documents null during active recording. A small kotlinx.serialization event model
inside `:core:obs` accepts nullable/omitted paths and maps typed recording phases; no
client-library upgrade or raw protocol logic outside the adapter is needed.

Shared telemetry now records the recording read's start as well as its successful
receipt. Pending confirmation excludes reads already in flight before acknowledgement,
even when their delayed response arrives afterward. Events/failures invalidate both.

## W5b bookmark decisions (verified)

- More → Bookmarks is the first surface in `:feature:history`; W6 session history
  and export are still separate incomplete work. Saved rows remain available offline
  and identify their original connection profile and collection.
- Mark now captures before suspension/name editing. It stores device calendar time
  separately from OBS-reported stream and recording elapsed counters plus sample ages.
  No counter extrapolation, additional polling or OBS write is involved. Paused values
  retain the reported recording position; reconnecting stream status is explicit.
  Missing, failed, wrong-session, negative or older-than-three-second values are unknown.
  Inactive outputs have no invented zero timestamp. Counters represent outputs, not
  guaranteed positions inside a particular split file.
- Capture requires a synchronously current resolved workspace. An immutable captured
  moment can save after disconnect without moving to another scope. Each capture has
  one UUID for idempotent retry. An observation UUID changes on connection/collection
  replacement or process/repository recreation; it does not invent continuous history
  through gaps or claim to identify an actual OBS recording file.
- Room schema 7 adds a standalone bookmark table, preserving previous tables. Keep
  at most 500 bookmarks globally, ordered/pruned by insertion sequence so a changed
  device clock cannot remove a new mark. The page explains automatic oldest removal.
  W6 will add the broader history retention/export controls.
- Optional labels are local, trimmed and limited to 120 characters. Edits compare
  the original label; failed/stale edits preserve the draft. Delete confirms the
  concrete bookmark. Failed saves retain the immutable capture for explicit retry or
  discard; retries never recapture time or duplicate a committed row.

Official source checked: [GetRecordStatus](https://github.com/obsproject/obs-websocket/blob/master/src/requesthandler/RequestHandler_Record.cpp)
uses [GetOutputDuration](https://github.com/obsproject/obs-websocket/blob/master/src/utils/Obs_NumberHelper.cpp),
which computes output duration from output frames/frame time. The app stores the
reported value rather than estimating pause intervals from wall-clock time.

## W5c resumption findings

`MixerRepository` already owns a subscribed two-second media poll and
`setMediaInputCursor`; `MediaStatus` already carries nullable cursor/duration. The current
media cache has no read timestamps and silently retains prior values after failed reads.
Before exposing progress/seeking, add freshness and read-order guards so a delayed earlier
read cannot overwrite newer status or confirm a seek. Keep the existing shared polling
boundary; validate duration, clamp targets, capability-gate writes, prevent duplicates,
and reconcile a post-write status without automatically retrying uncertain writes.
