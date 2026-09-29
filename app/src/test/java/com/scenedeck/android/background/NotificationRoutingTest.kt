package com.scenedeck.android.background

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Notification quick-action routing: receiver → service intent (M7). */
@RunWith(RobolectricTestRunner::class)
class NotificationRoutingTest {

    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `service intent targets ObsSessionService with the given action`() {
        val intent = ObsSessionService.intent(app, ObsSessionService.ACTION_DISCONNECT)

        assertEquals(ObsSessionService::class.java.name, intent.component?.className)
        assertEquals(ObsSessionService.ACTION_DISCONNECT, intent.action)
    }

    @Test
    fun `notification disconnect action starts the service with ACTION_DISCONNECT`() {
        NotificationActionReceiver().onReceive(
            app,
            Intent(NotificationActionReceiver.ACTION_DISCONNECT),
        )

        val started = shadowOf(app).nextStartedService
        assertEquals(ObsSessionService::class.java.name, started.component?.className)
        assertEquals(ObsSessionService.ACTION_DISCONNECT, started.action)
    }
}
