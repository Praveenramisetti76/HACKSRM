package com.example.healthpro.safety

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log

/**
 * Battery-efficient motion detection using accelerometer.
 *
 * Strategy:
 *  - Uses SENSOR_DELAY_NORMAL (slowest) + maxReportLatency for batching
 *  - Detects significant motion via delta threshold
 *  - Re-registers periodically instead of constant polling
 *  - Calls [onMotionDetected] when meaningful movement is observed
 *
 * Lifecycle:
 *  - Call [start] when monitoring begins
 *  - Call [stop] when monitoring stops or service is destroyed
 */
class MotionTracker(
    private val context: Context,
    private val onMotionDetected: () -> Unit
) : SensorEventListener {

    companion object {
        private const val TAG = "MotionTracker"

        // Minimum acceleration change (m/s²) that counts as "motion"
        // Resting phone is ~9.8 (gravity), so we look for delta > 1.5
        private const val MOTION_THRESHOLD = 1.5f

        // Batch window: deliver sensor events every 30s to save battery
        private const val BATCH_LATENCY_US = 30_000_000 // 30 seconds in microseconds
    }

    private val sensorManager: SensorManager? =
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val accelerometer: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private var isFirstReading = true
    private var isRunning = false

    // ═══════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════

    /**
     * Start listening for motion events.
     * Uses batching for battery efficiency.
     *
     * @return true if sensor registered successfully
     */
    fun start(): Boolean {
        if (isRunning) return true
        if (accelerometer == null) {
            Log.w(TAG, "No accelerometer sensor available on this device")
            return false
        }

        isFirstReading = true
        val registered = sensorManager?.registerListener(
            this,
            accelerometer,
            SensorManager.SENSOR_DELAY_NORMAL,   // ~200ms interval
            BATCH_LATENCY_US                       // Batched delivery every 30s
        ) ?: false

        isRunning = registered
        Log.d(TAG, if (registered) "✅ Motion tracking started" else "❌ Failed to start motion tracking")
        return registered
    }

    /**
     * Stop listening for motion events. Safe to call multiple times.
     */
    fun stop() {
        if (!isRunning) return
        sensorManager?.unregisterListener(this)
        isRunning = false
        isFirstReading = true
        Log.d(TAG, "🛑 Motion tracking stopped")
    }

    /**
     * @return true if sensor is currently registered and listening
     */
    fun isActive(): Boolean = isRunning

    // ═══════════════════════════════════════════════════════════════
    // SENSOR CALLBACKS
    // ═══════════════════════════════════════════════════════════════

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        if (isFirstReading) {
            lastX = x
            lastY = y
            lastZ = z
            isFirstReading = false
            return
        }

        val deltaX = Math.abs(x - lastX)
        val deltaY = Math.abs(y - lastY)
        val deltaZ = Math.abs(z - lastZ)

        // Check if any axis exceeded motion threshold
        if (deltaX > MOTION_THRESHOLD || deltaY > MOTION_THRESHOLD || deltaZ > MOTION_THRESHOLD) {
            Log.d(TAG, "📳 Motion detected — Δx=${"%.2f".format(deltaX)}, Δy=${"%.2f".format(deltaY)}, Δz=${"%.2f".format(deltaZ)}")
            onMotionDetected()
        }

        lastX = x
        lastY = y
        lastZ = z
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for motion detection
    }
}
