package com.classic.myappblocker

import android.graphics.drawable.Drawable

/** One row in the app list. */
class AppEntry {
    var pkg: String = ""
    var label: String = ""
    var uid: Int = 0
    var flags: Int = 0
    var icon: Drawable? = null
}
