package com.example.healthpro.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Entity — persists every FALL_DETECTED BLE event to local storage.
 *
 * Logged in two scenarios:
 *   1. User taps "I'm Okay"  → wasConfirmedFall = false
 *   2. 45-second timer expires → wasConfirmedFall = true, SOS was sent
 *
 * Table: fall_events
 */
@Entity(tableName = "fall_events")
data class FallEventEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Unix epoch millis when FALL_DETECTED message arrived. */
    val timestamp: Long = System.currentTimeMillis(),

    /**
     * true  = countdown reached zero, SOS was dispatched.
     * false = user pressed "I'm Okay" before timeout.
     */
    val wasConfirmedFall: Boolean,

    /**
     * Seconds between alert appearing and user action.
     * Range: 0–45. Equals 45 if SOS was triggered automatically.
     */
    val responseTimeSeconds: Int
)
