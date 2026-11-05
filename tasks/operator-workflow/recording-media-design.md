# W5 recording and media tools

Status: active. W5a is implemented and verified; W5b–d are incomplete. No rundown,
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

Local persisted bookmarks (W5b) must capture OBS-reported stream and recording
elapsed values separately, without extrapolating through recording pauses. They
must work without chapter support, clearly describe uncertain/gapped context, and
be usable in the later bounded session-history/export feature (W6).

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
