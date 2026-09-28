package com.scenedeck.android.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class ThemeFamily {
    SCENEDECK,
}

// Placeholder palette — deep indigo/violet accent. The design-system agent will
// replace this with the full token set from docs/DESIGN_SYSTEM.md.
private val SceneDeckDarkColorScheme = darkColorScheme(
    primary = Color(0xFFCBB8FF),
    onPrimary = Color(0xFF331069),
    primaryContainer = Color(0xFF4A2F81),
    onPrimaryContainer = Color(0xFFE9DDFF),
    secondary = Color(0xFFCBC2DB),
    surface = Color(0xFF141218),
    background = Color(0xFF141218),
)

private val SceneDeckLightColorScheme = lightColorScheme(
    primary = Color(0xFF6247A6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE9DDFF),
    onPrimaryContainer = Color(0xFF1D0050),
    secondary = Color(0xFF625B70),
    surface = Color(0xFFFDF7FF),
    background = Color(0xFFFDF7FF),
)

@Composable
fun SceneDeckTheme(
    family: ThemeFamily = ThemeFamily.SCENEDECK,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val baseScheme = when (family) {
        ThemeFamily.SCENEDECK ->
            if (darkTheme) SceneDeckDarkColorScheme else SceneDeckLightColorScheme
    }
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        else -> baseScheme
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
