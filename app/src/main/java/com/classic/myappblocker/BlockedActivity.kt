package com.classic.myappblocker

import android.app.Activity
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.hardware.fingerprint.FingerprintManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.widget.TextView

/**
 * Full-screen cover shown over an app that is blocked from opening.
 * Shows only "App blocked" + the app name.
 * - Press back -> exit straight to the home screen.
 * - Bypass: touch the phone's fingerprint sensor (the one that unlocks the
 *   phone). When the print matches, the app opens for 60 s. The check is
 *   silent - no dialog ever appears - so to anyone else the screen keeps
 *   looking like a dead lock.
 * While the sensor is armed, screen taps are ignored so a finger pressed
 * on an in-display sensor is never mistaken for "tap -> go home".
 */
class BlockedActivity : Activity() {

    private var pkg = ""
    private var downAt = 0L

    // Silent fingerprint bypass bookkeeping.
    private var wantListening = false // the cover is visible - keep the sensor armed
    private var sessionActive = false  // a fingerprint session is running right now
    private var cancelSignal: CancellationSignal? = null
    private var errorRestarts = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blocked)
        overridePendingTransition(0, 0) // no enter animation - instant cover
        Stealth.secure(window)

        pkg = intent?.getStringExtra(EXTRA_PKG) ?: ""
        findViewById<TextView>(R.id.blocked_name).text = labelFor(pkg)
    }

    override fun onResume() {
        super.onResume()
        wantListening = true
        errorRestarts = 0
        startFingerprint()
    }

    override fun onPause() {
        super.onPause()
        wantListening = false
        stopFingerprint()
    }

    /** Arms the fingerprint listener. Silent - no prompt, no UI at all. */
    @Suppress("DEPRECATION")
    private fun startFingerprint() {
        if (!wantListening || sessionActive) return
        if (!fingerprintReady()) return
        try {
            val fm = getSystemService(FingerprintManager::class.java) ?: return
            val signal = CancellationSignal()
            cancelSignal = signal
            sessionActive = true
            fm.authenticate(null, signal, 0, fpCallback, Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
            // No usable fingerprint service here - the cover simply stays locked.
            sessionActive = false
            cancelSignal = null
        }
    }

    private fun stopFingerprint() {
        if (!sessionActive) return
        sessionActive = false
        val signal = cancelSignal
        cancelSignal = null
        try {
            signal?.cancel()
        } catch (e: Exception) {
            // ignore
        }
    }

    /** True when this phone has a fingerprint sensor with a print enrolled. */
    @Suppress("DEPRECATION")
    private fun fingerprintReady(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= 28) {
                val bm = getSystemService(BiometricManager::class.java) ?: return false
                val status = if (Build.VERSION.SDK_INT >= 30) {
                    bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                } else {
                    bm.canAuthenticate()
                }
                status == BiometricManager.BIOMETRIC_SUCCESS
            } else {
                val fm = getSystemService(FingerprintManager::class.java) ?: return false
                fm.isHardwareDetected && fm.hasEnrolledFingerprints()
            }
        } catch (e: Exception) {
            false
        }
    }

    @Suppress("DEPRECATION")
    private val fpCallback = object : FingerprintManager.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: FingerprintManager.AuthenticationResult?) {
            sessionActive = false
            bypass()
        }

        override fun onAuthenticationFailed() {
            // Wrong finger. The session stays alive, so only a tiny nudge so
            // the owner knows the sensor read them and can press again.
            window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
            sessionActive = false
            // 5 = cancelled (we do that when leaving), 10 = user cancelled - silent.
            if (errorCode != 5 && errorCode != 10) {
                window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
            // 7/9 = lockout, 11 = no print enrolled, 12 = no sensor:
            // re-arming right away would just fail again.
            if (errorCode == 7 || errorCode == 9 || errorCode == 11 || errorCode == 12) return
            // Transient hiccup (sensor busy, interrupted) - arm again while the
            // cover is visible, but never more than a few times in a row.
            if (!wantListening || errorRestarts >= MAX_RESTARTS) return
            errorRestarts++
            startFingerprint()
        }
    }

    /** The fingerprint matched: allow the app for 60 s and open it. */
    private fun bypass() {
        LaunchGuardService.allow(pkg, 60_000)
        val launch = packageManager.getLaunchIntentForPackage(pkg)
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launch)
        }
        finish()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // While the fingerprint sensor is armed, screen touches are ignored:
        // on in-display sensor phones the finger lands on the display itself
        // and must never be mistaken for a "tap -> go home".
        if (sessionActive) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> downAt = SystemClock.elapsedRealtime()
            // Short quick tap -> go home (only when no fingerprint is armed).
            MotionEvent.ACTION_UP ->
                if (SystemClock.elapsedRealtime() - downAt < 350) goHome()
        }
        return super.onTouchEvent(event)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        goHome()
    }

    private fun goHome() {
        try {
            startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            // ignore
        }
        finish()
        overridePendingTransition(0, 0) // no exit animation either
    }

    private fun labelFor(pkg: String): String {
        return try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(pkg, 0)
            ).toString()
        } catch (e: Exception) {
            pkg
        }
    }

    companion object {
        const val EXTRA_PKG = "pkg"
        private const val MAX_RESTARTS = 3
    }
}
