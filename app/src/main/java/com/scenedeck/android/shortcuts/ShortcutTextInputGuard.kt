package com.scenedeck.android.shortcuts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputInterceptor

/** Main-thread leases cover hardware editing even while the software keyboard is hidden. */
internal class ShortcutTextInputGuard {
    private val sessions = mutableSetOf<Any>()
    val editing: Boolean
        get() = sessions.isNotEmpty()

    fun enter(): Any = Any().also { sessions.add(it) }

    fun leave(session: Any) {
        sessions.remove(session)
    }
}

@Composable
internal fun ShortcutTextInputBoundary(
    guard: ShortcutTextInputGuard,
    content: @Composable () -> Unit,
) {
    val interceptor =
        remember(guard) {
            PlatformTextInputInterceptor { request, nextHandler ->
                val session = guard.enter()
                try {
                    nextHandler.startInputMethod(request)
                } finally {
                    guard.leave(session)
                }
            }
        }
    InterceptPlatformTextInput(interceptor, content)
}
