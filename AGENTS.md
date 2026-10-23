# AGENTS.md — SceneDeck Android

Guidance for AI coding agents working in this repository.

## What this project is

SceneDeck Android is an **OBS Studio remote control deck** for Android, modeled after the
desktop Linux app at `../scenedeck` (Rust/GTK4). Read these before making changes:

- `docs/FEATURE_SPEC.md` — what to build (functional spec)
- `docs/TECH_STACK.md` — pinned stack & versions (single source of truth for dependencies)
- `docs/ARCHITECTURE.md` — module/layer rules (do not violate boundaries)
- `docs/DESIGN_SYSTEM.md` — theming, iconography, motion (all UI must follow this)
- `docs/ROADMAP.md` — phased delivery plan

## Project-level skills

Skills live in `.kimi/skills/` and **must** be consulted for their domains:

- `android-compose` — any Kotlin/Compose/build work
- `material3-expressive` — any UI/theme/motion/icon work
- `obs-websocket-v5` — any OBS protocol work

If you change documented behavior, update the matching doc in `docs/` **and** this file.

## Non-negotiables (summary)

- Compose-first UI, Material 3 Expressive design direction, edge-to-edge.
- Unidirectional data flow: immutable `UiState` in `ViewModel` exposed as `StateFlow`;
  collect with `collectAsStateWithLifecycle()`.
- Hilt DI, KSP (never kapt), Gradle Kotlin DSL + `libs.versions.toml` version catalog.
- kotlinx.serialization (never Gson/Moshi), Ktor client for WebSocket, Coil 3 for images.
- OBS WebSocket protocol logic lives **only** in the `:core:obs` module.
- The app must **never mutate the OBS scene collection**; curation metadata is local-only.
- Secrets (OBS password) go to EncryptedSharedPreferences / Keystore — never plain DataStore.
- Keep `gradle/libs.versions.toml` versions in sync with `docs/TECH_STACK.md`.

## Verification

Before declaring work done: `./gradlew assembleDebug` must build, `./gradlew testDebugUnitTest`
must pass, and Compose preview(s) for changed screens must render.

## Dev tooling

- `.editorconfig` mirrors the ktfmt kotlinlang style; keep IDE settings aligned.
- `scripts/install-git-hooks.sh` installs the pre-commit hook (ktfmt + detekt on staged Kotlin).
- `scripts/check-quality.sh` is the full local gate (JDK 21 + uv); CI runs the same checks as
  parallel GitHub Actions jobs with SARIF code-scanning uploads.

## Current UX contract

- Main tabs: Scenes, Mixer, Stats; Inventory and setup/diagnostic tools are under More.
- Scenes defaults to every OBS scene, with a Primary-only Deck filter. Keep audio on Mixer.
- Keep transport compact and advanced controls in menus; preserve output confirmations.
- Connected OBS is distinct from streaming/on-air state.
- Scene search preserves curation; disable reorder while filtering.
- Mixer mute stays direct; channel options group lock and advanced settings.
- Stats prioritize key readings and compact tonal gauge panels.
- Stats FPS/render thresholds derive from the OBS canvas FPS (`GetVideoSettings`); 60 fps is only the unknown fallback. STREAM LIVE chip is program red, REC chip amber.
- Default styling: charcoal/azure studio palette; scene grid tiles use restrained accents and theme-aware labels.
- Scene cards show numbered positions (search keeps deck numbers); drag grips appear only in explicit reorder mode, where captions/a11y describe reordering instead of switching.
- Studio T-bar is full-width; fraction follows the absolute pointer, fires past 80% on release. VCam shows Stop (tertiary) when active and Starting…/Stopping… until OBS confirms.
- Inventory: unassigned scenes show an outlined "Unassigned" chip; stale removal offers Undo. Graph edges are deduplicated per parent→child pair and role fills never use program red.
- Settings density (SceneDeckTheme.spacing), Mixer audio allow-list and meter refresh are persisted in DataStore; meter refresh is client-side sampling.
- Scene order: saved registry positions first, then OBS order (descending `sceneIndex`), then name; Scenes and Inventory share it. Reorders renumber densely and never assign roles; import applies the file's order so export → import round-trips.

- Use StudioPageHeader/StudioSectionHeader/StudioIconWell and shared control colors from StudioChrome; scene cards have no left accent rail.
- The living StudioStylebook and docs/DESIGN_SYSTEM.md define the common visual language; connection forms open fully expanded and group identity/server/security with secure password visibility (shared `StudioPasswordField`, also used by onboarding).

- Apply status-bar insets outside main-screen scroll containers; Mixer headers must remain below system icons and cutouts.
- Compact-height windows use a navigation rail and a single-row status strip; the shell applies horizontal safe-drawing insets (end only beside the rail; the rail side clears cutouts). Snackbars use StudioSnackbarHost bottom-aligned; system-bar icons follow the resolved app theme via `SystemBarStyle.auto` (never light/dark styles, which drop the contrast scrim).

## Quality and reliability contract

- Run `scripts/check-quality.sh` with JDK21 and uv before declaring hardening done.
- Cognitive and cyclomatic complexity checks include Compose; no blanket baselines.
- Cancellation must propagate from suspend best-effort operations; settings/locks update atomically.
- Reconnect backoff (1–30 s) is skippable via `retryNow()` (Retry now button, app
  foreground, network available); it only wakes a pending backoff and keeps the attempt count.
- Latest Mixer discovery wins; Doctor failures must not produce false stale/broken-source findings.
- Doctor failed runs (incl. graph-walk timeouts) show a sanitized error, never prior results or All clear; inversions need explicit roles, except production → explicit Debug/Archive.
- OBS requests have a 10-second admission-and-response deadline, remain bound to their
  originating session, and never retry timed-out writes automatically. Caller cancellation
  propagates; diagnostic failures exclude server comments and transport/parser details.
  Typed requests, initial GetVersion and the complete receiver share one serial protocol
  execution lane while up to eight requests remain suspended in flight. Recheck the pinned
  ktobs non-suspending UUID critical-section assumption and cleanup regressions on upgrades.
- Thumbnail polling admits at most two capture jobs per awaited batch, prunes removed
  scenes, and rejects images above 2 MiB compressed or 1,048,576 pixels before bitmap
  allocation. Cached memory still scales with eligible scenes; do not claim measured
  device performance without profiling.
- Mixer mute/volume use confirmed values plus pending write tokens; obsolete failures
  cannot roll back newer actions/events. Discovery is coalesced separately from immediate
  event reduction.
- OBS scene renames update deck/tallies/Mixer selection at once and move curation in one
  locked registry transaction; an existing entry for the new name wins and both are kept.
- Registry deletion shares the mutation mutex; bulk mutations use one indexed snapshot
  and bulk write, preserving duplicate-import and ordering semantics. Graph metadata
  indexes preserve the first duplicate entry.
- Password drafts never enter saved-state Bundles; diagnostic strings redact credentials.
- Widget counts are bounded before allocation; foreground startup failure/timeouts disconnect safely.
- Widgets deduplicate their six visible scenes, program scene and connection projection;
  Ready with no scenes clears stale actions, while unready preserves the cached grid.
  Explicit widget refresh remains unconditional.
- Stream transport: blue idle Stream, red Stop while active; Full motion breathing halo only.
- Timers remain visible; green stream, amber recording with readable light-theme contrast.
- Onboarding Connect retries reuse one draft profile; never add a profile per attempt.
- Connection operations expose sanitized failures, preserve failed-save drafts and gate duplicate writes; blank edit-test credentials reuse encrypted storage.
- Scenes header owns status/timers (Standby, LIVE, REC); simultaneous outputs show both. Direct row: Stream, Record, VCam.

- Replay Buffer is excluded from Android: no replay controls or background polling.

- Persistent status bar shows OFF AIR / LIVE with stream time / REC with record time on all main tabs; disconnected output state is unknown, never assumed idle.
- Persistent performance metrics appear only while streaming, never in standby or recording-only mode.
- Keep-alive notification mirrors the status bar (LIVE/REC times, OFF AIR only when both outputs are known); denied notification permission is explained, blocked permission links to app notification settings; Keep-alive & background is listed under More.
- Scene deep-link failures (no profile, not connected, rejected switch) show a sanitized toast; success stays silent.

- AGP build-tool security minimums are constrained on the root buildscript `classpath`,
  with catalog versions documented in TECH_STACK.md; avoid forcing those jars into
  Android runtime configurations. Recheck `buildEnvironment` and debug signing after
  changing them.

## Delivery contract

- `.github/workflows/release.yml` gates APK/AAB builds on quality checks.
- Signing uses environment credentials; never commit upload keys or service-account JSON.
- `scripts/configure-play-signing.py` uploads signing secrets using a local hidden password prompt; `scripts/play-infra.py` uses the current gcloud login without printing or saving access tokens.
- Play delivery defaults to internal/draft; setup is documented in `docs/RELEASING.md`.
- Play application ID is `com.worxbend.scenedeck`; Kotlin namespaces remain `com.scenedeck.android`.
- Settings links to `docs/PRIVACY_POLICY.md`; Play Data safety for standard builds must account for ML Kit diagnostics, not claim that no data is collected.
- `infra/google-play` provisions publishing APIs/account and restricted GitHub OIDC trust; tag track/status and repository listing uploads are explicit opt-ins.
- OIDC trust checks the environment claim directly alongside numeric repository/owner IDs and the release workflow path; do not assume GitHub's legacy subject format.
- Validate delivery changes with `python3 scripts/test_release_metadata.py` and `ruby fastlane/test_publish.rb`.
- Resolve the default Play metadata path from the Fastfile directory; listing uploads must work from both repository and Fastlane working directories.

## F-Droid contract

- App source is MIT licensed, matching desktop SceneDeck; bundled font assets retain SIL OFL 1.1 copyright/license texts from `LICENSES`.
- Inter 4.1 and JetBrains Mono 2.304 are bundled; do not reintroduce the Google Play services font provider.
- `-PfdroidBuild=true` excludes `feature/connections/src/play` QR code/dependencies and camera permission; hide Scan QR and preserve manual connections. Normal Play/GitHub builds retain QR pairing.
- Register extra Kotlin sources through AGP built-in Kotlin's variant source API, not Java directories.
- F-Droid mode ignores upload signing credentials and produces an unsigned release APK; its packaging recipe removes only the excluded Play QR source directory.
- Release builds use R8 code/resource shrinking; keep VCS info and the APK dependency-metadata block disabled so F-Droid builds stay byte-reproducible.
- F-Droid publishes the upstream-signed APK (`Binaries` + `AllowedAPKSigningKeys`); only the `fdroid-v*` workflow's `signed-release` job signs it (upload key, v2/v3, alignment preserved).
- Validate F-Droid source/APK with `scripts/check-fdroid-build.py`, fdroidserver 2.4.5 and the F-Droid workflow; complete build/submission steps are in `docs/FDROID.md`.
- F-Droid versions are static in `fdroid/version.properties`; increment code/name and publish matching `fdroid-vX.Y.Z` tags. F-Droid auto-update metadata reads this file; do not freeze future versions with recipe Gradle overrides.
