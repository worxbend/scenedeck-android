package com.scenedeck.android.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import dagger.hilt.android.EntryPointAccessors

/** Manifest entry point for the mini-deck widget (config-less). */
class SceneDeckWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SceneDeckWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        pokeUpdater(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // A newly placed widget must not wait for the next DeckState change.
        pokeUpdater(context)
    }

    private fun pokeUpdater(context: Context) {
        EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
            .widgetUpdater()
            .poke()
    }
}
