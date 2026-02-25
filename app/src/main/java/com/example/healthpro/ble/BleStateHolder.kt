package com.example.healthpro.ble

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-level singleton that bridges [FallDetectionService] (a Service)
 * to [BleViewModel] (a ViewModel) without binding.
 *
 * Pattern: Service writes → StateHolder → ViewModel/UI reads.
 * This avoids the complexity of a bound service while keeping state reactive.
 */
object BleStateHolder {

    private val _connectionState = MutableStateFlow(BleConnectionState.DISCONNECTED)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _deviceName = MutableStateFlow<String?>(null)
    val deviceName: StateFlow<String?> = _deviceName.asStateFlow()

    private val _lastHeartbeatTime = MutableStateFlow<Long?>(null)
    val lastHeartbeatTime: StateFlow<Long?> = _lastHeartbeatTime.asStateFlow()

    private val _fallAlertPending = MutableStateFlow(false)
    val fallAlertPending: StateFlow<Boolean> = _fallAlertPending.asStateFlow()

    private val _lastRawMessage = MutableStateFlow<String?>(null)
    val lastRawMessage: StateFlow<String?> = _lastRawMessage.asStateFlow()

    // ═══════════════════════════════════════════════════════════════
    // Write API — called only by FallDetectionService
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

    fun signalFallDetected() {
        _fallAlertPending.value = true
    }

    fun clearFallAlert() {
        _fallAlertPending.value = false
    }

    fun updateLastRawMessage(msg: String) {
        _lastRawMessage.value = msg
    }
}
