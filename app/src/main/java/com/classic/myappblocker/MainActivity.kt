package com.classic.myappblocker

import android.app.Activity
import android.app.AlertDialog
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var adapter: AppAdapter
    private lateinit var master: Switch
    private lateinit var status: TextView
    private var uiBusy = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        master = findViewById(R.id.switch_master)
        status = findViewById(R.id.text_status)
        val search = findViewById<EditText>(R.id.edit_search)
        val list = findViewById<ListView>(R.id.list)

        adapter = AppAdapter(this) { refreshUi() }
        list.adapter = adapter
        list.setOnItemLongClickListener { _, _, position, _ ->
            showBlockDialog(adapter.getItem(position))
            true
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                adapter.filter(s?.toString())
            }
        })

        findViewById<Button>(R.id.btn_block_all).setOnClickListener {
            adapter.blockAll()
            refreshVpn(this)
            refreshUi()
        }
        findViewById<Button>(R.id.btn_allow_all).setOnClickListener {
            adapter.allowAll()
            refreshVpn(this)
            refreshUi()
        }
        findViewById<Switch>(R.id.switch_blocked_only).setOnCheckedChangeListener { _, b ->
            adapter.setBlockedOnly(b)
        }
        val autoNew = findViewById<Switch>(R.id.switch_auto_new)
        autoNew.isChecked = Store.blockNewApps(this)
        autoNew.setOnCheckedChangeListener { _, b ->
            Store.setBlockNewApps(this, b)
            Toast.makeText(
                this,
                if (b) "Newly installed apps will be blocked" else "Newly installed apps will be allowed",
                Toast.LENGTH_SHORT
            ).show()
        }
        maybeShowTips()

        master.setOnCheckedChangeListener { _, on ->
            if (!uiBusy) {
                if (on) startFirewall() else stopFirewall()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        adapter.reload()

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
            running -> "Active on ${currentNetworkName()} - blocking ${adapter.blockedCount()} app(s)"
            Store.enabled(this) -> "Starting..."
            else -> "Off - flip the switch to start blocking"
        }
    }

    private fun startFirewall() {
        val prepare = VpnService.prepare(this)
        if (prepare != null) {
            try {
                @Suppress("DEPRECATION")
                startActivityForResult(prepare, REQUEST_VPN)
            } catch (e: ActivityNotFoundException) {
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

    private fun maybeShowTips() {
        if (Store.tipsSeen(this)) return
        Store.setTipsSeen(this, true)
        AlertDialog.Builder(this)
            .setTitle("How it works")
            .setMessage(
                "This app runs a tiny local VPN on your phone to enforce the blocks:\n\n" +
                        "\u2022 Switch ON and allow the VPN permission (one time).\n" +
                        "\u2022 Tap an app's switch to block its internet.\n" +
                        "\u2022 Long-press an app to block Wi-Fi only or data only.\n" +
                        "\u2022 'Auto-block new apps' blocks every app you install later.\n" +
                        "\u2022 Add the 'App Blocker' tile in quick settings for one-tap on/off.\n\n" +
                        "Nothing leaves your phone - the VPN only drops traffic of blocked apps. " +
                        "The key icon in the status bar is Android's own VPN indicator."
            )
            .setPositiveButton("Got it", null)
            .show()
    }

    private fun currentNetworkName(): String {
        return try {
            val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "no network"
            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile data"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                else -> "network"
            }
        } catch (e: Exception) {
            "network"
        }
    }

    private fun showBlockDialog(app: AppEntry) {
        val options = arrayOf(
            "Allow internet (no blocking)",
            "Block Wi-Fi only",
            "Block mobile data only",
            "Block everything (Wi-Fi + data)"
        )
        val selected = when (app.flags) {
            Store.BLOCK_WIFI -> 1
            Store.BLOCK_DATA -> 2
            Store.BLOCK_ALL -> 3
            else -> 0
        }
        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setSingleChoiceItems(options, selected) { dialog, which ->
                app.flags = when (which) {
                    1 -> Store.BLOCK_WIFI
                    2 -> Store.BLOCK_DATA
                    3 -> Store.BLOCK_ALL
                    else -> 0
                }
                Store.setFlags(this, app.pkg, app.flags)
                adapter.notifyDataSetChanged()
                refreshVpn(this)
                refreshUi()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    companion object {
        private const val REQUEST_VPN = 1

        /** Tell the running service to rebuild its rules after a change. */
        fun refreshVpn(context: Context) {
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
