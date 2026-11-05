# W9 emulator evidence

Checked 2026-11-05 on an owned API36 x86_64 Pixel 7 AVD, `scenedeck-demo-check`,
serial `emulator-5560`. This is emulator evidence, not a physical-device or real-OBS test.
The original `scenedeck` AVD was not modified.

## Executed paths

- Fresh normal app → Try offline demo → entry explanation → Open demo. Live onboarding
  remained unfinished after Exit. Five demo scenes, aliases/raw names, thumbnails and
  age labels were visible. Android showed separate live/demo PIDs and tasks.
- Confirmed simulated Stream and Record; simultaneous elapsed timers and sampled Stats
  appeared. Rotation to compact landscape retained the running visit/timer; the navigation
  rail, banner and status stayed available. Portrait Mixer mute changed to Unmute.
- Saved a live-only sentinel profile without connecting it. Demo settings changed to Light
  and Compact. SHA-256 comparison of the live DataStore and encrypted-secret file remained
  equal; no secret content was printed. Exit returned to the saved live profile unchanged.
  Earlier fresh-install SQLite snapshots also showed no demo rows in live profile,
  registry, History or bookmark tables. More extensive destructive isolation is JVM-tested.
- Demo Connections showed a restriction explanation and Exit. No real endpoint was tested
  or connected. Demo's More → Shortcuts recorded F8, selected Start stream and saved it.
  F8 on Scenes opened the single-use simulated-output confirmation; confirm started the
  simulated stream. Exit/re-entry used the same process with OFF AIR and retained F8 binding.
- After Exit, killed only the owned demo PID using debug `run-as` as a process-death test.
  Live PID stayed alive. Deliberate re-entry was fresh/idle and F8 no longer invoked a binding.
  This is a test action; app teardown never kills processes.
- Installed the F-Droid debug APK over the normal debug app. Connections retained the live
  sentinel and Add manually, with Scan QR absent. More → demo opened five local scenes and
  idle outputs. Normal build had shown Scan QR before this install.

## Artifacts and repeatability

Build/test logs are in the W9 progress entry. Temporary local evidence includes
`/tmp/scenedeck-demo-scenes-final.png`, `/tmp/scenedeck-demo-settings-isolation.png`,
`/tmp/scenedeck-demo-after-process-death.txt`, `/tmp/scenedeck-demo-fdroid-device.txt` and
`/tmp/scenedeck-demo-live-hashes.json`. They are working evidence, not portable release assets.
Committed Roborazzi images cover four themes and compact/large-text states.

The helper `/tmp/scenedeck-demo-device.py` reads a fresh UIAutomator tree, rejects failed
hierarchy dumps and taps exact visible labels. All app actions above were driven through
normal UI, except keyboard/rotation events and the explicit process-death test.

Two software-GPU launches crashed in the host emulator's ColorBuffer/SwiftShader path
(exit 139) when opening More. Restarting with the host GPU completed the same flow:

```sh
$ANDROID_SDK_ROOT/emulator/emulator -avd scenedeck-demo-check -port 5560 \
  -no-window -no-audio -no-snapshot -gpu host -feature -Vulkan
```

No app Java crash was observed for those native host failures. Do not infer physical GPU
behavior, measured performance, universal pedal support or network isolation measured at
packet level from these checks. The no-network/no-credential boundary is established by
the typed transport-free client, exact process providers and their failure-path tests.
