package com.example.healthpro.medicine.data.repository

import com.example.healthpro.medicine.data.db.IntakeLogDao
import com.example.healthpro.medicine.data.db.IntakeLogEntity
import com.example.healthpro.medicine.data.db.MedicineManagerDao
import com.example.healthpro.medicine.data.db.MedicineManagerEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.Calendar

/**
 * Repository for Medicine Manager module.
 * Handles medicines and intake logs with stock calculation.
 */
class MedicineManagerRepository(
    private val medicineDao: MedicineManagerDao,
    private val intakeLogDao: IntakeLogDao
) {

    fun getAllMedicinesWithStock(): Flow<List<MedicineWithStock>> =
        medicineDao.getAllMedicines().map { medicines ->
            medicines.map { med ->
                val taken = intakeLogDao.getTotalTakenCount(med.id)
                MedicineWithStock(
                    medicine = med,
                    totalTaken = taken,
                    remaining = med.remainingQuantity(taken),
                    needsReorder = med.needsReorder(taken)
                )
            }
        }

    fun getMedicineWithStock(id: Long): Flow<MedicineWithStock?> =
        kotlinx.coroutines.flow.combine(
            medicineDao.getMedicineByIdFlow(id),
            intakeLogDao.getTotalTakenCountFlow(id)
        ) { med, taken ->
            med?.let {
                MedicineWithStock(
                    medicine = it,
                    totalTaken = taken,
                    remaining = it.remainingQuantity(taken),
                    needsReorder = it.needsReorder(taken)
                )
            }
        }

    suspend fun getAllMedicines(): List<MedicineManagerEntity> =
        medicineDao.getAllMedicinesList()

    suspend fun getMedicineById(id: Long): MedicineManagerEntity? =
        medicineDao.getMedicineById(id)

    suspend fun insertMedicine(medicine: MedicineManagerEntity): Long =
        medicineDao.insertMedicine(medicine)

    suspend fun updateMedicine(medicine: MedicineManagerEntity) {
        medicineDao.updateMedicine(medicine)
    }

    suspend fun deleteMedicine(medicine: MedicineManagerEntity) {
        intakeLogDao.deleteLogsForMedicine(medicine.id)
        medicineDao.deleteMedicine(medicine)
    }

    suspend fun markTaken(medicineId: Long) {
        markTaken(medicineId, getTodayStartMillis())
    }

    suspend fun markTaken(medicineId: Long, date: Long) {
        // 1. Update the entity's isTakenToday flag
        medicineDao.markTakenToday(medicineId, date)

        // 2. Update/insert intake log for stock tracking
        val existing = intakeLogDao.getLog(medicineId, date)
        if (existing != null) {
            intakeLogDao.updateLog(existing.copy(taken = true))
        } else {
            intakeLogDao.insertLog(
                IntakeLogEntity(medicineId = medicineId, date = date, taken = true)
            )
        }
    }

    suspend fun markNotTaken(medicineId: Long, date: Long) {
        val existing = intakeLogDao.getLog(medicineId, date)
        if (existing != null) {
            intakeLogDao.updateLog(existing.copy(taken = false))
        }
    }

    suspend fun getTotalTaken(medicineId: Long): Int =
        intakeLogDao.getTotalTakenCount(medicineId)

    suspend fun getMedicinesNeedingReorder(): List<MedicineManagerEntity> {
        val medicines = medicineDao.getAllMedicinesList()
        return medicines.filter { med ->
            val taken = intakeLogDao.getTotalTakenCount(med.id)
            med.needsReorder(taken)
        }
    }

    suspend fun isTakenToday(medicineId: Long): Boolean {
        val today = getTodayStartMillis()
        val log = intakeLogDao.getLog(medicineId, today)
        return log?.taken == true
    }

    fun getTodayStartMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    data class MedicineWithStock(
        val medicine: MedicineManagerEntity,
        val totalTaken: Int,
        val remaining: Int,
        val needsReorder: Boolean
    )
}
