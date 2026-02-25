package com.example.healthpro.medicine.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for a stored prescription image.
 * Part of the Prescription Vault feature.
 */
@Entity(tableName = "prescriptions")
data class PrescriptionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val imageUri: String,
    val dateAdded: Long = System.currentTimeMillis(),
    val analyzed: Boolean = false
)
