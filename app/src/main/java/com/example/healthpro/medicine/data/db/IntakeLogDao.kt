package com.example.healthpro.medicine.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface IntakeLogDao {
    @Query("SELECT * FROM intake_log WHERE medicineId = :medicineId AND date = :date LIMIT 1")
    suspend fun getLog(medicineId: Long, date: Long): IntakeLogEntity?

    @Query("SELECT * FROM intake_log WHERE medicineId = :medicineId AND taken = 1")
    suspend fun getTakenCountForMedicine(medicineId: Long): List<IntakeLogEntity>

    @Query("SELECT COUNT(*) FROM intake_log WHERE medicineId = :medicineId AND taken = 1")
    suspend fun getTotalTakenCount(medicineId: Long): Int

    @Query("SELECT COUNT(*) FROM intake_log WHERE medicineId = :medicineId AND taken = 1")
    fun getTotalTakenCountFlow(medicineId: Long): Flow<Int>

    @Query("SELECT * FROM intake_log WHERE medicineId = :medicineId AND date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    fun getLogsForMedicineInRange(
        medicineId: Long,
        startDate: Long,
        endDate: Long
    ): Flow<List<IntakeLogEntity>>

    @Query("SELECT * FROM intake_log WHERE date = :date")
    suspend fun getLogsForDate(date: Long): List<IntakeLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: IntakeLogEntity): Long

    @Update
    suspend fun updateLog(log: IntakeLogEntity)

    @Query("DELETE FROM intake_log WHERE medicineId = :medicineId")
    suspend fun deleteLogsForMedicine(medicineId: Long)
}
