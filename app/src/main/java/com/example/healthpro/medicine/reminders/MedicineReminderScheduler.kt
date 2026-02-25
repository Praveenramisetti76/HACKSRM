package com.example.healthpro.medicine.reminders

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.healthpro.medicine.data.db.MedicineManagerEntity
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Schedules medicine reminders using WorkManager.
 * Each medicine gets one-time scheduled workers for each reminder time daily.
 */
object MedicineReminderScheduler {

    private const val TAG = "MedicineReminderScheduler"

    /**
     * Schedule reminders for a single medicine based on its reminder times.
     * Cancels any existing reminders for this medicine first.
     */
    fun scheduleForMedicine(context: Context, medicine: MedicineManagerEntity) {
        val workManager = WorkManager.getInstance(context)
        val times = medicine.getReminderTimesList()

        if (times.isEmpty()) {
            Log.d(TAG, "No reminder times for ${medicine.name}, skipping")
            return
        }

        // Cancel existing reminders for this medicine
        cancelForMedicine(context, medicine.id)

        val now = Calendar.getInstance()

        times.forEachIndexed { index, timeStr ->
            try {
                val parts = timeStr.split(":")
                if (parts.size != 2) return@forEachIndexed

                val hour = parts[0].toIntOrNull() ?: return@forEachIndexed
                val minute = parts[1].toIntOrNull() ?: return@forEachIndexed

                val target = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                // If time already passed today, schedule for tomorrow
                if (target.before(now)) {
                    target.add(Calendar.DAY_OF_YEAR, 1)
                }

                val delayMs = target.timeInMillis - now.timeInMillis

                val data = Data.Builder()
                    .putString("medicine_name", medicine.name)
                    .putLong("medicine_id", medicine.id)
                    .putString("dosage", medicine.dosage)
                    .build()

                // Use PeriodicWorkRequest with 24-hour interval
                val workRequest = PeriodicWorkRequestBuilder<MedicineReminderWorker>(
                    24, TimeUnit.HOURS
                )
                    .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                    .setInputData(data)
                    .addTag(getTag(medicine.id))
                    .build()

                val uniqueName = "medicine_reminder_${medicine.id}_$index"
                workManager.enqueueUniquePeriodicWork(
                    uniqueName,
                    ExistingPeriodicWorkPolicy.REPLACE,
                    workRequest
                )

                Log.d(TAG, "Scheduled reminder for ${medicine.name} at $timeStr (delay: ${delayMs / 60000}min)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to schedule reminder at $timeStr: ${e.message}")
            }
        }
    }

    /**
     * Cancel all reminders for a specific medicine.
     */
    fun cancelForMedicine(context: Context, medicineId: Long) {
        WorkManager.getInstance(context).cancelAllWorkByTag(getTag(medicineId))
        Log.d(TAG, "Cancelled reminders for medicine $medicineId")
    }

    private fun getTag(medicineId: Long): String = "medicine_reminder_$medicineId"
}
