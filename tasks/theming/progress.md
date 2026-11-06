# Progress

## Initial checkpoint

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
- Owned Android 36 emulator: Ocean/Violet/True black preview did not write preferences;
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

## Publication correction

- main pushed at 8465797; annotated v0.2.0 points to that release preparation commit.
- CI security job127851096207 flagged a generic-api-key false positive in the documentation
  phrase describing Ocean/Violet/True black. SARIF identifies only progress.md line53 in
  commit58aa55d. Added that exact historical fingerprint to the existing .gitleaksignore
  and reworded the current line; no blanket path/rule exclusion and no credential exposure.
- Preserve the published v0.2.0 tag. Corrected release will be v0.2.1 / code1012, with
  the same feature set and validated security correction. Verify its tag workflow/artifacts.

## Corrected remote verification in progress

- v0.2.1 resolves to 3f1ccf42418540e96e2c1671425ed3b96c749fe1; subsequent CI-only
  corrections go to main without moving the published tag.
- Corrected history/worktree secret scans passed locally; metadata (15) and publish-lane
  (20) tests passed. Main CI security job is also green after the fingerprint correction.
- Release run: https://github.com/worxbend/scenedeck-android/actions/runs/42206398820
- Main CI: https://github.com/worxbend/scenedeck-android/actions/runs/42206394065
- F-Droid verification: https://github.com/worxbend/scenedeck-android/actions/runs/42206394224
- CodeQL: https://github.com/worxbend/scenedeck-android/actions/runs/42206394128
- Superseded v0.2.0 and old-main runs were cancelled; tags remain immutable. Resume by
  reading these remote runs. Do not claim release assets published until the tag run finishes.

## CI timing regression correction

- Corrected main CI unit run exposed StatsViewModelTest.mapsTelemetryIntoUiState expecting
  CPU12.5 while history samples had arrived ahead of the independent telemetry StateFlow.
  Existing canvas-FPS test already waits for both feeds for the same reason. The mapping
  assertion now awaits the fixture's positive CPU reading as well as a sample, then retains
  all exact numeric assertions. Test teardown also cancels the ViewModel scope.
- This is a test synchronization/cleanup fix; production sources and release behavior are
  unchanged. Fresh full JDK21/uv quality gate passed (20s Gradle; all tests/security clean),
  recorded in build/theming-verification/quality-stats-test.log.
- Main static analysis itself passed, but GitHub rejected the combined SARIF upload:
  23 runs exceeded its 20-run limit. Collector/upload steps now group app/core/feature
  separately (1/8/14 runs), preserving results and stable per-module categories. Collector
  rejects new unconfigured groups or overflow; three fixture tests prove coverage,
  category stability, stale cleanup and fail-closed bounds. All fixture tests and real
  collection passed locally. This affects CI reporting only.
- v0.2.1 release quality/security gate succeeded; signed APK/AAB build is running.
  F-Droid and CodeQL verification at the tag's main commit both succeeded. Push the CI
  corrections to main and verify its new run; leave the release tag immutable.

## Final release preparation — v0.2.2

- Main CI correction committed/pushed as c6f568a. GitHub's push response surfaced the open
  Dependabot alert 8: AGP's buildscript selects Guava 33.4.0-jre, affected by
  CVE-2026-102554 (unbounded Java deserialization allocation). Upstream confirms
  33.7.2-jre as patched: https://github.com/google/guava/releases/tag/v33.7.2.
- Build inspection confirmed the JRE tool minimum selects 33.7.2-jre. It also revealed a
  distinct Android 33.3.1-android transitive dependency through media/CameraX/coroutines.
  Added a separate catalog-backed app implementation minimum of 33.7.2-android; the JRE
  jar stays confined to build tools. Verify both selections, debug signing and full
  tests/quality before tagging.
- Cancelled v0.2.1 before signed artifacts/publication; its quality/security gate had passed.
  Keep v0.2.0 and v0.2.1 tags immutable. Final release is v0.2.2 / code1013, including
  test/reporting corrections and patched build tools. F-Droid publication remains separate.
- Updated release notes, version metadata, TECH_STACK/AGENTS and workflow tracking. Metadata
  (15), Fastlane lane (20) and workflow syntax checks pass for the new version.
- Final patched dependency resolution is verified in guava-resolved.log: root tools use
  33.7.2-jre and app debug runtime uses 33.7.2-android. Full quality-022.log passed in
  6m55s (1,242 Gradle tasks), Semgrep zero findings and both secret scans clean. XML
  aggregate remains 1,169 tests / 203 suites, zero failures/errors/skips. F-Droid debug
  build also passed (24s, 468 tasks), followed by normal versioned assembleDebug and
  testDebugUnitTest (34s, 981 tasks). Normal output is restored, version0.2.2/code1013;
  debug APK signature verification passed. Ready to commit/push/tag.
- CI correction c6f568a is fully green on main: CI42207873675, F-Droid42207872206 and
  CodeQL42207872178. The final dependency/release commit must get its own remote checks.

## Final remote verification in progress

- main and annotated v0.2.2 point to 945db8012bd557c934b3fb9d7c2a675cfcde4510;
  remote refs verified. Version0.2.2/code1013 debug APK manifest and signature verified.
- Release: https://github.com/worxbend/scenedeck-android/actions/runs/42209606626
- Main CI: https://github.com/worxbend/scenedeck-android/actions/runs/42209601728
- Tag CI: https://github.com/worxbend/scenedeck-android/actions/runs/42209606576
- F-Droid: https://github.com/worxbend/scenedeck-android/actions/runs/42209601581
- Main CodeQL: https://github.com/worxbend/scenedeck-android/actions/runs/42209601573
- Tag CodeQL: https://github.com/worxbend/scenedeck-android/actions/runs/42209606675
- All are running/queued at this checkpoint. After the release publishes, install curated
  notes from docs/releases/0.2.2.md, verify APK/AAB checksum and the known upload certificate
  (48e613d25d603670a423da9ee69f980ef8f687cd4518756724e372f4427f8800), record actual
  outcomes and close T6. Keep the visible emulator/OBS showcase available for the user.
- Main and tag CI plus both CodeQL runs have succeeded. GitHub's dependency graph now
  records the patched root JRE tools and app Android runtime, but alert8 remains open
  after moving from 33.4.0-jre to standalone-library 33.3.1-android metadata. Other
  auxiliary/test configurations also appear in the SBOM. Do not claim zero Dependabot
  alerts or a repository-wide Guava upgrade; this release patches the selected root
  build tools and final app runtime. Broader dependency-graph cleanup is separate scope.
- Final F-Droid source/release verification succeeded. Release gate succeeded with 1,242
  Gradle tasks (12m33s), all metadata/publish contract tests, Semgrep zero findings and
  both Gitleaks scans clean. Log: build/theming-verification/release-022-verify.log.
  Signed normal APK/AAB build is running; do not mark publication complete before assets
  and checksums/certificate are verified.

## Complete — 2026-11-06

- v0.2.2 is published as the latest stable GitHub release with curated notes:
  https://github.com/worxbend/scenedeck-android/releases/tag/v0.2.2.
  Release workflow42209606626 succeeded, including signed normal APK/AAB build and
  publication. Google Play was intentionally not enabled; no F-Droid publication tag
  was created. Earlier candidate tags remain immutable without published release assets.
- All final source checks succeeded at 945db8012bd557c934b3fb9d7c2a675cfcde4510:
  main/tag CI, main/tag CodeQL, F-Droid source/release build and release quality/security.
  Run links are recorded above. Local final evidence remains 1,169 tests / 203 suites,
  zero failures/errors/skips; normal/F-Droid builds and required renders pass.
- Downloaded the published APK/AAB/SHA256SUMS. Both checksums match. APK v2 signature
  and AAB JAR signature verify; both match the existing upload certificate recorded
  above. APK manifest confirms com.worxbend.scenedeck, version0.2.2/code1013, target36.
  APK digest: c350eec1865c45a882a8d455ad2586c1db60c9489a1f24f7185fdff86db9fa45.
  AAB digest: ed1504a84ac3644bab15aab4734691891c9be5a9e7731acf3c670c23b03a5419.
- Close README/plan/ROADMAP/AGENTS with this documentation-only follow-up; no production
  changes after the verified tag. T0–T6 are complete. Preserve operator-workflow and the
  older tasks/plan.md review. The visible emulator remains available for the user.
- Known separate follow-up: Dependabot alert8 still represents auxiliary/library/test
  configurations, as recorded above. The release app/runtime and root tool selection
  are patched; no claim of a repository-wide dependency audit or zero advisories.
