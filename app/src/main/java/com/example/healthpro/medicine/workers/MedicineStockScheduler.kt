package com.example.healthpro.medicine.workers

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules MedicineStockWorker to run once daily.
 */
object MedicineStockScheduler {

    fun schedule(context: Context) {
        val work = PeriodicWorkRequestBuilder<MedicineStockWorker>(
            24, TimeUnit.HOURS
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            MedicineStockWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            work
        )
    }
}
