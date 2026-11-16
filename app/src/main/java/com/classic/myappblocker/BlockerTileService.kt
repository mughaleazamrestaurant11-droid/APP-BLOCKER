package com.classic.myappblocker

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick Settings tile: one-tap on/off for the firewall. */
class BlockerTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val context = applicationContext
        if (BlockerVpnService.isRunning) {
            Store.setEnabled(context, false)
            try {
                startService(
                    Intent(context, BlockerVpnService::class.java).setAction(BlockerVpnService.ACTION_STOP)
                )
            } catch (e: Exception) {
                // ignore
            }
        } else {
            if (VpnService.prepare(this) == null) {
                Store.setEnabled(context, true)
                try {
                    startForegroundService(
                        Intent(context, BlockerVpnService::class.java).setAction(BlockerVpnService.ACTION_START)
                    )
                } catch (e: Exception) {
                    // ignore
                }
            } else {
                // VPN permission not granted yet - open the app for the dialog.
                val intent = Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (Build.VERSION.SDK_INT >= 34) {
                    val pi = PendingIntent.getActivity(this, 3, intent, PendingIntent.FLAG_IMMUTABLE)
                    startActivityAndCollapse(pi)
                } else {
                    @Suppress("DEPRECATION")
                    startActivityAndCollapse(intent)
                }
            }
        }
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        tile.state = if (BlockerVpnService.isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = if (BlockerVpnService.isRunning) "Blocking" else "Off"
        }
        tile.updateTile()
    }
}
