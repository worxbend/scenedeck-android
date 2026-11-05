package com.scenedeck.android.core.obs.demo

import com.scenedeck.android.core.model.ChannelLevels
import com.scenedeck.android.core.model.MonitorTypeKind
import com.scenedeck.android.core.model.ObsEvent
import com.scenedeck.android.core.model.VolumeMeterReading
import kotlin.math.log10
import kotlin.math.sin

internal data class DemoInput(
    var muted: Boolean = false,
    var volume: Double = 0.75,
    var balance: Double = 0.5,
    var syncOffsetMs: Int = 0,
    var monitor: MonitorTypeKind = MonitorTypeKind.NONE,
)

internal class DemoAudio(private val emit: (ObsEvent) -> Unit) {
    private val inputs =
        linkedMapOf(
            DemoCatalog.MIC to DemoInput(),
            DemoCatalog.MUSIC to DemoInput(volume = 0.35),
            DemoCatalog.CLIP to DemoInput(volume = 0.6),
        )

    fun input(name: String): DemoInput {
        demoRequire(DemoCatalog.inputs.any { it.name == name }, missing = true)
        return inputs[name]
            ?: run {
                demoRequire(false)
                error("Unreachable")
            }
    }

    fun mute(name: String, muted: Boolean) {
        input(name).muted = muted
        emit(ObsEvent.InputMuteStateChanged(name, muted))
    }

    fun volume(name: String, value: Double) {
        demoRequire(value.isFinite() && value in 0.0..20.0)
        input(name).volume = value
        emit(ObsEvent.InputVolumeChanged(name, value, if (value > 0) 20 * log10(value) else -100.0))
    }

    fun balance(name: String, value: Double) {
        demoRequire(value.isFinite() && value in 0.0..1.0)
        input(name).balance = value
        emit(ObsEvent.InputAudioBalanceChanged(name, value))
    }

    fun sync(name: String, value: Int) {
        demoRequire(value in -20_000..20_000)
        input(name).syncOffsetMs = value
        emit(ObsEvent.InputAudioSyncOffsetChanged(name, value))
    }

    fun monitor(name: String, value: MonitorTypeKind) {
        input(name).monitor = value
        emit(ObsEvent.InputAudioMonitorTypeChanged(name, value))
    }

    fun meters(elapsedMs: Long, clipPlaying: Boolean): List<VolumeMeterReading> =
        inputs.map { (name, input) ->
            val signal =
                if (name == DemoCatalog.CLIP && !clipPlaying) 0.0
                else 0.16 + 0.08 * sin(elapsedMs / 700.0 + name.length)
            val peak = if (input.muted) 0f else (signal * input.volume).toFloat()
            VolumeMeterReading(name, listOf(ChannelLevels(peak * 0.7f, peak, signal.toFloat())))
        }
}
