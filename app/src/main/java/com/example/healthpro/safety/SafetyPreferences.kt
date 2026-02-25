package com.example.healthpro.safety

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Calendar

/**
 * Persistent storage for Safety Monitoring preferences.
 *
 * Manages:
 *  - Enable/disable monitoring
 *  - Inactivity threshold (seconds, range: 10s to 7200s / 120min)
 *  - Voice confirmation toggle
 *  - Last activity timestamps
 *  - Safety timeline event log
 */
class SafetyPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("sahay_safety", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_MONITORING_ENABLED = "monitoring_enabled"
        private const val KEY_INACTIVITY_THRESHOLD_SECONDS = "inactivity_threshold_sec"
        private const val KEY_VOICE_CONFIRMATION_ENABLED = "voice_confirmation_enabled"
        private const val KEY_LAST_TOUCH_TIMESTAMP = "last_touch_timestamp"
        private const val KEY_LAST_MOTION_TIMESTAMP = "last_motion_timestamp"
        private const val KEY_LAST_ACTIVITY_TIMESTAMP = "last_activity_timestamp"
        private const val KEY_SAFETY_TIMELINE = "safety_timeline"
        private const val KEY_SERVICE_RUNNING = "service_running"
        private const val KEY_SLEEP_MODE_ENABLED = "sleep_mode_enabled"
        private const val KEY_SLEEP_START_TIME = "sleep_start_time"
        private const val KEY_SLEEP_END_TIME = "sleep_end_time"

        const val DEFAULT_THRESHOLD_SECONDS = 30     // 30 seconds default
        const val MIN_THRESHOLD_SECONDS = 10          // 10 seconds minimum
        const val MAX_THRESHOLD_SECONDS = 7200        // 120 minutes maximum

        const val DEFAULT_SLEEP_START = "22:00"
        const val DEFAULT_SLEEP_END = "06:00"
    }

    // ═══════════════════════════════════════════════════════════════
    // MONITORING TOGGLE
    // ═══════════════════════════════════════════════════════════════

    var isMonitoringEnabled: Boolean
        get() = prefs.getBoolean(KEY_MONITORING_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_MONITORING_ENABLED, value).apply()

    // ═══════════════════════════════════════════════════════════════
    // INACTIVITY THRESHOLD (stored in seconds)
    // ═══════════════════════════════════════════════════════════════

    /** Threshold in seconds (10s – 7200s / 120min) */
    var inactivityThresholdSeconds: Int
        get() = prefs.getInt(KEY_INACTIVITY_THRESHOLD_SECONDS, DEFAULT_THRESHOLD_SECONDS)
        set(value) {
            val clamped = value.coerceIn(MIN_THRESHOLD_SECONDS, MAX_THRESHOLD_SECONDS)
            prefs.edit().putInt(KEY_INACTIVITY_THRESHOLD_SECONDS, clamped).apply()
        }

    /** Threshold converted to milliseconds for comparisons */
    val inactivityThresholdMs: Long
        get() = inactivityThresholdSeconds * 1_000L

    /** Human-readable display text for the threshold */
    fun thresholdDisplayText(): String {
        val sec = inactivityThresholdSeconds
        return when {
            sec < 60 -> "${sec}s"
            sec % 60 == 0 -> "${sec / 60} min"
            else -> "${sec / 60}m ${sec % 60}s"
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // VOICE CONFIRMATION TOGGLE
    // ═══════════════════════════════════════════════════════════════

    var isVoiceConfirmationEnabled: Boolean
        get() = prefs.getBoolean(KEY_VOICE_CONFIRMATION_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_VOICE_CONFIRMATION_ENABLED, value).apply()

    // ═══════════════════════════════════════════════════════════════
    // ACTIVITY TIMESTAMPS
    // ═══════════════════════════════════════════════════════════════

    var lastTouchTimestamp: Long
        get() = prefs.getLong(KEY_LAST_TOUCH_TIMESTAMP, System.currentTimeMillis())
        set(value) = prefs.edit().putLong(KEY_LAST_TOUCH_TIMESTAMP, value).apply()

    var lastMotionTimestamp: Long
        get() = prefs.getLong(KEY_LAST_MOTION_TIMESTAMP, System.currentTimeMillis())
        set(value) = prefs.edit().putLong(KEY_LAST_MOTION_TIMESTAMP, value).apply()

    var lastActivityTimestamp: Long
        get() = prefs.getLong(KEY_LAST_ACTIVITY_TIMESTAMP, System.currentTimeMillis())
        set(value) = prefs.edit().putLong(KEY_LAST_ACTIVITY_TIMESTAMP, value).apply()

    fun recordActivity() {
        val now = System.currentTimeMillis()
        lastTouchTimestamp = now
        lastMotionTimestamp = now
        lastActivityTimestamp = now
    }

    fun recordTouch() {
        val now = System.currentTimeMillis()
        lastTouchTimestamp = now
        lastActivityTimestamp = now
    }

    fun recordMotion() {
        val now = System.currentTimeMillis()
        lastMotionTimestamp = now
        lastActivityTimestamp = now
    }

    // ═══════════════════════════════════════════════════════════════
    // SERVICE STATE
    // ═══════════════════════════════════════════════════════════════

    var isServiceRunning: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_RUNNING, false)
        set(value) = prefs.edit().putBoolean(KEY_SERVICE_RUNNING, value).apply()

    // ═══════════════════════════════════════════════════════════════
    // SLEEP HOURS (Disable Monitoring)
    // ═══════════════════════════════════════════════════════════════

    var isSleepModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_SLEEP_MODE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SLEEP_MODE_ENABLED, value).apply()

    /** Sleep start time in HH:mm 24-hour format (e.g. "22:00") */
    var sleepStartTime: String
        get() = prefs.getString(KEY_SLEEP_START_TIME, DEFAULT_SLEEP_START) ?: DEFAULT_SLEEP_START
        set(value) = prefs.edit().putString(KEY_SLEEP_START_TIME, value).apply()

    /** Sleep end time in HH:mm 24-hour format (e.g. "06:00") */
    var sleepEndTime: String
        get() = prefs.getString(KEY_SLEEP_END_TIME, DEFAULT_SLEEP_END) ?: DEFAULT_SLEEP_END
        set(value) = prefs.edit().putString(KEY_SLEEP_END_TIME, value).apply()

    /**
     * Check if the current time falls within the configured sleep window.
     *
     * Handles midnight crossover correctly:
     *   Start=22:00, End=06:00 → active from 22:00 tonight through 06:00 tomorrow.
     *
     * @return true if monitoring should be SUPPRESSED right now.
     */
    fun isSleepHoursActive(): Boolean {
        if (!isSleepModeEnabled) return false

        return try {
            val startParts = sleepStartTime.split(":")
            val endParts = sleepEndTime.split(":")
            if (startParts.size != 2 || endParts.size != 2) return false

            val startHour = startParts[0].toInt()
            val startMin = startParts[1].toInt()
            val endHour = endParts[0].toInt()
            val endMin = endParts[1].toInt()

            val cal = Calendar.getInstance()
            val nowMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            val startMinutes = startHour * 60 + startMin
            val endMinutes = endHour * 60 + endMin

            val inRange = if (startMinutes <= endMinutes) {
                // Same-day range (e.g. 01:00 → 05:00)
                nowMinutes in startMinutes until endMinutes
            } else {
                // Crosses midnight (e.g. 22:00 → 06:00)
                nowMinutes >= startMinutes || nowMinutes < endMinutes
            }

            if (inRange) {
                Log.d("SafetyPrefs", "💤 Sleep hours active ($sleepStartTime → $sleepEndTime) — monitoring suppressed")
            }
            inRange
        } catch (e: Exception) {
            Log.e("SafetyPrefs", "Sleep hours parse error: ${e.message}")
            false  // Fail-safe: don't suppress monitoring if parse fails
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // SAFETY TIMELINE (Event Log)
    // ═══════════════════════════════════════════════════════════════

    fun addTimelineEvent(event: SafetyTimelineEvent) {
        val events = getTimelineEvents().toMutableList()
        events.add(0, event) // newest first
        // Keep only last 50 events
        val trimmed = if (events.size > 50) events.take(50) else events
        val json = Gson().toJson(trimmed)
        prefs.edit().putString(KEY_SAFETY_TIMELINE, json).apply()
    }

    fun getTimelineEvents(): List<SafetyTimelineEvent> {
        val json = prefs.getString(KEY_SAFETY_TIMELINE, "") ?: ""
        if (json.isBlank()) return emptyList()
        val type = object : TypeToken<List<SafetyTimelineEvent>>() {}.type
        return try {
            Gson().fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clearTimeline() {
        prefs.edit().remove(KEY_SAFETY_TIMELINE).apply()
    }
}

/**
 * Represents a single event in the Safety Timeline log.
 */
data class SafetyTimelineEvent(
    val timestamp: Long = System.currentTimeMillis(),
    val type: String,       // "inactivity_detected", "voice_check_ok", "voice_check_help", "sos_triggered", "monitoring_started", "monitoring_stopped"
    val description: String
)
