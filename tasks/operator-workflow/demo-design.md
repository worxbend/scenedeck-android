# Offline demo mode — W9

Status: **implemented and verified**. W9a/b/c are complete. W1–W11 are verified;
final integration evidence is in progress.md.
Show rundown, cue lists and timed advancement remain excluded.

## Isolation decision

Use a private `:demo` process and separate Demo task with its own Hilt singleton graph.
The live activity/session stays untouched when opening demo. Demo binds an in-memory Room
database and preferences, a no-credential secrets implementation and `DemoObsClient`.
It must never bind/open the real database, settings files, Keystore or network client.
All local features then use their existing repositories without changing namespace or
transaction semantics. This also isolates global History/bookmark retention and deletion,
not just scene curation. A profile ID alone in the real database would not provide that.

Android supports a private process through the activity's `android:process` attribute.
The existing live MainActivity is singleTask, which removes activities above it on reuse;
give demo its own task identity rather than relying on a child activity surviving there.
These are application-graph boundaries, not a claim of a separate permission sandbox.
Sources: [activity manifest](https://developer.android.com/guide/topics/manifest/activity-element),
[tasks and back stack](https://developer.android.com/guide/components/activities/tasks-and-back-stack).

Demo process selection must come from the exact application process name, never an intent
extra, host name or saved profile. Unknown process identity fails closed. Exported links,
widgets, notification receivers and the keep-alive service stay in the live process.
The demo Application must not start widget updates or network reconnect triggers, and its
KeepAliveController must refuse service intents. Separate connection-test clients must use
an injected factory that rejects real connection testing in demo before accessing secrets.
Connections/setup/background tools should explain the demo restriction and offer Exit.

## Simulated OBS boundary

Implement `DemoObsClient` in core:obs using the existing typed interface. It contains no
transport/factory/credential dependency. Only a fixed demo target with no password can
connect. Reads and writes preserve Ready identity and `withSession` origin checks;
re-entry invalidates old actions/events. Data is process-local and outputs start idle.

Provide a small fixed scene/input collection, controllable program/preview and Studio
mode/transition, mute/volume and sampled synthetic meters, output timers with recording
pause, media playback/seeking and bounded synthetic telemetry. Advertise only supported
controls. Invalid targets fail explicitly. No real file creation, broadcast, recording,
camera capture, source-collection mutation or automatic sequencing is performed.
Use a supplied elapsed clock and a bounded subscribed meter loop; Stats keeps the existing
shared read pipeline. Sample preview frames must be offline/local and use the existing
image limits/freshness path. No network fetches or measured-performance claim.

## Entry, lifetime and presentation

Entry is explicit from onboarding and More. Keep live onboarding/preferences untouched.
Demo has a persistent, accessible “DEMO · Local simulation” banner with Exit, including
all More pages and compact windows. Transport/status text must make simulation unmistakable;
sample collection/profile names and exported observations must also identify demo.
Live OBS may continue outside the demo; demo actions cannot affect that session.

DemoRuntime creates one fixed local profile, connects the simulator, and seeds scoped
curation/aliases and confirmation preferences before revealing the app. Startup has a
15-second bound and a sanitized retry state. The retained ViewModel owns a visit identity;
rotation keeps that visit and obsolete owners cannot close a replacement. Exit synchronously
invalidates the simulator and active target before removing its task. Re-entry starts idle
outputs; local edits, settings, shortcuts and history remain only while Android retains the
demo process. Process recreation creates fresh samples with no resumed actions or consent.
This explicit lifetime avoids clearing repositories while old History commands are queued.
Runtime teardown never kills a process or clears live storage.

## Delivered UI and boundary

- Non-exported DemoActivity has private process/task identity; MainActivity shares the
  themed Compose/shortcut host but checks Activity/process agreement. External links,
  widgets and background-service intents remain live-only.
- Onboarding and More offer deliberate entry; the dialog explains that any live session
  continues independently. DemoFrame owns the top safe inset, banner and Exit. Connections,
  setup and background routes explain their restriction. More and output consent repeat
  the simulation marker; the status strip identifies DEMO.
- Local generated JPEG frames run through the existing thumbnail limits/freshness path.
  Scene names, collection and profile identify demo; History filenames use `demo-`.
  Explicit document exports still create the user-selected file.
- DemoRuntime, DemoViewModel, DemoChrome and DemoPreviewFrames are under `app/.../demo`.
  LocalSimulation is presentation-only, never the selector for storage/client authority.

## Verification

Foundation commit `b073224` provides deterministic session/cancellation, clocks/pause,
Studio, exact names/capabilities, meters, media and screenshot-bound tests. Provider tests
exercise destructive demo curation/history/bookmark operations beside live Room sentinels;
preferences/secrets/service guards assert no live Context access. These are JVM checks.

The complete UI/runtime slice adds startup failure/retry/cancellation, retained and
replacement owners, synchronous exit, idle re-entry, fresh-graph behavior, manifest/process
routing, local JPEG decode and consent callbacks. Roborazzi covers SceneDeck/OBS light/dark,
compact height and large text; onboarding, transport and shortcut confirmations render.
Full JDK21/uv quality gate and explicit debug build/unit tests pass: **1,120 tests / 196
suites**, no failures/errors/skips. Normal and F-Droid debug APKs build and run.

API36 x86_64 emulator checks exercised separate processes/tasks, entry, simulated stream
and recording, rotation retaining timers, Mixer mute, Stats, foreground F8 output consent,
restricted connections, Exit and repeated entry. Demo settings changes left live DataStore
and encrypted-secret bytes unchanged; the live saved profile survived. Killing only the
owned demo process as a test produced a fresh idle demo with its old shortcut absent.
F-Droid retained manual connections and demo while hiding Scan QR; normal Scan QR was visible.
See [demo-device-check.md](demo-device-check.md) and the progress log for exact evidence.
No physical pedal/device, real OBS broadcast, or measured-performance claim is made.
