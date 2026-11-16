package com.classic.myappblocker

import android.app.Activity
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView

/** Adapter that lists every installed app that holds the INTERNET permission. */
class AppAdapter(
    private val activity: Activity,
    private val onChanged: (() -> Unit)? = null
) : BaseAdapter() {

    private val all = ArrayList<AppEntry>()
    private val shown = ArrayList<AppEntry>()
    private var query = ""

    fun reload() {
        all.clear()
        val pm = activity.packageManager
        for (info in pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)) {
            if (!hasInternetPermission(info)) continue
            if (info.packageName == activity.packageName) continue
            val appInfo = info.applicationInfo ?: continue
            val entry = AppEntry().apply {
                pkg = info.packageName
                uid = appInfo.uid
                label = appInfo.loadLabel(pm)?.toString() ?: info.packageName
                icon = try {
                    pm.getApplicationIcon(info.packageName)
                } catch (e: Exception) {
                    null
                }
                flags = Store.flags(activity, info.packageName)
            }
            all.add(entry)
        }
        all.sortBy { it.label.lowercase() }
        refilter()
    }

    fun filter(q: String?) {
        query = (q ?: "").trim().lowercase()
        refilter()
    }

    private fun refilter() {
        shown.clear()
        for (e in all) {
            if (query.isEmpty() ||
                e.label.lowercase().contains(query) ||
                e.pkg.lowercase().contains(query)
            ) {
                shown.add(e)
            }
        }
        notifyDataSetChanged()
    }

    fun blockedCount(): Int = all.count { it.flags != 0 }

    private fun hasInternetPermission(info: PackageInfo): Boolean {
        val requested = info.requestedPermissions ?: return false
        for (p in requested) {
            if (p == "android.permission.INTERNET") return true
        }
        return false
    }

    override fun getCount(): Int = shown.size

    override fun getItem(position: Int): AppEntry = shown[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView
            ?: LayoutInflater.from(activity).inflate(R.layout.item_app, parent, false)
        val entry = getItem(position)

        val icon = view.findViewById<ImageView>(R.id.app_icon)
        val label = view.findViewById<TextView>(R.id.app_label)
        val pkg = view.findViewById<TextView>(R.id.app_pkg)
        val block = view.findViewById<Switch>(R.id.switch_block)

        icon.setImageDrawable(
            entry.icon ?: activity.getDrawable(android.R.drawable.sym_def_app_icon)
        )
        label.text = entry.label
        pkg.text = entry.pkg

        block.setOnCheckedChangeListener(null)
        block.isChecked = entry.flags != 0
        block.setOnCheckedChangeListener { _, checked ->
            entry.flags = if (checked) Store.BLOCK_ALL else 0
            Store.setFlags(activity, entry.pkg, entry.flags)
            MainActivity.refreshVpn(activity)
            onChanged?.invoke()
        }
        return view
    }
}
