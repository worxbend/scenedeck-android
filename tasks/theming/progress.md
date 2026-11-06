# Progress

## Current checkpoint

- Active: T1 theme resolution. Branch `feat/operator-workflow`, clean baseline `9a57d3d`.
- User chose both more palettes and accent/true-black controls; no open product questions.
- Existing implementation has 11 fixed theme families plus wallpaper colors (the twelfth
  choice is dynamic color, not an enum family). Both light and dark variants exist.
- Current app state writes family/mode/wallpaper back via a snapshotFlow without an editor
  save outcome. New appearance editor will own atomic persisted saves; shell observes them.
- Existing baseline from completed workflow: 1,136 tests / 198 suites, full quality and
  normal/F-Droid debug builds passed. A fresh final-source gate remains required here.
- Required JDK: `/home/worxbend/.sdkman/candidates/java/21.0.8-tem`; use one Gradle owner.
- Consulted repository Android/Compose and Material 3 Expressive skills. No wire changes.

## Implementation and focused verification

- Previous goal turn classified **progress**: implemented theme palettes/resolution,
  persistence/app wiring, editor/draft/save flow, tests, renders and matching documentation.
- Added Ocean/Rosewood/Amber/Paper, six primary-accent overrides and dark-only True black;
  High Contrast without wallpaper retains exact roles. ThemePreview is local/read-only.
- AppearanceViewModel owns current/original/draft/saving/error StateFlow. Atomic saves
  compare opening appearance, preserve other settings, gate duplicates and retain failed
  drafts. Shell observes committed appearance and reverse-writes only its other controls.
- New store keys default old installs to DEFAULT accent / false True black; demo bindings
  remain ephemeral. Added numeric text-role contrast, ViewModel cancel/conflict/failure,
  repository preservation, restore and editor interaction tests. Added light/dark/black
  sample renders and SceneDeck/OBS editor renders including tablet/compact/large text.
- Before environment reset, focused record/build/static check passed (36s / 528 tasks),
  Settings 25 tests/5 suites and design system 108 tests/21 suites, no failures/errors/skips.
  Temporary logs were lost; current XML/goldens remain, but a fresh full gate is required.
- Added app-state regression proving other shell controls cannot overwrite committed
  appearance. Prevent sheet dismissal during admitted saves so failure remains reviewable.
- Fresh formatter passed in 4m29s after cold environment setup. User requested stopping
  existing processes; stopped the owned Gradle daemon and theming emulator, then restarting
  verification with one Gradle owner. Current logs live under build/theming-verification.
- Product/design/architecture/AGENTS and project skill guidance updated. No dependency,
  OBS protocol, signing or delivery configuration change. Final gates still pending.

## Fresh final-source verification — 2026-11-06

- Fresh formatting passed in 12s after stopping old task processes at the user's request.
- First full gate failed only the new app-state regression: its test blocked the Compose
  dispatcher while awaiting storage. Added `waitForIdle()` before the blocking assertion;
  focused app regression + assemble passed (13s, 487 tasks). Production behavior unchanged.
- Full JDK21/uv gate then passed: `build/theming-verification/quality-final.log`, 29s,
  1,242 Gradle tasks; seven Semgrep rules / 443 targets / zero findings; both Gitleaks scans
  clean. Final explicit `assembleDebug testDebugUnitTest` passed (6s, 981 tasks):
  `build/theming-verification/normal-final.log`. Final XML aggregate: **1,169 tests / 203
  suites**, zero failures/errors/skips, including up-to-date results.
- Final F-Droid debug build passed (22s, 468 tasks): `build/theming-verification/fdroid.log`.
  Normal build output restored. No dependency pins, permissions or signing changes.
- Owned API36 x86_64 emulator: Ocean/Violet/True black preview did not write preferences;
  Apply changed the app; process restart retained it. Reset defaults then Cancel preserved
  saved preference bytes. Demo Apply left live preferences unchanged (SHA-256 comparison),
  and Exit restored live appearance. F-Droid showed manual connection entry without QR;
  its Teal draft survived rotation and Apply saved it. Normal variant again showed Scan QR.
  Screenshots and UI snapshots are under build/theming-verification (ignored working evidence).
- Host-GPU emulator exited in native GLES during APK replacement twice. Relaunching and
  installing with app stopped completed the F-Droid flow. Swangle software backend then
  opened a visible emulator successfully. These are emulator-host limitations, not app
  Java crash evidence. No physical-device, battery-saving or measured-performance claim.
- User requested a visible showcase and local OBS connection. OBS is running on port4455,
  authentication disabled; connect the emulator through 10.0.2.2. User confirmed to finish
  connection then leave controls to them. User stopped the first visible emulator; restarting
  it now. Keep the visible emulator running for the user after connection.

## User-authorized showcase and release

- Visible emulator restarted using Swangle, normal APK restored, and Local OBS Showcase
  profile connected to 10.0.2.2:4455 (authentication disabled in the user's OBS configuration).
  App is Ready with nine scenes, idle outputs and working preview reads. It remains open
  for the user; no stream/recording was started. No OBS scene collection mutation.
- User explicitly authorized finishing commits, pushing all work to main, and cutting a
  release tag. Remote main is an ancestor (23 local workflow commits ahead), unprotected;
  latest release is v0.1.4. Plan additive release v0.2.0 / code1011; Play tag publishing is
  disabled (no enabling variable). Existing tag workflow signs/publishes GitHub APK/AAB assets.
- Added curated release notes under docs/releases/0.2.0.md and Play changelog1011; aligned
  fdroid/version.properties and its source-build documentation. F-Droid publication tag is
  separate; this request publishes the normal v0.2.0 release.
- Release contract tests passed: 15 Python metadata tests and 20 stubbed Fastlane checks.
  Rechecking versioned F-Droid and normal output before commits/push.
