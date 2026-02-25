package com.example.healthpro.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for FallEventEntity.
 *
 * Used by FallDetectionService to log events and
 * by BleDevicePairingScreen to show fall history.
 */
@Dao
interface FallEventDao {

    /**
     * Insert a new fall event record.
     * Called from a coroutine inside FallDetectionService or FallAlertScreen.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: FallEventEntity)

    /**
     * Stream the 50 most recent fall events, newest first.
     * Observed as Flow so the UI updates automatically.
     */
    @Query("SELECT * FROM fall_events ORDER BY timestamp DESC LIMIT 50")
    fun getRecentEvents(): Flow<List<FallEventEntity>>

    /** Total number of confirmed falls (SOS was sent). */
    @Query("SELECT COUNT(*) FROM fall_events WHERE wasConfirmedFall = 1")
    suspend fun confirmedFallCount(): Int

    /** Total number of false alarms (user tapped "I'm Okay"). */
    @Query("SELECT COUNT(*) FROM fall_events WHERE wasConfirmedFall = 0")
    suspend fun falseAlarmCount(): Int
}
