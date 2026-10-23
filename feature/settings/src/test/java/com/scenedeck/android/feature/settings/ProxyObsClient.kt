package com.scenedeck.android.feature.settings

import com.scenedeck.android.core.model.ConnectionState
import com.scenedeck.android.core.model.ObsVersionInfo
import com.scenedeck.android.core.model.SceneItemInfo
import com.scenedeck.android.core.model.SceneListSnapshot
import com.scenedeck.android.core.model.SceneSummary
import com.scenedeck.android.core.model.SpecialInputs
import com.scenedeck.android.core.obs.ObsClient
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Minimal [ObsClient] for Settings tests: answers only what audio-input discovery reads (a single
 * scene with one audio input plus Desktop Audio) and fails every other call.
 */
internal fun audioObsClient(ready: Boolean = true): ObsClient {
    val state: ConnectionState =
        if (ready) ConnectionState.Ready(ObsVersionInfo("32.2.2", "5.7.4", 1, "test"))
        else ConnectionState.Disconnected
    val connection = MutableStateFlow(state)
    val answers: Map<String, () -> Any> =
        mapOf(
            "getConnectionState" to { connection },
            "getSceneList" to { SceneListSnapshot("Show", listOf(SceneSummary("Show", 0))) },
            "getSpecialInputs" to { SpecialInputs(desktop1 = "Desktop Audio") },
            "getSceneItemList" to
                {
                    listOf(SceneItemInfo(1, 0, "Mic", enabled = true, isGroup = false, "pulse"))
                },
            "getInputVolume" to { 1.0 },
            "getInputMute" to { false },
        )
    return Proxy.newProxyInstance(
        ObsClient::class.java.classLoader,
        arrayOf(ObsClient::class.java),
    ) { _, method, _ ->
        answers[method.name]?.invoke() ?: throw UnsupportedOperationException(method.name)
    } as ObsClient
}
