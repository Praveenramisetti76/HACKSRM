# SAHAY – BLE Fall Detection Integration Plan

> **Scope**: Android-side BLE Central implementation.  
> **Hardware**: Arduino Nano 33 BLE Sense Rev2 ("Sahay-Nano")  
> **No pushing** until implementation is verified locally.

---

## 🔌 BLE UUIDs (from Arduino firmware)

| Role | UUID |
|:---|:---|
| Service | `6E400001-B5A3-F393-E0A9-E50E24DCCA9E` |
| TX Characteristic (Notify) | `6E400003-B5A3-F393-E0A9-E50E24DCCA9E` |
| RX Characteristic (Write) | `6E400002-B5A3-F393-E0A9-E50E24DCCA9E` |
| CCCD Descriptor (subscribe) | `00002902-0000-1000-8000-00805f9b34fb` |

**Arduino Nano advertises device name**: `Sahay-Nano`

---

## 📂 Files to Create

| File | Package | Purpose |
|:---|:---|:---|
| `FallDetectionService.kt` | `ble` | Foreground service: BLE scan, GATT, reconnect, message routing |
| `BleMessage.kt` | `ble` | Sealed class for typed message parsing |
| `FallEventEntity.kt` | `database` | Room Entity for logging fall events |
| `FallEventDao.kt` | `database` | Room DAO for FallEventEntity |
| `FallAlertScreen.kt` | `screens` | Full-screen countdown alert UI |
| `BleDevicePairingScreen.kt` | `screens` | Scan / pair / disconnect device management UI |
| `BleViewModel.kt` | `ble` | StateFlow-based ViewModel for BLE connection state |

---

## 📂 Files to Modify

| File | Change |
|:---|:---|
| `AndroidManifest.xml` | Add BLE permissions, `FallDetectionService`, `FallAlertScreen` activity flags |
| `SahayDatabase.kt` (v3 → v4) | Add `FallEventEntity::class` to entities list |
| `HomeScreen.kt` | Add small BLE connection status dot in top-right status bar |
| `navigation/Screen.kt` | Add `FallAlert`, `BlePairing` routes |
| `app/build.gradle.kts` | No new dependencies needed — Android BLE is part of the SDK |

---

## Phase 1: Manifest & Permissions

### 1.1 – `AndroidManifest.xml` Changes

**Add BLE Permissions** (after existing permissions):
```xml
<!-- BLE Fall Detection Permissions (API 31+) -->
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />

<!-- BLE hardware feature declaration -->
<uses-feature android:name="android.hardware.bluetooth_le" android:required="false" />
```

**Register `FallDetectionService`** (inside `<application>`):
```xml
<service
    android:name=".ble.FallDetectionService"
    android:exported="false"
    android:foregroundServiceType="connectedDevice" />
```

---

## Phase 2: New Files

### 2.1 – `BleMessage.kt`
Package: `com.example.healthpro.ble`

```kotlin
sealed class BleMessage {
    object Ready           : BleMessage()
    object Heartbeat       : BleMessage()
    object Impact          : BleMessage()
    object FallDetected    : BleMessage()
    data class Unknown(val raw: String) : BleMessage()

    companion object {
        fun from(raw: String): BleMessage = when (raw.trim()) {
            "READY"         -> Ready
            "HEARTBEAT"     -> Heartbeat
            "IMPACT"        -> Impact
            "FALL_DETECTED" -> FallDetected
            else            -> Unknown(raw)
        }
    }
}
```

---

### 2.2 – `FallEventEntity.kt`
Package: `com.example.healthpro.database`

```kotlin
@Entity(tableName = "fall_events")
data class FallEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val wasConfirmedFall: Boolean,     // true = SOS was sent; false = "I'm OK" pressed
    val responseTimeSeconds: Int       // seconds between alert and user action (0–45)
)
```

**Update `SahayDatabase.kt`**:
- Add `FallEventEntity::class` to entities list.
- Bump version to `4`.
- `fallbackToDestructiveMigration()` is already set — no migration needed.

---

### 2.3 – `FallEventDao.kt`
Package: `com.example.healthpro.database`

```kotlin
@Dao
interface FallEventDao {
    @Insert
    suspend fun insert(event: FallEventEntity)

    @Query("SELECT * FROM fall_events ORDER BY timestamp DESC LIMIT 50")
    fun getRecentEvents(): Flow<List<FallEventEntity>>
}
```

---

### 2.4 – `BleViewModel.kt`
Package: `com.example.healthpro.ble`

StateFlows exposed to UI:

| StateFlow | Type | Description |
|:---|:---|:---|
| `connectionState` | `BleConnectionState` | `DISCONNECTED / SCANNING / CONNECTING / CONNECTED` |
| `deviceName` | `String?` | Name of connected device |
| `lastHeartbeatTime` | `Long?` | Epoch millis of last HEARTBEAT message |
| `fallAlertActive` | `Boolean` | Whether FallAlertScreen should be shown |

The ViewModel binds to `FallDetectionService` via a `ServiceConnection`.

---

### 2.5 – `FallDetectionService.kt`
Package: `com.example.healthpro.ble`

#### Initialization Sequence:
1. Show persistent foreground notification: `"Sahay-Nano monitoring active"`.
2. Check if Bluetooth is enabled on device.
3. Start `BluetoothLeScanner` filtered by:
   - Device name `"Sahay-Nano"` OR
   - Service UUID `6E400001-...`
4. On device found → stop scan → call `device.connectGatt(...)`.

#### GATT Callback Chain:
```
onConnectionStateChange(CONNECTED)
  └── discoverServices()
          └── onServicesDiscovered()
                  └── find TX Characteristic (6E400003)
                          └── setCharacteristicNotification(true)
                                  └── write CCCD descriptor (0x2902 = 0x01, 0x00)
                                          └── onDescriptorWrite() 
                                                  → "SUBSCRIBED, ready for messages"
```

#### On Notification Received:
```
onCharacteristicChanged()
  └── parse bytes as UTF-8 String
          └── BleMessage.from(raw)
                  READY        → log, update state to CONNECTED
                  HEARTBEAT    → update lastHeartbeatTime, broadcast to ViewModel
                  IMPACT       → vibrate 200ms, Log "BLE MSG: IMPACT"
                  FALL_DETECTED→ launch FallAlertScreen, vibrate 1000ms
```

#### Auto-Reconnect Logic:
```
onConnectionStateChange(DISCONNECTED)
  └── wait 3 seconds
          └── restart BLE scan
                  └── repeat until connected
```

---

### 2.6 – `FallAlertScreen.kt`
Package: `com.example.healthpro.screens`

**UI Layout:**
```
Full-screen red gradient background
    ┌────────────────────────────────┐
    │  ⚠️  FALL DETECTED             │
    │                                │
    │   Countdown Ring: 45s → 0s    │
    │                                │
    │  "Emergency SOS will be sent   │
    │   unless you respond."         │
    │                                │
    │  [  ✅  I'M OKAY  ]            │
    └────────────────────────────────┘
```

**Behaviour:**
- Timer starts at 45 seconds, counts down.
- Device vibrates every 5 seconds.
- If user taps **"I'M OKAY"**:
  - Stop timer.
  - Log `FallEventEntity(wasConfirmedFall = false, responseTimeSeconds = elapsed)`.
  - Dismiss screen.
- If timer reaches **0**:
  - Call `SOSManager.sendEmergencySMS(contacts, mapsLink)`.
  - Send WhatsApp message via `WhatsAppHelper`.
  - Place emergency call via Intent.
  - Log `FallEventEntity(wasConfirmedFall = true, responseTimeSeconds = 45)`.

---

### 2.7 – `BleDevicePairingScreen.kt`
Package: `com.example.healthpro.screens`

**UI Elements:**

| Element | Description |
|:---|:---|
| Status badge | `Connected` (green) / `Scanning` (amber pulse) / `Disconnected` (red) |
| Device name | Shows `"Sahay-Nano"` once discovered |
| Last heartbeat | `"Last seen: 2 seconds ago"` — updated live |
| Scan button | Starts BLE scan; grays out while scanning |
| Disconnect button | Closes GATT connection |

---

## Phase 3: Modify Existing Files

### 3.1 – `HomeScreen.kt`

Add a **small BLE status dot** in the top-right status row (alongside Signal/WiFi/Battery icons):

```kotlin
// Collect from BleViewModel
val connectionState by bleViewModel.connectionState.collectAsState()

val dotColor = when (connectionState) {
    BleConnectionState.CONNECTED    -> Color(0xFF22C55E)  // Green
    BleConnectionState.SCANNING     -> Color(0xFFF59E0B)  // Amber
    else                            -> Color(0xFFEF4444)  // Red
}

Box(
    modifier = Modifier
        .size(10.dp)
        .clip(CircleShape)
        .background(dotColor)
)
```

**Also add** a new FeatureCard in the grid:
```
Row 4: Fall Detection
Icon: Icons.Default.MonitorHeart
Label: "Fall\nDetector"
Gradient: Red → Dark Red
onClick → navController.navigate(Screen.BlePairing.route)
```

---

### 3.2 – `navigation/Screen.kt`

**Add new routes:**
```kotlin
object BlePairing  : Screen("ble_pairing")
object FallAlert   : Screen("fall_alert")
```

---

## Phase 4: Runtime Permission Handling

In `BleDevicePairingScreen`, before starting the scan, check and request:

```kotlin
val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT
    )
} else {
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
}
```

Use `rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions())`.

---

## Phase 5: Testing Checklist

### Logcat Tags to Monitor

| Tag | What it shows |
|:---|:---|
| `FallDetectionService` | BLE scan start/stop, GATT connection, subscription success |
| `BleMessage` | Every parsed message: `BLE MSG: HEARTBEAT` etc. |
| `FallAlertScreen` | Countdown ticks, button tap, SOS dispatch |

### Physical Test Sequence:
1. **Turn on "Sahay-Nano"** — Logcat: `BLE MSG: READY`
2. **Wait 5 seconds** — Logcat: `BLE MSG: HEARTBEAT`
3. **Simulate impact (shake)** — Logcat: `BLE MSG: IMPACT` (no SOS)
4. **Simulate fall (harder shake)** — Logcat: `BLE MSG: FALL_DETECTED` → FallAlertScreen launches
5. **Let countdown expire** → Verify SMS is sent to emergency contacts
6. **Repeat step 4**, tap **"I'M OKAY"** → Verify SOS is NOT sent

---

## 🔗 Integration Points with Existing SAHAY Code

| Existing Component | How BLE Uses It |
|:---|:---|
| `SOSManager.kt` | Called directly from `FallAlertScreen` on countdown expiry |
| `WhatsAppHelper.kt` | Called to send WhatsApp alert on confirmed fall |
| `SahayDatabase.kt` | Extended with `FallEventEntity` for fall event logs |
| `SafetyMonitoringService.kt` | Runs independently — BLE service is a separate companion service |
| `HomeScreen.kt` | Modified to show BLE dot status and a new FeatureCard |

---

## ⚠️ Key Notes

- **`FallDetectionService`** must use `foregroundServiceType="connectedDevice"` in Manifest (Android 14+ requirement).
- **CCCD Descriptor write** must happen **after** `onServicesDiscovered()` fires — common mistake is writing it too early.
- **Auto-reconnect delay** should be at least **3 seconds** to avoid GATT spam on rapid disconnect/reconnect.
- Arduino UUIDs are **uppercase** — Android's `UUID.fromString()` is case-insensitive, but always use the exact string to avoid confusion.
- The **45-second countdown** comes from domain knowledge about elderly fall response windows — do not shorten without discussion.
