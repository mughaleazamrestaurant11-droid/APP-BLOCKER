package com.classic.myappblocker

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import android.content.DialogInterface

/** Dashboard: protection switch, live counters and the main menu. */
class MainActivity : Activity() {

    private lateinit var master: Switch
    private lateinit var status: TextView
    private lateinit var countInternet: TextView
    private lateinit var countOpen: TextView
    private var uiBusy = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        maybeLock()

        master = findViewById(R.id.switch_master)
        status = findViewById(R.id.text_status)
        countInternet = findViewById(R.id.text_count_internet)
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

        master.setOnCheckedChangeListener { _, on ->
            if (!uiBusy) {
                if (on) startFirewall() else stopFirewall()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // Restore the blocker after a reboot / process death
        // (no dialog needed if VPN permission was already granted).
        if (Store.enabled(this) && !BlockerVpnService.isRunning && VpnService.prepare(this) == null) {
            startForegroundService(
                Intent(this, BlockerVpnService::class.java).setAction(BlockerVpnService.ACTION_START)
            )
            handler.postDelayed({ refreshUi() }, 600)
        }
        refreshUi()
    }

    private fun refreshUi() {
        val running = BlockerVpnService.isRunning
        uiBusy = true
        master.isChecked = running
        uiBusy = false
        status.text = when {
            running -> "Active on ${currentNetworkName()} - protection is on"
            Store.enabled(this) -> "Starting..."
            else -> "Off - flip the switch to start protection"
        }
        refreshCounts()
    }

    private fun refreshCounts() {
        var internet = 0
        var open = 0
        for (info in packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)) {
            val f = Store.flags(this, info.packageName)
            if (f == 0) continue
            if (f and Store.BLOCK_INTERNET != 0) internet++
            if (f and Store.BLOCK_OPEN != 0) open++
        }
        countInternet.text = internet.toString()
        countOpen.text = open.toString()
    }

    private fun currentNetworkName(): String {
        return try {
            val cm = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "no network"
            when {
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile data"
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                else -> "network"
            }
        } catch (e: Exception) {
            "network"
        }
    }

    private fun startFirewall() {
        val prepare = VpnService.prepare(this)
        if (prepare != null) {
            try {
                @Suppress("DEPRECATION")
                startActivityForResult(prepare, REQUEST_VPN)
            } catch (e: android.content.ActivityNotFoundException) {
                Toast.makeText(this, "VPN permission dialog not found", Toast.LENGTH_SHORT).show()
                refreshUi()
            }
            return
        }
        launchFirewall()
    }

    private fun launchFirewall() {
        Store.setEnabled(this, true)
        startForegroundService(
            Intent(this, BlockerVpnService::class.java).setAction(BlockerVpnService.ACTION_START)
        )
        refreshUi()
        handler.postDelayed({ refreshUi() }, 600)
    }

    private fun stopFirewall() {
        Store.setEnabled(this, false)
        try {
            startService(
                Intent(this, BlockerVpnService::class.java).setAction(BlockerVpnService.ACTION_STOP)
            )
        } catch (e: Exception) {
            // Service not running - nothing to stop.
        }
        refreshUi()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_VPN) {
            if (resultCode == RESULT_OK) {
                launchFirewall()
            } else {
                Toast.makeText(this, "VPN permission denied", Toast.LENGTH_SHORT).show()
                refreshUi()
            }
        }
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

    companion object {
        private const val REQUEST_VPN = 1

        /** Tell the running service to rebuild its rules after a change. */
        fun refreshVpn(context: android.content.Context) {
            if (!BlockerVpnService.isRunning) return
            try {
                context.startService(
                    Intent(context, BlockerVpnService::class.java)
                        .setAction(BlockerVpnService.ACTION_REBUILD)
                )
            } catch (e: Exception) {
                // ignore
            }
        }
    }
}
