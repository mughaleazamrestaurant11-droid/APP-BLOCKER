package com.classic.myappblocker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Optionally blocks the internet of every newly installed app (when enabled in settings). */
class NewAppReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_PACKAGE_ADDED) return
        if (!Store.blockNewApps(context)) return
        val pkg = intent.data?.schemeSpecificPart ?: return
        if (pkg == context.packageName) return
        Store.setFlags(context, pkg, Store.BLOCK_ALL)
        MainActivity.refreshVpn(context)
    }
}
