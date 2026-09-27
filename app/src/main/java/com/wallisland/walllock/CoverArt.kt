package com.wallisland.walllock

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.max
import kotlin.math.min

/**
 * Draws the lock screen wallpaper like the iPhone's big album art: the cover itself, untouched and
 * whole, as a large rounded box with a soft shadow, over a blur of its own colours (or black).
 */
object CoverArt {
    const val HIGH = 0
    const val MIDDLE = 1
    const val LOW = 2

    fun compose(art: Bitmap, w: Int, h: Int, blackBackground: Boolean, position: Int): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val src = if (art.config == Bitmap.Config.HARDWARE) art.copy(Bitmap.Config.ARGB_8888, false) else art

        if (blackBackground) {
            c.drawColor(Color.BLACK)
        } else {
            val bg = blurred(src)
            val scale = max(w.toFloat() / bg.width, h.toFloat() / bg.height)
            val bw = bg.width * scale
            val bh = bg.height * scale
            c.drawBitmap(bg, null, RectF((w - bw) / 2f, (h - bh) / 2f, (w + bw) / 2f, (h + bh) / 2f),
                Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG))
            // A touch darker, so the clock and the box stand out.
            c.drawColor(0x40000000)
        }

        // The box: the whole cover as it is (never cropped), as big as fits.
        val scale = min(w * 0.82f / src.width, h * 0.46f / src.height)
        val bw = src.width * scale
        val bh = src.height * scale
        val cy = h * when (position) {
            HIGH -> 0.40f
            LOW -> 0.57f
            else -> 0.48f
        }
        val box = RectF((w - bw) / 2f, cy - bh / 2f, (w + bw) / 2f, cy + bh / 2f)
        val radius = min(bw, bh) * 0.035f

        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            setShadowLayer(w * 0.05f, 0f, w * 0.02f, 0x80000000.toInt())
        }
        c.drawRoundRect(box, radius, radius, shadow)
        val cover = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(Matrix().apply {
                    setScale(scale, scale)
                    postTranslate(box.left, box.top)
                })
            }
        }
        c.drawRoundRect(box, radius, radius, cover)
        return out
    }

    /**
     * A very soft blur of the cover for the background: blurred small, enlarged, then blurred again
     * so no blockiness shows once it's stretched over the whole screen.
     */
    private fun blurred(src: Bitmap): Bitmap {
        val s = min(src.width, src.height)
        val square = Bitmap.createBitmap(src, (src.width - s) / 2, (src.height - s) / 2, s, s)
        val small = boxBlur(Bitmap.createScaledBitmap(square, 40, 40, true), radius = 3, passes = 3)
        return boxBlur(Bitmap.createScaledBitmap(small, 160, 160, true), radius = 6, passes = 2)
    }

    private fun boxBlur(bmp: Bitmap, radius: Int, passes: Int): Bitmap {
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        repeat(passes) {
            blurLines(px, w, h, radius, horizontal = true)
            blurLines(px, w, h, radius, horizontal = false)
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    private fun blurLines(px: IntArray, w: Int, h: Int, radius: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val line = IntArray(len)
        for (l in 0 until lines) {
            for (i in 0 until len) line[i] = px[if (horizontal) l * w + i else i * w + l]
            for (i in 0 until len) {
                var r = 0
                var g = 0
                var b = 0
                var n = 0
                for (k in max(0, i - radius)..min(len - 1, i + radius)) {
                    val v = line[k]
                    r += (v shr 16) and 0xFF; g += (v shr 8) and 0xFF; b += v and 0xFF; n++
                }
                px[if (horizontal) l * w + i else i * w + l] =
                    (0xFF shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
            }
        }
    }
}
