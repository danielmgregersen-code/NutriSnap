package com.danielag_nutritrack.app.utils

import android.content.Context
import android.os.PowerManager
import android.util.Log

private const val TAG = "AnalysisWakeLock"
private const val WAKE_LOCK_TAG = "NutriTrack:analysis"

/** Safety net: no real analysis runs this long, but a hung one must not drain the battery. */
private const val MAX_HOLD_MILLIS = 10 * 60 * 1000L

/**
 * Runs [block] with a partial wake lock held.
 *
 * An analysis can take minutes. Keeping the screen on stops it timing out on its own, but
 * if the power button is pressed — or the app leaves the foreground — the CPU can suspend
 * and the connection is dropped, losing an answer OpenAI has already produced. A partial
 * wake lock keeps the CPU running until the request finishes.
 *
 * The lock is a best effort: if it cannot be acquired, [block] still runs.
 */
suspend fun <T> withAnalysisWakeLock(context: Context, block: suspend () -> T): T {
    val wakeLock = try {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)?.also {
            it.acquire(MAX_HOLD_MILLIS)
        }
    } catch (e: Exception) {
        Log.w(TAG, "Could not acquire wake lock, continuing without it: ${e.message}")
        null
    }

    return try {
        block()
    } finally {
        // A lock that hit its timeout has already released itself; releasing it again throws.
        if (wakeLock != null && wakeLock.isHeld) {
            try {
                wakeLock.release()
            } catch (e: Exception) {
                Log.w(TAG, "Could not release wake lock: ${e.message}")
            }
        }
    }
}
