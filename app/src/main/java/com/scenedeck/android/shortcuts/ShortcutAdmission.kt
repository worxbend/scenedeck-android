package com.scenedeck.android.shortcuts

import com.scenedeck.android.navigation.SceneDeckDestination

/** Main-thread admission. Focus only gates keys: the owned consent dialog takes window focus. */
internal class ShortcutAdmission(private val invalidate: () -> Unit) {
    private var route: SceneDeckDestination? = null
    private var modal = false
    var foreground = false
        private set

    private var focused = false
    var activation = 0L
        private set

    val eligible: Boolean
        get() = foreground && focused && shellEligible

    private val shellEligible: Boolean
        get() = route in SceneDeckDestination.primary && !modal

    fun shell(destination: SceneDeckDestination?, blocked: Boolean) {
        if (route == destination && modal == blocked) return
        invalidate()
        activation++
        route = destination
        modal = blocked
    }

    /** Called before mutating the back stack or opening shell sheets. */
    fun navigating() = shell(null, true)

    fun foreground(value: Boolean) {
        if (foreground == value) return
        if (!value) invalidate()
        foreground = value
        activation++
    }

    fun focus(value: Boolean) {
        if (focused == value) return
        focused = value
        activation++
    }
}
