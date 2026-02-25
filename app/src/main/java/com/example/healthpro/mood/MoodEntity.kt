package com.example.healthpro.mood

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Mood types for the daily emotional check-in.
 */
enum class MoodType {
    GOOD,       // 🙂
    OKAY,       // 😐
    NOT_GOOD,   // 🙁
    UNWELL      // 🤒
}

/**
 * Room Entity for storing daily mood entries.
 *
 * Stored locally only — no cloud, no analytics.
 */
@Entity(tableName = "mood_entries")
data class MoodEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val moodType: String,       // MoodType enum name stored as String
    val timestamp: Long = System.currentTimeMillis()
)
