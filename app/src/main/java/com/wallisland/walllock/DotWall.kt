package com.wallisland.walllock

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.max

/**
 * The lock screen wallpaper: the Nothing dot grid, with each dot sized by the album cover behind it,
 * so the cover shows through as a big faint halftone. Rendered once into an alpha mask, so the lock
 * screen draws it as one bitmap per frame.
 */
class DotWall(private val cover: Bitmap?, private val width: Int, private val height: Int, val pitch: Float) {
    val cols = max(1, (width / pitch).toInt())
    val rows = max(1, (height / pitch).toInt())

    /** 0..1 per dot, row by row; how big each dot is drawn. */
    val levels = FloatArray(cols * rows)

    /** Where the grid starts, so it sits centred on the screen. */
    val left = (width - cols * pitch) / 2f + pitch / 2f
    val top = (height - rows * pitch) / 2f + pitch / 2f

    init {
        if (cover != null) {
            val s = SMALL
            val soft = if (cover.config == Bitmap.Config.HARDWARE) cover.copy(Bitmap.Config.ARGB_8888, false) else cover
            val small = Bitmap.createScaledBitmap(soft, s, s, true)
            val px = IntArray(s * s)
            small.getPixels(px, 0, s, 0, 0, s, s)
            val lum = FloatArray(s * s) { i ->
                val c = px[i]
                (0.2126f * (c shr 16 and 0xFF) + 0.7152f * (c shr 8 and 0xFF) + 0.0722f * (c and 0xFF)) / 255f
            }
            // Stretch contrast so dark covers still make a pattern.
            val lo = lum.minOrNull() ?: 0f
            val span = ((lum.maxOrNull() ?: 1f) - lo).coerceAtLeast(0.15f)
            // Cover the screen like a centre-cropped wallpaper.
            val scale = max(width.toFloat(), height.toFloat()) / s
            val offX = (width - s * scale) / 2f
            val offY = (height - s * scale) / 2f
            for (r in 0 until rows) for (c in 0 until cols) {
                val x = ((left + c * pitch - offX) / scale).toInt().coerceIn(0, s - 1)
                val y = ((top + r * pitch - offY) / scale).toInt().coerceIn(0, s - 1)
                levels[r * cols + c] = ((lum[y * s + x] - lo) / span).coerceIn(0f, 1f)
            }
        }
    }

    /** The dots as an alpha mask (white where lit), drawn tinted by the paint. */
    fun render(offRadius: Float): Bitmap {
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (r in 0 until rows) for (c in 0 until cols) {
            val v = levels[r * cols + c]
            val x = left + c * pitch
            val y = top + r * pitch
            // Squared so only the bright parts of the cover stand out; kept faint so it stays a wallpaper.
            val k = v * v
            if (cover == null || k < 0.06f) {
                paint.alpha = 0x26
                canvas.drawCircle(x, y, offRadius, paint)
            } else {
                paint.alpha = (0x20 + 0x48 * k).toInt()
                canvas.drawCircle(x, y, pitch * 0.5f * (0.2f + 0.5f * k), paint)
            }
        }
        return out
    }

    private companion object {
        const val SMALL = 64
    }
}
