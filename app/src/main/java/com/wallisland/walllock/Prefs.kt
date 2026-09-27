package com.wallisland.walllock

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("walllock", Context.MODE_PRIVATE)

    /** Put the album cover on the lock screen while music plays. */
    var enabled: Boolean
        get() = sp.getBoolean("enabled", true)
        set(v) = sp.edit().putBoolean("enabled", v).apply()

    /** Keep the cover for a while after the music is paused, like the iPhone does. */
    var showPaused: Boolean
        get() = sp.getBoolean("show_paused", true)
        set(v) = sp.edit().putBoolean("show_paused", v).apply()

    /** [CoverArt.COVER] (the picture melting into dots) or [CoverArt.DOTS] (all dots). */
    var style: Int
        get() = sp.getInt("style", CoverArt.COVER)
        set(v) = sp.edit().putInt("style", v).apply()

    /** Black and white, the Nothing way. */
    var mono: Boolean
        get() = sp.getBoolean("mono", false)
        set(v) = sp.edit().putBoolean("mono", v).apply()

    /** Whether the lock screen currently shows our cover, so it can be put back even after a restart. */
    var coverActive: Boolean
        get() = sp.getBoolean("cover_active", false)
        set(v) = sp.edit().putBoolean("cover_active", v).apply()
}
