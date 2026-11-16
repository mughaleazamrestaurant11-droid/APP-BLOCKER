package com.classic.myappblocker

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.widget.TextView
import kotlin.math.abs

/**
 * Full-screen cover shown over an app that is blocked from opening.
 * Shows only "App blocked" + the app name.
 * - Tap anywhere or press back -> exit straight to the home screen.
 * - Secret gesture: swipe DOWN twice in a row -> the app opens for 60 s.
 */
class BlockedActivity : Activity() {

    private var pkg = ""
    private var downX = 0f
    private var downY = 0f
    private var downAt = 0L
    private var swipeCount = 0
    private var lastSwipeAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blocked)

        pkg = intent?.getStringExtra(EXTRA_PKG) ?: ""
        findViewById<TextView>(R.id.blocked_name).text = labelFor(pkg)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                downAt = SystemClock.elapsedRealtime()
            }
            MotionEvent.ACTION_UP -> {
                val dy = event.y - downY
                val dx = abs(event.x - downX)
                val minSwipe = resources.displayMetrics.heightPixels * 0.20f
                when {
                    // Deliberate swipe down (at least a fifth of the screen)
                    dy > minSwipe && dx < minSwipe -> onSwipeDown()
                    // Short quick tap -> go home
                    dx < 60 && abs(dy) < 60 &&
                        SystemClock.elapsedRealtime() - downAt < 350 -> goHome()
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun onSwipeDown() {
        val now = SystemClock.elapsedRealtime()
        // Two swipes within 3 seconds; an older swipe doesn't count.
        swipeCount = if (now - lastSwipeAt > 3000) 1 else swipeCount + 1
        lastSwipeAt = now

        // Silent confirmation the swipe registered (owner feels it, others don't).
        window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

        if (swipeCount >= 2) {
            swipeCount = 0
            LaunchGuardService.allow(pkg, 60_000)
            val launch = packageManager.getLaunchIntentForPackage(pkg)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launch)
            }
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        goHome()
    }

    private fun goHome() {
        try {
            startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            // ignore
        }
        finish()
    }

    private fun labelFor(pkg: String): String {
        return try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(pkg, 0)
            ).toString()
        } catch (e: Exception) {
            pkg
        }
    }

    companion object {
        const val EXTRA_PKG = "pkg"
    }
}
