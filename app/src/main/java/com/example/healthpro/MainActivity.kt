package com.example.healthpro

import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.healthpro.mood.MoodCheckInDialog
import com.example.healthpro.mood.MoodCheckInViewModel
import com.example.healthpro.navigation.SahayNavGraph
import com.example.healthpro.safety.SafetyMonitoringService
import com.example.healthpro.safety.SafetyMonitoringWorker
import com.example.healthpro.safety.SafetyPreferences
import com.example.healthpro.ui.theme.HealthProTheme

class MainActivity : ComponentActivity() {

    private lateinit var safetyPreferences: SafetyPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        safetyPreferences = SafetyPreferences(applicationContext)

        setContent {
            HealthProTheme {
                // Mood check-in ViewModel (scoped to Activity lifecycle)
                val moodViewModel: MoodCheckInViewModel = viewModel()
                val showMoodDialog by moodViewModel.showDialog.collectAsState()
                val isProcessing by moodViewModel.isProcessing.collectAsState()

                // Show mood check-in once when the app opens
                LaunchedEffect(Unit) {
                    moodViewModel.showCheckIn()
                }

                // Main navigation
                SahayNavGraph()

                // Mood check-in dialog overlay (on top of everything)
                if (showMoodDialog) {
                    MoodCheckInDialog(
                        isProcessing = isProcessing,
                        onMoodSelected = { moodType ->
                            moodViewModel.onMoodSelected(moodType)
                        }
                    )
                }
            }
        }

        // Restart safety monitoring service if it was enabled before app kill
        if (safetyPreferences.isMonitoringEnabled) {
            SafetyMonitoringService.start(this)
            SafetyMonitoringWorker.schedule(this)
        }
    }

    override fun onResume() {
        super.onResume()
        // Mood check-in is handled once in onCreate via LaunchedEffect(Unit)
    }

    /**
     * Track ALL touch events to record user activity.
     * This is the most reliable way to detect screen interaction.
     */
    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev?.action == MotionEvent.ACTION_DOWN || ev?.action == MotionEvent.ACTION_MOVE) {
            if (::safetyPreferences.isInitialized) {
                safetyPreferences.recordTouch()
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onDestroy() {
        // Cancel the WorkManager watchdog if monitoring is disabled
        if (!safetyPreferences.isMonitoringEnabled) {
            SafetyMonitoringWorker.cancel(this)
        }
        super.onDestroy()
    }
}