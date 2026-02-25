package com.example.healthpro.genie

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.healthpro.safety.VoiceSOSListenerService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.delay
import com.example.healthpro.mood.MoodRepository
import com.example.healthpro.mood.MoodType
import kotlinx.coroutines.withContext

/**
 * ViewModel for Genie voice assistant.
 *
 * State machine: IDLE → LISTENING → PROCESSING → CONFIRMING
 *   → LAUNCHING → AUTOMATING → DONE | ERROR
 *
 * Uses SupervisorJob for coroutine cancellation safety.
 */
class GenieViewModel(application: Application) : AndroidViewModel(application) {

    // ── State ────────────────────────────────────────────────

    enum class GenieState {
        IDLE,
        LISTENING,
        PROCESSING,
        CONFIRMING,
        CONSENT_REQUIRED,     // First-run or session consent
        LAUNCHING,
        AUTOMATING,
        DONE,
        ERROR,
        SOS_TRIGGERED         // Emergency detected
    }

    private val _state = MutableStateFlow(GenieState.IDLE)
    val state: StateFlow<GenieState> = _state.asStateFlow()

    private val _recognizedText = MutableStateFlow("")
    val recognizedText: StateFlow<String> = _recognizedText.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val _parsedIntent = MutableStateFlow<GenieIntent?>(null)
    val parsedIntent: StateFlow<GenieIntent?> = _parsedIntent.asStateFlow()

    private val _errorMessage = MutableStateFlow("")
    val errorMessage: StateFlow<String> = _errorMessage.asStateFlow()

    private val _statusText = MutableStateFlow("")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    // Automation failure details
    private val _failedStep = MutableStateFlow<AutomationResult.Failed?>(null)
    val failedStep: StateFlow<AutomationResult.Failed?> = _failedStep.asStateFlow()

    // Current flow config (for retry)
    private var currentFlowConfig: UiFlowConfig? = null
    private var currentSearchQuery: String = ""

    private var speechRecognizer: SpeechRecognizer? = null
    private var automationCollectorJob: Job? = null

    // Dependency injection (or simple instantiation here)
    private val emergencyRepo = EmergencyDetectionRepository()
    private val moodRepo = MoodRepository(application)
    private val ttsRepository = ElevenLabsTTSRepository(application)
    private var ttsJob: Job? = null

    /**
     * Voice configuration — bundles voice ID + ElevenLabs tuning per mood.
     */
    private data class VoiceConfig(
        val voiceId: String,
        val stability: Double,
        val similarityBoost: Double
    )

    companion object {
        private const val TAG = "GenieVM"
        private const val VOICE_RACHEL = "21m00Tcm4TlvDq8ikWAM" // Neutral/Good
        private const val VOICE_BELLA  = "EXAVITQu4vr4xnSDxMaL" // Soft/Calming
    }

    /**
     * Returns the correct voice + settings based on the user's current mood.
     *
     * GOOD/OKAY  → Rachel, moderate stability, natural energy
     * NOT_GOOD/UNWELL → Bella, high stability, ultra-soft & soothing
     */
    private fun getVoiceConfig(mood: MoodType?): VoiceConfig = when (mood) {
        MoodType.NOT_GOOD, MoodType.UNWELL -> VoiceConfig(
            voiceId = VOICE_BELLA,
            stability = 0.90,         // Very calm, steady pacing
            similarityBoost = 0.30    // Gentle, warm timbre
        )
        else -> VoiceConfig(
            voiceId = VOICE_RACHEL,
            stability = 0.65,         // Natural but composed
            similarityBoost = 0.60    // Clear, slightly warm
        )
    }

    // Error handler for uncaught TTS network/decoding exceptions
    private val errorHandler = CoroutineExceptionHandler { _, exception ->
        Log.e(TAG, "Uncaught coroutine exception: ${exception.message}", exception)
    }

    // ── Voice Recognition ────────────────────────────────────
    private var isListening = false

    fun startListening() {
        val context = getApplication<Application>()

        // 1. Check permissions first
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _errorMessage.value = "Microphone permission is required."
            _state.value = GenieState.ERROR
            return
        }

        // 2. Prevent concurrent starts
        if (isListening) {
            Log.d(TAG, "Already listening, ignoring start request")
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _errorMessage.value = "Speech recognition is not available on this device."
            _state.value = GenieState.ERROR
            isListening = false
            return
        }

        // 3. Stop background listener entirely to avoid Microphone conflict (ERROR_RECOGNIZER_BUSY)
        Log.d(TAG, "Stopping VoiceSOSListenerService for Genie")
        VoiceSOSListenerService.stop(context)

        _state.value = GenieState.LISTENING
        _recognizedText.value = ""
        _partialText.value = ""
        _parsedIntent.value = null
        _failedStep.value = null
        _errorMessage.value = ""
        _statusText.value = "Listening..."
        
        // Use a coroutine for mood greeting, then open mic
        viewModelScope.launch(Dispatchers.Main) {
            // 4. Determine mood and greet FIRST
            val latestMood = moodRepo.getLatestMood()
            val vc = getVoiceConfig(latestMood)
            val greeting = when (latestMood) {
                MoodType.NOT_GOOD, MoodType.UNWELL -> "I'm with you. Ready to help."
                else -> "Ready for your command."
            }

            // Speak greeting and WAIT for it to finish before opening mic
            _statusText.value = greeting
            ttsJob?.cancel()
            val greetingJob = launch(errorHandler) {
                ttsRepository.play(greeting, vc.voiceId, vc.stability, vc.similarityBoost)
            }
            
            // Wait for greeting to finish
            greetingJob.join()
            delay(300) // Small breather after speech

            // Bail out if user cancelled during greeting
            if (_state.value != GenieState.LISTENING) {
                Log.d(TAG, "State changed during greeting, aborting mic open")
                return@launch
            }

            // NOW set isListening — after greeting is done
            isListening = true
            
            cleanupRecognizer()
            
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d(TAG, "🟢 Ready for speech")
                    }

                    override fun onBeginningOfSpeech() {
                        Log.d(TAG, "🎤 Speech started")
                    }

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        Log.d(TAG, "Speech ended")
                        _statusText.value = "Processing..."
                    }

                    override fun onError(error: Int) {
                        val message = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                            SpeechRecognizer.ERROR_CLIENT -> "Internal error"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                            SpeechRecognizer.ERROR_NETWORK -> "Network error"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                            SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that. Please try again."
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Microphone busy. Try again in a second."
                            SpeechRecognizer.ERROR_SERVER -> "Server error"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Please try again."
                            else -> "Recognition error"
                        }
                        Log.e(TAG, "❌ Speech error: $error - $message")
                        
                        // Speak the error message (ended note)
                        val shortError = if (error == SpeechRecognizer.ERROR_NO_MATCH) "I didn't catch that." else message
                        ttsJob?.cancel()
                        ttsJob = viewModelScope.launch(errorHandler) {
                            val mood = moodRepo.getLatestMood()
                            val vc = getVoiceConfig(mood)
                            ttsRepository.play(shortError, vc.voiceId, vc.stability, vc.similarityBoost)
                        }
                        
                        // Restart background listener as we failed
                        cleanupRecognizer()
                        isListening = false
                        VoiceSOSListenerService.start(context)
                        
                        _errorMessage.value = message
                        _state.value = GenieState.ERROR
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        Log.d(TAG, "✅ Recognized: $text")
                        
                        // Cleanup recognizer — do NOT restart background service here.
                        // processRecognizedText() will restart it on the non-emergency path.
                        cleanupRecognizer()
                        isListening = false
                        
                        _recognizedText.value = text
                        processRecognizedText(text)
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        _partialText.value = matches?.firstOrNull() ?: ""
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                }
                
                try {
                    startListening(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Fatal startListening exception: ${e.message}")
                    _errorMessage.value = "Failed to start microphone."
                    _state.value = GenieState.ERROR
                    cleanupRecognizer()
                    isListening = false
                    VoiceSOSListenerService.start(context)
                }
            }
        }
    }        

    private fun cleanupRecognizer() {
        try {
            speechRecognizer?.apply {
                cancel()
                destroy()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning recognizer: ${e.message}")
        } finally {
            speechRecognizer = null
        }
    }

    fun stopListening() {
        cleanupRecognizer()
        isListening = false
        VoiceSOSListenerService.start(getApplication())
    }

    // ── Intent Processing ────────────────────────────────────

    private fun processRecognizedText(text: String) {
        val context = getApplication<Application>()
        _state.value = GenieState.PROCESSING
        _statusText.value = "Understanding your request..."

        // Launch a coroutine to check for emergencies first
        viewModelScope.launch(errorHandler) {
            val emergencyResult = emergencyRepo.checkEmergency(text)
            
            if (emergencyResult.trigger_sos && emergencyResult.confidence >= 70) {
                // Intercept and trigger SOS
                Log.w(TAG, "🚨 SOS Trigger Detected in voice command! Confidence: ${emergencyResult.confidence}")
                
                // Immediately cancel any TTS or ongoing automation securely
                ttsJob?.cancel()
                ttsRepository.stop()
                cancelOrder()
                
                // Stop background listener to prevent duplicate SOS navigation
                VoiceSOSListenerService.stop(context)
                
                _state.value = GenieState.SOS_TRIGGERED
                _statusText.value = "Emergency detected. Activating SOS..."
                return@launch
            }
            
            // Not an emergency — safe to restart background listener now
            VoiceSOSListenerService.start(context)
            
            // Not an emergency, proceed normally
            val intent = GenieIntentParser.parse(text)
            if (intent != null) {
                _parsedIntent.value = intent
                _state.value = GenieState.CONFIRMING
                _statusText.value = "Ready to confirm"
                
                val reply = when (intent.type) {
                    IntentType.FOOD -> "Preparing to order ${intent.item} from ${intent.platform.appName}."
                    IntentType.PRODUCT -> "Adding ${intent.item} from ${intent.platform.appName} to your order."
                    IntentType.MEDICINE -> "Setting up medicine order on ${intent.platform.appName}."
                    else -> "Executing your request now."
                }
                ttsJob?.cancel()
                ttsJob = launch(errorHandler) {
                    val mood = moodRepo.getLatestMood()
                    val vc = getVoiceConfig(mood)
                    ttsRepository.play(reply, vc.voiceId, vc.stability, vc.similarityBoost)
                }
            } else {
                val errorMsg = "I couldn't understand that."
                _errorMessage.value = "$errorMsg\n\nTry:\n\"Order me a sandwich from Swiggy\"\n\"Buy a charger from Amazon\""
                _state.value = GenieState.ERROR
                
                ttsJob?.cancel()
                ttsJob = launch(errorHandler) {
                    val mood = moodRepo.getLatestMood()
                    val vc = getVoiceConfig(mood)
                    ttsRepository.play(errorMsg, vc.voiceId, vc.stability, vc.similarityBoost)
                }
            }
        }
    }

    // ── Order Execution ──────────────────────────────────────

    fun confirmOrder() {
        val intent = _parsedIntent.value ?: return
        val context = getApplication<Application>()

        // Medicine flow — navigate to mock screen (handled by UI)
        if (intent.type == IntentType.MEDICINE) {
            _state.value = GenieState.DONE
            _statusText.value = "Opening medicine order screen..."
            return
        }

        // Check feature flag
        if (!FeatureFlags.isAutomationEnabled(context)) {
            // Deep link fallback
            launchDeepLink(intent)
            return
        }

        // Check consent
        if (!ConsentManager.hasConsent(context)) {
            _state.value = GenieState.CONSENT_REQUIRED
            return
        }

        // Check session confirmation
        if (!ConsentManager.isSessionConfirmed(context)) {
            _state.value = GenieState.CONSENT_REQUIRED
            return
        }

        // Check accessibility service
        if (!GenieAccessibilityService.isRunning()) {
            // Fallback to deep link with message
            _statusText.value = "Accessibility not enabled. Opening ${intent.platform.appName} for you..."
            launchDeepLink(intent)
            return
        }

        // Check app installed
        if (!PlatformLauncher.isAppInstalled(context, intent.platform)) {
            _statusText.value = "${intent.platform.appName} is not installed. Opening Play Store..."
            _state.value = GenieState.LAUNCHING
            PlatformLauncher.openPlayStore(context, intent.platform)
            _state.value = GenieState.DONE
            return
        }

        // Get flow config
        val config = FlowConfigManager.getConfig(context, intent.platform)
        if (config == null) {
            // No config — fallback to deep link
            _statusText.value = "No automation available for ${intent.platform.appName}. Opening search..."
            launchDeepLink(intent)
            return
        }

        // Check security guards
        val service = GenieAccessibilityService.instance
        if (service != null && !service.isScreenUnlocked()) {
            _errorMessage.value = "Please unlock your screen before automation can start."
            _state.value = GenieState.ERROR
            return
        }

        // Play TTS confirmation before starting automation!
        ttsJob?.cancel()
        ttsJob = viewModelScope.launch(errorHandler) {
            _state.value = GenieState.LAUNCHING
            
            val confirmationMsg = when (intent.type) {
                IntentType.FOOD -> "Ordering ${intent.item} from ${intent.platform.appName}"
                IntentType.PRODUCT -> "Ordering ${intent.item} from ${intent.platform.appName}"
                IntentType.MEDICINE -> "Ordering medicine on ${intent.platform.appName}"
                else -> "Executing your request"
            }

            _statusText.value = confirmationMsg
            
            // Stream and play audio confirmation (safely inside a cancellable Job)
            try {
                val mood = moodRepo.getLatestMood()
                val vc = getVoiceConfig(mood)
                ttsRepository.play(confirmationMsg, vc.voiceId, vc.stability, vc.similarityBoost)
            } catch (e: Exception) {
                Log.e(TAG, "TTS play threw error: ${e.message}")
            }
            
            // Short delay to allow audio to start cleanly before the UI transitions
            kotlinx.coroutines.delay(1000)

            startAutomation(intent, config)
        }
    }

    private fun startAutomation(intent: GenieIntent, config: UiFlowConfig) {
        val context = getApplication<Application>()

        _state.value = GenieState.LAUNCHING
        _statusText.value = "Opening ${intent.platform.appName}..."
        currentFlowConfig = config
        currentSearchQuery = intent.item

        // Launch the app first
        PlatformLauncher.launchApp(context, intent.platform)

        // Start collecting automation status
        _state.value = GenieState.AUTOMATING
        startStatusCollector()

        // Execute the flow via AccessibilityService
        GenieAccessibilityService.instance?.executeFlow(config, intent.item)
    }

    private fun startStatusCollector() {
        automationCollectorJob?.cancel()
        automationCollectorJob = viewModelScope.launch {
            // Collect step status updates
            launch {
                GenieAccessibilityService.statusFlow.collect { status ->
                    _statusText.value = "${status.description} (${status.stepIndex + 1}/${status.totalSteps})"
                }
            }
            // Collect completion
            launch {
                GenieAccessibilityService.completionFlow.collect { result ->
                    handleAutomationResult(result)
                }
            }
        }
    }

    private fun handleAutomationResult(result: AutomationResult) {
        when (result) {
            is AutomationResult.Completed -> {
                _statusText.value = "Automation completed!"
                _state.value = GenieState.DONE
            }
            is AutomationResult.StoppedAtPayment -> {
                _statusText.value = result.message
                _state.value = GenieState.DONE
            }
            is AutomationResult.StoppedForAuth -> {
                _statusText.value = "Authentication required: ${result.reason}\nPlease complete it manually."
                _state.value = GenieState.DONE
            }
            is AutomationResult.Failed -> {
                _failedStep.value = result
                _errorMessage.value = result.reason
                _statusText.value = "Failed at step: ${result.stepName}"
                _state.value = GenieState.ERROR
            }
            is AutomationResult.Cancelled -> {
                _statusText.value = "Automation cancelled."
                _state.value = GenieState.IDLE
            }
        }
        automationCollectorJob?.cancel()
    }

    // ── Deep Link Fallback ───────────────────────────────────

    private fun launchDeepLink(intent: GenieIntent) {
        val context = getApplication<Application>()
        _state.value = GenieState.LAUNCHING

        if (PlatformLauncher.isAppInstalled(context, intent.platform)) {
            PlatformLauncher.launchDeepLink(context, intent.platform, intent.item)
            _statusText.value = "Opened ${intent.platform.appName} — search for \"${intent.item}\""
        } else {
            _statusText.value = "${intent.platform.appName} is not installed. Opening Play Store..."
            PlatformLauncher.openPlayStore(context, intent.platform)
        }
        _state.value = GenieState.DONE
    }

    // ── Actions ──────────────────────────────────────────────

    fun cancelOrder() {
        Log.d(TAG, "Canceling current automation process")
        GenieAccessibilityService.instance?.cancelAutomation()
        automationCollectorJob?.cancel()
        reset()
    }

    fun retryStep() {
        val config = currentFlowConfig ?: return
        val failed = _failedStep.value ?: return
        val intent = _parsedIntent.value ?: return

        _failedStep.value = null
        _errorMessage.value = ""
        _state.value = GenieState.AUTOMATING
        startStatusCollector()
        GenieAccessibilityService.instance?.retryFromStep(config, currentSearchQuery, failed.failedAtStep)
    }

    fun retryAll() {
        val intent = _parsedIntent.value ?: return
        _failedStep.value = null
        _errorMessage.value = ""
        confirmOrder()
    }

    fun grantConsent() {
        val context = getApplication<Application>()
        ConsentManager.grantConsent(context)
        ConsentManager.confirmSession(context)
        // Resume order flow
        confirmOrder()
    }

    fun confirmSessionAndProceed() {
        val context = getApplication<Application>()
        ConsentManager.confirmSession(context)
        confirmOrder()
    }

    fun reset() {
        // Stop any active TTS audio
        ttsRepository.stop()
        
        _state.value = GenieState.IDLE
        _recognizedText.value = ""
        _partialText.value = ""
        _parsedIntent.value = null
        _errorMessage.value = ""
        _statusText.value = ""
        _failedStep.value = null
        currentFlowConfig = null
        currentSearchQuery = ""
    }

    // ── Cleanup ──────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        cleanupRecognizer()
        VoiceSOSListenerService.start(getApplication())
        ttsRepository.release()
        automationCollectorJob?.cancel()
        ttsJob?.cancel()
    }
}
