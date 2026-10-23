package com.scenedeck.android.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class KeepAliveNoticeTest {

    @Test
    fun `denial with rationale can be asked again, without rationale is blocked`() {
        assertEquals(
            NotificationPermissionDenial.DENIED,
            classifyDenial(shouldShowRationale = true),
        )
        assertEquals(
            NotificationPermissionDenial.BLOCKED,
            classifyDenial(shouldShowRationale = false),
        )
    }

    @Test
    fun `denials are explained while notifications stay off`() {
        assertEquals(
            KeepAliveNotice.PERMISSION_DENIED,
            keepAliveNotice(false, false, NotificationPermissionDenial.DENIED),
        )
        assertEquals(
            KeepAliveNotice.PERMISSION_BLOCKED,
            keepAliveNotice(false, false, NotificationPermissionDenial.BLOCKED),
        )
    }

    @Test
    fun `enabled notifications clear any notice`() {
        assertEquals(
            KeepAliveNotice.NONE,
            keepAliveNotice(true, true, NotificationPermissionDenial.BLOCKED),
        )
        assertEquals(KeepAliveNotice.NONE, keepAliveNotice(false, true, null))
    }

    @Test
    fun `keep-alive on with notifications off warns about the hidden notification`() {
        assertEquals(KeepAliveNotice.NOTIFICATIONS_OFF, keepAliveNotice(true, false, null))
        assertEquals(KeepAliveNotice.NONE, keepAliveNotice(false, false, null))
    }
}
