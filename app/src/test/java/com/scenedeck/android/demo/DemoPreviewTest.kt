package com.scenedeck.android.demo

import android.app.Application
import android.graphics.BitmapFactory
import com.scenedeck.android.core.obs.ScreenshotRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DemoPreviewTest {
    @Test
    fun localPreviewIsDecodableAndBoundedRegardlessOfRequestedDimensions() {
        val bytes =
            DemoPreviewFrames.capture(
                ScreenshotRequest("Demo · Camera", width = Int.MAX_VALUE, height = Int.MAX_VALUE)
            )
        assertTrue(bytes.size in 1..ScreenshotRequest.MAX_IMAGE_BYTES)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        try {
            assertEquals(360, bitmap.width)
            assertEquals(360, bitmap.height)
            assertTrue(bitmap.getPixel(0, 0) != bitmap.getPixel(0, 190))
        } finally {
            bitmap.recycle()
        }
    }
}
