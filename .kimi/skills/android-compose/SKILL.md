---
name: android-compose
description: Modern Android development with Jetpack Compose for SceneDeck Android — Kotlin 2.4, Compose BOM 2026.09, Material 3 Expressive, Hilt, KSP, UDF architecture. Use for any Kotlin/Compose/Gradle work in this project.
---

# Android + Jetpack Compose rules

Compose-first Android development. Snapshot: **Kotlin 2.4.0, Compose BOM 2026.09.00
(core 1.11.x), material3 stable 1.4.x + Expressive 1.5.x line, AGP 9.x,
compileSdk/targetSdk 36, minSdk 28.** Versions drift — check
`gradle/libs.versions.toml` first and treat `docs/TECH_STACK.md` as the source of truth.

## Core rules

1. **Compose-first.** New UI in Compose, never XML. View interop only if truly forced.
2. **Hoist state, one-way data flow.** Composables take state down as params, send
   events up as lambdas (or a single `onEvent(UiEvent)`). UI state = immutable
   `data class` in a `ViewModel`, exposed as `StateFlow`.
3. **Strong skipping is on** (since Kotlin 2.0.20). Don't reflexively `remember`/
   `derivedStateOf` everything. Pass stable types, mark models `@Immutable`.
4. **Material 3 Expressive is the design direction.** `MaterialExpressiveTheme`,
   `MotionScheme`, expressive components; keep `@ExperimentalMaterial3ExpressiveApi`
   opt-ins honest and fall back to stable M3 where needed.
5. **Compose compiler via `org.jetbrains.kotlin.plugin.compose`**, versioned with
   Kotlin. `composeOptions { kotlinCompilerExtensionVersion }` is obsolete — delete it.
6. **Collect with `collectAsStateWithLifecycle()`**, never `collectAsState()`.
7. **KSP, never kapt.** Gradle Kotlin DSL + `libs.versions.toml` catalog.
   Edge-to-edge by default (`enableEdgeToEdge()`; enforced at targetSdk 35+).

## Project structure (this repo)

Multi-module per `docs/ARCHITECTURE.md`: `:app`, `:core:{obs,model,common,data,
datastore,database,designsystem,ui}`, `:feature:{live,mixer,stats,inventory,graph,
doctor,connections,settings,onboarding}`. Rules:

- OBS WebSocket protocol code lives ONLY in `:core:obs` behind the `ObsClient` interface.
- Feature modules never define colors/shapes/type ad hoc — use `:core:designsystem`.
- kotlinx.serialization only; Ktor client for WebSocket; Coil 3 for images; Hilt DI
  with `hiltViewModel()`; Proto DataStore for prefs; Room for structured data;
  passwords in Keystore/EncryptedSharedPreferences only.
- The app NEVER mutates the OBS scene collection — curation metadata stays local.

## Quick patterns

Stateful screen + stateless content:

```kotlin
@Composable
fun LiveScreen(viewModel: LiveViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LiveContent(state = state, onEvent = viewModel::onEvent)
}
```

Immutable UI state:

```kotlin
data class LiveUiState(val scenes: ImmutableList<SceneCardState> = persistentListOf())

class LiveViewModel @Inject constructor(...) : ViewModel() {
    private val _uiState = MutableStateFlow(LiveUiState())
    val uiState: StateFlow<LiveUiState> = _uiState.asStateFlow()
}
```

Expressive theme:

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SceneDeckTheme(family: ThemeFamily, dark: Boolean, content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = family.colorScheme(dark),
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
```

Lazy lists with stable keys: `items(items, key = { it.id })`.

## Testing & verification

- ViewModels/reducers: JUnit + MockK + Turbine + coroutines-test.
- Screens: Compose Test (`createComposeRule`, semantics/testTags).
- Visuals: Roborazzi goldens, one per theme family for key components.
- Performance: Baseline Profiles + Macrobenchmark for deck scroll and meters.
- Done means: `assembleDebug` builds, `testDebugUnitTest` passes, previews render.

## Pitfalls (version-tagged)

- kapt → KSP; `composeOptions` → compose compiler Gradle plugin; Accompanist →
  first-party APIs; `collectAsState` → `collectAsStateWithLifecycle`; Navigation 2
  string routes → type-safe `@Serializable` routes (Nav3 back stack owned by app).
- Don't read state eagerly in composition when it can be deferred to draw/layout
  (lambda modifiers) for high-frequency values like meter levels.
