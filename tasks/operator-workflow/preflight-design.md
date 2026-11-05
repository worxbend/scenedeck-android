# W3 — Preflight

Status: **implemented and verified**. W3a–d are complete; verification evidence is
in `progress.md`. W4–W11 remain active in the durable workflow.

- Add a dedicated `:feature:preflight` screen under More, plus a link from start-output
  confirmations. Opening Preflight cancels the pending output confirmation; returning
  requires a new deliberate start. No automatic start, mandatory scan, or rundown.
- Room 6 stores one bounded configuration per profile + collection: expected collection,
  required raw scene names, selected raw microphone input, minimum free disk MB, and
  editable manual checks with persisted completion. Local aliases are display-only.
  Defaults: current collection, no required scenes/microphone/manual checks, 2,048 MB.
  A missing selection is explicitly unknown, never a passing microphone check.
- Configuration edits carry origin and their original configuration; transactional saves
  reject stale editors. Context changes invalidate checks and dismiss editors. Failed
  saves retain drafts. Manual check completion changes invalidate the prior run.
- A deliberate run reads scenes, disk stats and microphone mute concurrently through
  `ObsClient.withSession`; each read has an eight-second bound within existing protocol
  deadlines. Failures produce independent unknown rows without exposing exception text.
  Missing scenes, wrong collection, muted mic and low disk are failures. No OBS writes.
- Optional signal check is armed for one run and prompts the user to speak. Observe only
  context-stamped shared meters for four seconds; finite post-fader peaks above -50 dB
  count as detected. No samples means unknown; observed quiet samples mean no signal
  detected during this window, not a broken microphone. Mute/control and signal are
  distinct results. No new meter poller/subscription or persisted arming.
- Clear previous results at run start/cancel/config or context change. Completed reports
  show timestamp, checked workspace and independent Pass/Fail/Unknown rows. They are a
  snapshot, never an ongoing guarantee; no all-clear claim while any row is unknown.
- Bounds: 100 required scenes, 30 manual checks, 120 characters per manual label,
  4,096 characters per OBS reference; disk threshold 1–1,000,000 MB. Manual IDs preserve
  individual completion; renaming a checklist item resets its completion.

Verified persistence/migration/reopen/scoping, partial failure/timeout/cancellation/
stale context/meter tests, failed-draft and output-confirmation coverage, eight
four-theme report/setup renders, documentation and full quality/build/test gates.
Real OBS and physical-device validation have not been performed for this checkpoint.
