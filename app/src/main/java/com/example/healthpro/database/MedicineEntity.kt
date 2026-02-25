package com.example.healthpro.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a medicine extracted from a medical report.
 *
 * Used for:
 *   - Medicine dashboard display
 *   - Stock tracking (tablets remaining)
 *   - Refill reminder calculation
 *   - Smart purchase deep-linking
 */
@Entity(tableName = "medicines")
data class MedicineEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val dosage: String,                    // e.g., "500mg"
    val frequency: String,                 // e.g., "Twice daily"
    val tabletsPerDay: Int = 1,
    val durationDays: Int = 30,
    val totalTablets: Int = 30,
    val tabletsRemaining: Int = 30,
    val morningDose: Boolean = false,
    val afternoonDose: Boolean = false,
    val nightDose: Boolean = false,
    val reportId: Long = 0,                // Link to the source report
    val addedTimestamp: Long = System.currentTimeMillis(),
    val lastTakenTimestamp: Long = 0
) {
    /**
     * Calculate remaining days of supply.
     */
    fun remainingDays(): Int {
        return if (tabletsPerDay > 0) tabletsRemaining / tabletsPerDay else 0
    }

    /**
     * Check if refill is needed (≤ 3 days remaining).
     */
    fun needsRefill(): Boolean = remainingDays() <= 3

    /**
     * Generate deep link search query for pharmacy apps.
     */
    fun searchQuery(): String = "$name $dosage"
}
