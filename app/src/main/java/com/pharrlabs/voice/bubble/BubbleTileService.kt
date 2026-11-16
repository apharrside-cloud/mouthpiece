package com.pharrlabs.voice.bubble

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.pharrlabs.voice.R
import com.pharrlabs.voice.settings.Prefs

/** Quick Settings tile that shows or hides the floating button. */
class BubbleTileService : TileService() {
    override fun onStartListening() = refreshTile()

    override fun onClick() {
        val prefs = Prefs(this)
        val now = System.currentTimeMillis()
        val next = BubbleVisibility.toggle(prefs.bubbleOn, prefs.bubbleHiddenUntil, now)
        prefs.bubbleOn = next.on
        prefs.bubbleHiddenUntil = next.hiddenUntil
        BubbleService.refresh()
        refreshTile()
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val prefs = Prefs(this)
        val now = System.currentTimeMillis()
        tile.state = if (prefs.bubbleActive(now)) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Mouthpiece"
        tile.subtitle = BubbleVisibility.subtitle(prefs.bubbleOn, prefs.bubbleHiddenUntil, now)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_mic)
        tile.updateTile()
    }
}
