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

/** Adapter that lists installed apps (launchable and/or internet-capable). */
class AppAdapter(
    private val activity: Activity,
    private val onChanged: (() -> Unit)? = null
) : BaseAdapter() {

    private val all = ArrayList<AppEntry>()
    private val shown = ArrayList<AppEntry>()
    private var query = ""
    private var blockedOnly = false

    fun reload() {
        all.clear()
        val pm = activity.packageManager
        for (info in pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)) {
            if (info.packageName == activity.packageName) continue
            val launchable = pm.getLaunchIntentForPackage(info.packageName) != null
            if (!launchable && !hasInternetPermission(info)) continue
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
        all.sortWith(compareBy({ it.flags == 0 }, { it.label.lowercase() }))
        refilter()
    }

    fun filter(q: String?) {
        query = (q ?: "").trim().lowercase()
        refilter()
    }

    fun setBlockedOnly(b: Boolean) {
        blockedOnly = b
        refilter()
    }

    fun blockAll() {
        for (e in all) {
            e.flags = Store.BLOCK_ALL
            Store.setFlags(activity, e.pkg, e.flags)
        }
        refilter()
    }

    fun allowAll() {
        for (e in all) {
            e.flags = 0
            Store.setFlags(activity, e.pkg, 0)
        }
        refilter()
    }

    private fun refilter() {
        shown.clear()
        for (e in all) {
            if (blockedOnly && e.flags == 0) continue
            if (query.isNotEmpty() &&
                !e.label.lowercase().contains(query) &&
                !e.pkg.lowercase().contains(query)
            ) continue
            shown.add(e)
        }
        notifyDataSetChanged()
    }

    fun blockedCount(): Int = all.count { it.flags != 0 }

    fun shownCount(): Int = shown.size

    fun totalCount(): Int = all.size

    /** Copy of the full list, used for backup export. */
    fun exportAll(): List<AppEntry> = ArrayList(all)

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
        val state = view.findViewById<TextView>(R.id.app_state)
        val pkg = view.findViewById<TextView>(R.id.app_pkg)
        val block = view.findViewById<Switch>(R.id.switch_block)

        icon.setImageDrawable(
            entry.icon ?: activity.getDrawable(android.R.drawable.sym_def_app_icon)
        )
        label.text = entry.label
        pkg.text = entry.pkg
        state.text = stateText(entry.flags)
        state.setTextColor(
            view.context.getColor(if (entry.flags == 0) R.color.text_secondary else R.color.primary)
        )

        block.setOnCheckedChangeListener(null)
        block.isChecked = entry.flags != 0
        block.setOnCheckedChangeListener { _, checked ->
            entry.flags = if (checked) Store.BLOCK_ALL else 0
            Store.setFlags(activity, entry.pkg, entry.flags)
            onChanged?.invoke()
        }
        return view
    }

    private fun stateText(flags: Int): String {
        return if (flags == 0) "Allowed" else "Opening blocked"
    }
}
