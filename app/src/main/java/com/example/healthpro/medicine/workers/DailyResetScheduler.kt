package com.example.healthpro.medicine.workers

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Schedules the DailyResetWorker to run every 24 hours,
 * starting from the next 11:59 PM.
 *
 * Uses PeriodicWorkRequest with an initial delay calculated from now → 11:59 PM.
 * KEEP policy ensures no duplicate workers.
 * Survives app restart and device reboot automatically (WorkManager persistence).
 */
object DailyResetScheduler {

    private const val TAG = "DailyResetScheduler"

    fun schedule(context: Context) {
        val initialDelay = calculateDelayToMidnight()
        Log.d(TAG, "Scheduling daily reset in ${initialDelay / 60_000} minutes")

        val work = PeriodicWorkRequestBuilder<DailyResetWorker>(
            24, TimeUnit.HOURS
        )
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DailyResetWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            work
        )
    }

    /**
     * Calculate milliseconds from now until next 11:59 PM.
     * If it's already past 11:59 PM today, schedule for tomorrow.
     */
    private fun calculateDelayToMidnight(): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // If target is in the past, push to tomorrow
        if (target.before(now)) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }

        return target.timeInMillis - now.timeInMillis
    }
}
