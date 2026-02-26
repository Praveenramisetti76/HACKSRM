package com.example.healthpro.mood

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Room DAO for mood entries.
 *
 * Operations:
 *  - Insert a new mood entry
 *  - Get last N entries (for consecutive-low and weekly-low pattern detection)
 */
@Dao
interface MoodDao {

    /**
     * Insert a new mood entry.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMood(mood: MoodEntity): Long

    /**
     * Get the last [limit] mood entries, ordered newest first.
     * Used for pattern detection rules:
     *   - limit=3 → 3-consecutive-low rule
     *   - limit=7 → 5-in-7-days rule
     */
    @Query("SELECT * FROM mood_entries ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getLastMoods(limit: Int): List<MoodEntity>

    /**
     * Get the single most recent mood entry.
     */
    @Query("SELECT * FROM mood_entries ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestMood(): MoodEntity?
}
