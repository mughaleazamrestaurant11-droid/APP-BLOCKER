package com.classic.myappblocker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts the blocker automatically after the phone reboots. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!Store.enabled(context)) return
        try {
            context.startForegroundService(
                Intent(context, BlockerVpnService::class.java).setAction(BlockerVpnService.ACTION_START)
            )
        } catch (e: Exception) {
            // VPN permission missing or FGS start not allowed - user opens the app once.
        }
    }
}
