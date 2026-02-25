package com.example.healthpro.ble

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Possible states of the BLE connection to Sahay-Nano.
 */
enum class BleConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED
}

/**
 * ViewModel that owns and exposes BLE connection state to the UI layer.
 *
 * State is populated by [FallDetectionService] via [BleStateHolder] singleton.
 * UI screens observe these flows; no direct BLE logic lives here.
 */
class BleViewModel(application: Application) : AndroidViewModel(application) {

    // ─── Connection State ────────────────────────────────────────────
    private val _connectionState = MutableStateFlow(BleConnectionState.DISCONNECTED)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    // ─── Connected Device Name ───────────────────────────────────────
    private val _deviceName = MutableStateFlow<String?>(null)
    val deviceName: StateFlow<String?> = _deviceName.asStateFlow()

    // ─── Last Heartbeat Timestamp (epoch millis) ─────────────────────
    private val _lastHeartbeatTime = MutableStateFlow<Long?>(null)
    val lastHeartbeatTime: StateFlow<Long?> = _lastHeartbeatTime.asStateFlow()

    // ─── Fall Alert Active (drives FallAlertScreen) ──────────────────
    private val _fallAlertActive = MutableStateFlow(false)
    val fallAlertActive: StateFlow<Boolean> = _fallAlertActive.asStateFlow()

    // ─── Last Raw BLE Message (for debug log UI) ─────────────────────
    private val _lastRawMessage = MutableStateFlow<String?>(null)
    val lastRawMessage: StateFlow<String?> = _lastRawMessage.asStateFlow()

    // ═══════════════════════════════════════════════════════════════
    // Called by FallDetectionService via BleStateHolder
    // ═══════════════════════════════════════════════════════════════

    fun updateConnectionState(state: BleConnectionState) {
        _connectionState.value = state
    }

    fun updateDeviceName(name: String?) {
        _deviceName.value = name
    }

    fun updateHeartbeat() {
        _lastHeartbeatTime.value = System.currentTimeMillis()
    }

    fun triggerFallAlert() {
        _fallAlertActive.value = true
    }

    fun dismissFallAlert() {
        _fallAlertActive.value = false
    }

    fun updateLastRawMessage(msg: String) {
        _lastRawMessage.value = msg
    }
}
