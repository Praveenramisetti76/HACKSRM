package com.example.healthpro.safety

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.healthpro.MainActivity
import com.example.healthpro.R
import com.example.healthpro.data.contacts.ContactsRepository
import com.example.healthpro.location.LocationHelper
import com.example.healthpro.sos.SOSManager
import com.example.healthpro.sos.SosCallManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground Service for Safety Monitoring.
 *
 * Runs in the background to:
 *  1. Track motion via accelerometer ([MotionTracker])
 *  2. Monitor inactivity ([InactivityManager])
 *  3. Trigger voice confirmation check ([VoiceCheckManager])
 *  4. Auto-trigger SOS if user is unresponsive
 *
 * Architecture:
 *  - Uses a foreground notification to survive background restrictions
 *  - Uses SupervisorJob for coroutine safety
 *  - All components are lifecycle-aware (start/stop with service)
 *
 * Battery Impact: Minimal
 *  - Sensor uses batched delivery (30s intervals)
 *  - Inactivity check runs every 60s (Handler, not coroutine)
 *  - No GPS until SOS is actually triggered
 */
class SafetyMonitoringService : Service() {

    companion object {
        private const val TAG = "SafetyMonitorSvc"
        private const val NOTIFICATION_CHANNEL_ID = "sahay_safety_monitoring"
        private const val NOTIFICATION_ID = 42001
        private const val ACTION_STOP_MONITORING = "com.example.healthpro.STOP_MONITORING"

        /**
         * Start the monitoring service.
         */
        fun start(context: Context) {
            val intent = Intent(context, SafetyMonitoringService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Stop the monitoring service.
         */
        fun stop(context: Context) {
            val intent = Intent(context, SafetyMonitoringService::class.java)
            context.stopService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var preferences: SafetyPreferences
    private lateinit var motionTracker: MotionTracker
    private lateinit var inactivityManager: InactivityManager
    private lateinit var voiceCheckManager: VoiceCheckManager

    // ═══════════════════════════════════════════════════════════════
    // SERVICE LIFECYCLE
    // ═══════════════════════════════════════════════════════════════

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🟢 Service created")

        preferences = SafetyPreferences(applicationContext)

        // Initialize Motion Tracker
        motionTracker = MotionTracker(
            context = applicationContext,
            onMotionDetected = {
                inactivityManager.onMotionDetected()
            }
        )

        // Initialize Inactivity Manager
        inactivityManager = InactivityManager(
            context = applicationContext,
            preferences = preferences,
            onInactivityDetected = {
                handleInactivityDetected()
            }
        )

        // Initialize Voice Check Manager
        voiceCheckManager = VoiceCheckManager(applicationContext)
        voiceCheckManager.initialize()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle stop action from notification
        if (intent?.action == ACTION_STOP_MONITORING) {
            Log.d(TAG, "Stop action received from notification")
            preferences.isMonitoringEnabled = false
            preferences.isServiceRunning = false
            stopSelf()
            return START_NOT_STICKY
        }

        Log.d(TAG, "🟢 Service started")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        // Start monitoring components
        motionTracker.start()
        inactivityManager.start()
        preferences.isServiceRunning = true

        // Log to timeline
        preferences.addTimelineEvent(
            SafetyTimelineEvent(
                type = "monitoring_started",
                description = "Safety monitoring activated"
            )
        )

        return START_STICKY  // Restart if killed by system
    }

    override fun onDestroy() {
        Log.d(TAG, "🔴 Service destroyed")
        motionTracker.stop()
        inactivityManager.stop()
        voiceCheckManager.release()
        preferences.isServiceRunning = false
        serviceScope.cancel()

        preferences.addTimelineEvent(
            SafetyTimelineEvent(
                type = "monitoring_stopped",
                description = "Safety monitoring deactivated"
            )
        )

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ═══════════════════════════════════════════════════════════════
    // INACTIVITY HANDLER
    // ═══════════════════════════════════════════════════════════════

    /**
     * Called when inactivity exceeds the threshold.
     *
     * Flow:
     *  1. If voice confirmation enabled → start voice check
     *  2. If voice disabled → directly trigger SOS
     */
    private fun handleInactivityDetected() {
        // Defense-in-depth: sleep hours check
        if (preferences.isSleepHoursActive()) {
            Log.d(TAG, "💤 Sleep hours active — ignoring inactivity event")
            inactivityManager.resetInactivityAlert()
            return
        }

        Log.w(TAG, "🚨 Inactivity detected — starting safety check")

        // Update notification to alert state
        updateNotification("⚠️ Inactivity detected — checking safety...")

        if (preferences.isVoiceConfirmationEnabled) {
            startVoiceCheck()
        } else {
            // No voice check — go directly to SOS
            triggerSOSFromInactivity()
        }
    }

    /**
     * Start the voice confirmation check via Genie.
     */
    private fun startVoiceCheck() {
        voiceCheckManager.startVoiceCheck(
            onSafe = {
                Log.d(TAG, "✅ User confirmed safe via voice")
                updateNotification("✅ Safety confirmed — monitoring active")
                inactivityManager.resetInactivityAlert()

                preferences.addTimelineEvent(
                    SafetyTimelineEvent(
                        type = "voice_check_ok",
                        description = "User confirmed safety via voice"
                    )
                )

                // Genie follow-up
                voiceCheckManager.speakMessage(
                    "Great! Glad you're safe. I'll keep monitoring. If this was a mistake, you can open the app and adjust your settings."
                )
            },
            onHelp = {
                Log.w(TAG, "🚨 User said HELP")
                preferences.addTimelineEvent(
                    SafetyTimelineEvent(
                        type = "voice_check_help",
                        description = "User requested help via voice command"
                    )
                )
                voiceCheckManager.speakMessage("Sending help right now. Stay calm.")
                triggerSOSFromInactivity()
            },
            onTimeout = {
                Log.w(TAG, "⏰ No voice response — triggering SOS")
                preferences.addTimelineEvent(
                    SafetyTimelineEvent(
                        type = "voice_check_timeout",
                        description = "No response to voice check — auto-triggering SOS"
                    )
                )
                voiceCheckManager.speakMessage("No response detected. Sending emergency alerts now.")
                triggerSOSFromInactivity()
            }
        )
    }

    // ═══════════════════════════════════════════════════════════════
    // SOS TRIGGER
    // ═══════════════════════════════════════════════════════════════

    /**
     * Trigger the existing SOS system due to inactivity detection.
     *
     * Uses the existing SOSManager + LocationHelper + ContactsRepository.
     */
    private fun triggerSOSFromInactivity() {
        Log.w(TAG, "🚨 TRIGGERING SOS FROM INACTIVITY")

        updateNotification("🚨 SOS TRIGGERED — Sending emergency alerts...")

        preferences.addTimelineEvent(
            SafetyTimelineEvent(
                type = "sos_triggered",
                description = "Automatic SOS triggered due to inactivity detection"
            )
        )

        serviceScope.launch {
            try {
                val contactsRepo = ContactsRepository(applicationContext)
                val emergencyContacts = contactsRepo.getEmergencyContacts()

                if (emergencyContacts.isEmpty()) {
                    Log.w(TAG, "No emergency contacts saved — cannot send SOS")
                    updateNotification("⚠️ No emergency contacts — please add contacts")
                    return@launch
                }

                val locationHelper = LocationHelper(applicationContext)
                val sosManager = SOSManager(applicationContext)

                // Step 1: Get location
                val location = locationHelper.getCurrentLocation()
                val mapsLink = if (location != null) {
                    LocationHelper.generateMapsLink(location.latitude, location.longitude)
                } else {
                    // Fallback: try last known location
                    val lastKnown = locationHelper.getLastKnownLocation()
                    if (lastKnown != null) {
                        LocationHelper.generateMapsLink(lastKnown.latitude, lastKnown.longitude)
                    } else {
                        "Location unavailable — please call for help"
                    }
                }

                // Step 2: Send SMS with inactivity reason
                val reasonMessage = SOSManager.buildInactivitySOSMessage(mapsLink)
                val smsSent = sosManager.sendEmergencySMSCustom(emergencyContacts, reasonMessage)
                Log.d(TAG, "SMS sent to $smsSent contacts")

                // Step 3: Send WhatsApp
                sosManager.sendEmergencyWhatsApp(emergencyContacts, mapsLink)

                // Step 4: Optional auto-call to emergency line
                sosManager.callEmergencyNumber("112")

                // Step 5: Place 10-second missed calls to each emergency contact
                delay(2000) // Wait for 112 call to initiate
                val callManager = SosCallManager(applicationContext)
                val callResult = callManager.placeSequentialMissedCalls(emergencyContacts)
                Log.d(TAG, "Missed calls: ${callResult.callsPlaced}/${callResult.totalContacts} placed")

                updateNotification("✅ Emergency alerts sent to ${emergencyContacts.size} contacts")
                Log.d(TAG, "SOS from inactivity completed")

            } catch (e: Exception) {
                Log.e(TAG, "SOS trigger failed: ${e.message}", e)
                updateNotification("❌ SOS failed — ${e.message}")
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // NOTIFICATION
    // ═══════════════════════════════════════════════════════════════

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Safety Monitoring",
                NotificationManager.IMPORTANCE_LOW  // Low importance = no sound
            ).apply {
                description = "SAHAY is monitoring your safety in the background"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String = "🛡️ Safety monitoring active"): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, SafetyMonitoringService::class.java).apply {
                action = ACTION_STOP_MONITORING
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("SAHAY Safety Monitor")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .addAction(0, "Stop Monitoring", stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(statusText: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification(statusText))
    }
}
