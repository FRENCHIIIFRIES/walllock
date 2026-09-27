package com.wallisland.walllock

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Setup, built in code so every pixel follows the dot-matrix look. */
class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private val permissionRows = mutableListOf<PermissionRow>()
    private lateinit var updateText: TextView
    private lateinit var updateButton: TextView
    private var pendingRelease: Updater.Release? = null
    private var busy = false
    private var leftForInstaller = false

    private class PermissionRow(val granted: () -> Boolean, val dot: View, val action: TextView)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val scroll = ScrollView(this).apply {
            background = DotGridDrawable(dp(18f), dp(1f))
            isVerticalScrollBarEnabled = false
            fitsSystemWindows = true
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(40))
        }
        scroll.addView(col)
        setContentView(scroll)

        buildHeader(col)
        buildSetup(col)
        buildLockScreen(col)
        buildUpdates(col)
        col.addView(hint("Walllock is not affiliated with Nothing Technology.").apply {
            setPadding(dp(6), dp(28), dp(6), 0)
        })
    }

    override fun onPause() {
        super.onPause()
        if (busy && pendingRelease != null) leftForInstaller = true
    }

    override fun onResume() {
        super.onResume()
        for (row in permissionRows) {
            val ok = row.granted()
            (row.dot.background as GradientDrawable).setColor(if (ok) Look.GREY else Look.accent)
            row.action.text = if (ok) "ON" else "ALLOW"
            row.action.alpha = if (ok) 0.4f else 1f
            row.action.isEnabled = !ok
        }
        if (leftForInstaller) {
            // Back from Android's "Update this app?" without it installing: let them try again.
            leftForInstaller = false
            busy = false
            updateButton.isEnabled = true
            updateText.text = "Not installed. Tap Update to try again."
        }
    }

    // ---- Sections. -------------------------------------------------------------------------------

    private fun buildHeader(col: LinearLayout) {
        col.addView(TextView(this).apply {
            text = "WALLLOCK"
            typeface = Look.dot(context)
            fontVariationSettings = "'wght' 900, 'ROND' 100"
            textSize = 40f
            setTextColor(Look.WHITE)
            includeFontPadding = false
        })
        col.addView(TextView(this).apply {
            text = "Your album cover on the lock screen. Tap it to make it big."
            typeface = Look.mono(context)
            textSize = 13f
            setTextColor(Look.GREY)
            setPadding(0, dp(6), 0, 0)
        })
    }

    private fun buildSetup(col: LinearLayout) {
        col.addView(sectionLabel("SETUP"))
        val card = card()
        card.addView(permissionRow("Notification access", "Lets Walllock see and control what's playing.", {
            WalllockListener.hasAccess(this)
        }) {
            startSafely(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })
        if (Build.VERSION.SDK_INT >= 33) {
            card.addView(divider())
            card.addView(permissionRow("Notifications", "Opens the cover screen when the phone locks.", {
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            }) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            })
        }
        if (Build.VERSION.SDK_INT >= 34) {
            card.addView(divider())
            card.addView(permissionRow("Full-screen alerts", "Lets it appear over the lock screen.", {
                LockActivity.canUseFullScreen(this)
            }) {
                startSafely(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
            })
        }
        if (Build.VERSION.SDK_INT < 35) {
            card.addView(divider())
            card.addView(permissionRow("Draw over apps", "Opens the cover screen instantly.", {
                Settings.canDrawOverlays(this)
            }) {
                startSafely(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            })
        }
        col.addView(card)
        col.addView(hint("Greyed out? Settings → Apps → Walllock → ⋮ → Allow restricted settings, then try again."))
    }

    private fun buildLockScreen(col: LinearLayout) {
        col.addView(sectionLabel("LOCK SCREEN"))
        val card = card()
        card.addView(toggleRow("Show on lock screen", "While music plays.", prefs.enabled) { prefs.enabled = it })
        card.addView(divider())
        card.addView(toggleRow("Keep after pause", "For 10 minutes, like the iPhone.", prefs.showPaused) {
            prefs.showPaused = it
        })
        card.addView(divider())
        card.addView(toggleRow("Open with big cover", "Tap the cover any time to switch.", prefs.expanded) {
            prefs.expanded = it
        })
        card.addView(divider())
        card.addView(toggleRow("Dot-matrix cover", "Draw the cover as coloured dots.", prefs.dotCover) {
            prefs.dotCover = it
        })
        col.addView(card)
        col.addView(pillButton(this, "Preview", filled = true) {
            if (NowPlaying.current == null) toast("Play some music first, then try again.")
            else LockActivity.preview(this)
        }.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(14) }
        })
    }

    private fun buildUpdates(col: LinearLayout) {
        col.addView(sectionLabel("UPDATES"))
        val card = card()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(16), dp(16), dp(16))
        }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, dp(12), 0) }
        texts.addView(titleView("Build ${Updater.currentBuild(this)}"))
        updateText = subView("Tap Check to look for a new build.")
        texts.addView(updateText)
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        updateButton = pillButton(this, "Check", filled = true) { onUpdateButton() }
        row.addView(updateButton)
        card.addView(row)
        col.addView(card)
    }

    /** Check first; once a newer build is found the same button becomes Update. */
    private fun onUpdateButton() {
        if (busy) return
        val release = pendingRelease
        if (release == null) {
            busy = true
            updateButton.isEnabled = false
            updateText.text = "Checking…"
            Updater.check(this) { result ->
                if (isFinishing) return@check
                busy = false
                updateButton.isEnabled = true
                when (result) {
                    is Updater.Check.Available -> {
                        pendingRelease = result.release
                        updateText.text = "Build ${result.release.build} is out."
                        updateButton.text = "UPDATE"
                    }
                    Updater.Check.UpToDate -> updateText.text = "You're on the latest build."
                    is Updater.Check.Failed -> updateText.text = "Couldn't check: ${result.reason}."
                }
            }
            return
        }
        if (!Updater.ensureCanInstall(this)) {
            toast("Allow Walllock to install updates, then tap Update again")
            return
        }
        busy = true
        updateButton.isEnabled = false
        Updater.install(this, release,
            progress = { pct -> updateText.text = if (pct >= 0) "Downloading… $pct%" else "Downloading…" },
            committed = { updateText.text = "Installing build ${release.build}…" },
            failed = { reason ->
                busy = false
                updateButton.isEnabled = true
                updateText.text = "Update failed: $reason."
            },
        )
    }

    // ---- Building blocks. ------------------------------------------------------------------------

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            setColor(Look.SURFACE)
            cornerRadius = dp(24f)
            setStroke(dp(1), Look.LINE)
        }
        clipToOutline = true
    }

    private fun sectionLabel(text: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(6), dp(30), 0, dp(12))
        addView(View(context).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Look.accent) }
        }, LinearLayout.LayoutParams(dp(6), dp(6)).apply { marginEnd = dp(10) })
        addView(TextView(context).apply {
            this.text = text
            typeface = Look.dot(context)
            fontVariationSettings = "'wght' 800, 'ROND' 100"
            textSize = 13f
            letterSpacing = 0.1f
            setTextColor(Look.GREY)
            includeFontPadding = false
        })
    }

    private fun hint(text: String) = TextView(this).apply {
        this.text = text
        typeface = Look.mono(context)
        textSize = 11.5f
        setTextColor(Look.GREY)
        setPadding(dp(6), dp(12), dp(6), 0)
    }

    private fun divider() = View(this).apply {
        setBackgroundColor(Look.LINE)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
            marginStart = dp(20); marginEnd = dp(20)
        }
    }

    private fun titleView(text: String) = TextView(this).apply {
        this.text = text
        typeface = Look.mono(context)
        textSize = 14.5f
        setTextColor(Look.WHITE)
    }

    private fun subView(text: String) = TextView(this).apply {
        this.text = text
        typeface = Look.mono(context)
        textSize = 11.5f
        setTextColor(Look.GREY)
        setPadding(0, dp(2), 0, 0)
    }

    private fun toggleRow(title: String, sub: String, value: Boolean, onChange: (Boolean) -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, dp(16), 0) }
        texts.addView(titleView(title))
        texts.addView(subView(sub))
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val toggle = NToggle(this).apply {
            set(value)
            this.onChange = onChange
        }
        row.addView(toggle)
        row.setOnClickListener { toggle.performClick() }
        return row
    }

    private fun permissionRow(title: String, sub: String, granted: () -> Boolean, onGrant: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(16), dp(16), dp(16))
        }
        val dot = View(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Look.accent) }
        }
        row.addView(dot, LinearLayout.LayoutParams(dp(8), dp(8)).apply { marginEnd = dp(14) })
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, dp(12), 0) }
        texts.addView(titleView(title))
        texts.addView(subView(sub))
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val action = pillButton(this, "Allow", filled = true, onClick = onGrant)
        row.addView(action)
        permissionRows += PermissionRow(granted, dot, action)
        return row
    }

    private fun startSafely(i: Intent) {
        try {
            startActivity(i)
        } catch (_: Exception) {
            toast("Couldn't open that settings page on this phone.")
        }
    }

    private fun toast(s: String) = android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show()
}
