# W4 — Actionable live alerts

Status: **implemented and verified**. W4a–d are complete; full quality/build/test evidence
is in `progress.md`. W5–W11 remain active in the durable workflow.
Show rundown/cue lists/timed advancement remain excluded.

## Foundation

The existing shared telemetry poller is reused. Before warnings consume it, snapshots
need connection/collection tokens and separate monotonic successful receipt times for
stats, stream counters and record state. Late reads and buffered output events must
reject replacement sessions. Output events invalidate counter receipt times instead
of pretending old counters are newly sampled. Disconnect/collection replacement clears
history and cached canvas FPS. Output polling stays independent of local workspace
resolution so collection discovery failures do not disable output readings/controls.
The foundation was verified and committed as `43c3f88` before the alert behavior below.
Shared telemetry remains independent of the warning UI and local setup resolution.

## Implemented behavior

- A process-scoped reducer consumes shared telemetry and context-stamped meters; it
  creates no additional OBS polling or subscriptions. All warnings have explicit
  unknown/inactive states; stale/missing/invalid readings cannot prove recovery.
- Dropped-frame warnings use deltas of streaming counters, never the lifetime ratio.
  Counter resets/reconnects establish a new baseline. Only active streaming qualifies.
- Render pressure derives from the canvas frame budget; 60 fps remains the unknown
  fallback. Recording-only operation can still warn about render load and disk space.
- Disk warnings use the scoped Preflight threshold (2,048 MB for a default setup).
  Failed or unresolved setup is unknown; it never substitutes a lower threshold.
  No recording-path information is collected/displayed.
- Sustained onset and recovery, separate threshold bands and dismissal cooldowns keep
  warnings from flickering or repeating on every sample. Sample gaps break accumulation;
  a clock tick cannot turn a repeated stale counter into new evidence.
- Unexpected connection loss reports that output state is unknown, not that a broadcast
  stopped. Deliberate disconnect/profile replacement clears the prior alert context.
- Inline warnings offer navigation to Stats, Mixer or Connections. Dismissal does not
  change OBS or stop monitoring; recovery clears warnings. Optional alert haptics are
  off by default, with no audible alert. Foreground UI warnings are not an OS alarm or
  a guarantee of background notification/delivery.
- Microphone silence detection requires explicit arming for a selected raw input while
  speech is expected. Arming is session-local, never restored after disconnect/context
  change/process recreation. Missing/invalid/stale meter samples are unknown, not silent.
  The UI must explain monitoring state and provide Stop monitoring and rearm actions.

Implemented thresholds: drops >=1% / recovery <0.5%; render >=90% / recovery <70% of
frame budget; disk below configured minimum / recovery at least max(256 MB, 10%) above.
All require five seconds of fresh evidence for onset/recovery. Connection loss uses two
seconds. Speech requires ten seconds of actual quiet meter samples and recovers on signal;
brief signal between UI ticks is preserved. Telemetry expires after three seconds and
meters after two. Dismissal lasts 60 seconds, with monotonic occurrence identity.

The More page and compact shell bar link to affected tools; warning cards include their
current reading. Disk alerts link to Preflight, which owns the threshold and disk check.
Eight page/bar theme captures and consent/haptic/navigation tests passed. Full quality and explicit build/tests passed (742 tests / 136 suites). No device or
real-OBS claim is made. W5–W11 are now verified; see progress.md.
