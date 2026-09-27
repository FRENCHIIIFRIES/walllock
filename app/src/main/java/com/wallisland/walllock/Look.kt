package com.wallisland.walllock

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue

/**
 * The palette and type of the whole app, in the Nothing style: monochrome, one red, dots everywhere.
 */
object Look {
    const val BLACK = 0xFF000000.toInt()
    const val SURFACE = 0xFF0E0E0E.toInt()
    const val RAISED = 0xFF161616.toInt()
    const val LINE = 0xFF262626.toInt()
    const val DOT_OFF = 0xFF2B2B2B.toInt()
    const val GREY = 0xFF8C8C8C.toInt()
    const val WHITE = 0xFFF2F2F2.toInt()
    const val RED = 0xFFD71921.toInt()

    /** The one colour in the UI. Nothing red unless the user picks another in settings. */
    @Volatile var accent = RED

    /** Accent choices offered in settings, as (name, colour). */
    val ACCENTS = listOf(
        "Red" to RED,
        "Orange" to 0xFFFF6A1A.toInt(),
        "Yellow" to 0xFFFFC21A.toInt(),
        "Green" to 0xFF2ED573.toInt(),
        "Blue" to 0xFF2F80FF.toInt(),
        "Purple" to 0xFF9B5CFF.toInt(),
        "Pink" to 0xFFFF4D97.toInt(),
        "White" to WHITE,
    )

    @Volatile private var dotFace: Typeface? = null
    @Volatile private var monoFace: Typeface? = null
    @Volatile private var monoBoldFace: Typeface? = null

    /** Doto: an open-source dot-matrix face, the closest free cousin of NDot. */
    fun dot(ctx: Context): Typeface =
        dotFace ?: loadFont(ctx, R.font.doto, Typeface.MONOSPACE).also { dotFace = it }

    fun mono(ctx: Context): Typeface =
        monoFace ?: loadFont(ctx, R.font.space_mono, Typeface.MONOSPACE).also { monoFace = it }

    fun monoBold(ctx: Context): Typeface =
        monoBoldFace ?: loadFont(ctx, R.font.space_mono_bold, Typeface.MONOSPACE).also { monoBoldFace = it }

    private fun loadFont(ctx: Context, id: Int, fallback: Typeface): Typeface =
        try {
            ctx.resources.getFont(id)
        } catch (_: Exception) {
            fallback
        }

    /** A paint set up for dot-matrix text; heavier weight so the dots read at small sizes. */
    fun dotPaint(ctx: Context, sizePx: Float, color: Int = WHITE, weight: Int = 800): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = dot(ctx)
            textSize = sizePx
            this.color = color
            fontVariationSettings = "'wght' $weight, 'ROND' 100"
        }
}

fun Context.dp(v: Float): Float = v * resources.displayMetrics.density
fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
fun Context.sp(v: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)
