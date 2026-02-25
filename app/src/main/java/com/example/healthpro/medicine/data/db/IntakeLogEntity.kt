package com.example.healthpro.medicine.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for daily intake tracking.
 * Schema per spec: id, medicineId, date, taken (Boolean)
 */
@Entity(
    tableName = "intake_log",
    foreignKeys = [
        ForeignKey(
            entity = MedicineManagerEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicineId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("medicineId"), Index("date")]
)
data class IntakeLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val medicineId: Long,
    val date: Long,
    val taken: Boolean
)
