package com.scenedeck.android.demo

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.test.core.app.ApplicationProvider
import com.scenedeck.android.MainActivity
import com.scenedeck.android.background.ObsSessionService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class DemoManifestTest {
    @Test
    fun demoHasPrivateProcessAndSeparateTaskWhileExternalEntryPointsStayLive() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.packageManager
        val demo = manager.getActivityInfo(ComponentName(context, DemoActivity::class.java), 0)
        val live = manager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
        val service =
            manager.getServiceInfo(ComponentName(context, ObsSessionService::class.java), 0)
        assertFalse(demo.exported)
        assertEquals("${context.packageName}:demo", demo.processName)
        assertEquals(ActivityInfo.LAUNCH_SINGLE_TASK, demo.launchMode)
        assertNotEquals(live.taskAffinity, demo.taskAffinity)
        assertEquals(context.packageName, live.processName)
        assertEquals(context.packageName, service.processName)
        val intent = DemoActivity.intent(context)
        assertEquals(ComponentName(context, DemoActivity::class.java), intent.component)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }
}
