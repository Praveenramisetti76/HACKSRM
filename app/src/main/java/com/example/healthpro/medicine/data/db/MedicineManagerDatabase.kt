package com.example.healthpro.medicine.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room Database for Medicine Manager module.
 * Isolated from main SahayDatabase to avoid affecting other modules.
 *
 * Tables:
 *   - prescriptions:    Stored prescription images
 *   - medicine_manager: Medicines with dosage, frequency, reminder times
 *   - intake_log:       Daily intake tracking
 */
@Database(
    entities = [
        PrescriptionEntity::class,
        MedicineManagerEntity::class,
        IntakeLogEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class MedicineManagerDatabase : RoomDatabase() {

    abstract fun prescriptionDao(): PrescriptionDao
    abstract fun medicineManagerDao(): MedicineManagerDao
    abstract fun intakeLogDao(): IntakeLogDao

    companion object {
        private const val DB_NAME = "medicine_manager_db"

        @Volatile
        private var INSTANCE: MedicineManagerDatabase? = null

        fun getInstance(context: Context): MedicineManagerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MedicineManagerDatabase::class.java,
                    DB_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
