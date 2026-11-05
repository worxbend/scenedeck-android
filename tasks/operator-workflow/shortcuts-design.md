# Keyboard and compatible foot pedals — W8

Status: **storage, input and action backend implemented; app integration pending** after the
verified W7 checkpoint `5dd9a21`. This document distinguishes the foundation from planned
behavior. W8a/b/c remain unchecked until the whole configuration and invocation flow is
implemented and verified. Verification evidence lives in `progress.md`.

## User flow and scope

More → Shortcuts will manage bindings for the connected profile + OBS collection.
There are no automatically active default shortcuts. An operator explicitly records a
key chord, selects its action/target and saves it. Each binding can be edited, disabled
or cleared. Help explains supported keys, modifier handling, active-screen restrictions,
and that a compatible pedal must emit Android keyboard events. No global/background
interception, device-driver configuration or universal hardware compatibility is promised.

Initial supported actions are named-scene selection (follows Studio mode), Take preview,
named-input mute/unmute and explicit start/stop stream/recording. These cover the deck,
microphone and transport workflow without a cue list, next-cue behavior or sequencing.
Scene selection addresses the literal OBS name; aliases remain presentation only.
Renamed/removed targets become unavailable and require deliberate editing, rather than
silently retargeting a saved binding. Take requires Studio mode and a valid preview.

Runtime shortcuts operate while the app is foreground/focused on Scenes, Mixer or Stats.
Configuration/diagnostic pages and onboarding do not run OBS shortcuts. Child controls,
text editing, dialogs and system/navigation keys retain priority. Pending confirmations
are dismissed on navigation/background/context changes. No key state or pending invocation
is restored after process interruption.

## Persistence

Implemented Room schema 12 adds one bounded aggregate of bindings scoped by profile +
collection. Limit 32 bindings per workspace. A stable local ID and monotonic revision
protect failed-save retries and stale editors/deletes. The chord must be unique within
that workspace, including disabled bindings, with a clear conflict result. Same chords
on different workspaces remain independent. Profile removal retains local definitions.

Store a strict versioned typed action payload, literal target names (nonblank, at most
1,024 characters), a canonical key identifier, four modifier bits and enabled state.
Reject malformed/unknown payloads or excess rows as failed state, never an empty/default
configuration. Reads and writes retain the originating WorkspaceSession and recheck it
before/after suspension and inside transactions. No OBS writes occur while configuring.

Canonical keys initially cover letters, digits, F1–F12 and numpad digits. Navigation,
editing, modifier-only, system and media keys are reserved; the editor must explain a
rejected capture. Collapse left/right Ctrl, Alt, Shift and Meta; ignore lock-state bits.
This finite allow-list avoids overriding Enter/Space/Tab/D-pad activation and navigation.

## Input admission

The isolated Android adapter expects the normal focused child to have the first
opportunity to handle the event, then considers an unhandled key for a shortcut. Explicit
text-input-session, window/lifecycle and active-route guards are still required when it
is installed; unhandled F-keys in a text field must not run OBS actions. The pinned Compose
text-input interception API is verified with focused hardware-key tests, including hidden
software keyboard state. Keep this guard independent of software-keyboard visibility.

Fire once on an eligible initial key-down. Track ownership by physical device + key;
auto-repeat and duplicate downs do not invoke again. Owned key-up releases the latch and
does not invoke. Unowned key-up does nothing. Clear held state on focus/lifecycle/route/
workspace replacement, bound the held-key map, and reject repeat-only events after reset.
Capture the displayed binding, workspace and action before dispatch. Recheck the binding
revision and active context at the command boundary so delayed work cannot reach a new host.

## Execution and confirmations

Use one foreground command/confirmation gate. Reject competing commands while one is
pending instead of silently queuing pedal actions. Fresh reads validate scene/Studio state,
input availability and output state. Input locks/allow-list and advertised request support
remain authoritative. Recheck the saved binding, policy and context before the write.
Use existing session-bound repository/ObsClient controls; protocol code stays in core:obs.
Mute uses MixerRepository's confirmed/pending token path, after the shortcut layer
checks locks and allowed inputs. Recording tools that are busy block conflicting outputs.

Output Safety uses the concrete start/stop target and the shared gate. A required dialog
has one-use consent bound to the binding revision, policy, origin and a short expiry. A
stale callback must not act, and a policy/context/binding change discards pending consent.
An already-matching target does not toggle to the opposite value. Failed/uncertain writes
are sanitized and never automatically retried. Scene/Studio actions must not infer an
idle output or a usable target from stale UI state. Known successful action feedback can
remain quiet; failures and uncertainty must be visible.

## Validation and completion

The foundation implements `ShortcutBinding`/`ShortcutDraft`, `ShortcutRepository`,
the schema migration, `ShortcutRunner`/`SharedShortcutExecutionSource` and isolated app
`ShortcutKeyRouter`/`ShortcutTextInputBoundary`.
Neither adapter is installed in MainActivity. Keep that integration coupled to the
editor/help and guarded executor; do not expose bindings that can bypass their policy.
The router takes an activation epoch plus a Ready snapshot (or null), calls no OBS APIs,
and relies on the caller to recheck current lifecycle/window/route/modal/text-input
eligibility on every event. Text-input leases remain active with a hidden software
keyboard and release on focus loss/disposal; use this guard alongside child-first dispatch.

The executor now uses `ShortcutProbe` for captured Studio/preview checks and
`FOLLOW_STUDIO_SCENE`/`STUDIO_TRANSITION` capabilities mapped from complete advertised
request sets in `:core:obs`. `ShortcutRunner.run(origin, binding)` owns one invocation
through preparation, optional consent, dispatch and verification; competing calls return
false without queuing or replacing the current state. The caller owns its lifetime.
`confirm(id)` consumes one waiting consent; `cancel(id)` cannot affect a different run.
Both the timer and injected elapsed clock enforce 60-second consent expiry. Preparation
and post-consent work each have a 20-second bound; target confirmation has four seconds.
The output gate checks the explicit requested target, never a toggle. Initially matching
targets return without dispatch; matching fresh reads after consent also do not write.
Definition, policy, context and target changes invalidate work, and caller cancellation
propagates. Dispatched, unconfirmed work is unknown and never automatically retried.
Take confirms the captured scene in program and permits its normal preview exchange after
dispatch. No invocation or consent survives process interruption.

App integration still needs a foreground caller and lifecycle/navigation cancellation.
Window focus gates key admission but an owned output dialog must not cancel its own
confirmation just because that dialog takes window focus. Keep route/background changes
and dismiss callbacks separate from that focus guard. Key capture in the editor will need
the same canonical mapper as the app router; move that adapter into shared `:core:ui`
when adding `:feature:shortcuts`, rather than importing app code from a feature.

- Real Room migration/reopen, scope isolation, chord conflicts, bounds/corruption,
  idempotent creation, revisions, failed drafts and cancellation.
- Pure key admission tests: modifiers, child-consumed events, repeats/duplicate downs,
  key-up ownership, multiple devices, bounded state, focus/route/workspace resets.
- Actual Compose text field focus with hardware-key events, disabled/background/modal
  routing, key capture and conflict/edit/clear/help flows.
- Studio off/on and changes during execution, missing/renamed targets, input locks,
  output confirmations, fresh/unknown output status, duplicate admission, session
  replacement, caller cancellation and uncertain writes without retries.
- Required theme/compact/accessibility renders, full quality/build/tests, and separate
  reporting of any physical keyboard/pedal or real-OBS verification.

W9 demo isolation, W10 tablet layout and W11 final integration remain required afterward.
