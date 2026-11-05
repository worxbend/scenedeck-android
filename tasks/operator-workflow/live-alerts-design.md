# W4 — Actionable live alerts

Status: telemetry foundation **verified**; alert implementation remains active.
Preflight is committed as `1f8ee30`. W4a–d remain open.
Show rundown/cue lists/timed advancement remain excluded.

## Foundation

The existing shared telemetry poller is reused. Before warnings consume it, snapshots
need connection/collection tokens and separate monotonic successful receipt times for
stats, stream counters and record state. Late reads and buffered output events must
reject replacement sessions. Output events invalidate counter receipt times instead
of pretending old counters are newly sampled. Disconnect/collection replacement clears
history and cached canvas FPS. Output polling stays independent of local workspace
resolution so collection discovery failures do not disable output readings/controls.
The full quality gate and explicit build/tests passed; see `progress.md`. This is
foundation work, not a delivered alert feature. Next: implement sustained warning
conditions/cooldowns, wire them into foreground UI, and add deliberate speech monitoring.

## Planned behavior

- A process-scoped reducer consumes shared telemetry and context-stamped meters; it
  creates no additional OBS polling or subscriptions. All warnings have explicit
  unknown/inactive states; stale/missing/invalid readings cannot prove recovery.
- Dropped-frame warnings use deltas of streaming counters, never the lifetime ratio.
  Counter resets/reconnects establish a new baseline. Only active streaming qualifies.
- Render pressure derives from the canvas frame budget; 60 fps remains the unknown
  fallback. Recording-only operation can still warn about render load and disk space.
- Disk warnings use the scoped Preflight threshold when available, with an explicitly
  documented default otherwise; no recording-path information is collected/displayed.
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

Finalize numerical thresholds/cooldowns with deterministic reducer tests, then add
shell/contextual UI, microphone consent, accessibility and four-theme renders. Verify
counter resets, stale individual reads, same-name replacement, canceled/late work,
recording-only cases, sample gaps, rearming and notification limitations. Full quality,
explicit build/unit tests and matching behavior docs are required before checking W4.
