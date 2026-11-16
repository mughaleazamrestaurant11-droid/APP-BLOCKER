package com.classic.myappblocker

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView

/**
 * Full-screen cover shown over an app that is blocked from opening.
 * Shows only "App blocked"; tapping anywhere or pressing back exits
 * straight to the home screen - never back into the blocked app.
 */
class BlockedActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blocked)

        val pkg = intent?.getStringExtra(EXTRA_PKG) ?: ""
        findViewById<TextView>(R.id.blocked_name).text = labelFor(pkg)

        findViewById<View>(R.id.block_root).setOnClickListener { goHome() }
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
