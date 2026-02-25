package com.example.healthpro.medicine.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WorkManager worker for medicine reminder notifications.
 * Shows a high-priority, elder-friendly notification with large text.
 *
 * Input data:
 *   - "medicine_name": Name of the medicine
 *   - "medicine_id": ID of the medicine (for unique notification ID)
 *   - "dosage": Dosage info
 */
class MedicineReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "MedicineReminderWorker"
        const val CHANNEL_ID = "sahay_medicine_reminder"
        private const val CHANNEL_NAME = "Medicine Reminders"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            val medicineName = inputData.getString("medicine_name") ?: "your medicine"
            val medicineId = inputData.getLong("medicine_id", 0L)
            val dosage = inputData.getString("dosage") ?: ""

            createNotificationChannel()

            val title = "💊 Time to take $medicineName"
            val body = if (dosage.isNotBlank()) {
                "Take $dosage of $medicineName now"
            } else {
                "It's time for your $medicineName dose"
            }

            sendNotification(
                id = (medicineId * 100 + System.currentTimeMillis() % 100).toInt(),
                title = title,
                body = body
            )

            Log.d(TAG, "Reminder sent for: $medicineName")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Reminder failed: ${e.message}")
            Result.failure()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders to take your medicines on time"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun sendNotification(id: Int, title: String, body: String) {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(body)
                    .setBigContentTitle(title)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(id, notification)
    }
}
