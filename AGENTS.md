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
