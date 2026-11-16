package com.classic.myappblocker

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Switch
import android.widget.TextView

/** The app list: per-app opening blocks (Launch Guard). */
class AppsActivity : Activity() {

    private lateinit var adapter: AppAdapter
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_apps)
        actionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Apps & blocking"

        status = findViewById(R.id.text_status)
        val search = findViewById<EditText>(R.id.edit_search)
        val list = findViewById<ListView>(R.id.list)

        adapter = AppAdapter(this) { refreshFooter() }
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
            refreshFooter()
        }
        findViewById<Button>(R.id.btn_allow_all).setOnClickListener {
            adapter.allowAll()
            refreshFooter()
        }
        findViewById<Switch>(R.id.switch_blocked_only).setOnCheckedChangeListener { _, b ->
            adapter.setBlockedOnly(b)
            refreshFooter()
        }
    }

    override fun onResume() {
        super.onResume()
        adapter.reload()
        refreshFooter()
    }

    private fun refreshFooter() {
        status.text =
            "Showing ${adapter.shownCount()} of ${adapter.totalCount()} apps - ${adapter.blockedCount()} blocked from opening"
    }

    private fun showBlockDialog(app: AppEntry) {
        val options = arrayOf(
            "Allow opening",
            "Block opening (only from this app)"
        )
        val selected = if (app.flags != 0) 1 else 0
        val dialog = AlertDialog.Builder(this)
            .setTitle(app.label)
            .setSingleChoiceItems(options, selected) { d, which ->
                app.flags = if (which == 1) Store.BLOCK_ALL else 0
                Store.setFlags(this, app.pkg, app.flags)
                adapter.notifyDataSetChanged()
                refreshFooter()
                d.dismiss()
            }
            .setNegativeButton("Cancel", null)

        if (app.flags != 0) {
            dialog.setNeutralButton("Open now (1 min bypass)") { _, _ ->
                LaunchGuardService.allow(app.pkg, 60_000)
                val launch = packageManager.getLaunchIntentForPackage(app.pkg)
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(launch)
                }
            }
        }
        dialog.show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
