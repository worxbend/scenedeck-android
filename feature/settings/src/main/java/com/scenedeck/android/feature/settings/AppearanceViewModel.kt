package com.scenedeck.android.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scenedeck.android.core.data.DarkMode
import com.scenedeck.android.core.data.SettingsRepository
import com.scenedeck.android.core.data.UserSettings
import com.scenedeck.android.core.designsystem.theme.ThemeAccent
import com.scenedeck.android.core.designsystem.theme.ThemeFamily
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppearanceSettings(
    val family: ThemeFamily = ThemeFamily.SCENEDECK,
    val mode: DarkMode = DarkMode.SYSTEM,
    val wallpaper: Boolean = false,
    val accent: ThemeAccent = ThemeAccent.DEFAULT,
    val trueBlack: Boolean = false,
) {
    fun isDark(system: Boolean): Boolean =
        when (mode) {
            DarkMode.SYSTEM -> system
            DarkMode.DARK -> true
            DarkMode.LIGHT -> false
        }
}

data class AppearanceState(
    val current: AppearanceSettings? = null,
    val original: AppearanceSettings? = null,
    val draft: AppearanceSettings? = null,
    val saving: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class AppearanceViewModel @Inject constructor(private val settings: SettingsRepository) :
    ViewModel() {
    private val mutable = MutableStateFlow(AppearanceState())
    val state = mutable.asStateFlow()

    init {
        viewModelScope.launch {
            settings.settings
                .catch {
                    mutable.update {
                        it.copy(error = "Couldn't load appearance. Reopen Settings to retry.")
                    }
                }
                .collect { saved -> mutable.update { it.copy(current = saved.appearance()) } }
        }
    }

    fun open() {
        mutable.update {
            if (it.saving || it.draft != null || it.current == null) it
            else it.copy(original = it.current, draft = it.current, error = null)
        }
    }

    fun edit(change: (AppearanceSettings) -> AppearanceSettings) {
        mutable.update { old ->
            if (old.saving) old else old.copy(draft = old.draft?.let(change), error = null)
        }
    }

    fun close() {
        mutable.update {
            if (it.saving) it else it.copy(original = null, draft = null, error = null)
        }
    }

    /** Admission occurs before launch; edits/close cannot replace an admitted save. */
    fun save() {
        val before = mutable.value
        val draft = before.draft
        val original = before.original
        if (before.saving || draft == null || original == null) return
        mutable.value = before.copy(saving = true, error = null)
        viewModelScope.launch { persist(original, draft) }
    }

    @Suppress(
        "TooGenericExceptionCaught"
    ) // Storage details never enter UI; cancellation propagates.
    private suspend fun persist(original: AppearanceSettings, draft: AppearanceSettings) {
        try {
            settings.update { current ->
                check(current.appearance() == original) { "Appearance changed" }
                current.copy(
                    themeFamily = draft.family.name,
                    darkMode = draft.mode,
                    dynamicColor = draft.wallpaper,
                    themeAccent = draft.accent.name,
                    trueBlack = draft.trueBlack,
                )
            }
            mutable.update {
                it.copy(current = draft, original = null, draft = null, saving = false)
            }
        } catch (cancelled: CancellationException) {
            mutable.update { it.copy(saving = false) }
            throw cancelled
        } catch (_: Exception) {
            mutable.update {
                it.copy(
                    saving = false,
                    error =
                        "Couldn't save appearance. Draft kept. Retry or reopen to use current settings.",
                )
            }
        }
    }
}

internal fun UserSettings.appearance() =
    AppearanceSettings(
        family =
            ThemeFamily.entries.firstOrNull { it.name == themeFamily } ?: ThemeFamily.SCENEDECK,
        mode = darkMode,
        wallpaper = dynamicColor,
        accent = ThemeAccent.fromName(themeAccent),
        trueBlack = trueBlack,
    )
