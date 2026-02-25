package com.example.healthpro.mood

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.*
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * WorkManager + AlarmManager scheduling for the daily 7 PM mood check-in.
 *
 * Strategy:
 *  - WorkManager: primary scheduler (battery-friendly, survives doze)
 *  - AlarmManager: fallback for exact timing (setAlarmClock)
 *  - Boot receiver: re-schedules after reboot
 *
 * No constant background work. Simply fires at 7 PM daily.
 */
class MoodCheckInWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "MoodCheckInWorker"
        private const val WORK_NAME = "sahay_mood_checkin_daily"
        private const val PREFS_NAME = "mood_checkin_prefs"
        private const val KEY_SHOULD_SHOW = "should_show_mood_dialog"

        /**
         * Schedule the check-in every 1 minute.
         *
         * Enqueues a OneTimeWorkRequest with a 1-minute delay.
         * Re-schedules itself after execution.
         */
        fun scheduleMinuteCheckIn(context: Context) {
            val delay = 60_000L // 1 minute

            val workRequest = OneTimeWorkRequestBuilder<MoodCheckInWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .addTag("mood_checkin")
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )

            Log.d(TAG, "✅ Mood check-in scheduled to fire in 1 minute")
        }

        /**
         * Cancel the scheduled check-in.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "🛑 Mood check-in cancelled")
        }

        /**
         * Calculate milliseconds until next 7:00 PM.
         * If already past 7 PM today, schedules for tomorrow.
         */
        private fun calculateDelayUntil7PM(): Long {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 19) // 7 PM
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // If 7 PM has already passed today, schedule for tomorrow
            if (target.before(now) || target == now) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }

            return target.timeInMillis - now.timeInMillis
        }

        /**
         * Set a flag in SharedPreferences that the dialog should be shown.
         * The Activity reads this flag on resume.
         */
        fun setShouldShowDialog(context: Context, show: Boolean) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_SHOULD_SHOW, show)
                .apply()
        }

        /**
         * Check if the dialog should be shown.
         */
        fun shouldShowDialog(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_SHOULD_SHOW, false)
        }

        /**
         * AlarmManager fallback for exact timing.
         * Schedules an exact alarm at the next 7 PM.
         */
        fun scheduleExactAlarm(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                    ?: return

                val intent = Intent(context, MoodAlarmReceiver::class.java)
                val pendingIntent = PendingIntent.getBroadcast(
                    context, 7001, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val targetTime = System.currentTimeMillis() + 60_000L

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setAlarmClock(
                            AlarmManager.AlarmClockInfo(targetTime, pendingIntent),
                            pendingIntent
                        )
                    } else {
                        // Fallback: inexact alarm
                        alarmManager.set(AlarmManager.RTC_WAKEUP, targetTime, pendingIntent)
                    }
                } else {
                    alarmManager.setAlarmClock(
                        AlarmManager.AlarmClockInfo(targetTime, pendingIntent),
                        pendingIntent
                    )
                }

                Log.d(TAG, "⏰ AlarmManager fallback set for 1 minute from now")
            } catch (e: Exception) {
                Log.e(TAG, "AlarmManager scheduling failed: ${e.message}")
            }
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "🕖 Triggering mood check-in (1-minute interval)")

        // Set flag for the Activity to show the dialog
        setShouldShowDialog(applicationContext, true)

        // Re-schedule for next minute
        scheduleMinuteCheckIn(applicationContext)

        // Also re-schedule the AlarmManager fallback
        scheduleExactAlarm(applicationContext)

        return Result.success()
    }
}

/**
 * BroadcastReceiver for AlarmManager fallback.
 * Fires at 7 PM when WorkManager might be delayed.
 */
class MoodAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        Log.d("MoodAlarmReceiver", "⏰ Alarm fired — setting mood dialog flag")
        MoodCheckInWorker.setShouldShowDialog(context, true)

        // Re-schedule for tomorrow
        MoodCheckInWorker.scheduleExactAlarm(context)
    }
}

/**
 * Boot receiver to re-schedule mood check-in after device reboot.
 */
class MoodBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        context ?: return

        Log.d("MoodBootReceiver", "📱 Device rebooted — re-scheduling mood check-in")
        MoodCheckInWorker.scheduleMinuteCheckIn(context)
        MoodCheckInWorker.scheduleExactAlarm(context)
    }
}
