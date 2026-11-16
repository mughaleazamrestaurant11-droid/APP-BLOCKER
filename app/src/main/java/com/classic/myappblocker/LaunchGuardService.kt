package com.classic.myappblocker

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * Watches which app comes to the foreground (via AccessibilityService) and,
 * if that app is blocked from opening, covers it with the BlockedActivity screen.
 * Apps can only be opened normally by launching them from this app.
 */
class LaunchGuardService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        running = true
    }

    override fun onUnbind(intent: Intent?): Boolean {
        running = false
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {
        // no feedback required
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = e.packageName?.toString() ?: return
        if (pkg == packageName) return
        if (isSystemOverlayPkg(pkg)) return

        // Foreground changed - forget the previous app's bypass and block
        // memory, so returning to a blocked app is covered again instantly.
        if (pkg != lastSeenPkg) {
            lastSeenPkg = pkg
            if (allowedPkg != null && SystemClock.elapsedRealtime() > graceUntil) {
                allowedPkg = null
            }
            lastBlocked = null
        }

        if (!Store.openGuardEnabled(this)) return
        val flags = Store.flags(this, pkg)
        if (flags and Store.BLOCK_OPEN == 0) return
        if (allowedPkg == pkg && SystemClock.elapsedRealtime() < allowedUntil) return
        if (pkg == lastBlocked && SystemClock.elapsedRealtime() < lastBlockedUntil) return

        lastBlocked = pkg
        lastBlockedUntil = SystemClock.elapsedRealtime() + 500

        try {
            val i = Intent(this, BlockedActivity::class.java)
                .putExtra(BlockedActivity.EXTRA_PKG, pkg)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
            startActivity(i)
        } catch (e: Exception) {
            // Background activity start refused on this device - app opens normally.
        }
    }

    private fun isSystemOverlayPkg(pkg: String): Boolean {
        if (pkg.startsWith("com.android.systemui")) return true
        val ime = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        if (ime != null) {
            val imePkg = ime.substringBefore('/')
            if (imePkg.isNotEmpty() && pkg == imePkg) return true
        }
        return false
    }

    companion object {
        var running = false

        private var allowedPkg: String? = null
        private var allowedUntil = 0L
        private var graceUntil = 0L
        private var lastSeenPkg: String? = null
        private var lastBlocked: String? = null
        private var lastBlockedUntil = 0L

        /** Allow a blocked app to open normally for [durationMs]. */
        fun allow(pkg: String, durationMs: Long) {
            allowedPkg = pkg
            allowedUntil = SystemClock.elapsedRealtime() + durationMs
            graceUntil = SystemClock.elapsedRealtime() + 3000
        }

        fun isServiceEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.contains(context.packageName)
        }
    }
}
