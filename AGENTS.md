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
- Default styling: charcoal/azure studio palette; scene grid tiles use restrained accents and theme-aware labels.
- Scene cards show numbered positions; drag grips appear only in explicit reorder mode.

- Use StudioPageHeader/StudioSectionHeader/StudioIconWell and shared control colors from StudioChrome; scene cards have no left accent rail.
- The living StudioStylebook and docs/DESIGN_SYSTEM.md define the common visual language; connection forms open fully expanded and group identity/server/security with secure password visibility.

- Apply status-bar insets outside main-screen scroll containers; Mixer headers must remain below system icons and cutouts.

## Quality and reliability contract

- Run `scripts/check-quality.sh` with JDK21 and uv before declaring hardening done.
- Cognitive and cyclomatic complexity checks include Compose; no blanket baselines.
- Cancellation must propagate from suspend best-effort operations; settings/locks update atomically.
- Latest Mixer discovery wins; Doctor failures must not produce false stale/broken-source findings.
- Password drafts never enter saved-state Bundles; diagnostic strings redact credentials.
- Widget counts are bounded before allocation; foreground startup failure/timeouts disconnect safely.
- Stream transport: blue idle Stream, red Stop while active; Full motion breathing halo only.
- Timers remain visible; green stream, amber recording with readable light-theme contrast.
- Connection operations expose sanitized failures, preserve failed-save drafts and gate duplicate writes; blank edit-test credentials reuse encrypted storage.
- Scenes header owns status/timers (Standby, LIVE, REC); simultaneous outputs show both. Direct row: Stream, Record, VCam.

- Replay Buffer is excluded from Android: no replay controls or background polling.
