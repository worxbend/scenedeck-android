package com.scenedeck.android.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ScenePreviewStatusTest {
    @Test
    fun ageAndFailureNeverPretendAnOldImageIsFresh() {
        assertEquals(
            ScenePreviewStatus(PreviewFreshness.FRESH, 3),
            scenePreviewStatus(1_000, 4_900, false, true),
        )
        assertEquals(
            ScenePreviewStatus(PreviewFreshness.STALE, 20),
            scenePreviewStatus(1_000, 21_000, false, true),
        )
        assertEquals(
            ScenePreviewStatus(PreviewFreshness.STALE, 1),
            scenePreviewStatus(1_000, 2_000, true, true),
        )
        assertEquals(
            ScenePreviewStatus(PreviewFreshness.FRESH, 0),
            scenePreviewStatus(5_000, 4_000, false, true),
        )
    }

    @Test
    fun noImageDistinguishesPendingUnavailableAndDisconnected() {
        assertEquals(
            ScenePreviewStatus(PreviewFreshness.PENDING),
            scenePreviewStatus(null, 50_000, false, true),
        )
        assertEquals(
            ScenePreviewStatus(PreviewFreshness.UNAVAILABLE),
            scenePreviewStatus(null, 50_000, true, true),
        )
        assertEquals(
            ScenePreviewStatus(PreviewFreshness.DISCONNECTED),
            scenePreviewStatus(1_000, 50_000, false, false),
        )
    }
}
