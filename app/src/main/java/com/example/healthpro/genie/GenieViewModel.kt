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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for Genie voice assistant.
 *
 * State machine: IDLE → LISTENING → PROCESSING → CONFIRMING
 *   → LAUNCHING → AUTOMATING → DONE | ERROR
 *
 * Uses SupervisorJob for coroutine cancellation safety.
 */
class GenieViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "GenieVM"
    }

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
        ERROR
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

    // ── Voice Recognition ────────────────────────────────────

    fun startListening() {
        val context = getApplication<Application>()

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _errorMessage.value = "Speech recognition is not available on this device."
            _state.value = GenieState.ERROR
            return
        }

        _state.value = GenieState.LISTENING
        _recognizedText.value = ""
        _partialText.value = ""
        _parsedIntent.value = null
        _failedStep.value = null
        _errorMessage.value = ""
        _statusText.value = "Listening..."

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "Ready for speech")
                }

                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "Speech started")
                }

                override fun onRmsChanged(rmsdB: Float) {
                    // Could use for waveform visualization amplitude
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    Log.d(TAG, "Speech ended")
                    _statusText.value = "Processing..."
                }

                override fun onError(error: Int) {
                    val message = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                        SpeechRecognizer.ERROR_CLIENT -> "Client error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that. Please try again."
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                        SpeechRecognizer.ERROR_SERVER -> "Server error"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Please try again."
                        else -> "Recognition error"
                    }
                    Log.w(TAG, "Speech error: $error - $message")
                    _errorMessage.value = message
                    _state.value = GenieState.ERROR
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    Log.d(TAG, "Recognized: $text")
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
            startListening(intent)
        }
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
    }

    // ── Intent Processing ────────────────────────────────────

    private fun processRecognizedText(text: String) {
        _state.value = GenieState.PROCESSING
        _statusText.value = "Understanding your request..."

        val intent = GenieIntentParser.parse(text)
        if (intent != null) {
            _parsedIntent.value = intent
            _state.value = GenieState.CONFIRMING
            _statusText.value = "Ready to confirm"
        } else {
            _errorMessage.value = "I couldn't understand that. Please try again with something like:\n" +
                    "\"Order me a sandwich from Swiggy\" or\n" +
                    "\"Buy a phone charger from Amazon\""
            _state.value = GenieState.ERROR
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

        // Start automation!
        startAutomation(intent, config)
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
        speechRecognizer?.destroy()
        speechRecognizer = null
        automationCollectorJob?.cancel()
    }
}
