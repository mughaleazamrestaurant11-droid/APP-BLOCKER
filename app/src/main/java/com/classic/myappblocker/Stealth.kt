package com.classic.myappblocker

import android.view.Window
import android.view.WindowManager

/** Privacy helpers. */
object Stealth {

    /** Blanks this screen in screenshots and the recents/app-switcher preview. */
    fun secure(window: Window) {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
    }
}
