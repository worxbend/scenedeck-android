package com.scenedeck.android.demo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.graphics.createBitmap
import com.scenedeck.android.core.obs.ScreenshotRequest
import java.io.ByteArrayOutputStream

/** Synthetic video slate, generated locally at a fixed size. No image/network/file inputs. */
internal object DemoPreviewFrames {
    fun capture(request: ScreenshotRequest): ByteArray {
        val bitmap = createBitmap(360, 360)
        return try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.rgb(19, 26, 37))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            // These are the contents of a simulated test signal, not app-theme colors.
            val bars =
                intArrayOf(
                    0xffecdd72.toInt(),
                    0xff73c9d4.toInt(),
                    0xff8ac58b.toInt(),
                    0xff9b8bcc.toInt(),
                    0xffcf8696.toInt(),
                )
            bars.forEachIndexed { i, color ->
                paint.color = color
                canvas.drawRect(i * 72f, 0f, (i + 1) * 72f, 12f, paint)
            }
            paint.color = bars[Math.floorMod(request.sourceName.hashCode(), bars.size)]
            paint.alpha = 70
            canvas.drawCircle(180f, 150f, 110f, paint)
            paint.alpha = 255
            paint.color = Color.rgb(24, 34, 48)
            canvas.drawCircle(180f, 150f, 88f, paint)
            paint.color = Color.WHITE
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = 16f
            canvas.drawText("DEMO", 180f, 70f, paint)
            ByteArrayOutputStream().use { bytes ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 75, bytes))
                bytes.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }
}
