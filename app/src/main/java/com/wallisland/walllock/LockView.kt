package com.wallisland.walllock

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import android.text.format.DateFormat
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The whole lock screen player, drawn by hand in the Nothing style so the cover can grow smoothly.
 *
 * Small: a black card near the bottom with a thumbnail, over a dot grid where every dot is sized by
 * the album cover, so the cover shows through as a faint halftone. Big (tap the cover): the cover
 * springs up to fill the top of the screen under the dot-matrix clock and fades into black below.
 * Tap it again to shrink it.
 */
@SuppressLint("ViewConstructor")
class LockView(ctx: Context, startExpanded: Boolean, private val dotCover: Boolean) : View(ctx) {

    var onExpandedChanged: ((Boolean) -> Unit)? = null
    var onUnlock: (() -> Unit)? = null
    var onOpenApp: ((NowPlaying.Track) -> Unit)? = null

    private val d = resources.displayMetrics.density
    private fun dp(v: Float) = v * d

    private var track: NowPlaying.Track? = null
    private var artKey: String? = null
    private var wall: Bitmap? = null
    private var dots: DotArt? = null
    private var coverPaint: Paint? = null
    private var coverSize = 0f

    // ---- Expansion spring: p is 0 (small card) to 1 (big cover), and may overshoot a little. -------
    private var expanded = startExpanded
    private var p = if (startExpanded) 1f else 0f
    private var pv = 0f
    private var lastFrame = 0L
    private var springing = false

    private val spring = object : Runnable {
        override fun run() {
            val now = SystemClock.uptimeMillis()
            val dt = ((now - lastFrame) / 1000f).coerceIn(0.001f, 0.032f)
            lastFrame = now
            val target = if (expanded) 1f else 0f
            // Just a touch of bounce, like iOS.
            val k = 300f
            val c = 2f * sqrt(k) * 0.82f
            pv += (-k * (p - target) - c * pv) * dt
            p += pv * dt
            if (abs(p - target) < 0.001f && abs(pv) < 0.01f) {
                p = target
                pv = 0f
                springing = false
            } else {
                postOnAnimation(this)
            }
            invalidate()
        }
    }

    // ---- Twice a second for the clock and progress. -----------------------------------------------
    private val tick = object : Runnable {
        override fun run() {
            invalidate()
            postDelayed(this, 500)
        }
    }

    // ---- Insets and layout. -----------------------------------------------------------------------
    private var insetTop = 0
    private var insetBottom = 0

    private val card = RectF()
    private val small = RectF()
    private val big = RectF()
    private val art = RectF()
    private val bar = RectF()
    private val prevHit = RectF()
    private val playHit = RectF()
    private val nextHit = RectF()
    private val textHit = RectF()
    private val square = RectF()

    // ---- Paints. ---------------------------------------------------------------------------------
    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val wallPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { color = Look.WHITE }
    private val fadePaint = Paint().apply {
        // A unit gradient, stretched onto the bottom of the cover each frame.
        shader = LinearGradient(0f, 0f, 0f, 1f, 0x00000000, Look.BLACK, Shader.TileMode.CLAMP)
    }
    private val shadePaint = Paint()
    private val fadeMatrix = Matrix()
    private val coverMatrix = Matrix()
    private val clockPaint = Look.dotPaint(ctx, dp(92f), Look.WHITE, 900).apply {
        setShadowLayer(dp(10f), 0f, 0f, 0x66000000)
    }
    private val datePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Look.WHITE
        textAlign = Paint.Align.CENTER
        textSize = dp(13f)
        typeface = Look.mono(ctx)
        letterSpacing = 0.12f
        setShadowLayer(dp(8f), 0f, 0f, 0x66000000)
    }
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Look.WHITE
        textSize = dp(15f)
        typeface = Look.monoBold(ctx)
    }
    private val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Look.GREY
        textSize = dp(12f)
        typeface = Look.mono(ctx)
        letterSpacing = 0.06f
    }
    private val timePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Look.GREY
        textSize = dp(11f)
        typeface = Look.mono(ctx)
    }
    private val hintPaint = TextPaint(Look.dotPaint(ctx, dp(12f), Look.GREY, 800)).apply {
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.12f
    }

    private val timeFmt = SimpleDateFormat(if (DateFormat.is24HourFormat(ctx)) "HH:mm" else "h:mm", Locale.getDefault())
    private val dateFmt = SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEdMMM"), Locale.getDefault())

    init {
        isClickable = true
        isHapticFeedbackEnabled = true
    }

    fun setTrack(t: NowPlaying.Track) {
        track = t
        if (t.artKey != artKey) {
            artKey = t.artKey
            rebuildWall()
            val a = t.art
            dots = if (dotCover && a != null) runCatching { DotArt(a, 44) }.getOrNull() else null
            coverPaint = a?.let { bmp ->
                // Centre-crop in case the app hands over a cover that isn't square.
                coverSize = min(bmp.width, bmp.height).toFloat()
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    shader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                }
            }
        }
        invalidate()
    }

    private fun rebuildWall() {
        if (width == 0 || height == 0) return
        wall = runCatching { DotWall(track?.art, width, height, dp(11f)).render(dp(1.1f)) }.getOrNull()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildWall()
        // Darker over the dot cover: dot-matrix time on dots needs the help.
        val shade = if (dotCover) 0x99000000.toInt() else 0x66000000
        shadePaint.shader = LinearGradient(0f, 0f, 0f, dp(280f), shade, 0x00000000, Shader.TileMode.CLAMP)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post(tick)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        removeCallbacks(spring)
        super.onDetachedFromWindow()
    }

    @Suppress("DEPRECATION")
    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        insetTop = insets.systemWindowInsetTop
        insetBottom = insets.systemWindowInsetBottom
        requestLayout()
        return insets
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val w = width.toFloat()
        val h = height.toFloat()
        val side = dp(16f)
        val cardH = dp(196f)
        val cardBottom = h - insetBottom - dp(76f)
        card.set(side, cardBottom - cardH, w - side, cardBottom)

        val thumb = dp(64f)
        small.set(card.left + dp(16f), card.top + dp(16f), card.left + dp(16f) + thumb, card.top + dp(16f) + thumb)

        // Big: the whole screen from the top down to the song title, the cover centre-cropped to fit.
        big.set(0f, 0f, w, max(w, card.top + dp(24f)))

        val barY = small.bottom + dp(24f)
        bar.set(card.left + dp(20f), barY - dp(3f), card.right - dp(20f), barY + dp(3f))

        val cy = card.bottom - dp(36f)
        val cx = card.centerX()
        val hit = dp(30f)
        playHit.set(cx - hit, cy - hit, cx + hit, cy + hit)
        prevHit.set(cx - dp(84f) - hit, cy - hit, cx - dp(84f) + hit, cy + hit)
        nextHit.set(cx + dp(84f) - hit, cy - hit, cx + dp(84f) + hit, cy + hit)
    }

    // ---- Drawing. --------------------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val t = track
        val q = p.coerceIn(0f, 1f)

        canvas.drawColor(Look.BLACK)
        canvas.save()
        canvas.translate(0f, dragY)

        // 1. Wallpaper: the dot grid, each dot sized by the cover. It dims as the cover grows.
        wall?.let {
            // Gone once the cover is big, so nothing shows through under it.
            wallPaint.alpha = (255 * (1f - q)).toInt()
            canvas.drawBitmap(it, 0f, 0f, wallPaint)
        }

        // 2. The black card, fading out as the cover leaves it.
        val cardAlpha = 1f - q
        if (cardAlpha > 0f) {
            fill.color = Look.SURFACE
            fill.alpha = (0xF0 * cardAlpha).toInt()
            canvas.drawRoundRect(card, dp(24f), dp(24f), fill)
            stroke.color = Look.LINE
            stroke.alpha = (255 * cardAlpha).toInt()
            canvas.drawRoundRect(card, dp(24f), dp(24f), stroke)
        }

        // 3. The cover, on its way between the thumbnail and the top of the screen.
        lerp(small, big, p, art)
        drawCover(canvas, art, dp(12f) * (1f - q))
        if (q > 0f) {
            // Fade the bottom of the big cover into black.
            val fadeTop = art.top + art.height() * 0.58f
            fadeMatrix.setScale(1f, art.bottom + 1f - fadeTop)
            fadeMatrix.postTranslate(0f, fadeTop)
            fadePaint.shader.setLocalMatrix(fadeMatrix)
            fadePaint.alpha = (255 * q).toInt()
            canvas.drawRect(art.left - 1f, fadeTop, art.right + 1f, art.bottom + 1f, fadePaint)
            // A little shade behind the clock so it reads on bright covers.
            shadePaint.alpha = (255 * q).toInt()
            canvas.drawRect(0f, 0f, w, dp(280f), shadePaint)
        }

        // 4. Date and dot-matrix time, with the colon in red.
        val now = Date()
        val dateY = insetTop + dp(56f)
        canvas.drawText(dateFmt.format(now).uppercase(), w / 2f, dateY, datePaint)
        drawClock(canvas, timeFmt.format(now), w / 2f, dateY + dp(92f))

        // 5. Song, progress and buttons.
        if (t != null) drawPlayer(canvas, t, q)

        // 6. How to leave.
        val hintAlpha = (1f + dragY / dp(160f)).coerceIn(0f, 1f)
        val hy = h - insetBottom - dp(28f)
        fill.color = Look.GREY
        fill.alpha = (255 * hintAlpha).toInt()
        val arrow = dp(9f)
        Glyph.ARROW.draw(canvas, w / 2f - Glyph.ARROW.width(arrow) / 2f, hy - dp(22f), arrow, fill)
        hintPaint.alpha = (255 * hintAlpha).toInt()
        canvas.drawText("SWIPE UP TO UNLOCK", w / 2f, hy, hintPaint)
        canvas.restore()
    }

    private fun drawClock(canvas: Canvas, time: String, cx: Float, baseline: Float) {
        val i = time.indexOf(':')
        if (i < 0) {
            clockPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(time, cx, baseline, clockPaint)
            return
        }
        val hh = time.substring(0, i)
        val mm = time.substring(i + 1)
        clockPaint.textAlign = Paint.Align.LEFT
        val wh = clockPaint.measureText(hh)
        val wc = clockPaint.measureText(":")
        val wm = clockPaint.measureText(mm)
        var x = cx - (wh + wc + wm) / 2f
        clockPaint.color = Look.WHITE
        canvas.drawText(hh, x, baseline, clockPaint)
        x += wh
        clockPaint.color = Look.accent
        canvas.drawText(":", x, baseline, clockPaint)
        x += wc
        clockPaint.color = Look.WHITE
        canvas.drawText(mm, x, baseline, clockPaint)
    }

    private fun drawCover(canvas: Canvas, r: RectF, radius: Float) {
        val dotArt = dots
        val cp = coverPaint
        when {
            dotArt != null -> {
                // The colour halftone version of the cover, on black, cropped like the picture.
                fill.color = Look.BLACK
                canvas.drawRoundRect(r, radius, radius, fill)
                val side = max(r.width(), r.height())
                square.set(r.centerX() - side / 2f, r.centerY() - side / 2f, r.centerX() + side / 2f, r.centerY() + side / 2f)
                canvas.save()
                canvas.clipRect(r)
                dotArt.draw(canvas, square, fill, round = false, alpha = 255, colored = true)
                canvas.restore()
            }
            cp != null -> {
                // A shader, not a clip, so the rounded corners stay smooth while it grows.
                val bmp = track?.art ?: return
                val scale = max(r.width(), r.height()) / coverSize
                coverMatrix.setTranslate(-bmp.width / 2f, -bmp.height / 2f)
                coverMatrix.postScale(scale, scale)
                coverMatrix.postTranslate(r.centerX(), r.centerY())
                cp.shader.setLocalMatrix(coverMatrix)
                canvas.drawRoundRect(r, radius, radius, cp)
            }
            else -> {
                fill.color = Look.RAISED
                canvas.drawRoundRect(r, radius, radius, fill)
                fill.color = Look.GREY
                val g = r.height() * 0.36f
                Glyph.HEADPHONES.draw(canvas, r.centerX() - Glyph.HEADPHONES.width(g) / 2f, r.centerY(), g, fill)
            }
        }
    }

    private fun drawPlayer(canvas: Canvas, t: NowPlaying.Track, q: Float) {
        // Title and artist sit beside the thumbnail, then slide left into the space it leaves.
        val textLeft = lerp(small.right + dp(14f), card.left + dp(20f), q)
        val textRight = card.right - dp(20f)
        val titleY = lerp(small.top + dp(28f), small.top + dp(22f), q)
        val avail = textRight - textLeft
        canvas.drawText(ellipsize(t.title, titlePaint, avail), textLeft, titleY, titlePaint)
        canvas.drawText(ellipsize(t.artist.uppercase(), artistPaint, avail), textLeft, titleY + dp(22f), artistPaint)
        textHit.set(textLeft, titleY - dp(22f), textRight, titleY + dp(30f))

        // Progress: a row of dots, lit up to the red head.
        val dur = t.durationMs
        val pos = scrubMs ?: t.positionNow()
        val frac = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
        val cy = bar.centerY()
        val pitch = dp(7f)
        val count = (bar.width() / pitch).toInt().coerceAtLeast(2)
        val step = bar.width() / (count - 1)
        val head = bar.left + bar.width() * frac
        for (i in 0 until count) {
            val x = bar.left + i * step
            fill.color = if (x <= head) Look.WHITE else Look.DOT_OFF
            canvas.drawCircle(x, cy, dp(1.8f), fill)
        }
        if (dur > 0) {
            fill.color = Look.accent
            canvas.drawCircle(head, cy, if (scrubMs != null) dp(8f) else dp(5f), fill)
        }
        val ty = cy + dp(22f)
        timePaint.textAlign = Paint.Align.LEFT
        canvas.drawText(fmt(pos), bar.left, ty, timePaint)
        timePaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(if (dur > 0) "-" + fmt(dur - pos) else "", bar.right, ty, timePaint)

        // Dot-matrix buttons.
        drawButton(canvas, if (t.playing) Glyph.PAUSE else Glyph.PLAY, playHit, dp(28f))
        drawButton(canvas, Glyph.PREV, prevHit, dp(21f))
        drawButton(canvas, Glyph.NEXT, nextHit, dp(21f))
    }

    private fun drawButton(canvas: Canvas, g: Glyph, hit: RectF, size: Float) {
        fill.color = if (pressed === hit) Look.GREY else Look.WHITE
        g.draw(canvas, hit.centerX() - g.width(size) / 2f, hit.centerY(), size, fill)
    }

    // ---- Touch. ----------------------------------------------------------------------------------

    private var downX = 0f
    private var downY = 0f
    private var downAt = 0L
    private var dragY = 0f
    private var mode = Mode.NONE
    private var pressed: RectF? = null
    private var scrubMs: Long? = null

    private enum class Mode { NONE, TAP, SCRUB, SWIPE }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x
                downY = e.y
                downAt = SystemClock.uptimeMillis()
                mode = Mode.TAP
                pressed = listOf(playHit, prevHit, nextHit).firstOrNull { it.contains(e.x, e.y) }
                if (pressed == null && track?.durationMs?.let { it > 0 } == true &&
                    e.x in bar.left - dp(8f)..bar.right + dp(8f) && abs(e.y - bar.centerY()) < dp(20f)
                ) {
                    mode = Mode.SCRUB
                    scrubTo(e.x)
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> when (mode) {
                Mode.SCRUB -> scrubTo(e.x)
                Mode.TAP -> if (e.y - downY < -dp(12f) && abs(e.y - downY) > abs(e.x - downX)) {
                    mode = Mode.SWIPE
                    pressed = null
                } else if (abs(e.x - downX) > dp(16f) || abs(e.y - downY) > dp(16f)) {
                    // Wandered off: not a tap any more.
                    mode = Mode.NONE
                    pressed = null
                    invalidate()
                }
                Mode.SWIPE -> {
                    dragY = min(0f, e.y - downY)
                    invalidate()
                }
                Mode.NONE -> Unit
            }
            MotionEvent.ACTION_UP -> {
                when (mode) {
                    Mode.SCRUB -> {
                        scrubMs?.let { track?.controller?.transportControls?.seekTo(it) }
                        scrubMs = null
                    }
                    Mode.SWIPE -> {
                        val speed = dragY / max(1L, SystemClock.uptimeMillis() - downAt) * 1000f
                        if (dragY < -dp(110f) || speed < -dp(900f)) {
                            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onUnlock?.invoke()
                        }
                        settleDrag()
                    }
                    Mode.TAP -> tap(e.x, e.y)
                    Mode.NONE -> Unit
                }
                pressed = null
                mode = Mode.NONE
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                scrubMs = null
                pressed = null
                mode = Mode.NONE
                settleDrag()
                invalidate()
            }
        }
        return true
    }

    private fun tap(x: Float, y: Float) {
        val t = track ?: return
        val controls = t.controller.transportControls
        when {
            playHit.contains(x, y) -> if (t.playing) controls.pause() else controls.play()
            prevHit.contains(x, y) -> controls.skipToPrevious()
            nextHit.contains(x, y) -> controls.skipToNext()
            art.contains(x, y) -> toggle()
            textHit.contains(x, y) -> onOpenApp?.invoke(t)
            else -> return
        }
        performClick()
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    override fun performClick(): Boolean = super.performClick()

    private fun toggle() {
        expanded = !expanded
        onExpandedChanged?.invoke(expanded)
        if (!springing) {
            springing = true
            lastFrame = SystemClock.uptimeMillis()
            postOnAnimation(spring)
        }
    }

    private fun scrubTo(x: Float) {
        val dur = track?.durationMs ?: return
        val f = ((x - bar.left) / bar.width()).coerceIn(0f, 1f)
        scrubMs = (dur * f).toLong()
        invalidate()
    }

    private fun settleDrag() {
        if (dragY == 0f) return
        val start = dragY
        val anim = android.animation.ValueAnimator.ofFloat(1f, 0f).setDuration(260)
        anim.interpolator = android.view.animation.DecelerateInterpolator()
        anim.addUpdateListener {
            dragY = start * (it.animatedValue as Float)
            invalidate()
        }
        anim.start()
    }

    // ---- Helpers. --------------------------------------------------------------------------------

    private val fitted = HashMap<String, String>()

    /** Ellipsized text, cached: the title is re-fitted every frame while the cover moves. */
    private fun ellipsize(s: String, paint: TextPaint, width: Float): String {
        val key = "${paint.textSize}|${width.toInt()}|$s"
        return fitted.getOrPut(key) {
            if (fitted.size > 64) fitted.clear()
            TextUtils.ellipsize(s, paint, max(0f, width), TextUtils.TruncateAt.END).toString()
        }
    }

    private fun fmt(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60)
        else "%d:%02d".format(s / 60, s % 60)
    }

    private fun lerp(a: Float, b: Float, f: Float) = a + (b - a) * f

    private fun lerp(a: RectF, b: RectF, f: Float, out: RectF) {
        out.set(lerp(a.left, b.left, f), lerp(a.top, b.top, f), lerp(a.right, b.right, f), lerp(a.bottom, b.bottom, f))
    }
}
