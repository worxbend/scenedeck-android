package com.scenedeck.android.core.data

import com.scenedeck.android.core.model.ObsEvent

internal fun ObsEvent.invalidatesShortcut(
    binding: ShortcutBinding,
    target: ShortcutTarget?,
    dispatched: Boolean,
): Boolean {
    val scene = (binding.action as? ShortcutAction.Scene)?.name
    val input = (binding.action as? ShortcutAction.Mute)?.inputName
    val take = binding.action == ShortcutAction.TakePreview
    val preview = (target as? ShortcutTarget.Take)?.previewName
    return when (this) {
        is ObsEvent.SceneNameChanged -> referencesScene(oldSceneName, scene, take, preview)
        is ObsEvent.SceneRemoved -> referencesScene(sceneName, scene, take, preview)
        is ObsEvent.SceneListChanged ->
            listOfNotNull(scene, preview).any { name -> scenes.none { it.name == name } }
        is ObsEvent.InputNameChanged -> oldInputName == input
        is ObsEvent.InputRemoved -> inputName == input
        is ObsEvent.StudioModeStateChanged -> scene != null || take
        is ObsEvent.CurrentPreviewSceneChanged -> take && !dispatched
        else -> false
    }
}

private fun referencesScene(name: String, scene: String?, take: Boolean, preview: String?) =
    name == scene || (take && (preview == null || name == preview))
