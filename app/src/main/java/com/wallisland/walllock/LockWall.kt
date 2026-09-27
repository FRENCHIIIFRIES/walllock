package com.wallisland.walllock

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Display
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

/**
 * Puts the album cover on the lock screen as its wallpaper while music plays, and puts the usual
 * wallpaper back when it stops. The phone's own clock, notifications and player stay on top.
 */
object LockWall {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()

    /** Song and look of the wallpaper currently set, so the same one isn't set twice. */
    @Volatile private var appliedKey: String? = null
    private var app: Context? = null

    private val evaluate = Runnable { app?.let { evaluate(it) } }

    /**
     * Something changed (song, play state, settings). Waits a moment so skipping through songs
     * doesn't set a wallpaper for each one.
     */
    fun update(ctx: Context, delayMs: Long = 1200) {
        app = ctx.applicationContext
        main.removeCallbacks(evaluate)
        main.postDelayed(evaluate, delayMs)
    }

    private fun evaluate(ctx: Context) {
        val prefs = Prefs(ctx)
        val t = NowPlaying.current
        val art = t?.art
        val sincePlaying = SystemClock.elapsedRealtime() - NowPlaying.lastPlayingAt
        val grace = if (prefs.showPaused) PAUSED_GRACE_MS else 0L
        val want = prefs.enabled && t != null && art != null && (t.playing || sincePlaying < grace)
        if (want) {
            val key = "${t!!.artKey}|${prefs.blackBackground}|${prefs.position}"
            if (!t.playing) {
                // Look again once the pause has gone on long enough.
                main.postDelayed(evaluate, grace - sincePlaying + 1000)
            }
            if (key == appliedKey && prefs.coverActive) return
            appliedKey = key
            val black = prefs.blackBackground
            val position = prefs.position
            worker.execute { apply(ctx, art!!, black, position) }
        } else if (prefs.coverActive || appliedKey != null) {
            appliedKey = null
            worker.execute { restore(ctx) }
        }
    }

    private fun apply(ctx: Context, art: Bitmap, black: Boolean, position: Int) {
        try {
            val (w, h) = screenSize(ctx)
            val bmp = CoverArt.compose(art, w, h, black, position)
            WallpaperManager.getInstance(ctx).setBitmap(bmp, null, false, WallpaperManager.FLAG_LOCK)
            Prefs(ctx).coverActive = true
        } catch (_: Exception) {
            appliedKey = null
        }
    }

    /** Back to the picked image, or to the home screen wallpaper when nothing was picked. */
    private fun restore(ctx: Context) {
        try {
            val wm = WallpaperManager.getInstance(ctx)
            val picked = restoreFile(ctx).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
            if (picked != null) wm.setBitmap(picked, null, false, WallpaperManager.FLAG_LOCK)
            else wm.clear(WallpaperManager.FLAG_LOCK)
            Prefs(ctx).coverActive = false
        } catch (_: Exception) {
        }
    }

    fun restoreFile(ctx: Context) = File(ctx.filesDir, "restore.jpg")

    /** The full screen in pixels, portrait. */
    fun screenSize(ctx: Context): Pair<Int, Int> {
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        ctx.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY).getRealMetrics(dm)
        return min(dm.widthPixels, dm.heightPixels) to max(dm.widthPixels, dm.heightPixels)
    }

    /** How long after pausing the cover stays, like the iPhone's lock screen player. */
    const val PAUSED_GRACE_MS = 10 * 60_000L
}
