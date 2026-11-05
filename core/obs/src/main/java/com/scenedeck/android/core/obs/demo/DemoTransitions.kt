package com.scenedeck.android.core.obs.demo

import com.scenedeck.android.core.model.CurrentTransition
import com.scenedeck.android.core.model.ObsEvent
import com.scenedeck.android.core.model.TransitionInfo
import com.scenedeck.android.core.model.TransitionListSnapshot

internal class DemoTransitions(private val emit: (ObsEvent) -> Unit) {
    private var transitionName = "Fade"
    private var durationMs = 300

    fun list() =
        TransitionListSnapshot(
            transitionName,
            listOf(
                TransitionInfo("Cut", "cut_transition", true, null),
                TransitionInfo("Fade", "fade_transition", false, durationMs),
            ),
        )

    fun current(): CurrentTransition {
        val cut = transitionName == "Cut"
        return CurrentTransition(
            transitionName,
            if (cut) "cut_transition" else "fade_transition",
            if (cut) null else durationMs,
            !cut,
            cut,
        )
    }

    fun select(name: String) {
        demoRequire(name == "Cut" || name == "Fade", missing = true)
        transitionName = name
        emit(ObsEvent.CurrentSceneTransitionChanged(name))
    }

    fun duration(value: Int) {
        demoRequire(transitionName != "Cut" && value in 50..20_000)
        durationMs = value
        emit(ObsEvent.CurrentSceneTransitionDurationChanged(value))
    }
}
