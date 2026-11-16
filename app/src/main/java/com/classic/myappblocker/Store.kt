package com.classic.myappblocker

import android.content.Context
import android.content.SharedPreferences

/** Simple storage for which apps are blocked and whether the blocker is enabled. */
object Store {
    const val BLOCK_WIFI = 1
    const val BLOCK_DATA = 2
    const val BLOCK_ALL = BLOCK_WIFI or BLOCK_DATA

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences("blocker", Context.MODE_PRIVATE)

    fun flags(context: Context, pkg: String): Int = prefs(context).getInt("f_$pkg", 0)

    fun setFlags(context: Context, pkg: String, flags: Int) {
        val editor = prefs(context).edit()
        if (flags == 0) editor.remove("f_$pkg") else editor.putInt("f_$pkg", flags)
        editor.apply()
    }

    fun enabled(context: Context): Boolean = prefs(context).getBoolean("enabled", false)

    fun setEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("enabled", value).apply()
    }
}
