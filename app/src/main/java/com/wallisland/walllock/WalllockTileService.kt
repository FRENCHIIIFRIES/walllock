package com.wallisland.walllock

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick Settings tile: cover on or off, even from the lock screen's pull-down. */
class WalllockTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        val prefs = Prefs(this)
        prefs.enabled = !prefs.enabled
        LockWall.update(this, 0)
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        tile.state = if (Prefs(this).enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}
