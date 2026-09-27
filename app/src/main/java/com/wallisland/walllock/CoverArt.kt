package com.wallisland.walllock

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.ceil
import kotlin.math.min

/**
 * Draws the lock screen wallpaper: the album cover as wide as the screen at the top, like the
 * iPhone's full-screen artwork, then the Nothing part: its bottom breaks up into dots of its own
 * colours, which shrink and grey out into a faint dot grid behind the notifications.
 */
object CoverArt {
    const val COVER = 0
    const val DOTS = 1

    /** Dots across the screen. */
    private const val COLS = 36

    fun compose(art: Bitmap, w: Int, h: Int, style: Int, mono: Boolean): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.BLACK)

        val src = if (art.config == Bitmap.Config.HARDWARE) art.copy(Bitmap.Config.ARGB_8888, false) else art
        // Centre-crop in case the app hands over a cover that isn't square.
        val s = min(src.width, src.height)
        val square = Rect((src.width - s) / 2, (src.height - s) / 2, (src.width + s) / 2, (src.height + s) / 2)
        val side = w.toFloat()
        val pitch = side / COLS

        if (style == COVER) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            if (mono) p.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
            c.drawBitmap(src, square, RectF(0f, 0f, side, side), p)
        }

        // The cover's colours on the dot grid, one sample per dot.
        val grid = Bitmap.createBitmap(src, square.left, square.top, s, s).let {
            Bitmap.createScaledBitmap(it, COLS, COLS, true)
        }
        val px = IntArray(COLS * COLS)
        grid.getPixels(px, 0, COLS, 0, 0, COLS, COLS)

        // The melt: the picture fades out while its dots fade in, then the dots shrink and grey out
        // into the plain grid.
        val fadeFrom = side * 0.55f
        val shrinkFrom = side * 0.9f
        val shrinkTo = side + h * 0.36f

        if (style == COVER) {
            val fade = Paint().apply {
                shader = LinearGradient(0f, fadeFrom, 0f, side, 0x00000000, Color.BLACK, Shader.TileMode.CLAMP)
            }
            c.drawRect(0f, fadeFrom, side, side + 1f, fade)
        }

        val dot = Paint(Paint.ANTI_ALIAS_FLAG)
        val rows = ceil(h / pitch).toInt()
        for (r in 0 until rows) {
            val y = pitch * (r + 0.5f)
            if (style == COVER && y < fadeFrom) continue
            val alpha = if (style == COVER) ((y - fadeFrom) / (shrinkFrom - fadeFrom)).coerceIn(0f, 1f) else 1f
            val k = ((y - shrinkFrom) / (shrinkTo - shrinkFrom)).coerceIn(0f, 1f)
            // Smooth shrink: full-size dots at the start of the melt, the tiny grid at the end.
            val grow = 1f - k * k * (3f - 2f * k)
            // Below the cover, the last row of it carries on downwards.
            val sy = min(COLS - 1, (y / side * COLS).toInt())
            for (col in 0 until COLS) {
                val x = pitch * (col + 0.5f)
                val sample = px[sy * COLS + col]
                val lum = (0.2126f * Color.red(sample) + 0.7152f * Color.green(sample) + 0.0722f * Color.blue(sample)) / 255f
                val base = if (mono) grey(lum) else brighten(sample)
                val full = pitch * 0.5f * (0.5f + 0.45f * lum)
                val tiny = pitch * 0.09f
                dot.color = blend(base, Look.DOT_OFF, k)
                dot.alpha = (255 * alpha).toInt()
                c.drawCircle(x, y, tiny + (full - tiny) * grow, dot)
            }
        }

        // A little shade under the status bar and clock so they read on bright covers.
        val shade = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h * 0.24f, 0x59000000, 0x00000000, Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, w.toFloat(), h * 0.24f, shade)
        return out
    }

    private fun grey(lum: Float): Int {
        val v = (40 + 215 * lum).toInt().coerceIn(0, 255)
        return Color.rgb(v, v, v)
    }

    /** Dots are small, so lift them a little to keep the colour. */
    private fun brighten(c: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(c, hsv)
        hsv[2] = (hsv[2] * 1.15f + 0.06f).coerceAtMost(1f)
        return Color.HSVToColor(hsv)
    }

    private fun blend(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt(),
    )
}
