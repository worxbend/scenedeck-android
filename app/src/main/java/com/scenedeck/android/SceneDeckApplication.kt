package com.scenedeck.android

import android.app.Application
import com.scenedeck.android.widget.SceneDeckWidgetUpdater
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SceneDeckApplication : Application() {

    @Inject lateinit var widgetUpdater: SceneDeckWidgetUpdater

    override fun onCreate() {
        super.onCreate()
        // Push DeckState → home-screen widget for the whole process lifetime
        // (this also runs when the system cold-starts the process for a widget tap).
        widgetUpdater.start()
    }
}
