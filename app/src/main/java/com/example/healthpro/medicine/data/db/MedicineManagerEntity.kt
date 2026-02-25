package com.example.healthpro.medicine.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for Medicine Manager module.
 * Tracks medicines with dosage, frequency, reminder times, prescription link,
 * and daily taken state.
 *
 * isTakenToday: Resets at 11:59 PM daily via DailyResetWorker.
 * lastTakenDate: Epoch millis of the day the medicine was last taken.
 */
@Entity(tableName = "medicine_manager")
data class MedicineManagerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val prescriptionId: Long = 0,
    val name: String,
    val dosage: String,
    val frequencyPerDay: Int,
    val totalQuantity: Int,
    val startDate: Long,
    val reorderThreshold: Int,
    val durationDays: Int = 30,
    val reminderTimes: String = "",   // Comma-separated HH:mm values, e.g. "08:00,14:00,21:00"
    val isTakenToday: Boolean = false,
    val lastTakenDate: Long = 0L      // Epoch millis (start of day)
) {
    /**
     * Calculate remaining quantity based on intake logs.
     * remaining = totalQuantity - totalTaken
     */
    fun remainingQuantity(totalTaken: Int): Int =
        (totalQuantity - totalTaken).coerceAtLeast(0)

    /**
     * Approximate days of supply remaining.
     */
    fun remainingDays(totalTaken: Int): Int {
        val remaining = remainingQuantity(totalTaken)
        return if (frequencyPerDay > 0) remaining / frequencyPerDay else 0
    }

    /**
     * Check if reorder reminder should trigger (days remaining <= threshold).
     */
    fun needsReorder(totalTaken: Int): Boolean =
        remainingDays(totalTaken) <= reorderThreshold

    /**
     * Parse reminder times from the comma-separated string.
     * Returns list of "HH:mm" strings.
     */
    fun getReminderTimesList(): List<String> =
        if (reminderTimes.isBlank()) emptyList()
        else reminderTimes.split(",").map { it.trim() }.filter { it.isNotBlank() }
}
