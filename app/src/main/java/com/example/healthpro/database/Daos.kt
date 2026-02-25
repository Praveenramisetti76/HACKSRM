package com.example.healthpro.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicineDao {
    @Query("SELECT * FROM medicines ORDER BY addedTimestamp DESC")
    fun getAllMedicines(): Flow<List<MedicineEntity>>

    @Query("SELECT * FROM medicines ORDER BY addedTimestamp DESC")
    suspend fun getAllMedicinesList(): List<MedicineEntity>

    @Query("SELECT * FROM medicines WHERE id = :id")
    suspend fun getMedicineById(id: Long): MedicineEntity?

    @Query("SELECT * FROM medicines WHERE reportId = :reportId")
    suspend fun getMedicinesByReport(reportId: Long): List<MedicineEntity>

    @Query("SELECT * FROM medicines WHERE tabletsRemaining / tabletsPerDay <= 3 AND tabletsPerDay > 0")
    suspend fun getMedicinesNeedingRefill(): List<MedicineEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedicine(medicine: MedicineEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(medicines: List<MedicineEntity>)

    @Update
    suspend fun updateMedicine(medicine: MedicineEntity)

    @Query("UPDATE medicines SET tabletsRemaining = tabletsRemaining - :count, lastTakenTimestamp = :timestamp WHERE id = :id AND tabletsRemaining >= :count")
    suspend fun takeMedicine(id: Long, count: Int = 1, timestamp: Long = System.currentTimeMillis())

    @Delete
    suspend fun deleteMedicine(medicine: MedicineEntity)

    @Query("DELETE FROM medicines")
    suspend fun deleteAll()
}
