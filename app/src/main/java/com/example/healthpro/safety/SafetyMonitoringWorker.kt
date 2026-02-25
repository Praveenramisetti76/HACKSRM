package com.example.healthpro.safety

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * WorkManager fallback for safety monitoring reliability.
 *
 * Purpose:
 *  - If the foreground service is killed by the system (battery saver, etc.),
 *    this periodic worker will restart it.
 *  - Runs every 15 minutes (minimum WorkManager interval).
 *
 * This is a safety net, NOT the primary monitoring mechanism.
 */
class SafetyMonitoringWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "SafetyMonitorWorker"
        private const val WORK_NAME = "sahay_safety_monitoring_watchdog"

        /**
         * Schedule the periodic watchdog.
         * Safe to call multiple times — uses KEEP policy.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SafetyMonitoringWorker>(
                15, TimeUnit.MINUTES  // Minimum interval for WorkManager
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.d(TAG, "✅ Watchdog worker scheduled")
        }

        /**
         * Cancel the periodic watchdog.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "🛑 Watchdog worker cancelled")
        }
    }

    override suspend fun doWork(): Result {
        val preferences = SafetyPreferences(applicationContext)

        if (preferences.isMonitoringEnabled && !preferences.isServiceRunning) {
            Log.w(TAG, "⚠️ Monitoring enabled but service not running — restarting!")
            SafetyMonitoringService.start(applicationContext)
        }

        return Result.success()
    }
}
