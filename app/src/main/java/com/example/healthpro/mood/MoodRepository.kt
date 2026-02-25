package com.example.healthpro.mood

import android.content.Context
import android.util.Log
import com.example.healthpro.database.SahayDatabase

/**
 * Repository for mood check-in data and pattern detection.
 *
 * Responsibilities:
 *  - Save mood entries to Room
 *  - Detect 3-consecutive-low-mood pattern (Rule 1)
 *  - Detect 5-low-in-7-days pattern (Rule 2)
 *  - Track escalation timestamps to prevent duplicate alerts
 *
 * All pattern checks run only AFTER a new mood is saved — no polling.
 */
class MoodRepository(context: Context) {

    companion object {
        private const val TAG = "MoodRepository"
        private const val PREFS_NAME = "mood_pattern_prefs"
        private const val KEY_LAST_3DAY_ALERT = "last_3day_alert_timestamp"
        private const val KEY_LAST_7DAY_ALERT = "last_7day_alert_timestamp"

        // Cooldown: don't re-alert within 24 hours for the 3-day rule
        private const val THREE_DAY_COOLDOWN_MS = 24 * 60 * 60 * 1000L

        // Cooldown: don't re-alert within 7 days for the weekly rule
        private const val SEVEN_DAY_COOLDOWN_MS = 7 * 24 * 60 * 60 * 1000L
    }

    private val moodDao: MoodDao = SahayDatabase.getInstance(context).moodDao()
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Save a new mood entry.
     *
     * @return the inserted row ID
     */
    suspend fun saveMood(moodType: MoodType): Long {
        val entity = MoodEntity(
            moodType = moodType.name,
            timestamp = System.currentTimeMillis()
        )
        val id = moodDao.insertMood(entity)
        Log.d(TAG, "✅ Mood saved: ${moodType.name} (id=$id)")
        return id
    }

    // ═══════════════════════════════════════════════════════════════
    // RULE 1: 3 Consecutive Low Moods
    // ═══════════════════════════════════════════════════════════════

    /**
     * Check if the last 3 mood entries are ALL low (NOT_GOOD or UNWELL).
     * Only triggers an alert if cooldown period has passed.
     *
     * @return true if 3 consecutive low moods detected AND alert should fire
     */
    suspend fun shouldAlertThreeConsecutiveLow(): Boolean {
        val last3 = moodDao.getLastMoods(3)

        if (last3.size < 3) {
            Log.d(TAG, "Less than 3 entries — Rule 1 not applicable")
            return false
        }

        val allLow = last3.all { isLowMood(it.moodType) }
        Log.d(TAG, "Rule 1 check: ${last3.map { it.moodType }} → allLow=$allLow")

        if (!allLow) return false

        // Check cooldown to prevent duplicate alerts
        val lastAlert = prefs.getLong(KEY_LAST_3DAY_ALERT, 0L)
        val now = System.currentTimeMillis()
        if (now - lastAlert < THREE_DAY_COOLDOWN_MS) {
            Log.d(TAG, "Rule 1: cooldown active, skipping alert")
            return false
        }

        // Record this alert timestamp
        prefs.edit().putLong(KEY_LAST_3DAY_ALERT, now).apply()
        return true
    }

    // ═══════════════════════════════════════════════════════════════
    // RULE 2: 5 Low Moods in Last 7 Entries
    // ═══════════════════════════════════════════════════════════════

    /**
     * Check if 5 or more of the last 7 mood entries are low.
     * Only triggers an alert once per 7-day cycle.
     *
     * @return true if 5+ low moods in last 7 entries AND alert should fire
     */
    suspend fun shouldAlertFiveInSevenLow(): Boolean {
        val last7 = moodDao.getLastMoods(7)

        if (last7.size < 5) {
            Log.d(TAG, "Less than 5 entries — Rule 2 not applicable")
            return false
        }

        val lowCount = last7.count { isLowMood(it.moodType) }
        Log.d(TAG, "Rule 2 check: ${last7.map { it.moodType }} → lowCount=$lowCount")

        if (lowCount < 5) return false

        // Check 7-day cooldown to prevent notification spam
        val lastAlert = prefs.getLong(KEY_LAST_7DAY_ALERT, 0L)
        val now = System.currentTimeMillis()
        if (now - lastAlert < SEVEN_DAY_COOLDOWN_MS) {
            Log.d(TAG, "Rule 2: cooldown active (7-day cycle), skipping alert")
            return false
        }

        // Record this alert timestamp
        prefs.edit().putLong(KEY_LAST_7DAY_ALERT, now).apply()
        return true
    }

    // ═══════════════════════════════════════════════════════════════
    // HELPER
    // ═══════════════════════════════════════════════════════════════

    /**
     * A mood is considered "low" if it's NOT_GOOD or UNWELL.
     */
    private fun isLowMood(moodType: String): Boolean =
        moodType == MoodType.NOT_GOOD.name || moodType == MoodType.UNWELL.name
}
