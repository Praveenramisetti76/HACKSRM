package com.example.healthpro.medicine.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicineManagerDao {
    @Query("SELECT * FROM medicine_manager ORDER BY name ASC")
    fun getAllMedicines(): Flow<List<MedicineManagerEntity>>

    @Query("SELECT * FROM medicine_manager ORDER BY name ASC")
    suspend fun getAllMedicinesList(): List<MedicineManagerEntity>

    @Query("SELECT * FROM medicine_manager WHERE id = :id")
    suspend fun getMedicineById(id: Long): MedicineManagerEntity?

    @Query("SELECT * FROM medicine_manager WHERE id = :id")
    fun getMedicineByIdFlow(id: Long): Flow<MedicineManagerEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedicine(medicine: MedicineManagerEntity): Long

    @Update
    suspend fun updateMedicine(medicine: MedicineManagerEntity)

    @Delete
    suspend fun deleteMedicine(medicine: MedicineManagerEntity)

    @Query("DELETE FROM medicine_manager")
    suspend fun deleteAll()

    // ── Medicine Taken ──

    /**
     * Mark a medicine as taken today.
     * Sets isTakenToday = true and records the date.
     */
    @Query("UPDATE medicine_manager SET isTakenToday = 1, lastTakenDate = :todayMillis WHERE id = :medicineId")
    suspend fun markTakenToday(medicineId: Long, todayMillis: Long)

    // ── Daily Reset ──

    /**
     * Reset all medicines' taken state at end of day (11:59 PM).
     * Does NOT delete medicines, only clears the daily flag.
     */
    @Query("UPDATE medicine_manager SET isTakenToday = 0")
    suspend fun resetAllDailyState()

    // ── Stock Queries ──

    /**
     * Check if medicine name already exists (case-insensitive) to avoid duplicates.
     */
    @Query("SELECT * FROM medicine_manager WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getMedicineByName(name: String): MedicineManagerEntity?
}
