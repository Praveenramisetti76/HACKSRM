package com.example.healthpro.mood

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthpro.data.contacts.ContactsRepository
import com.example.healthpro.sos.SOSManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for the Emotional Check-In feature.
 *
 * Handles:
 *  1. Saving mood to Room
 *  2. Pattern detection after each entry
 *  3. Gentle notification alerts when patterns are detected
 *
 * ❌ NO automatic calling
 * ❌ NO WhatsApp auto-message
 * ✅ Only pattern-based gentle alerts
 *
 * MVVM: No business logic in Activity/Composable.
 * Lifecycle-aware via AndroidViewModel.
 */
class MoodCheckInViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MoodCheckInVM"
        private const val CHANNEL_ID = "sahay_mood_alerts"
        private const val CHANNEL_NAME = "Mood Pattern Alerts"
        private const val NOTIF_ID_3DAY = 7301
        private const val NOTIF_ID_7DAY = 7302
    }

    private val repository = MoodRepository(application.applicationContext)
    private val contactsRepository = ContactsRepository(application.applicationContext)
    private val sosManager = SOSManager(application.applicationContext)

    // ═══════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════

    private val _showDialog = MutableStateFlow(false)
    val showDialog: StateFlow<Boolean> = _showDialog.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    /**
     * Show the mood check-in dialog.
     */
    fun showCheckIn() {
        _showDialog.value = true
    }

    /**
     * Hide the dialog (only after selection).
     */
    private fun dismissDialog() {
        _showDialog.value = false
    }

    // ═══════════════════════════════════════════════════════════════
    // MOOD SELECTION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Handle user's mood selection.
     *
     * Flow:
     *  1. Save mood to Room
     *  2. Run pattern detection (no immediate escalation)
     *  3. Dismiss dialog
     *
     * ❌ No auto-call. ❌ No WhatsApp.
     * ✅ Pattern-based gentle alerts only.
     */
    fun onMoodSelected(moodType: MoodType) {
        if (_isProcessing.value) return  // Prevent double-tap
        _isProcessing.value = true

        viewModelScope.launch {
            try {
                // Step 1: Save mood
                repository.saveMood(moodType)

                // Step 2: Check patterns (only runs checks, no polling)
                checkMoodPatterns()

            } catch (e: Exception) {
                Log.e(TAG, "Error processing mood: ${e.message}", e)
            } finally {
                _isProcessing.value = false
                dismissDialog()
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // PATTERN DETECTION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Run both pattern rules after saving a mood.
     * Each rule has its own cooldown in the Repository to prevent spam.
     *
     * Rule 1: 3 consecutive LOW days → gentle SMS alert
     * Rule 2: 5 LOW in last 7 entries → stronger SMS alert
     *
     * Uses existing SOSManager.sendEmergencySMSCustom for delivery.
     * Falls back to local notification if SMS permission is missing
     * or no caretaker contacts are set.
     */
    private suspend fun checkMoodPatterns() {
        // Rule 1: 3 consecutive low moods
        if (repository.shouldAlertThreeConsecutiveLow()) {
            Log.w(TAG, "📉 Pattern Rule 1: 3 consecutive low moods detected")
            sendPatternAlert(
                message = "⚠️ SAHAY Mood Alert ⚠️\n" +
                        "Mood has been low for 3 consecutive days.\n" +
                        "Please check on your loved one.",
                notifTitle = "Mood Alert",
                notifBody = "Mood has been low for 3 consecutive days.",
                notifId = NOTIF_ID_3DAY
            )
        }

        // Rule 2: 5 low moods in last 7 entries
        if (repository.shouldAlertFiveInSevenLow()) {
            Log.w(TAG, "📉 Pattern Rule 2: 5 low moods in last 7 days detected")
            sendPatternAlert(
                message = "🚨 SAHAY Mood Alert 🚨\n" +
                        "Multiple low mood days detected this week.\n" +
                        "Please check on your loved one.",
                notifTitle = "Weekly Mood Alert",
                notifBody = "Multiple low mood days detected this week.",
                notifId = NOTIF_ID_7DAY
            )
        }
    }

    /**
     * Send a pattern alert via SMS to emergency contacts.
     *
     * If SMS permission is missing or no contacts are set,
     * falls back to a local notification on the device.
     *
     * ❌ No automatic calling
     * ❌ No WhatsApp auto-message
     * ✅ Gentle SMS + local notification only
     */
    private suspend fun sendPatternAlert(
        message: String,
        notifTitle: String,
        notifBody: String,
        notifId: Int
    ) {
        val context = getApplication<Application>()

        // Always show a local notification on the elder's device
        showLocalNotification(notifTitle, notifBody, notifId)

        // Try to SMS the caretaker
        val emergencyContacts = contactsRepository.getEmergencyContacts()
        if (emergencyContacts.isEmpty()) {
            Log.w(TAG, "No emergency contacts set — local notification only")
            return
        }

        val hasSmsPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasSmsPermission) {
            Log.w(TAG, "SMS permission not granted — local notification only")
            return
        }

        // Send via existing SOSManager infrastructure
        val sent = sosManager.sendEmergencySMSCustom(emergencyContacts, message)
        Log.d(TAG, "📨 Pattern alert sent to $sent/${emergencyContacts.size} contacts")
    }

    // ═══════════════════════════════════════════════════════════════
    // LOCAL NOTIFICATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Show a gentle local notification on the device.
     * This ensures the elder also sees the alert, not just the caretaker.
     */
    private suspend fun showLocalNotification(title: String, body: String, notifId: Int) {
        withContext(Dispatchers.Main) {
            val context = getApplication<Application>()

            // Create channel (required for Android O+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Gentle alerts when mood patterns are detected"
                }
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                        as NotificationManager
                manager.createNotificationChannel(channel)
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                    as NotificationManager
            manager.notify(notifId, notification)

            Log.d(TAG, "🔔 Local notification shown: $title")
        }
    }
}
