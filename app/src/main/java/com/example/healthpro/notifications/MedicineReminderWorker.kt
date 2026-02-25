package com.example.healthpro.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.example.healthpro.database.SahayDatabase
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

/**
 * Medicine Reminder Worker — Fires notifications for medicine doses.
 *
 * Runs periodically via WorkManager to check which medicines need
 * reminders (morning/afternoon/night) and if any need refilling.
 */
class MedicineReminderWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    companion object {
        private const val TAG = "MedicineReminder"
        private const val CHANNEL_ID = "sahay_medicine_reminders"
        private const val CHANNEL_NAME = "Medicine Reminders"
        private const val REFILL_CHANNEL_ID = "sahay_refill_alerts"
        private const val REFILL_CHANNEL_NAME = "Refill Alerts"

        const val WORK_NAME_MORNING = "medicine_reminder_morning"
        const val WORK_NAME_AFTERNOON = "medicine_reminder_afternoon"
        const val WORK_NAME_NIGHT = "medicine_reminder_night"
        const val WORK_NAME_REFILL = "medicine_refill_check"

        const val KEY_TIME_SLOT = "time_slot"
        const val TIME_MORNING = "morning"
        const val TIME_AFTERNOON = "afternoon"
        const val TIME_NIGHT = "night"
    }

    override fun doWork(): Result {
        val timeSlot = inputData.getString(KEY_TIME_SLOT) ?: TIME_MORNING

        return try {
            createNotificationChannels()

            val db = SahayDatabase.getInstance(context)
            val medicines = runBlocking { db.medicineDao().getAllMedicinesList() }

            // Filter medicines for this time slot
            val dueNow = medicines.filter { med ->
                when (timeSlot) {
                    TIME_MORNING -> med.morningDose
                    TIME_AFTERNOON -> med.afternoonDose
                    TIME_NIGHT -> med.nightDose
                    else -> false
                }
            }

            // Send dose reminder
            if (dueNow.isNotEmpty()) {
                val names = dueNow.joinToString(", ") { "${it.name} ${it.dosage}" }
                sendNotification(
                    id = timeSlot.hashCode(),
                    title = "💊 Time for your medicine",
                    body = "$timeSlot dose: $names",
                    channelId = CHANNEL_ID
                )
            }

            // Check for refills needed
            val needRefill = medicines.filter { it.needsRefill() }
            if (needRefill.isNotEmpty()) {
                needRefill.forEach { med ->
                    sendNotification(
                        id = (med.id + 1000).toInt(),
                        title = "🔔 Refill Reminder",
                        body = "Your medicine ${med.name} is about to finish. Order now.",
                        channelId = REFILL_CHANNEL_ID
                    )
                }
            }

            Log.d(TAG, "Reminder check complete: $timeSlot, due=${dueNow.size}, refill=${needRefill.size}")
            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Reminder worker failed: ${e.message}")
            Result.retry()
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val medicineChannel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders to take your medicine"
                enableVibration(true)
            }

            val refillChannel = NotificationChannel(
                REFILL_CHANNEL_ID, REFILL_CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when medicine stock is running low"
                enableVibration(true)
            }

            manager.createNotificationChannel(medicineChannel)
            manager.createNotificationChannel(refillChannel)
        }
    }

    private fun sendNotification(id: Int, title: String, body: String, channelId: String) {
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(id, notification)
    }
}

/**
 * ReminderScheduler — Schedule periodic medicine reminders via WorkManager.
 */
object ReminderScheduler {

    private const val TAG = "ReminderScheduler"

    /**
     * Schedule all three daily reminders + a refill check.
     */
    fun scheduleAllReminders(context: Context) {
        scheduleMorningReminder(context)
        scheduleAfternoonReminder(context)
        scheduleNightReminder(context)
        scheduleRefillCheck(context)
        Log.d(TAG, "All reminders scheduled")
    }

    private fun scheduleMorningReminder(context: Context) {
        val work = PeriodicWorkRequestBuilder<MedicineReminderWorker>(
            24, TimeUnit.HOURS
        ).setInputData(
            workDataOf(MedicineReminderWorker.KEY_TIME_SLOT to MedicineReminderWorker.TIME_MORNING)
        ).setInitialDelay(
            calculateDelayToHour(8), TimeUnit.MILLISECONDS  // 8 AM
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            MedicineReminderWorker.WORK_NAME_MORNING,
            ExistingPeriodicWorkPolicy.KEEP,
            work
        )
    }

    private fun scheduleAfternoonReminder(context: Context) {
        val work = PeriodicWorkRequestBuilder<MedicineReminderWorker>(
            24, TimeUnit.HOURS
        ).setInputData(
            workDataOf(MedicineReminderWorker.KEY_TIME_SLOT to MedicineReminderWorker.TIME_AFTERNOON)
        ).setInitialDelay(
            calculateDelayToHour(14), TimeUnit.MILLISECONDS  // 2 PM
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            MedicineReminderWorker.WORK_NAME_AFTERNOON,
            ExistingPeriodicWorkPolicy.KEEP,
            work
        )
    }

    private fun scheduleNightReminder(context: Context) {
        val work = PeriodicWorkRequestBuilder<MedicineReminderWorker>(
            24, TimeUnit.HOURS
        ).setInputData(
            workDataOf(MedicineReminderWorker.KEY_TIME_SLOT to MedicineReminderWorker.TIME_NIGHT)
        ).setInitialDelay(
            calculateDelayToHour(21), TimeUnit.MILLISECONDS  // 9 PM
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            MedicineReminderWorker.WORK_NAME_NIGHT,
            ExistingPeriodicWorkPolicy.KEEP,
            work
        )
    }

    private fun scheduleRefillCheck(context: Context) {
        val work = PeriodicWorkRequestBuilder<MedicineReminderWorker>(
            12, TimeUnit.HOURS
        ).setInputData(
            workDataOf(MedicineReminderWorker.KEY_TIME_SLOT to "refill_check")
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            MedicineReminderWorker.WORK_NAME_REFILL,
            ExistingPeriodicWorkPolicy.KEEP,
            work
        )
    }

    /**
     * Calculate milliseconds delay from now to the next occurrence of the given hour.
     */
    private fun calculateDelayToHour(targetHour: Int): Long {
        val now = java.util.Calendar.getInstance()
        val target = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, targetHour)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }

        if (target.before(now)) {
            target.add(java.util.Calendar.DAY_OF_MONTH, 1)
        }

        return target.timeInMillis - now.timeInMillis
    }

    /**
     * Cancel all scheduled reminders.
     */
    fun cancelAllReminders(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(MedicineReminderWorker.WORK_NAME_MORNING)
        wm.cancelUniqueWork(MedicineReminderWorker.WORK_NAME_AFTERNOON)
        wm.cancelUniqueWork(MedicineReminderWorker.WORK_NAME_NIGHT)
        wm.cancelUniqueWork(MedicineReminderWorker.WORK_NAME_REFILL)
        Log.d(TAG, "All reminders cancelled")
    }
}
