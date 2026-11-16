package com.classic.myappblocker

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.widget.RemoteViews

/** Home screen widget: one tap toggles the firewall. */
class ToggleWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        refresh(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TOGGLE -> {
                toggle(context)
                refresh(context)
            }
            ACTION_REFRESH -> refresh(context)
        }
    }

    private fun toggle(context: Context) {
        if (BlockerVpnService.isRunning) {
            Store.setEnabled(context, false)
            try {
                context.startService(
                    Intent(context, BlockerVpnService::class.java).setAction(BlockerVpnService.ACTION_STOP)
                )
            } catch (e: Exception) {
                // ignore
            }
        } else {
            if (Store.enabled(context) || VpnService.prepare(context) == null) {
                Store.setEnabled(context, true)
                try {
                    context.startForegroundService(
                        Intent(context, BlockerVpnService::class.java).setAction(BlockerVpnService.ACTION_START)
                    )
                } catch (e: Exception) {
                    // ignore
                }
            } else {
                // VPN permission not granted yet - open the app.
                try {
                    context.startActivity(
                        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }

    private fun refresh(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val running = BlockerVpnService.isRunning
        val views = RemoteViews(context.packageName, R.layout.widget_toggle)
        views.setTextViewText(R.id.widget_text, if (running) "Blocker ON" else "Blocker OFF")
        views.setInt(
            R.id.widget_text, "setBackgroundResource",
            if (running) R.drawable.widget_bg_on else R.drawable.widget_bg_off
        )
        views.setOnClickPendingIntent(
            R.id.widget_text,
            PendingIntent.getBroadcast(
                context, 0,
                Intent(context, ToggleWidget::class.java).setAction(ACTION_TOGGLE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        manager.updateAppWidget(ComponentName(context, ToggleWidget::class.java), views)
    }

    companion object {
        const val ACTION_TOGGLE = "com.classic.myappblocker.WIDGET_TOGGLE"
        const val ACTION_REFRESH = "com.classic.myappblocker.WIDGET_REFRESH"

        fun poke(context: Context) {
            try {
                context.sendBroadcast(
                    Intent(context, ToggleWidget::class.java).setAction(ACTION_REFRESH)
                )
            } catch (e: Exception) {
                // ignore
            }
        }
    }
}
