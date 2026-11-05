# Offline demo mode — W9

Status: **in progress; not exposed in the app**. W9a/b/c remain unchecked until the
complete entry, isolated runtime, labeling and exit flow is verified. W1–W8 are verified.
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

Bootstrap sample profiles/curation in the in-memory graph before connecting. Activity
rotation keeps the current demo; process recreation creates a fresh local sample session
with idle outputs and cannot fall back to a real profile. Exit disconnects the simulation,
stops its jobs and removes its task; another deliberate entry resets the sample state.
No automatic return-to-live command or resumption of output actions/consent is permitted.

## Verification required

- Deterministic simulation behavior, session replacement/cancellation, event/meter stamps,
  clocks/paused recording, media bounds and exact advertised controls.
- Provider/process tests proving no live DB/preferences/credential/network access; real
  sentinel curation/history/bookmarks survive demo edits/deletion/retention unchanged.
- Demo connection-test, widget/service/deep-link boundaries and no cross-process actions.
- Entry/exit, rotation/process recreation, clear labeling, foreground shortcuts and
  simulated output confirmations through existing feature/repository paths.
- SceneDeck/OBS light/dark, compact/large-text previews; full JDK21/uv quality gate and
  explicit assembleDebug/testDebugUnitTest. Normal/F-Droid parity remains required.
- Report JVM/render evidence separately from any actual multi-process device/emulator test.

## Foundation checkpoint

Implemented and foundation-verified, not exposed: the typed simulator in `core/obs/.../demo`, exact process
selection in `ObsProcessModule`, in-memory Room/atomic preferences, no-credential store,
lazy live widget/reconnect startup and guarded keep-alive service intents. Separate
connection tests now use an injected factory, denied in demo before credential lookup.
No DemoActivity/manifest entry, onboarding/More entry, banner or sample preview generator
is present yet. Live navigation and default process behavior remain unchanged.

Deterministic tests cover session replacement/cancellation, output clocks/pause, Studio,
exact names/capabilities, audio/meters, media seeking and screenshot bounds. Provider tests
exercise destructive demo curation/history/bookmark operations against a live Room sentinel
database and assert that demo preferences/secrets/service guards never touch Context.
These are JVM/Robolectric checks, not a multi-process device or real-OBS test.

Next: guarded app entry/exit with sample preview frames and persistent simulation labeling.
Settle reuse of an already-running demo process explicitly: Hilt singletons outlive an
Activity, so exit must invalidate pending commands; recreating an Activity alone does not
reset in-memory repositories. Any reset must preserve History generation tombstones until
old queued starts are rejected. Never kill the process as a shortcut or reset live storage.
Foundation alone does not complete W9 or the durable goal. W10/W11 remain required.

Foundation verification: full JDK21/uv quality gate and explicit debug build/unit tests
passed; 1,098 tests across 191 suites, no failures/errors/skips. See the W9 progress entry
for exact commands/logs. W9 remains unchecked; no new UI or device behavior is claimed.
