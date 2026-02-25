package com.example.healthpro.safety

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

/**
 * Voice Confirmation Check Manager.
 *
 * Provides a human-like confirmation flow when inactivity is detected:
 *   1. Genie speaks: "Hi, I haven't detected activity. Are you safe? Please say YES or HELP."
 *   2. Starts speech recognition (10-15 second window)
 *   3. Analyzes response:
 *      - "Yes" / "OK" / "I'm fine" → [onUserSafe] → Cancel alert
 *      - "Help" / "Emergency"       → [onUserNeedsHelp] → Trigger SOS
 *      - No response (timeout)      → [onNoResponse] → Auto-trigger SOS
 *
 * Integrates with existing Genie TTS and SpeechRecognizer APIs.
 *
 * Thread Safety: All callbacks run on main thread.
 */
class VoiceCheckManager(private val context: Context) {

    companion object {
        private const val TAG = "VoiceCheckManager"
        private const val UTTERANCE_ID = "safety_check"
        private const val LISTENING_TIMEOUT_MS = 15_000L  // 15 seconds to respond

        // Words indicating user is safe
        private val SAFE_WORDS = listOf(
            "yes", "yeah", "yep", "okay", "ok", "fine", "i'm okay",
            "i'm fine", "i am okay", "i am fine", "good", "all good",
            "i'm good", "i am good", "safe", "i'm safe", "i am safe",
            "alright", "no problem", "i'm alright"
        )

        // Words indicating user needs help
        private val HELP_WORDS = listOf(
            "help", "emergency", "sos", "danger", "call", "ambulance",
            "hospital", "hurt", "pain", "please help", "send help",
            "i need help", "not okay", "not fine", "not good", "fallen"
        )
    }

    // ═══════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════

    enum class CheckState {
        IDLE,
        SPEAKING,           // Genie is speaking the prompt
        LISTENING,          // Waiting for voice response
        PROCESSING,         // Analyzing response
        RESOLVED            // Decision made
    }

    var currentState = CheckState.IDLE
        private set

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isTtsReady = false
    private val handler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null

    // Callbacks
    private var onUserSafe: (() -> Unit)? = null
    private var onUserNeedsHelp: (() -> Unit)? = null
    private var onNoResponse: (() -> Unit)? = null
    private var onStateChanged: ((CheckState) -> Unit)? = null

    // ═══════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Initialize TTS engine. Call once before using.
     */
    fun initialize() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setSpeechRate(0.9f)  // Slightly slower for clarity
                tts?.setPitch(1.0f)
                isTtsReady = true
                Log.d(TAG, "✅ TTS initialized")
            } else {
                Log.e(TAG, "❌ TTS initialization failed, status=$status")
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // VOICE CHECK FLOW
    // ═══════════════════════════════════════════════════════════════

    /**
     * Start the voice confirmation check.
     *
     * Flow:
     * 1. Genie speaks the prompt
     * 2. Starts speech recognition
     * 3. Analyzes response or times out
     *
     * @param onSafe Called if user says "yes" or similar
     * @param onHelp Called if user says "help" or similar
     * @param onTimeout Called if no response within timeout
     * @param onState Called on every state change
     */
    fun startVoiceCheck(
        onSafe: () -> Unit,
        onHelp: () -> Unit,
        onTimeout: () -> Unit,
        onState: ((CheckState) -> Unit)? = null
    ) {
        this.onUserSafe = onSafe
        this.onUserNeedsHelp = onHelp
        this.onNoResponse = onTimeout
        this.onStateChanged = onState

        updateState(CheckState.SPEAKING)

        if (!isTtsReady) {
            Log.w(TAG, "TTS not ready, skipping speech → direct listen")
            startListening()
            return
        }

        speakPrompt()
    }

    /**
     * Genie speaks the safety check prompt, then starts listening.
     */
    private fun speakPrompt() {
        val prompt = "Hi, I haven't detected any activity for a while. Are you safe? Please say yes or help."

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d(TAG, "🗣️ TTS speaking...")
            }

            override fun onDone(utteranceId: String?) {
                Log.d(TAG, "🗣️ TTS finished, starting listener")
                handler.post { startListening() }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                Log.e(TAG, "TTS error, falling back to listening")
                handler.post { startListening() }
            }
        })

        tts?.speak(prompt, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    /**
     * Start speech recognition with timeout.
     */
    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "Speech recognition not available, triggering timeout")
            handleNoResponse()
            return
        }

        updateState(CheckState.LISTENING)

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "🎤 Ready for speech")
                }
                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "🎤 Speech started")
                }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    Log.d(TAG, "🎤 Speech ended")
                }

                override fun onError(error: Int) {
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "no match"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "timeout"
                        SpeechRecognizer.ERROR_AUDIO -> "audio error"
                        SpeechRecognizer.ERROR_NETWORK -> "network error"
                        else -> "error $error"
                    }
                    Log.w(TAG, "Speech recognition error: $msg")

                    // Timeout or no match → treat as no response
                    if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                    ) {
                        handleNoResponse()
                    }
                }

                override fun onResults(results: Bundle?) {
                    cancelTimeout()
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    Log.d(TAG, "🎤 Recognized: \"$text\"")
                    analyzeResponse(text)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    Log.d(TAG, "🎤 Partial: \"$text\"")
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            }
            startListening(intent)
        }

        // Set timeout
        startTimeout()
    }

    // ═══════════════════════════════════════════════════════════════
    // RESPONSE ANALYSIS
    // ═══════════════════════════════════════════════════════════════

    private fun analyzeResponse(text: String) {
        updateState(CheckState.PROCESSING)
        val lower = text.lowercase().trim()

        when {
            HELP_WORDS.any { lower.contains(it) } -> {
                Log.d(TAG, "🚨 User needs HELP")
                updateState(CheckState.RESOLVED)
                onUserNeedsHelp?.invoke()
            }
            SAFE_WORDS.any { lower.contains(it) } -> {
                Log.d(TAG, "✅ User is SAFE")
                updateState(CheckState.RESOLVED)
                onUserSafe?.invoke()
            }
            lower.isNotEmpty() -> {
                // Unrecognized speech — default to safe (benefit of doubt)
                Log.d(TAG, "❓ Unrecognized response: \"$text\" — defaulting to safe")
                updateState(CheckState.RESOLVED)
                onUserSafe?.invoke()
            }
            else -> {
                handleNoResponse()
            }
        }
    }

    private fun handleNoResponse() {
        cancelTimeout()
        Log.w(TAG, "⏰ No response — triggering SOS")
        updateState(CheckState.RESOLVED)
        onNoResponse?.invoke()
    }

    // ═══════════════════════════════════════════════════════════════
    // TIMEOUT
    // ═══════════════════════════════════════════════════════════════

    private fun startTimeout() {
        cancelTimeout()
        val runnable = Runnable {
            Log.w(TAG, "⏰ Voice check timed out after ${LISTENING_TIMEOUT_MS / 1000}s")
            speechRecognizer?.stopListening()
            handleNoResponse()
        }
        timeoutRunnable = runnable
        handler.postDelayed(runnable, LISTENING_TIMEOUT_MS)
    }

    private fun cancelTimeout() {
        timeoutRunnable?.let { handler.removeCallbacks(it) }
        timeoutRunnable = null
    }

    // ═══════════════════════════════════════════════════════════════
    // GENIE INTEGRATION: Extra Messages
    // ═══════════════════════════════════════════════════════════════

    /**
     * Genie speaks an additional message (e.g., "SOS has been triggered").
     */
    fun speakMessage(message: String) {
        if (isTtsReady) {
            tts?.speak(message, TextToSpeech.QUEUE_ADD, null, "genie_msg")
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // CANCEL / CLEANUP
    // ═══════════════════════════════════════════════════════════════

    /**
     * Cancel the current voice check flow. Safe to call multiple times.
     */
    fun cancel() {
        cancelTimeout()
        tts?.stop()
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        updateState(CheckState.IDLE)
    }

    /**
     * Release all resources. Call when the manager is no longer needed.
     */
    fun release() {
        cancel()
        tts?.shutdown()
        tts = null
        isTtsReady = false
    }

    // ═══════════════════════════════════════════════════════════════
    // STATE MANAGEMENT
    // ═══════════════════════════════════════════════════════════════

    private fun updateState(newState: CheckState) {
        currentState = newState
        onStateChanged?.invoke(newState)
        Log.d(TAG, "State → $newState")
    }
}
