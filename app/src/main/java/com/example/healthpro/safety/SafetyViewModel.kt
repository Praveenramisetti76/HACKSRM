package com.example.healthpro.safety

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Safety Dashboard UI.
 *
 * Manages:
 *  - Monitoring on/off state
 *  - Current status display
 *  - Timeline events
 *  - Settings (threshold, voice toggle)
 *
 * MVVM: No business logic in Activity — everything goes through this ViewModel.
 * Lifecycle-aware: Uses AndroidViewModel for Application context.
 */
class SafetyViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "SafetyVM"
    }

    private val preferences = SafetyPreferences(application.applicationContext)

    // ═══════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════

    data class SafetyUiState(
        val isMonitoringEnabled: Boolean = false,
        val isServiceRunning: Boolean = false,
        val inactivityThresholdSeconds: Int = SafetyPreferences.DEFAULT_THRESHOLD_SECONDS,
        val thresholdDisplayText: String = "30s",
        val isVoiceConfirmationEnabled: Boolean = true,
        val lastActivityTimestamp: Long = System.currentTimeMillis(),
        val lastActivityText: String = "Just now",
        val timelineEvents: List<SafetyTimelineEvent> = emptyList(),
        val statusText: String = "Inactive",
        // Sleep hours
        val isSleepModeEnabled: Boolean = false,
        val sleepStartTime: String = SafetyPreferences.DEFAULT_SLEEP_START,
        val sleepEndTime: String = SafetyPreferences.DEFAULT_SLEEP_END,
        val isSleepActive: Boolean = false
    )

    private val _uiState = MutableStateFlow(SafetyUiState())
    val uiState: StateFlow<SafetyUiState> = _uiState.asStateFlow()

    init {
        refreshState()
    }

    // ═══════════════════════════════════════════════════════════════
    // ACTIONS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Toggle monitoring on/off. Starts or stops the foreground service.
     */
    fun toggleMonitoring() {
        val context = getApplication<Application>()

        if (preferences.isMonitoringEnabled) {
            // Turn OFF
            Log.d(TAG, "Disabling safety monitoring")
            preferences.isMonitoringEnabled = false
            SafetyMonitoringService.stop(context)
        } else {
            // Turn ON
            Log.d(TAG, "Enabling safety monitoring")
            preferences.isMonitoringEnabled = true
            preferences.recordActivity() // Reset timestamps
            SafetyMonitoringService.start(context)
        }

        refreshState()
    }

    /**
     * Update the inactivity threshold (in seconds).
     */
    fun setThreshold(seconds: Int) {
        preferences.inactivityThresholdSeconds = seconds
        refreshState()
    }

    /**
     * Toggle voice confirmation on/off.
     */
    fun toggleVoiceConfirmation() {
        preferences.isVoiceConfirmationEnabled = !preferences.isVoiceConfirmationEnabled
        refreshState()
    }

    /**
     * Record user activity (called from touch events in UI).
     */
    fun recordUserActivity() {
        preferences.recordTouch()
        refreshState()
    }

    /**
     * Clear the timeline log.
     */
    fun clearTimeline() {
        preferences.clearTimeline()
        refreshState()
    }

    // ═══════════════════════════════════════════════════════════════
    // SLEEP HOURS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Toggle sleep mode on/off.
     */
    fun toggleSleepMode() {
        preferences.isSleepModeEnabled = !preferences.isSleepModeEnabled
        refreshState()
    }

    /**
     * Set sleep start time (HH:mm format).
     */
    fun setSleepStartTime(time: String) {
        preferences.sleepStartTime = time
        refreshState()
    }

    /**
     * Set sleep end time (HH:mm format).
     */
    fun setSleepEndTime(time: String) {
        preferences.sleepEndTime = time
        refreshState()
    }

    /**
     * Refresh all state from preferences.
     */
    fun refreshState() {
        viewModelScope.launch {
            val enabled = preferences.isMonitoringEnabled
            val running = preferences.isServiceRunning
            val threshold = preferences.inactivityThresholdSeconds
            val thresholdText = preferences.thresholdDisplayText()
            val voice = preferences.isVoiceConfirmationEnabled
            val lastActivity = preferences.lastActivityTimestamp
            val events = preferences.getTimelineEvents()

            val statusText = when {
                !enabled -> "Inactive"
                running -> "Active — Monitoring"
                else -> "Enabled — Starting..."
            }

            val elapsed = System.currentTimeMillis() - lastActivity
            val seconds = elapsed / 1_000
            val lastActivityText = when {
                seconds < 10 -> "Just now"
                seconds < 60 -> "${seconds}s ago"
                seconds < 3600 -> "${seconds / 60} min ago"
                else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m ago"
            }

            _uiState.value = SafetyUiState(
                isMonitoringEnabled = enabled,
                isServiceRunning = running,
                inactivityThresholdSeconds = threshold,
                thresholdDisplayText = thresholdText,
                isVoiceConfirmationEnabled = voice,
                lastActivityTimestamp = lastActivity,
                lastActivityText = lastActivityText,
                timelineEvents = events,
                statusText = statusText,
                // Sleep hours
                isSleepModeEnabled = preferences.isSleepModeEnabled,
                sleepStartTime = preferences.sleepStartTime,
                sleepEndTime = preferences.sleepEndTime,
                isSleepActive = preferences.isSleepHoursActive()
            )
        }
    }
}
