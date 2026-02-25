package com.example.healthpro.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.healthpro.database.FallEventDao
import com.example.healthpro.database.FallEventEntity
import com.example.healthpro.mood.MoodDao
import com.example.healthpro.mood.MoodEntity

/**
 * Main Room Database for SAHAY app.
 *
 * Tables:
 *   - medicines:       Extracted medicines from reports
 *   - mood_logs:       Daily mood check-ins
 */
@Database(
    entities = [
        MedicineEntity::class,
        MoodEntity::class,
        FallEventEntity::class          // ← Phase 2: BLE fall detection
    ],
    version = 4,
    exportSchema = false
)
abstract class SahayDatabase : RoomDatabase() {

    abstract fun medicineDao(): MedicineDao
    abstract fun moodDao(): MoodDao
    abstract fun fallEventDao(): FallEventDao

    companion object {
        @Volatile
        private var INSTANCE: SahayDatabase? = null

        fun getInstance(context: Context): SahayDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SahayDatabase::class.java,
                    "sahay_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
