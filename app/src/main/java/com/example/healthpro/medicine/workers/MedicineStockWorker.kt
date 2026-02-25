package com.example.healthpro.medicine.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.healthpro.medicine.data.db.MedicineManagerDatabase
import com.example.healthpro.medicine.data.repository.MedicineManagerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WorkManager worker: runs once daily to check stock and send low-stock notifications.
 */
class MedicineStockWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "MedicineStockWorker"
        const val WORK_NAME = "medicine_stock_check"
        private const val CHANNEL_ID = "sahay_medicine_stock"
        private const val CHANNEL_NAME = "Medicine Stock Alerts"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            val db = MedicineManagerDatabase.getInstance(applicationContext)
            val repo = MedicineManagerRepository(
                db.medicineManagerDao(),
                db.intakeLogDao()
            )
            val lowStock = repo.getMedicinesNeedingReorder()

            if (lowStock.isNotEmpty()) {
                createNotificationChannel()
                val names = lowStock.joinToString(", ") { it.name }
                sendNotification(
                    id = WORK_NAME.hashCode(),
                    title = "Medicine Stock Low",
                    body = "Time to reorder: $names"
                )
                Log.d(TAG, "Low stock notification sent for: $names")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "MedicineStockWorker failed: ${e.message}")
            Result.retry()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when medicine stock is running low"
                enableVibration(true)
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
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(id, notification)
    }
}
