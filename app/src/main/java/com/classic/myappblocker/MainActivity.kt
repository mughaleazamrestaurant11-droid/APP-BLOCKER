package com.classic.myappblocker

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.view.View
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Dashboard: Launch Guard switch, live counter and the main menu. */
class MainActivity : Activity() {

    private lateinit var guard: Switch
    private lateinit var status: TextView
    private lateinit var countOpen: TextView
    private var uiBusy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        maybeLock()

        guard = findViewById(R.id.switch_guard)
        status = findViewById(R.id.text_status)
        countOpen = findViewById(R.id.text_count_open)

        findViewById<View>(R.id.card_apps).setOnClickListener {
            startActivity(Intent(this, AppsActivity::class.java))
        }
        findViewById<View>(R.id.card_tiktok).setOnClickListener {
            startActivity(Intent(this, TikTokDownloaderActivity::class.java))
        }
        findViewById<View>(R.id.card_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        guard.setOnCheckedChangeListener { _, on ->
            if (uiBusy) return@setOnCheckedChangeListener
            Store.setOpenGuardEnabled(this, on)
            if (on && !LaunchGuardService.isServiceEnabled(this)) {
                Toast.makeText(
                    this,
                    "Now enable the Launch Guard service in Settings",
                    Toast.LENGTH_LONG
                ).show()
            }
            refreshUi()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    private fun refreshUi() {
        val guardOn = Store.openGuardEnabled(this)
        val serviceOn = LaunchGuardService.isServiceEnabled(this)
        val protected = countBlocked()

        uiBusy = true
        guard.isChecked = guardOn
        uiBusy = false

        status.text = when {
            guardOn && serviceOn -> "Active - $protected app(s) protected"
            guardOn -> "On, but the Accessibility service is off - enable it in Settings"
            else -> "Off - apps are not blocked from opening"
        }
        countOpen.text = protected.toString()
    }

    private fun countBlocked(): Int {
        var n = 0
        for (info in packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)) {
            if (Store.flags(this, info.packageName) != 0) n++
        }
        return n
    }

    private fun maybeLock() {
        if (Build.VERSION.SDK_INT < 28) return
        if (!Store.fingerprintLock(this)) return
        if (Build.VERSION.SDK_INT >= 30) {
            val bm = getSystemService(BiometricManager::class.java)
            if (bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                != BiometricManager.BIOMETRIC_SUCCESS
            ) {
                return // no fingerprint enrolled - do not lock the user out
            }
        }
        try {
            val prompt = BiometricPrompt.Builder(this)
                .setTitle("App Blocker locked")
                .setSubtitle("Use your fingerprint to continue")
                .setNegativeButton(
                    "Cancel", mainExecutor,
                    DialogInterface.OnClickListener { _, _ -> finish() }
                )
                .build()
            prompt.authenticate(CancellationSignal(), mainExecutor, authCallback)
        } catch (e: Exception) {
            // ignore
        }
    }

    private val authCallback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
            // unlocked
        }

        override fun onAuthenticationError(code: Int, errString: CharSequence?) {
            if (code == 5 || code == 10) {
                finish() // user cancelled
            }
        }
    }
}
