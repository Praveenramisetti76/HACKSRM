package com.example.healthpro.medicine.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.healthpro.medicine.data.db.MedicineManagerDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WorkManager worker: runs daily at ~11:59 PM to reset all medicines'
 * isTakenToday flag. Does NOT delete medicines or intake logs.
 *
 * Survives reboot via WorkManager's persistence.
 * Uses KEEP policy to avoid duplicate scheduling.
 */
class DailyResetWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "DailyResetWorker"
        const val WORK_NAME = "medicine_daily_reset"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            val db = MedicineManagerDatabase.getInstance(applicationContext)
            db.medicineManagerDao().resetAllDailyState()
            Log.d(TAG, "✅ Daily reset completed: all medicines set to pending")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Daily reset failed: ${e.message}")
            Result.retry()
        }
    }
}
