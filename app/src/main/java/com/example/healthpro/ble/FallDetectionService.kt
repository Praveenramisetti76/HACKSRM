package com.example.healthpro.ble

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.ParcelUuid
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.healthpro.MainActivity
import com.example.healthpro.R
import com.example.healthpro.database.FallEventEntity
import com.example.healthpro.database.SahayDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * FallDetectionService — Foreground Service for BLE Fall Detection.
 *
 * Lifecycle:
 *  1. Scan for "Sahay-Nano" BLE peripheral (by name + Service UUID filter)
 *  2. Connect GATT → Discover services → Subscribe to TX notifications
 *  3. Parse incoming ASCII messages via [BleMessage]
 *  4. Route FALL_DETECTED to FallAlertScreen via broadcast
 *  5. Auto-reconnect on disconnect (3s delay, re-scan)
 *
 * Runs persistently as a foreground service with START_STICKY.
 */
@SuppressLint("MissingPermission")
class FallDetectionService : Service() {

    // ─── BLE UUIDs ────────────────────────────────────────────────────
    companion object {
        private const val TAG = "FallDetectionService"

        // UART-style BLE Service from Arduino Nano 33 BLE firmware
        val SERVICE_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        val TX_CHAR_UUID: UUID  = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
        val RX_CHAR_UUID: UUID  = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
        val CCCD_UUID: UUID     = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        const val DEVICE_NAME = "Sahay-Nano"

        // Broadcast action picked up by FallAlertScreen / Navigation
        const val ACTION_FALL_DETECTED = "com.example.healthpro.FALL_DETECTED"

        private const val NOTIFICATION_CHANNEL_ID = "sahay_ble_fall_detection"
        private const val NOTIFICATION_ID = 42002

        fun start(context: Context) {
            val intent = Intent(context, FallDetectionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FallDetectionService::class.java))
        }
    }

    // ─── State ────────────────────────────────────────────────────────
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var isScanning = false
    private var isConnected = false

    // ─── Database ─────────────────────────────────────────────────────
    private val db by lazy { SahayDatabase.getInstance(applicationContext) }

    // ═══════════════════════════════════════════════════════════════
    // SERVICE LIFECYCLE
    // ═══════════════════════════════════════════════════════════════

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🟢 FallDetectionService created")

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        // Push state to shared holder so UI can react
        BleStateHolder.updateConnectionState(BleConnectionState.DISCONNECTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "🟢 FallDetectionService started")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("🔵 Scanning for Sahay-Nano..."))
        startBleScan()
        return START_STICKY   // Restart if killed by system
    }

    override fun onDestroy() {
        Log.d(TAG, "🔴 FallDetectionService destroyed")
        stopBleScan()
        bluetoothGatt?.close()
        bluetoothGatt = null
        isConnected = false
        BleStateHolder.updateConnectionState(BleConnectionState.DISCONNECTED)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ═══════════════════════════════════════════════════════════════
    // BLE SCAN
    // ═══════════════════════════════════════════════════════════════

    private fun startBleScan() {
        val adapter = bluetoothAdapter ?: run {
            Log.e(TAG, "❌ Bluetooth not available")
            return
        }

        if (!adapter.isEnabled) {
            Log.w(TAG, "⚠️ Bluetooth is disabled — cannot scan")
            updateNotification("⚠️ Bluetooth is off — please enable it")
            return
        }

        if (isScanning) {
            Log.d(TAG, "Already scanning, skipping")
            return
        }

        Log.d(TAG, "🔍 Starting BLE scan for '$DEVICE_NAME'...")
        BleStateHolder.updateConnectionState(BleConnectionState.SCANNING)
        updateNotification("🔍 Scanning for Sahay-Nano...")

        val scanner = adapter.bluetoothLeScanner ?: return

        // Filter by Service UUID — most reliable method
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        isScanning = true
        scanner.startScan(listOf(filter), settings, scanCallback)
    }

    private fun stopBleScan() {
        if (!isScanning) return
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            Log.d(TAG, "🛑 BLE scan stopped")
        } catch (e: Exception) {
            Log.w(TAG, "Scan stop exception (ignored): ${e.message}")
        }
        isScanning = false
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = device.name ?: result.scanRecord?.deviceName ?: "Unknown"

            // Accept by UUID (already filtered) or by device name
            if (name != DEVICE_NAME && !name.contains("Sahay", ignoreCase = true)) return

            Log.d(TAG, "✅ Found device: $name (${device.address})")
            stopBleScan()
            connectToDevice(device)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "❌ BLE scan failed with error: $errorCode")
            isScanning = false
            scheduleReconnect()
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // GATT CONNECTION
    // ═══════════════════════════════════════════════════════════════

    private fun connectToDevice(device: BluetoothDevice) {
        Log.d(TAG, "🔗 Connecting GATT to ${device.address}...")
        BleStateHolder.updateConnectionState(BleConnectionState.CONNECTING)
        updateNotification("🔗 Connecting to Sahay-Nano...")

        bluetoothGatt = device.connectGatt(
            this,
            false,              // autoConnect=false for faster initial connect
            gattCallback,
            BluetoothDevice.TRANSPORT_LE
        )
    }

    private val gattCallback = object : BluetoothGattCallback() {

        // ── Step 1: Connection state changed ─────────────────────────
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    isConnected = true
                    Log.d(TAG, "✅ GATT Connected — discovering services...")
                    BleStateHolder.updateConnectionState(BleConnectionState.CONNECTED)
                    BleStateHolder.updateDeviceName(gatt.device.name ?: DEVICE_NAME)
                    updateNotification("✅ Sahay-Nano Connected")
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    isConnected = false
                    Log.w(TAG, "🔌 GATT Disconnected (status=$status)")
                    BleStateHolder.updateConnectionState(BleConnectionState.DISCONNECTED)
                    BleStateHolder.updateDeviceName(null)
                    updateNotification("🔌 Sahay-Nano Disconnected — reconnecting...")
                    gatt.close()
                    bluetoothGatt = null
                    scheduleReconnect()
                }
            }
        }

        // ── Step 2: Services discovered ──────────────────────────────
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "❌ onServicesDiscovered failed: status=$status")
                return
            }

            val service = gatt.getService(SERVICE_UUID)
            val txChar  = service?.getCharacteristic(TX_CHAR_UUID)

            if (txChar == null) {
                Log.e(TAG, "❌ TX Characteristic not found — wrong firmware?")
                return
            }

            Log.d(TAG, "📡 Subscribing to TX Characteristic notifications...")
            subscribeToNotifications(gatt, txChar)
        }

        // ── Step 3: Descriptor written (CCCD = subscribed) ───────────
        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "✅ CCCD written — subscribed to TX notifications. Ready for BLE messages.")
            } else {
                Log.e(TAG, "❌ CCCD write failed (status=$status)")
            }
        }

        // ── Step 4: Notification received from Nano ───────────────────
        @Deprecated("Deprecated in API 33; kept for compatibility")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val rawBytes = characteristic.value ?: return
            val rawText  = String(rawBytes, Charsets.UTF_8).trim()
            handleBleMessage(rawText)
        }

        // API 33+ override (both must exist for compatibility)
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            val rawText = String(value, Charsets.UTF_8).trim()
            handleBleMessage(rawText)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // CCCD SUBSCRIPTION
    // ═══════════════════════════════════════════════════════════════

    private fun subscribeToNotifications(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) {
        // Tell Android to deliver callbacks for this characteristic
        gatt.setCharacteristicNotification(characteristic, true)

        // Write CCCD descriptor to tell the peripheral to start sending
        val descriptor = characteristic.getDescriptor(CCCD_UUID)
        if (descriptor == null) {
            Log.e(TAG, "❌ CCCD descriptor not found on TX characteristic")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // MESSAGE HANDLING
    // ═══════════════════════════════════════════════════════════════

    private fun handleBleMessage(raw: String) {
        Log.d(TAG, "BLE MSG: $raw")          // Key Logcat line for testing
        BleStateHolder.updateLastRawMessage(raw)

        when (BleMessage.from(raw)) {

            is BleMessage.Ready -> {
                Log.d(TAG, "🟢 Sahay-Nano is READY")
                BleStateHolder.updateConnectionState(BleConnectionState.CONNECTED)
            }

            is BleMessage.Heartbeat -> {
                Log.d(TAG, "💓 HEARTBEAT received")
                BleStateHolder.updateHeartbeat()
            }

            is BleMessage.Impact -> {
                Log.w(TAG, "⚡ IMPACT detected — vibrating (no SOS)")
                vibrateDevice(200L)   // Short buzz — alert but not SOS
            }

            is BleMessage.FallDetected -> {
                Log.w(TAG, "🚨 FALL_DETECTED — launching FallAlertScreen!")
                vibrateDevice(1000L)
                updateNotification("🚨 FALL DETECTED — responding?")
                broadcastFallDetected()
            }

            is BleMessage.Unknown -> {
                Log.d(TAG, "❓ Unknown BLE message: $raw")
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // FALL DETECTED → BROADCAST
    // ═══════════════════════════════════════════════════════════════

    private fun broadcastFallDetected() {
        // BleStateHolder flag so BleViewModel can observe
        BleStateHolder.signalFallDetected()

        // Also send a local broadcast so FallAlertScreen can launch
        // even if the app is in the background
        val broadcastIntent = Intent(ACTION_FALL_DETECTED).apply {
            setPackage(packageName)
        }
        sendBroadcast(broadcastIntent)

        Log.d(TAG, "📢 FALL_DETECTED broadcast sent")
    }

    // ═══════════════════════════════════════════════════════════════
    // AUTO-RECONNECT
    // ═══════════════════════════════════════════════════════════════

    private fun scheduleReconnect() {
        serviceScope.launch {
            Log.d(TAG, "⏳ Reconnect scheduled in 3 seconds...")
            delay(3_000L)
            if (!isConnected) {
                Log.d(TAG, "🔁 Attempting reconnect...")
                startBleScan()
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // VIBRATION
    // ═══════════════════════════════════════════════════════════════

    private fun vibrateDevice(durationMs: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(durationMs)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibration failed: ${e.message}")
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // NOTIFICATION
    // ═══════════════════════════════════════════════════════════════

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Fall Detection (BLE)",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "SAHAY BLE wearable monitoring — Sahay-Nano"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String = "🔵 Monitoring for falls..."): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("SAHAY Fall Detection")
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
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification(statusText))
    }
}
