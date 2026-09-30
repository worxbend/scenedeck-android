# Tech Stack — SceneDeck Android

Researched 2026-09-28. Versions below are the **target pins** for project bootstrap;
re-verify against Maven Central / AndroidX release notes at kickoff and quarterly.

## Language & build

| Component | Choice | Version (target) | Rationale |
|---|---|---|---|
| Kotlin | Kotlin 2.x | **2.4.20** (verified 2026-09-28) | Latest stable; ships Compose compiler plugin in lockstep |
| Compose compiler | `org.jetbrains.kotlin.plugin.compose` | = Kotlin version | `composeOptions { kotlinCompilerExtensionVersion }` is obsolete |
| AGP | Android Gradle Plugin | **9.4.1** | latest stable 9.x; supports up to `compileSdk 37`; **built-in Kotlin** (see note below) |
| Build DSL | Gradle **Kotlin DSL** + `libs.versions.toml` version catalog | — | single source of truth for versions |
| Gradle | Wrapper | **9.6.0** | AGP 9.4 requires Gradle ≥ 9.6.0 |
| Compile/test SDK | `compileSdk` **37** (installed platform), `targetSdk` **36**, `minSdk` 28 | — | OBS ≥ 28 era devices; edge-to-edge enforced |
| KSP | `com.google.devtools.ksp` | **2.3.12** (new standalone versioning) | **never kapt** |

> **AGP 9 built-in Kotlin:** `org.jetbrains.kotlin.android` must NOT be applied to Android
> modules — Kotlin support is built into AGP 9. The Compose compiler plugin is still applied
> explicitly with the Kotlin version: `alias(libs.plugins.kotlin.compose)`.

## UI

| Component | Choice | Version (target) | Rationale |
|---|---|---|---|
| UI toolkit | **Jetpack Compose** (BOM) | **BOM 2026.09.00** (core 1.11.x) | latest stable line |
| Design system | `androidx.compose.material3` | **1.5.0-alpha29 (Expressive)**, explicit pin overrides BOM | M3 Expressive is the design direction; opt in via `@ExperimentalMaterial3ExpressiveApi` where needed, fall back to stable M3 surface |
| Adaptive / large screens | `material3-adaptive` (nav suite, list-detail) | via BOM | phone + tablet/foldable support |
| Navigation | **Navigation 3** (`androidx.navigation3`) | **1.2.0** (stable) | app-owned back stack, Compose-native; fallback: Navigation Compose 2.9 type-safe routes |
| Icons | Hand-ported **Lucide-style `ImageVector` catalogue** in `:core:designsystem/icons/` (`SceneIcon`, `SceneDeckIcons`) — compose-icons Lucide pack is **NOT on Maven Central** (verified 2026-09-28); re-check periodically | modern, consistent icon set beyond stock Material icons |
| Images | **Coil 3** (`coil-compose`) | **3.6.3** | scene screenshot thumbnails |
| Charts | Custom Canvas composables (meters/gauges/trends) | — | full control of OBS-style meter look; evaluate Vico only if scope grows |
| Splash | `androidx.core:core-splashscreen` | latest | branded launch |
| Haptics | `androidx.compose.ui.hapticfeedback` + `HapticFeedbackType` | — | tactile deck feel |

## Architecture & DI

| Component | Choice | Rationale |
|---|---|---|
| Pattern | MVVM + UDF: immutable `UiState` data classes, `StateFlow`, `collectAsStateWithLifecycle()` | strong-skipping era best practice |
| DI | **Hilt** (`hiltViewModel()`) | standard for Compose apps; compile-time safety via KSP |
| Async | kotlinx-coroutines + Flow | — |
| Lifecycle | `androidx.lifecycle` ViewModel + runtime-compose | — |

## Networking & OBS protocol

| Component | Choice | Rationale |
|---|---|---|
| WebSocket | **Ktor client** (`ktor-client-okhttp` engine + `ktor-client-websockets`) | coroutine-native, pairs with kotlinx.serialization |
| OBS client | **`io.github.rejeq:ktobs-core` + `ktobs-ktor` 0.5.0** | existing KMP obs-websocket v5 client on Maven Central (Hello/Identify/auth, typed events/requests). **Kickoff spike:** verify coverage of `InputVolumeMeters`, `GetSourceScreenshot`, studio-mode requests; wrap behind our own `ObsClient` interface so we can drop in a hand-rolled client (Ktor + kotlinx.serialization) if gaps appear |
| Serialization | **kotlinx.serialization** | required by ktobs/ktor; never Gson/Moshi |

## Data & storage

| Component | Choice | Rationale |
|---|---|---|
| Preferences | **Proto DataStore** (settings, registry metadata) | typed, coroutine-based |
| Secrets | **EncryptedSharedPreferences / Keystore** (OBS passwords) | password never in plain config (mirrors desktop keyring approach) |
| Structured data | **Room** (connection profiles, registry, stats ring buffer if persisted) | KSP codegen |
| Import/export | YAML via `kaml` (or JSON via kotlinx.serialization) | registry backup/sharing, desktop-compatible spirit |

## Quality & tooling

| Component | Choice | Rationale |
|---|---|---|
| Unit tests | JUnit4/5, **MockK**, **Turbine**, coroutines-test | flow-first testing |
| UI tests | Compose Test (`createComposeRule`), semantics testTags | — |
| Screenshot tests | **Roborazzi 1.75.0** (`io.github.takahirom.roborazzi` plugin) + **Robolectric 4.17** (modules with goldens; `recordRoborazziDebug` / `verifyRoborazziDebug`, plain `testDebugUnitTest` verifies by default). Golden matrix since M8a: key screens × {SCENEDECK dark, OBS dark, NORD dark, HIGH_CONTRAST dark, SCENEDECK light} | golden-shot regression for themes/cards |
| Fonts | `androidx.compose.ui:ui-text-google-fonts` **1.12.1** (Inter + JetBrains Mono downloadable fonts) | brand typography without bundling font files |
| Widgets | **Glance 1.2.0** (`androidx.glance:glance-appwidget` + `glance-appwidget-testing` for unit tests) + androidx-core **1.19.1** | home-screen mini-deck (M7) |
| Camera/QR | CameraX **1.6.2** + ML Kit barcode **17.3.0** | `obsws://` QR pairing (M2) |
| Lint/format | **detekt** + **ktlint** (or ktfmt) + Android Lint | CI gate |
| Performance | **Baseline Profiles** + Macrobenchmark | cold start + deck scroll/meter jank budgets |
| Dependency updates | Renovate or Dependabot | keep BOM current |
| CI | GitHub Actions: build, detekt, unit + screenshot tests | — |

## Explicitly NOT used

- **XML layouts / View system** — Compose-first (interop only if ever forced).
- **Accompanist** — superseded by first-party APIs.
- **collectAsState()** — always `collectAsStateWithLifecycle()`.
- **Gson / Moshi / Retrofit** — no REST backend; WebSocket-only app.
- **kapt** — KSP only.

## Version-catalog pins (`gradle/libs.versions.toml`)

Verified against Maven Central / Google Maven at kickoff (2026-09-28):

```toml
[versions]
kotlin = "2.4.20"
ksp = "2.3.12"                # new standalone KSP versioning (no kotlin prefix)
agp = "9.4.1"                 # requires Gradle 9.6.0
compose-bom = "2026.09.00"
material3 = "1.5.0-alpha29"   # Expressive line; explicit pin overrides BOM
navigation3 = "1.2.0"
hilt = "2.60.1"               # hilt-navigation-compose 1.4.0
ktor = "3.6.0"
ktobs = "0.5.0"
kotlinx-serialization = "1.11.0"
kotlinx-coroutines = "1.11.0"
coil = "3.6.3"
room = "2.8.5"
datastore = "1.2.1"
lifecycle = "2.11.0"
activity-compose = "1.13.0"
core-splashscreen = "1.2.0"
junit = "4.13.2"
mockk = "1.14.11"
turbine = "1.2.1"
detekt = "1.23.8"
mockwebserver = "5.5.0"     # :core:obs protocol tests (matches ktor's okhttp 5.5.0)
datastore-preferences = "1.2.1"
androidx-test-core = "1.7.0"
javax-inject = "1"
camerax = "1.6.2"           # QR pairing (M2)
mlkit-barcode = "17.3.0"    # QR pairing (M2)
reorderable = "3.1.0"         # deck drag-to-reorder (M3)
kaml = "0.104.0"              # YAML registry export/import (M5)
# coroutines-guava reuses the kotlinx-coroutines pin
ui-text-google-fonts = "1.12.1"
roborazzi = "1.75.0"
robolectric = "4.17"
# compose-icons Lucide pack: NOT published on Maven Central — TODO re-check
```

## Hardening tooling — 2026-09-30

- Detekt 1.23.8: host JDK21, JVM target17, cognitive/cyclomatic thresholds15 (including Compose).
- ktfmt0.64: Kotlin style; `ktfmtCheck` verifies, `ktfmtFormat` applies.
- Semgrep1.156.0: checked-in security rules, local scan, no metrics submission.
- Android Lint checks all Android modules; CI stores reports and fails on errors.
- Concurrent futures/ktx1.2.0 and error-prone annotations2.30.0 align app/instrumentation runtime constraints.
- `scripts/check-quality.sh` runs the complete gate; requires JDK21 and uv.
- Gitleaks8.30.1: checksum-verified Linux scanner for working tree and Git history.
- Hosted CodeQL Kotlin/Java security-extended checks use a manual build; Dependabot proposes weekly updates.
- Reviewed lint upgrade advisories remain tied to the pinned stack; see HARDENING.md.
