package com.wallisland.walllock

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.max
import kotlin.math.min

/** Setup, built in code so every pixel follows the dot-matrix look. */
class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private val permissionRows = mutableListOf<PermissionRow>()
    private lateinit var preview: ImageView
    private lateinit var previewNote: TextView
    private lateinit var restoreText: TextView
    private lateinit var resetButton: TextView
    private lateinit var updateText: TextView
    private lateinit var updateButton: TextView
    private var previewKey: String? = null
    private var previewGen = 0
    private var pendingRelease: Updater.Release? = null
    private var busy = false
    private var leftForInstaller = false

    private class PermissionRow(val granted: () -> Boolean, val dot: View, val action: TextView)

    private val watcher: (NowPlaying.Track?) -> Unit = { refreshPreview() }

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
        buildPreview(col)
        buildLook(col)
        buildBehaviour(col)
        buildRestore(col)
        buildUpdates(col)
        col.addView(hint("Walllock is not affiliated with Nothing Technology.").apply {
            setPadding(dp(6), dp(28), dp(6), 0)
        })
    }

    override fun onStart() {
        super.onStart()
        NowPlaying.watch(watcher)
    }

    override fun onStop() {
        NowPlaying.unwatch(watcher)
        super.onStop()
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode == PICK && resultCode == RESULT_OK && uri != null) saveRestoreImage(uri)
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
            text = "While music plays, your lock screen wallpaper becomes the album cover. " +
                "Your clock, notifications and player stay on top."
            typeface = Look.mono(context)
            textSize = 13f
            setTextColor(Look.GREY)
            setPadding(0, dp(6), 0, 0)
        })
    }

    private fun buildSetup(col: LinearLayout) {
        col.addView(sectionLabel("SETUP"))
        val card = card()
        card.addView(permissionRow("Notification access", "Lets Walllock see what's playing.", {
            WalllockListener.hasAccess(this)
        }) {
            startSafely(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })
        col.addView(card)
        col.addView(hint("Greyed out? Settings → Apps → Walllock → ⋮ → Allow restricted settings, then try again."))
    }

    private fun buildPreview(col: LinearLayout) {
        col.addView(sectionLabel("PREVIEW"))
        val (sw, sh) = LockWall.screenSize(this)
        val w = (resources.displayMetrics.widthPixels * 0.56f).toInt()
        val h = w * sh / sw
        val frame = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                setColor(Look.BLACK)
                cornerRadius = dp(22f)
                setStroke(dp(1), Look.LINE)
            }
            clipToOutline = true
        }
        preview = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_XY }
        frame.addView(preview, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        previewNote = TextView(this).apply {
            text = "PLAY A SONG\nTO SEE IT HERE"
            typeface = Look.dot(context)
            fontVariationSettings = "'wght' 800, 'ROND' 100"
            textSize = 13f
            letterSpacing = 0.1f
            gravity = Gravity.CENTER
            setTextColor(Look.GREY)
        }
        frame.addView(previewNote, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        col.addView(frame, LinearLayout.LayoutParams(w, h).apply { gravity = Gravity.CENTER_HORIZONTAL })
        col.addView(hint("Just the wallpaper: your phone draws its own clock and notifications over it.").apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })
    }

    private fun buildLook(col: LinearLayout) {
        col.addView(sectionLabel("LOOK"))
        val card = card()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(16), dp(16), dp(16))
        }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, dp(12), 0) }
        texts.addView(titleView("Style"))
        texts.addView(subView("Cover melts into dots, or all dots."))
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val cover = choicePill("Cover")
        val dots = choicePill("Dots")
        fun paint() {
            styleChoice(cover, prefs.style == CoverArt.COVER)
            styleChoice(dots, prefs.style == CoverArt.DOTS)
        }
        cover.setOnClickListener { prefs.style = CoverArt.COVER; paint(); changed() }
        dots.setOnClickListener { prefs.style = CoverArt.DOTS; paint(); changed() }
        paint()
        row.addView(cover)
        row.addView(dots, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { marginStart = dp(8) })
        card.addView(row)
        card.addView(divider())
        card.addView(toggleRow("Black and white", "Monochrome cover and dots.", prefs.mono) {
            prefs.mono = it
            changed()
        })
        col.addView(card)
    }

    private fun buildBehaviour(col: LinearLayout) {
        col.addView(sectionLabel("LOCK SCREEN"))
        val card = card()
        card.addView(toggleRow("Cover on lock screen", "While music plays. Also a Quick Settings tile.", prefs.enabled) {
            prefs.enabled = it
            changed()
        })
        card.addView(divider())
        card.addView(toggleRow("Keep after pause", "For 10 minutes, like the iPhone.", prefs.showPaused) {
            prefs.showPaused = it
            changed()
        })
        col.addView(card)
    }

    private fun buildRestore(col: LinearLayout) {
        col.addView(sectionLabel("WHEN MUSIC STOPS"))
        val card = card()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(16), dp(16), dp(16))
        }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, dp(12), 0) }
        texts.addView(titleView("Lock screen goes back to"))
        restoreText = subView("")
        texts.addView(restoreText)
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(pillButton(this, "Pick", filled = true) {
            startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"), PICK,
            )
        })
        resetButton = pillButton(this, "Reset") {
            LockWall.restoreFile(this).delete()
            showRestoreChoice()
        }
        row.addView(resetButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { marginStart = dp(8) })
        card.addView(row)
        col.addView(card)
        col.addView(hint("If your lock screen had its own wallpaper (not the same as the home screen), pick it here so Walllock can put it back."))
        showRestoreChoice()
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

    // ---- Behaviour. ------------------------------------------------------------------------------

    /** A setting changed: redraw the preview and the lock screen straight away. */
    private fun changed() {
        previewKey = null
        refreshPreview()
        LockWall.update(this, 0)
    }

    private fun refreshPreview() {
        val t = NowPlaying.current
        val art = t?.art
        if (art == null) {
            previewKey = null
            preview.setImageDrawable(null)
            previewNote.visibility = View.VISIBLE
            return
        }
        val key = "${t.artKey}|${prefs.style}|${prefs.mono}"
        if (key == previewKey) return
        previewKey = key
        previewNote.visibility = View.GONE
        val gen = ++previewGen
        val (sw, sh) = LockWall.screenSize(this)
        // A third of full size is plenty for the preview and quick to draw.
        val w = sw / 3
        val h = sh / 3
        val style = prefs.style
        val mono = prefs.mono
        Thread {
            val bmp = runCatching { CoverArt.compose(art, w, h, style, mono) }.getOrNull()
            runOnUiThread { if (gen == previewGen && !isFinishing) preview.setImageBitmap(bmp) }
        }.start()
    }

    private fun showRestoreChoice() {
        val picked = LockWall.restoreFile(this).exists()
        // A lock screen wallpaper of its own, that isn't our cover: it'd be lost unless picked.
        val ownLock = !picked && !prefs.coverActive && runCatching {
            android.app.WallpaperManager.getInstance(this).getWallpaperId(android.app.WallpaperManager.FLAG_LOCK) >= 0
        }.getOrDefault(false)
        restoreText.text = when {
            picked -> "The image you picked."
            ownLock -> "Your lock screen has its own wallpaper. Pick it so it comes back."
            else -> "Your home screen wallpaper."
        }
        restoreText.setTextColor(if (ownLock) Look.accent else Look.GREY)
        resetButton.visibility = if (picked) View.VISIBLE else View.GONE
    }

    /** Copies the picked image, cropped to the screen, so it can be put back later. */
    private fun saveRestoreImage(uri: Uri) {
        val (sw, sh) = LockWall.screenSize(this)
        restoreText.text = "Saving…"
        Thread {
            val ok = runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= sw && bounds.outHeight / (sample * 2) >= sh) sample *= 2
                val src = contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                } ?: error("Couldn't read that image")
                val scale = max(sw.toFloat() / src.width, sh.toFloat() / src.height)
                val cw = min(src.width, (sw / scale).toInt())
                val ch = min(src.height, (sh / scale).toInt())
                val out = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
                val from = Rect((src.width - cw) / 2, (src.height - ch) / 2, (src.width + cw) / 2, (src.height + ch) / 2)
                Canvas(out).drawBitmap(src, from, Rect(0, 0, sw, sh), Paint(Paint.FILTER_BITMAP_FLAG))
                LockWall.restoreFile(this).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            }.isSuccess
            runOnUiThread {
                showRestoreChoice()
                if (!ok) toast("Couldn't use that image.")
            }
        }.start()
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

    /** A pill for a pick-one choice; [styleChoice] fills the chosen one. */
    private fun choicePill(label: String) = TextView(this).apply {
        text = label.uppercase()
        typeface = Look.monoBold(context)
        textSize = 12f
        letterSpacing = 0.06f
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(10), dp(12), dp(10))
        isClickable = true
    }

    private fun styleChoice(v: TextView, on: Boolean) {
        v.setTextColor(if (on) Look.BLACK else Look.WHITE)
        v.background = GradientDrawable().apply {
            cornerRadius = dp(100f)
            if (on) setColor(Look.WHITE) else {
                setColor(Look.BLACK)
                setStroke(dp(1), Look.GREY)
            }
        }
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

    private companion object {
        const val PICK = 7
    }
}
