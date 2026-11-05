package com.scenedeck.android.core.obs.demo

import com.scenedeck.android.core.model.ObsEvent
import com.scenedeck.android.core.model.SceneItemInfo
import com.scenedeck.android.core.model.SceneListSnapshot

internal class DemoStudio(private val emit: (ObsEvent) -> Unit) {
    var program = DemoCatalog.CAMERA
        private set

    private var preview = DemoCatalog.INTERVIEW
    var enabled = false
        private set

    val transitions = DemoTransitions(emit)
    private val items = DemoCatalog.items()

    fun scenes() = SceneListSnapshot(program, DemoCatalog.scenes)

    fun program(name: String) {
        DemoCatalog.requireScene(name)
        program = name
        emit(ObsEvent.CurrentProgramSceneChanged(name))
    }

    fun preview(): String {
        demoRequire(enabled)
        return preview
    }

    fun preview(name: String) {
        demoRequire(enabled)
        DemoCatalog.requireScene(name)
        preview = name
        emit(ObsEvent.CurrentPreviewSceneChanged(name))
    }

    fun enabled(value: Boolean) {
        enabled = value
        emit(ObsEvent.StudioModeStateChanged(value))
    }

    fun take() {
        demoRequire(enabled)
        val previous = program
        emit(ObsEvent.SceneTransitionStarted(transitions.current().name))
        program(preview)
        preview(previous)
        emit(ObsEvent.SceneTransitionEnded(transitions.current().name))
    }

    fun items(name: String): List<SceneItemInfo> {
        DemoCatalog.requireScene(name)
        return items.getValue(name)
    }

    fun item(name: String, id: Int): SceneItemInfo {
        val item = items(name).find { it.id == id }
        demoRequire(item != null, missing = true)
        return requireNotNull(item)
    }

    fun itemEnabled(name: String, id: Int, enabled: Boolean) {
        item(name, id)
        items[name] = items(name).map { if (it.id == id) it.copy(enabled = enabled) else it }
        emit(ObsEvent.SceneItemEnableStateChanged(name, id, enabled))
    }
}
