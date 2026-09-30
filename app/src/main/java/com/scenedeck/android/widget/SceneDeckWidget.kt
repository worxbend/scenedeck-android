package com.scenedeck.android.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.components.Scaffold
import androidx.glance.appwidget.components.TitleBar
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.GridCells
import androidx.glance.appwidget.lazy.LazyVerticalGrid
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.scenedeck.android.MainActivity
import com.scenedeck.android.R

/**
 * Home-screen mini-deck: 2-column grid of PRIMARY scenes (up to [SceneDeckWidgetKeys.MAX_SCENES])
 * with the program scene tallied red. Scene taps route through [SetSceneAction] → SceneSwitcher
 * (connect-then-act); the title bar opens the app. State is pushed by [SceneDeckWidgetUpdater] (no
 * periodic polling).
 */
class SceneDeckWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                WidgetContent(WidgetSnapshot.readFrom(currentState<Preferences>()))
            }
        }
    }
}

@Composable
internal fun WidgetContent(snapshot: WidgetSnapshot) {
    Scaffold(
        titleBar = {
            TitleBar(
                startIcon = ImageProvider(R.drawable.ic_launcher_monochrome),
                title = "SceneDeck · ${snapshot.connectionLabel}",
                iconColor = GlanceTheme.colors.primary,
                modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>()),
            )
        },
        modifier =
            GlanceModifier.appWidgetBackground().background(GlanceTheme.colors.widgetBackground),
    ) {
        if (snapshot.sceneNames.isEmpty()) {
            EmptyState()
        } else {
            LazyVerticalGrid(
                gridCells = GridCells.Fixed(2),
                modifier = GlanceModifier.fillMaxSize().padding(horizontal = 8.dp),
            ) {
                items(snapshot.sceneNames.size) { slot ->
                    SceneChip(
                        name = snapshot.sceneNames[slot],
                        isProgram = snapshot.sceneNames[slot] == snapshot.programScene,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "No scenes yet",
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Medium),
        )
        Text(
            text = "Tap to open SceneDeck",
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
        )
    }
}

@Composable
private fun SceneChip(name: String, isProgram: Boolean) {
    val background =
        if (isProgram) GlanceTheme.colors.error else GlanceTheme.colors.secondaryContainer
    val content =
        if (isProgram) GlanceTheme.colors.onError else GlanceTheme.colors.onSecondaryContainer
    Box(
        modifier =
            GlanceModifier.fillMaxWidth()
                .padding(4.dp)
                .cornerRadius(12.dp)
                .background(background)
                .clickable(
                    actionRunCallback<SetSceneAction>(
                        actionParametersOf(SceneDeckWidgetKeys.sceneNameParam to name)
                    )
                )
                .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name,
            maxLines = 1,
            style =
                TextStyle(
                    color = content,
                    fontWeight = if (isProgram) FontWeight.Bold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                ),
        )
    }
}
