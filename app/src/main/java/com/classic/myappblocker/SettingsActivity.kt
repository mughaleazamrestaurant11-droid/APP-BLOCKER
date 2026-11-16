package com.classic.myappblocker

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject

/** Settings: behaviour, Launch Guard, fingerprint lock and backup. */
class SettingsActivity : Activity() {

    private var uiBusy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        Stealth.secure(window)
        actionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Settings"

        findViewById<Switch>(R.id.switch_auto_new).setOnCheckedChangeListener { _, b ->
            if (uiBusy) return@setOnCheckedChangeListener
            Store.setBlockNewApps(this, b)
            Toast.makeText(
                this,
                if (b) "Newly installed apps will be blocked" else "Newly installed apps will be allowed",
                Toast.LENGTH_SHORT
            ).show()
        }

        val fp = findViewById<Switch>(R.id.switch_fingerprint)
        if (Build.VERSION.SDK_INT >= 28) {
            fp.setOnCheckedChangeListener { _, b ->
                if (uiBusy) return@setOnCheckedChangeListener
                Store.setFingerprintLock(this, b)
                Toast.makeText(
                    this,
                    if (b) "Fingerprint lock enabled" else "Fingerprint lock disabled",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            fp.visibility = View.GONE
        }

        findViewById<Switch>(R.id.switch_open_guard).setOnCheckedChangeListener { _, b ->
            if (uiBusy) return@setOnCheckedChangeListener
            Store.setOpenGuardEnabled(this, b)
            Toast.makeText(
                this,
                if (b) "Now enable the service in Accessibility settings"
                else "Launch Guard turned off",
                Toast.LENGTH_SHORT
            ).show()
            refreshGuardStatus()
        }

        findViewById<Button>(R.id.btn_accessibility).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(this, "Accessibility settings not available", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btn_backup).setOnClickListener { backup() }
        findViewById<Button>(R.id.btn_restore).setOnClickListener { restore() }
    }

    override fun onResume() {
        super.onResume()
        loadPrefs()
        refreshGuardStatus()
    }

    private fun loadPrefs() {
        uiBusy = true
        findViewById<Switch>(R.id.switch_auto_new).isChecked = Store.blockNewApps(this)
        if (Build.VERSION.SDK_INT >= 28) {
            findViewById<Switch>(R.id.switch_fingerprint).isChecked = Store.fingerprintLock(this)
        }
        findViewById<Switch>(R.id.switch_open_guard).isChecked = Store.openGuardEnabled(this)
        uiBusy = false
    }

    private fun refreshGuardStatus() {
        val on = LaunchGuardService.isServiceEnabled(this)
        findViewById<TextView>(R.id.text_guard_status).text =
            if (on) "Accessibility service: ACTIVE"
            else "Accessibility service: OFF (required - tap the button below)"
    }

    private fun backup() {
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(
                Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/json")
                    .putExtra(Intent.EXTRA_TITLE, "appblocker_backup.json"),
                REQUEST_BACKUP_SAVE
            )
        } catch (e: Exception) {
            Toast.makeText(this, "No file picker available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun restore() {
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("*/*"),
                REQUEST_BACKUP_LOAD
            )
        } catch (e: Exception) {
            Toast.makeText(this, "No file picker available", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_BACKUP_SAVE && resultCode == RESULT_OK) {
            val uri: Uri? = data?.data
            if (uri != null) {
                try {
                    val obj = JSONObject()
                    for (info in packageManager.getInstalledPackages(0)) {
                        val flags = Store.flags(this, info.packageName)
                        if (flags != 0) obj.put(info.packageName, flags)
                    }
                    contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(obj.toString(2).toByteArray(Charsets.UTF_8))
                        out.flush()
                    }
                    Toast.makeText(this, "Backup saved", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Backup failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            return
        }
        if (requestCode == REQUEST_BACKUP_LOAD && resultCode == RESULT_OK) {
            val uri: Uri? = data?.data
            if (uri != null) {
                try {
                    val text = contentResolver.openInputStream(uri)?.bufferedReader()
                        ?.use { it.readText() } ?: ""
                    val obj = JSONObject(text)
                    val keys = obj.keys()
                    var count = 0
                    while (keys.hasNext()) {
                        val pkg = keys.next()
                        Store.setFlags(this, pkg, obj.optInt(pkg, 0))
                        count++
                    }
                    Toast.makeText(this, "Restored $count entries", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Restore failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            return
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        private const val REQUEST_BACKUP_SAVE = 10
        private const val REQUEST_BACKUP_LOAD = 11
    }
}
