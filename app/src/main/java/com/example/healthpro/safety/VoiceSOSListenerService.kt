package com.example.healthpro.safety

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.healthpro.MainActivity
import com.example.healthpro.R
import com.example.healthpro.genie.EmergencyDetectionRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Foreground Service for always-listening emergency keyword detection.
 *
 * KEY FIX (v2): Reuse a single SpeechRecognizer instance across cycles.
 * Previous version destroyed and recreated the recognizer each cycle,
 * causing stale onError callbacks that corrupted state and prevented restarts.
 *
 * Architecture:
 *  - Creates ONE SpeechRecognizer in onStartCommand, reuses it for all cycles
 *  - On each onResults/onError, calls cancel() then startListening() after delay
 *  - On SOS match, emits event via SharedFlow with autoTrigger=true, then self-destructs
 *  - Uses START_STICKY to survive system kills
 */
class VoiceSOSListenerService : Service() {

    companion object {
        private const val TAG = "VoiceSOSListener"
        private const val CHANNEL_ID = "sahay_voice_sos_listener"
        private const val NOTIFICATION_ID = 42002
        private const val RESTART_DELAY_MS = 300L
        private const val ERROR_RESTART_DELAY_MS = 1500L
        private const val BUSY_RETRY_DELAY_MS = 2000L
        private const val MAX_CONSECUTIVE_ERRORS = 10
        private const val COOLDOWN_DELAY_MS = 30_000L

        // ── Event Bus ──────────────────────────────────────────
        private val _sosEventFlow = MutableSharedFlow<Boolean>(replay = 0, extraBufferCapacity = 1)

        /** Emits `true` when voice SOS is detected (autoTrigger=true). */
        val sosEventFlow = _sosEventFlow.asSharedFlow()

        fun start(context: Context) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                Log.e(TAG, "Cannot start: RECORD_AUDIO permission missing.")
                return
            }

            try {
                val intent = Intent(context, VoiceSOSListenerService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start service: ${e.message}")
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, VoiceSOSListenerService::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop service: ${e.message}")
            }
        }
    }

    private val emergencyRepo = EmergencyDetectionRepository()
    private val mainHandler = Handler(Looper.getMainLooper())

    // Single recognizer instance — reused across all cycles
    private var speechRecognizer: SpeechRecognizer? = null
    private val isListening = AtomicBoolean(false)
    private val sosTriggered = AtomicBoolean(false)
    private var consecutiveErrors = 0
    private var serviceAlive = true

    // Recognizer intent — built once, reused
    private val recognizerIntent by lazy {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // SERVICE LIFECYCLE
    // ═══════════════════════════════════════════════════════════════

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🟢 Service created")
        serviceAlive = true
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "🟢 onStartCommand")

        if (!hasRecordAudioPermission()) {
            Log.e(TAG, "❌ RECORD_AUDIO not granted. Stopping.")
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification("🎤 Listening for emergency keywords..."),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, buildNotification("🎤 Listening for emergency keywords..."))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to startForeground: ${e.message}")
            stopSelf()
            return START_NOT_STICKY
        }

        // Create recognizer ONCE if needed, then start listening
        if (speechRecognizer == null) {
            createRecognizer()
        }

        if (!isListening.get() && !sosTriggered.get()) {
            beginListening()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "🔴 Service destroyed")
        serviceAlive = false
        isListening.set(false)

        mainHandler.removeCallbacksAndMessages(null)
        destroyRecognizer()

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ═══════════════════════════════════════════════════════════════
    // RECOGNIZER MANAGEMENT (create once, reuse)
    // ═══════════════════════════════════════════════════════════════

    private fun createRecognizer() {
        if (speechRecognizer != null) return

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e(TAG, "❌ SpeechRecognizer not available. Stopping.")
            stopSelf()
            return
        }

        Log.d(TAG, "Creating SpeechRecognizer instance")
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(recognitionListener)
        }
    }

    private fun destroyRecognizer() {
        try {
            speechRecognizer?.apply {
                cancel()
                destroy()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying recognizer: ${e.message}")
        } finally {
            speechRecognizer = null
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // LISTENING CYCLE
    // ═══════════════════════════════════════════════════════════════

    /**
     * Start a new listening cycle on the existing recognizer.
     * This is the ONLY place startListening() is called.
     */
    private fun beginListening() {
        if (!serviceAlive || sosTriggered.get()) {
            return
        }
        if (isListening.getAndSet(true)) {
            Log.d(TAG, "Already listening, skipping duplicate startListening()")
            return
        }

        if (!hasRecordAudioPermission()) {
            Log.e(TAG, "❌ Permission revoked. Stopping.")
            isListening.set(false)
            stopSelf()
            return
        }

        val recognizer = speechRecognizer
        if (recognizer == null) {
            Log.e(TAG, "Recognizer is null, recreating")
            isListening.set(false)
            createRecognizer()
            scheduleRestart(RESTART_DELAY_MS)
            return
        }

        Log.d(TAG, "🎤 startListening()")
        try {
            recognizer.startListening(recognizerIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Exception in startListening: ${e.message}")
            isListening.set(false)
            scheduleRestart(ERROR_RESTART_DELAY_MS)
        }
    }

    /**
     * Schedule the next listening cycle after a delay.
     * All restarts go through this single method to prevent races.
     */
    private fun scheduleRestart(delayMs: Long) {
        if (!serviceAlive || sosTriggered.get()) return

        Log.d(TAG, "⏱ Scheduling restart in ${delayMs}ms")
        mainHandler.postDelayed({
            if (serviceAlive && !sosTriggered.get()) {
                beginListening()
            }
        }, delayMs)
    }

    // ═══════════════════════════════════════════════════════════════
    // RECOGNITION LISTENER
    // ═══════════════════════════════════════════════════════════════

    private val recognitionListener = object : RecognitionListener {

        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "✅ Ready for speech")
            consecutiveErrors = 0
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "🗣 Speech started")
        }

        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(TAG, "End of speech")
            // Do NOT restart here — wait for onResults or onError
        }

        override fun onError(error: Int) {
            val errorName = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO"
                SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS"
                SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT"
                SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY"
                SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT"
                else -> "UNKNOWN($error)"
            }
            Log.w(TAG, "Recognition error: $errorName")

            // CRITICAL: Mark as not listening so restart can proceed
            isListening.set(false)

            if (sosTriggered.get() || !serviceAlive) return

            when (error) {
                // Fatal — stop service
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    Log.e(TAG, "❌ Permission lost. Stopping.")
                    stopSelf()
                    return
                }

                // Expected in continuous mode — restart quickly
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    consecutiveErrors = 0
                    scheduleRestart(RESTART_DELAY_MS)
                    return
                }

                // Recognizer busy — cancel first, then retry with longer delay
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    try { speechRecognizer?.cancel() } catch (_: Exception) {}
                    scheduleRestart(BUSY_RETRY_DELAY_MS)
                    return
                }

                // ERROR_CLIENT after destroy — ignore stale callback
                SpeechRecognizer.ERROR_CLIENT -> {
                    // This often fires from a destroyed recognizer; just restart
                    scheduleRestart(RESTART_DELAY_MS)
                    return
                }
            }

            // All other errors — apply backoff
            consecutiveErrors++
            if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                Log.e(TAG, "🛑 Too many errors ($consecutiveErrors). Cooling down.")
                updateNotification("⚠️ Listening paused — retrying soon")
                mainHandler.postDelayed({
                    if (serviceAlive && !sosTriggered.get()) {
                        consecutiveErrors = 0
                        updateNotification("🎤 Listening for emergency keywords...")
                        scheduleRestart(RESTART_DELAY_MS)
                    }
                }, COOLDOWN_DELAY_MS)
                return
            }

            scheduleRestart(ERROR_RESTART_DELAY_MS)
        }

        override fun onResults(results: Bundle?) {
            Log.d(TAG, "📝 onResults received")

            // CRITICAL: Mark as not listening BEFORE processing
            isListening.set(false)

            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull() ?: ""

            if (text.isNotBlank()) {
                Log.d(TAG, "Heard: '$text'")
                processText(text)
            }

            // Continue the loop
            if (!sosTriggered.get()) {
                scheduleRestart(RESTART_DELAY_MS)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (sosTriggered.get()) return

            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partialText = matches?.firstOrNull() ?: ""

            if (partialText.isNotBlank()) {
                val result = emergencyRepo.checkEmergencyLocal(partialText)
                if (result.trigger_sos) {
                    Log.w(TAG, "🚨 Emergency in PARTIAL: '$partialText'")
                    triggerSOS()
                }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    // ═══════════════════════════════════════════════════════════════
    // DETECTION & TRIGGER
    // ═══════════════════════════════════════════════════════════════

    private fun processText(text: String) {
        val result = emergencyRepo.checkEmergencyLocal(text)
        if (result.trigger_sos) {
            Log.w(TAG, "🚨 Emergency detected: '$text' (confidence=${result.confidence})")
            triggerSOS()
        } else {
            Log.d(TAG, "No emergency: '$text'")
        }
    }

    private fun triggerSOS() {
        // Atomic guard: only trigger once
        if (!sosTriggered.compareAndSet(false, true)) {
            Log.d(TAG, "SOS already triggered, ignoring")
            return
        }
        isListening.set(false)

        Log.w(TAG, "🚨🚨🚨 VOICE SOS TRIGGERED")

        // Stop recognizer immediately
        try { speechRecognizer?.cancel() } catch (_: Exception) {}
        mainHandler.removeCallbacksAndMessages(null)

        updateNotification("🚨 Emergency detected! Activating SOS...")

        // Emit autoTrigger=true so the SOS screen fires immediately
        _sosEventFlow.tryEmit(true)

        // Self-destruct after brief delay for event propagation
        mainHandler.postDelayed({
            destroyRecognizer()
            stopSelf()
        }, 2000)
    }

    // ═══════════════════════════════════════════════════════════════
    // PERMISSIONS
    // ═══════════════════════════════════════════════════════════════

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    // ═══════════════════════════════════════════════════════════════
    // NOTIFICATION
    // ═══════════════════════════════════════════════════════════════

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Voice SOS Listener",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "SAHAY is listening for emergency keywords"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SAHAY Voice SOS")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(statusText: String) {
        getSystemService(NotificationManager::class.java)
            ?.notify(NOTIFICATION_ID, buildNotification(statusText))
    }
}
