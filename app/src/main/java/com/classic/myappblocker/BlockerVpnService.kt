package com.classic.myappblocker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream

/**
 * Local VPN that blocks internet access for the selected apps.
 *
 * Only the blocked apps' traffic is routed into this VPN (allowlist mode with
 * addAllowedApplication); everything else (system, other apps) bypasses the
 * VPN untouched. Packets that arrive in the tunnel are simply dropped, so the
 * blocked app cannot reach the internet and cannot resolve DNS either.
 */
class BlockerVpnService : VpnService() {

    private var connectivity: ConnectivityManager? = null
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var tunnel: ParcelFileDescriptor? = null
    private var drainer: Thread? = null
    private var lastSignature = Long.MIN_VALUE

    private val handler = Handler(Looper.getMainLooper())
    private val rebuildTask = Runnable { rebuild() }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        connectivity = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

        val channel = NotificationChannel(
            CHANNEL_ID, "Firewall status", NotificationManager.IMPORTANCE_MIN
        )
        channel.setShowBadge(false)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)

        callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                scheduleRebuild()
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                scheduleRebuild()
            }

            override fun onLost(network: Network) {
                scheduleRebuild()
            }
        }
        try {
            connectivity?.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(),
                callback!!
            )
        } catch (e: Exception) {
            Log.w(TAG, "network callback failed", e)
            callback = null
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        if (action == ACTION_STOP) {
            Store.setEnabled(this, false)
            shutdown()
            stopSelf()
            return START_NOT_STICKY
        }
        showNotification()
        rebuild()
        return START_STICKY
    }

    private fun scheduleRebuild() {
        handler.removeCallbacks(rebuildTask)
        handler.postDelayed(rebuildTask, 500)
    }

    private fun rebuild() {
        if (!Store.enabled(this)) {
            shutdown()
            stopSelf()
            return
        }

        val wifiActive = isWifiLikeActive()
        val pm = packageManager

        val blockedPackages = ArrayList<String>()
        for (info in pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)) {
            if (info.packageName == packageName) continue
            if (info.applicationInfo == null) continue
            if (!hasInternetPermission(info)) continue
            val f = Store.flags(this, info.packageName)
            val blockNow = if (wifiActive) {
                (f and Store.BLOCK_WIFI) != 0
            } else {
                (f and Store.BLOCK_DATA) != 0
            }
            if (blockNow) blockedPackages.add(info.packageName)
        }

        if (blockedPackages.isEmpty()) {
            // Nothing to block on the current network - the VPN is not needed.
            shutdown()
            stopSelf()
            return
        }

        // Skip rebuilding when nothing changed (network callbacks fire often).
        val signature = (if (wifiActive) 1L else 0L) * 31 + blockedPackages.hashCode()
        if (signature == lastSignature && tunnel != null) return
        lastSignature = signature

        var newTunnel: ParcelFileDescriptor? = null
        try {
            val builder = Builder() // VpnService.Builder (inner class of the service)
            builder.setSession(getString(R.string.app_name))
            builder.addAddress("10.111.222.3", 32)
            builder.addAddress("fd75:6d69:6e6a:2e17::3", 128)
            builder.addRoute("0.0.0.0", 0)
            builder.addRoute("::", 0)
            builder.setMtu(1500)
            for (pkgName in blockedPackages) {
                try {
                    builder.addAllowedApplication(pkgName)
                } catch (e: Exception) {
                    // App may have been uninstalled - skip it.
                }
            }
            newTunnel = builder.establish()
        } catch (e: Exception) {
            Log.e(TAG, "establish failed", e)
        }

        if (newTunnel == null) {
            // VPN permission was revoked or another VPN took over.
            Store.setEnabled(this, false)
            shutdown()
            stopSelf()
            return
        }

        stopDrainer()
        closeTunnel() // close the old tunnel only after the new one is up (no leak)
        tunnel = newTunnel
        startDrainer(newTunnel)
    }

    private fun isWifiLikeActive(): Boolean {
        return try {
            val network = connectivity?.activeNetwork ?: return true
            val caps = connectivity?.getNetworkCapabilities(network) ?: return true
            !caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        } catch (e: Exception) {
            true
        }
    }

    private fun hasInternetPermission(info: PackageInfo): Boolean {
        val requested = info.requestedPermissions ?: return false
        for (p in requested) {
            if (p == "android.permission.INTERNET") return true
        }
        return false
    }

    /** Reads and discards every packet that a blocked app sends into the tunnel. */
    private fun startDrainer(fd: ParcelFileDescriptor) {
        val thread = Thread {
            try {
                FileInputStream(fd.fileDescriptor).use { input ->
                    val buffer = ByteArray(8192)
                    while (input.read(buffer) >= 0) {
                        // Drop the packet - the blocked app simply gets no response.
                    }
                }
            } catch (e: Exception) {
                // Expected when the tunnel is closed.
            }
        }
        thread.name = "BlockerDrainer"
        drainer = thread
        thread.start()
    }

    private fun stopDrainer() {
        val thread = drainer
        drainer = null
        thread?.interrupt()
    }

    private fun closeTunnel() {
        val t = tunnel
        tunnel = null
        try {
            t?.close()
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun shutdown() {
        stopDrainer()
        closeTunnel()
    }

    override fun onRevoke() {
        // The user (or another VPN app) disconnected this VPN.
        Store.setEnabled(this, false)
        shutdown()
        stopSelf()
    }

    override fun onDestroy() {
        isRunning = false
        shutdown()
        callback?.let { cb ->
            try {
                connectivity?.unregisterNetworkCallback(cb)
            } catch (e: Exception) {
                // ignore
            }
        }
        callback = null
        super.onDestroy()
    }

    private fun showNotification() {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, BlockerVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(getString(R.string.app_name) + " is active")
            .setContentText("Blocking internet for selected apps")
            .setContentIntent(open)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(Notification.PRIORITY_MIN)
            .setVisibility(Notification.VISIBILITY_SECRET)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_stat), "Stop", stop
                ).build()
            )
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val TAG = "BlockerVpn"
        const val ACTION_START = "com.classic.myappblocker.action.START"
        const val ACTION_REBUILD = "com.classic.myappblocker.action.REBUILD"
        const val ACTION_STOP = "com.classic.myappblocker.action.STOP"
        private const val CHANNEL_ID = "firewall"
        private const val NOTIFICATION_ID = 1

        var isRunning: Boolean = false
    }
}
