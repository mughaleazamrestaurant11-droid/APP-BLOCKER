package com.classic.myappblocker

import android.content.Context
import android.content.SharedPreferences

/** Simple storage for which apps are blocked and whether the blocker is enabled. */
object Store {
    const val BLOCK_WIFI = 1
    const val BLOCK_DATA = 2
    const val BLOCK_OPEN = 4
    const val BLOCK_INTERNET = BLOCK_WIFI or BLOCK_DATA
    const val BLOCK_ALL = BLOCK_WIFI or BLOCK_DATA or BLOCK_OPEN

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

    fun blockNewApps(context: Context): Boolean = prefs(context).getBoolean("blockNew", false)

    fun setBlockNewApps(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("blockNew", value).apply()
    }

    fun tipsSeen(context: Context): Boolean = prefs(context).getBoolean("tipsSeen", false)

    fun setTipsSeen(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("tipsSeen", value).apply()
    }

    fun fingerprintLock(context: Context): Boolean = prefs(context).getBoolean("fingerprint", false)

    fun setFingerprintLock(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("fingerprint", value).apply()
    }

    fun openGuardEnabled(context: Context): Boolean = prefs(context).getBoolean("openGuard", false)

    fun setOpenGuardEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("openGuard", value).apply()
    }
}
