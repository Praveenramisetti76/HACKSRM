package com.example.healthpro.safety

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Manages the inactivity detection logic.
 *
 * Tracks:
 *  - Last screen touch timestamp
 *  - Last motion sensor timestamp
 *
 * Triggers [onInactivityDetected] when BOTH conditions are met:
 *  1. No touch activity for >= threshold
 *  2. No motion activity for >= threshold
 *
 * Uses a periodic check (every 60 seconds) instead of constant monitoring.
 *
 * Thread Safety: All checks run on the main thread via Handler.
 */
class InactivityManager(
    private val context: Context,
    private val preferences: SafetyPreferences,
    private val onInactivityDetected: () -> Unit
) {

    companion object {
        private const val TAG = "InactivityManager"

        // Minimum check interval (5 seconds for short thresholds)
        private const val MIN_CHECK_INTERVAL_MS = 5_000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private var isInactivityAlertActive = false

    /**
     * Dynamic check interval: threshold / 3, but at least 5 seconds.
     * E.g. 10s threshold → check every 5s. 30min threshold → check every 10min.
     */
    private fun getCheckInterval(): Long {
        val thirdOfThreshold = preferences.inactivityThresholdMs / 3
        return thirdOfThreshold.coerceAtLeast(MIN_CHECK_INTERVAL_MS)
    }

    private val checkRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            performInactivityCheck()
            handler.postDelayed(this, getCheckInterval())
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════

    /**
     * Start periodic inactivity checks.
     * Records initial activity timestamp.
     */
    fun start() {
        if (isRunning) return
        isRunning = true
        isInactivityAlertActive = false

        // Initialize timestamps to now
        preferences.recordActivity()

        // Start checking
        handler.postDelayed(checkRunnable, getCheckInterval())
        Log.d(TAG, "✅ Inactivity monitoring started (threshold=${preferences.thresholdDisplayText()}, check every ${getCheckInterval() / 1000}s)")
    }

    /**
     * Stop all inactivity checks. Safe to call multiple times.
     */
    fun stop() {
        isRunning = false
        isInactivityAlertActive = false
        handler.removeCallbacks(checkRunnable)
        Log.d(TAG, "🛑 Inactivity monitoring stopped")
    }

    /**
     * @return true if monitoring is active
     */
    fun isActive(): Boolean = isRunning

    // ═══════════════════════════════════════════════════════════════
    // ACTIVITY RECORDING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Call when screen touch is detected.
     */
    fun onTouchDetected() {
        preferences.recordTouch()
        resetInactivityAlert()
    }

    /**
     * Call when motion sensor detects movement.
     */
    fun onMotionDetected() {
        preferences.recordMotion()
        resetInactivityAlert()
    }

    /**
     * Reset the inactivity alert state (e.g., user confirmed they're OK).
     */
    fun resetInactivityAlert() {
        isInactivityAlertActive = false
        preferences.recordActivity()
    }

    // ═══════════════════════════════════════════════════════════════
    // INACTIVITY CHECK
    // ═══════════════════════════════════════════════════════════════

    private fun performInactivityCheck() {
        // ─── Sleep Hours Gate ───
        // If within configured sleep window, skip ALL inactivity detection.
        // Service keeps running (avoids restart bugs), just no triggers.
        if (preferences.isSleepHoursActive()) {
            return
        }

        if (isInactivityAlertActive) {
            // Already triggered, don't re-trigger until reset
            return
        }

        val now = System.currentTimeMillis()
        val threshold = preferences.inactivityThresholdMs
        val timeSinceTouch = now - preferences.lastTouchTimestamp
        val timeSinceMotion = now - preferences.lastMotionTimestamp

        val noTouch = timeSinceTouch >= threshold
        val noMotion = timeSinceMotion >= threshold

        Log.d(TAG, "Check — Touch: ${timeSinceTouch / 1000}s ago, Motion: ${timeSinceMotion / 1000}s ago, Threshold: ${threshold / 1000}s")

        if (noTouch && noMotion) {
            Log.w(TAG, "🚨 INACTIVITY DETECTED — no touch for ${timeSinceTouch / 1000}s, no motion for ${timeSinceMotion / 1000}s")
            isInactivityAlertActive = true

            // Log to timeline
            preferences.addTimelineEvent(
                SafetyTimelineEvent(
                    type = "inactivity_detected",
                    description = "No activity detected for ${preferences.thresholdDisplayText()}"
                )
            )

            onInactivityDetected()
        }
    }

    /**
     * Get human-readable time since last activity.
     * Used by UI to show "Last activity: 5 min ago"
     */
    fun getTimeSinceLastActivity(): String {
        val elapsed = System.currentTimeMillis() - preferences.lastActivityTimestamp
        val seconds = elapsed / 1_000
        return when {
            seconds < 10 -> "Just now"
            seconds < 60 -> "${seconds}s ago"
            seconds < 3600 -> "${seconds / 60} min ago"
            else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m ago"
        }
    }
}
