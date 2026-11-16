package com.classic.myappblocker

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick Settings tile: one-tap on/off for Launch Guard (app open blocking). */
class BlockerTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val context = applicationContext
        val now = !Store.openGuardEnabled(context)
        Store.setOpenGuardEnabled(context, now)
        updateTile()
        if (now && !LaunchGuardService.isServiceEnabled(context)) {
            // The accessibility service must still be enabled by the user.
            try {
                val pi = PendingIntent.getActivity(
                    this, 0,
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pi)
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val on = Store.openGuardEnabled(applicationContext)
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = if (on) "Blocking" else "Off"
        }
        tile.updateTile()
    }
}
