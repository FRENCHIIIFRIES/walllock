package com.wallisland.walllock

import android.app.Activity
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import java.lang.ref.WeakReference

/** The now-playing screen over the lock screen: a big, tappable album cover. */
class LockActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var view: LockView

    private val watcher: (NowPlaying.Track?) -> Unit = { t ->
        if (t == null) leave() else view.setTrack(t)
    }

    /** Opened from settings to try it out, rather than over the lock screen. */
    private val preview get() = intent?.getBooleanExtra(EXTRA_PREVIEW, false) == true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!preview && !isLocked()) {
            // The phone was unlocked before we got here: don't cover the home screen.
            finish()
            return
        }
        instance = WeakReference(this)
        prefs = Prefs(this)
        if (Build.VERSION.SDK_INT >= 27) setShowWhenLocked(true)
        else @Suppress("DEPRECATION") window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        view = LockView(this, prefs.expanded, prefs.dotCover).apply {
            onExpandedChanged = { prefs.expanded = it }
            onUnlock = { unlock(null) }
            onOpenApp = { t -> unlock { openApp(t) } }
        }
        setContentView(view)
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        NowPlaying.watch(watcher)
    }

    override fun onResume() {
        super.onResume()
        if (!preview && !isLocked()) leave()
    }

    override fun onStop() {
        NowPlaying.unwatch(watcher)
        super.onStop()
    }

    override fun onDestroy() {
        if (instance?.get() === this) instance = null
        super.onDestroy()
    }

    private fun isLocked() = getSystemService(KeyguardManager::class.java).isKeyguardLocked

    /** Goes away with a quick fade rather than a jump cut. */
    private fun leave() {
        if (isFinishing) return
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, android.R.anim.fade_out)
    }

    /** Swipe up: ask for the PIN / fingerprint if there is one, then step aside. */
    private fun unlock(then: (() -> Unit)?) {
        if (!isLocked()) {
            then?.invoke()
            leave()
            return
        }
        getSystemService(KeyguardManager::class.java).requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() {
                then?.invoke()
                leave()
            }
        })
    }

    private fun openApp(t: NowPlaying.Track) {
        try {
            val pi = t.controller.sessionActivity
            if (pi != null) {
                pi.send()
                return
            }
        } catch (_: Exception) {
        }
        packageManager.getLaunchIntentForPackage(t.pkg)?.let {
            try {
                startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        private const val CHANNEL = "lock"
        private const val NOTIFICATION_ID = 7
        private const val EXTRA_PREVIEW = "preview"
        private var instance: WeakReference<LockActivity>? = null

        val showing: Boolean get() = instance?.get()?.isFinishing == false

        fun close() {
            instance?.get()?.leave()
        }

        fun preview(ctx: Context) {
            ctx.startActivity(Intent(ctx, LockActivity::class.java).putExtra(EXTRA_PREVIEW, true))
        }

        /**
         * Puts the cover screen up. Up to Android 14 an app allowed to draw over others may start it
         * directly; from Android 15 that needs a full-screen notification, which opens it while the
         * screen is off (it's cancelled as soon as the screen appears).
         */
        fun show(ctx: Context) {
            val i = Intent(ctx, LockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT < 35 && Settings.canDrawOverlays(ctx)) {
                try {
                    ctx.startActivity(i)
                    return
                } catch (_: Exception) {
                }
            }
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (!nm.areNotificationsEnabled()) return
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, ctx.getString(R.string.channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                }
            )
            val pi = PendingIntent.getActivity(ctx, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n = Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(NowPlaying.current?.title ?: ctx.getString(R.string.app_name))
                .setContentText(NowPlaying.current?.artist)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(pi)
                .setFullScreenIntent(pi, true)
                .setAutoCancel(true)
                .setTimeoutAfter(15_000)
                .build()
            try {
                nm.notify(NOTIFICATION_ID, n)
            } catch (_: SecurityException) {
            }
        }

        fun canUseFullScreen(ctx: Context): Boolean =
            Build.VERSION.SDK_INT < 34 || ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    }
}
