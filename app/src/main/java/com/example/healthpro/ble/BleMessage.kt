package com.example.healthpro.ble

/**
 * Typed representation of every ASCII message the Sahay-Nano BLE peripheral can send.
 *
 * Arduino TX Characteristic sends UTF-8 strings over UART-style BLE service:
 *   Service UUID : 6E400001-B5A3-F393-E0A9-E50E24DCCA9E
 *   TX (Notify)  : 6E400003-B5A3-F393-E0A9-E50E24DCCA9E
 */
sealed class BleMessage {

    /** Nano has booted and BLE stack is ready. */
    object Ready : BleMessage()

    /** Periodic keep-alive sent by Arduino every ~5 seconds. */
    object Heartbeat : BleMessage()

    /**
     * A transient impact was detected (e.g. short jolt).
     * Does NOT automatically trigger SOS — only FALL_DETECTED does.
     */
    object Impact : BleMessage()

    /**
     * A confirmed fall pattern was detected by the Nano's IMU.
     * Triggers FallAlertScreen and the 45-second SOS countdown.
     */
    object FallDetected : BleMessage()

    /** Received a message the firmware doesn't recognise yet. */
    data class Unknown(val raw: String) : BleMessage()

    companion object {

        /**
         * Parse a raw UTF-8 string from the BLE characteristic into a typed [BleMessage].
         * Trims trailing newlines/spaces before matching.
         */
        fun from(raw: String): BleMessage = when (raw.trim()) {
            "READY"         -> Ready
            "HEARTBEAT"     -> Heartbeat
            "IMPACT"        -> Impact
            "FALL_DETECTED" -> FallDetected
            else            -> Unknown(raw.trim())
        }
    }
}
