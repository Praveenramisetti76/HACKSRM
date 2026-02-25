# SAHAY — Complete Technical Documentation

> **Version:** 1.0.0  
> **Last Updated:** February 25, 2026  
> **Codebase:** 99 Kotlin files · 19,000+ lines of code  
> **Architecture:** MVVM + Clean Architecture  

---

## Table of Contents

1. [Application Overview](#1-application-overview)
2. [Application Lifecycle & Entry Point](#2-application-lifecycle--entry-point)
3. [Authentication Module](#3-authentication-module)
4. [Navigation System](#4-navigation-system)
5. [Home Screen](#5-home-screen)
6. [Genie — Voice AI Assistant](#6-genie--voice-ai-assistant)
7. [Safety Monitoring System](#7-safety-monitoring-system-guardian-angel)
8. [Emergency SOS System](#8-emergency-sos-system)
9. [Medicine Management Module](#9-medicine-management-module)
10. [Mood Check-In System](#10-mood-check-in-system)
11. [Hospital Finder](#11-hospital-finder)
12. [Call Family Module](#12-call-family-module)
13. [Memories & Photos](#13-memories--photos-module)
14. [Food & Cabs Ordering](#14-food--cabs-ordering)
15. [DataHaven — Decentralized Storage](#15-datahaven--decentralized-medical-storage)
16. [Database Architecture](#16-database-architecture)
17. [Background Workers](#17-background-workers)
18. [Permissions Model](#18-permissions-model)
19. [UI Design System](#19-ui-design-system)
20. [Security Architecture](#20-security-architecture)
21. [End-to-End User Workflows](#21-end-to-end-user-workflows)

---

## 1. Application Overview

**SAHAY** (Hindi: *साहय* — "companion") is a native Android application designed as an elder-care launcher. It replaces the standard smartphone experience with a simplified, voice-first, safety-aware interface.

### Core Problem

Modern smartphones are hostile to elderly users:
- Tiny UI elements cause misclicks and frustration
- Complex navigation creates dependency on family
- No passive safety monitoring for falls or inactivity
- Medication management is fragmented across multiple apps
- Elders face scam calls and digital isolation

### Solution Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                        SAHAY Android App                              │
│                                                                      │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐  ┌─────────────┐   │
│  │  Jetpack    │  │  ViewModels │  │    Room    │  │ WorkManager │   │
│  │  Compose UI │←→│   (MVVM)   │←→│  Database  │  │   Workers   │   │
│  └────────────┘  └────────────┘  └────────────┘  └─────────────┘   │
│        ↑               ↑               ↑                ↑           │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐  ┌─────────────┐   │
│  │ Navigation │  │Repositories│  │   Sensors  │  │ Foreground  │   │
│  │  Compose   │  │  + DAOs    │  │Acceleromtr │  │  Service    │   │
│  └────────────┘  └────────────┘  └────────────┘  └─────────────┘   │
│                        ↑                                             │
│              ┌─────────┴──────────┐                                  │
│              │   Retrofit + API   │                                  │
│              └─────────┬──────────┘                                  │
└────────────────────────┼─────────────────────────────────────────────┘
                         ↓
               ┌──────────────────┐
               │  DataHaven       │
               │  Backend (TS)    │
               └────────┬─────────┘
                        ↓
               ┌──────────────────┐
               │  DataHaven       │
               │  Blockchain      │
               └──────────────────┘
```

---

## 2. Application Lifecycle & Entry Point

### `MainActivity.kt`

The single Activity serves as the entry point. It uses Jetpack Compose for the entire UI.

**Lifecycle Flow:**

```
onCreate()
  ├── enableEdgeToEdge()                     → Immersive full-screen mode
  ├── Initialize SafetyPreferences           → Load safety monitoring config
  ├── setContent { HealthProTheme }          → Material 3 theme wrapper
  │     ├── MoodCheckInViewModel             → Scoped to Activity lifecycle
  │     ├── LaunchedEffect(Unit)             → Trigger mood check-in on app open
  │     ├── SahayNavGraph()                  → Compose Navigation host
  │     └── MoodCheckInDialog()              → Overlay dialog if mood pending
  └── Restart SafetyMonitoringService        → If monitoring was enabled before kill

onResume()
  └── (Mood handled by LaunchedEffect)

dispatchTouchEvent()
  └── Records EVERY touch to SafetyPreferences   → For inactivity detection
      (ACTION_DOWN and ACTION_MOVE tracked)

onDestroy()
  └── Cancel WorkManager if monitoring disabled
```

**Key Design Decision:** `dispatchTouchEvent()` intercepts ALL touch events (before any view processes them) to track user activity. This is the most reliable way to detect that the elder is actively using the phone.

---

## 3. Authentication Module

### Files
```
auth/
├── AuthPreferences.kt      → SharedPreferences wrapper for session state
├── AuthRepository.kt       → OTP generation, verification, session management
├── AuthViewModel.kt        → 3-step auth state machine
├── OtpManager.kt           → OTP generation + validation logic
└── ui/
    ├── EmailScreen.kt       → Email input screen (Compose)
    ├── OtpScreen.kt         → 6-digit OTP verification screen
    └── NameSetupScreen.kt   → Preferred name configuration
```

### Authentication Flow

```
                    ┌──────────────┐
                    │  App Launch  │
                    └──────┬───────┘
                           ↓
                    ┌──────────────┐
                    │ isLoggedIn?  │
                    └──────┬───────┘
                     Yes ↙   ↘ No
                    ↓           ↓
              ┌──────────┐  ┌─────────────┐
              │HomeScreen│  │ EmailScreen  │
              └──────────┘  └──────┬──────┘
                                   ↓ submitEmail()
                            ┌─────────────┐
                            │  OtpScreen   │ ← OTP displayed (test mode)
                            └──────┬──────┘
                                   ↓ verifyOtp()
                            ┌─────────────┐
                            │NameSetupScreen│
                            └──────┬──────┘
                                   ↓ submitName()
                            ┌─────────────┐
                            │  HomeScreen  │
                            └─────────────┘
```

### State Machine (`AuthScreenState`)

| State | Trigger | Next State |
|:--|:--|:--|
| `Idle` | User opens app | `Loading` (on email submit) |
| `Loading` | Email submitted | `OtpSent` |
| `OtpSent` | OTP verified | `OtpVerified` |
| `OtpVerified` | Name entered | `SetupComplete` |
| `Error` | Any validation failure | Back to previous state |

### OTP System

- **Generation:** 6-digit random OTP via `OtpManager`
- **Expiry:** Configurable timeout
- **Attempts:** Max retry limit with remaining count shown
- **Resend:** Cooldown timer prevents spam
- **Storage:** OTP session stored in `AuthPreferences` (SharedPreferences)

### Validation Rules

| Field | Rule |
|:--|:--|
| Email | Non-empty + Android `Patterns.EMAIL_ADDRESS` regex |
| OTP | Exactly 6 digits |
| Name | 2-30 characters, letters and spaces only (`^[a-zA-Z ]+$`) |

---

## 4. Navigation System

### Files
```
navigation/
└── Navigation.kt   → SahayNavGraph, Screen sealed class, BottomNavBar
```

### Screen Routes

```kotlin
sealed class Screen(val route: String) {
    object Home           : Screen("home")
    object Memories       : Screen("memories")
    object Genie          : Screen("genie")
    object FoodOrder      : Screen("food_order")
    object CallFamily     : Screen("call_family")
    object Emergency      : Screen("emergency")
    object Inactivity     : Screen("inactivity")
    object Settings       : Screen("settings")
    object MedicineOrder  : Screen("medicine_order")
    object Medicine       : Screen("medicine")
    object AuthEmail      : Screen("auth_email")
    object AuthOtp        : Screen("auth_otp")
    object AuthNameSetup  : Screen("auth_name_setup")
}
```

### Bottom Navigation Bar

3 persistent tabs:
1. **Home** → `Screen.Home`
2. **Photos** → `Screen.Memories`
3. **Settings** → `Screen.Settings`

### Nav Graph Flow

```
SahayNavGraph()
  ├── Auth Flow (if not logged in)
  │    ├── AuthEmail → AuthOtp → AuthNameSetup
  │    └── Redirect to Home on SetupComplete
  ├── Main Flow
  │    ├── Home (start destination)
  │    ├── Memories
  │    ├── Settings
  │    ├── Genie
  │    ├── CallFamily
  │    ├── Emergency
  │    ├── Inactivity
  │    ├── FoodOrder
  │    ├── Medicine
  │    └── MedicineOrder
  └── Bottom Bar (visible on Home, Memories, Settings)
```

---

## 5. Home Screen

### Layout Architecture

```
┌──────────────────────────────┐
│  SAHAY          📶 📡 🔋     │  ← Status bar (brand + icons)
│                              │
│  Good Morning, Grandpa       │  ← Personalized greeting (AuthPreferences)
│  14:27                       │  ← Large time display
│  TUESDAY, FEB 25             │  ← Current date
│                              │
│       ┌──────────┐           │
│       │    🎤    │           │  ← Genie button (animated glow)
│       │  GENIE   │           │     Infinite transition: pulse + scale
│       └──────────┘           │
│                              │
│  ┌────────┐  ┌────────┐     │
│  │📞 Call │  │📸      │     │  ← Feature grid (2x3)
│  │ Family │  │Memories│     │     Each card: gradient background
│  └────────┘  └────────┘     │     + icon + label
│  ┌────────┐  ┌────────┐     │
│  │💊 Med  │  │🍔 Food │     │
│  │Manager │  │& Cabs  │     │
│  └────────┘  └────────┘     │
│  ┌────────┐  ┌────────┐     │
│  │🚑 Emrg │  │🛡️Safety│     │
│  │ Help   │  │Monitor │     │
│  └────────┘  └────────┘     │
└──────────────────────────────┘
```

### Genie Button Animation

- **Glow Alpha:** Infinite transition 0.3→0.7 (2s tween, reverse)
- **Scale:** Infinite transition 0.95→1.05 (breathing effect)
- **Visual:** Radial gradient (`PurpleAccent` → `PurpleGlow` → `BlueAccent` → Transparent)
- **Inner circle:** Linear gradient (`PurpleAccent` → `BlueAccent`) with 8dp shadow

### Feature Cards

Each `FeatureCard` composable:
- Height: 110dp
- Shape: RoundedCornerShape(20dp)
- Background: `Brush.linearGradient` with custom color pairs
- Contains: Icon (32dp, top-start) + Label (bottom-start)

---

## 6. Genie — Voice AI Assistant

### Files
```
genie/
├── GenieViewModel.kt              → State machine + speech recognition
├── GenieIntentParser.kt            → Rule-based NLP with Hinglish support
├── GenieAccessibilityService.kt    → Deep app automation (460 lines)
├── PlatformLauncher.kt             → App launching engine
├── FlowConfigManager.kt            → JSON config loader for UI flows
├── UiFlowConfig.kt                 → Flow step definitions
├── ConsentManager.kt               → User consent handling
├── FeatureFlags.kt                 → Feature toggles
└── MedicineRepository.kt           → Medicine search via Genie
```

### State Machine

```
IDLE → LISTENING → PROCESSING → CONFIRMING → LAUNCHING → AUTOMATING → DONE
                                                                      ↘ ERROR
```

| State | Description |
|:--|:--|
| `IDLE` | Waiting for user to tap mic |
| `LISTENING` | SpeechRecognizer active, capturing audio |
| `PROCESSING` | Intent parser analyzing recognized text |
| `CONFIRMING` | Showing parsed intent for user confirmation |
| `LAUNCHING` | Opening target app via deep link |
| `AUTOMATING` | AccessibilityService executing UI flow steps |
| `DONE` | Flow complete (stopped before payment) |
| `ERROR` | Any failure in the pipeline |

### Voice Recognition Pipeline

```
┌─────────┐     ┌──────────────────┐     ┌───────────────────┐
│  User   │────→│ SpeechRecognizer │────→│ GenieIntentParser │
│  Voice  │     │ (on-device)      │     │ parse(rawText)    │
└─────────┘     └──────────────────┘     └────────┬──────────┘
                                                   ↓
                                          ┌────────────────┐
                                          │  GenieIntent   │
                                          │  {type, item,  │
                                          │   platform}    │
                                          └────────┬───────┘
                                                   ↓
                                          ┌────────────────┐
                                          │ Confirm with   │
                                          │ user → proceed │
                                          └────────┬───────┘
                                                   ↓
                                     ┌─────────────────────────┐
                                     │ PlatformLauncher        │
                                     │ → deep link OR          │
                                     │ → AccessibilityService  │
                                     └─────────────────────────┘
```

### Intent Parser (GenieIntentParser)

**Rule-based NLP** with keyword matching and Hinglish normalization.

#### Supported Intent Types

| IntentType | Trigger Keywords |
|:--|:--|
| `FOOD` | eat, food, hungry, lunch, dinner, order, pizza, biryani... |
| `CAB` | cab, taxi, ride, auto, uber, ola, drive, pickup... |
| `CALL` | call, phone, ring, dial, contact, video call... |
| `MEDICINE` | medicine, prescription, pills, tablet, pharmacy, reorder... |
| `EMERGENCY` | emergency, help, sos, ambulance, accident, hospital... |

#### Hinglish Normalization

Maps romanized Hindi words to English:
```
"khana" → "food"    "dawai" → "medicine"    "bulao" → "call"
"gaadi" → "cab"     "goli" → "tablet"       "mujhe" → "me"
"khane" → "food"    "hospital" → "hospital"  "chahiye" → "want"
```

**Example:** `"mujhe pizza khana hai"` → `"me pizza food want"` → Intent: FOOD, item: "pizza"

#### Platform Detection

Detects specific app mentions:
```
"swiggy" → Platform.SWIGGY     "uber" → Platform.UBER
"zomato" → Platform.ZOMATO     "ola" → Platform.OLA
"apollo" → Platform.APOLLO     "dominos" → Platform.DOMINOS
"1mg" → Platform.ONEMG         "netmeds" → Platform.NETMEDS
```

If no platform mentioned, uses default for intent type (e.g., FOOD → SWIGGY).

### Accessibility Service (GenieAccessibilityService)

**Deep app automation** using Android's AccessibilityService API.

#### Flow Steps

Each automation is a sequence of `FlowStep` objects:

| Step Type | Action |
|:--|:--|
| `WaitForNode` | Wait until a UI element appears (with timeout) |
| `ClickNode` | Find and click a UI element |
| `TypeText` | Find an input field and type text (`QUERY` = user's search) |
| `PerformIme` | Submit the keyboard (search/go/done) |
| `ClickFirstMatch` | Click the first result in a list |
| `Scroll` | Scroll forward/backward |
| `Delay` | Wait for content to load |
| `StopBeforePayment` | **SAFETY STOP** — halts before any payment |
| `StopForAuth` | Stops when OTP/captcha/sign-in detected |

#### Node Selector Precedence

```
1. resourceId        → Most stable (e.g., "com.swiggy:id/search_bar")
2. text              → Exact text match
3. textContains      → Partial text match
4. contentDescription → Accessibility label
5. className         → Widget type (e.g., "android.widget.EditText")
6. useFirstClickable → Last resort fallback
```

If primary selector fails, `alternateSelectors` are tried in order.

#### Safety Guarantees

- ❌ **Never** taps "Place Order" / "Pay" / "Confirm Payment"
- ❌ **Never** enters payment information
- ✅ Stops at checkout — user makes final tap
- ✅ Stops on OTP/Captcha detection
- ✅ Logs contain only step name + pass/fail (no personal data)

#### Example Flow: "I want pizza" via Swiggy

```
Step 1: WaitForNode(Swiggy home loaded)           ✅
Step 2: ClickNode(search bar)                      ✅
Step 3: TypeText(search bar, "pizza")              ✅
Step 4: PerformIme(submit search)                  ✅
Step 5: Delay(2000ms, wait for results)            ✅
Step 6: ClickFirstMatch(first restaurant result)   ✅
Step 7: StopBeforePayment()                        ⏹️ USER TAKES OVER
```

### Flow Configs (JSON)

Stored in `res/raw/flow_configs.json`. Can be updated remotely.

```json
{
  "platformId": "SWIGGY",
  "version": 1,
  "appName": "Swiggy",
  "packageName": "in.swiggy.android",
  "steps": [...]
}
```

---

## 7. Safety Monitoring System (Guardian Angel)

### Files
```
safety/
├── SafetyMonitoringService.kt    → Foreground service (375 lines)
├── InactivityManager.kt          → Touch + motion timeout logic
├── MotionTracker.kt              → Accelerometer sensor tracking
├── SafetyMonitoringWorker.kt     → WorkManager watchdog
├── SafetyPreferences.kt          → Configuration + timestamps
├── SafetyViewModel.kt            → UI state management
└── VoiceCheckManager.kt          → "Are you okay?" voice system
```

### Architecture

```
┌────────────────────────────────────────────────────────┐
│            SafetyMonitoringService                      │
│            (Foreground Service)                         │
│                                                        │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐ │
│  │MotionTracker │  │Inactivity    │  │VoiceCheck    │ │
│  │(Acceleromtr) │  │Manager       │  │Manager       │ │
│  │              │→ │(Touch+Motion │→ │("Are you     │ │
│  │Detects motion│  │ timeout)     │  │  okay?")     │ │
│  └──────────────┘  └──────────────┘  └──────────────┘ │
│         ↕                  ↓                  ↓        │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐ │
│  │SafetyPrefs   │  │ Inactivity   │  │  No response │ │
│  │(Timestamps)  │  │  Detected!   │  │  in 60s?     │ │
│  └──────────────┘  └──────────────┘  └──────┬───────┘ │
│                                              ↓        │
│                                     ┌──────────────┐  │
│                                     │  TRIGGER SOS │  │
│                                     │  (auto SMS + │  │
│                                     │   call + GPS)│  │
│                                     └──────────────┘  │
└────────────────────────────────────────────────────────┘
```

### MotionTracker

- **Sensor:** `TYPE_ACCELEROMETER` with `SENSOR_DELAY_NORMAL`
- **Batching:** 30-second `maxReportLatency` for battery efficiency
- **Motion Threshold:** Δ > 1.5 m/s² on any axis (filters gravity noise)
- **Battery Impact:** Minimal — batched delivery, no GPS

### InactivityManager

**Dual-condition trigger:** Both must be true simultaneously:
1. No screen touch for ≥ threshold
2. No physical motion for ≥ threshold

**Configuration:**
- Default threshold: 6 hours (configurable in settings)
- Check interval: threshold / 3 (minimum 5 seconds)
- Sleep hours gate: No triggers during configured sleep window
- One-shot alert: Won't re-trigger until explicitly reset

**Check Logic:**
```
performInactivityCheck():
  IF sleep hours active → SKIP
  IF alert already active → SKIP
  
  timeSinceTouch = now - lastTouchTimestamp
  timeSinceMotion = now - lastMotionTimestamp
  
  IF timeSinceTouch >= threshold AND timeSinceMotion >= threshold:
    → TRIGGER inactivity alert
    → Log to safety timeline
    → Call onInactivityDetected()
```

### SafetyMonitoringService (Foreground Service)

**Service Type:** `FOREGROUND_SERVICE_SPECIAL_USE`

**Lifecycle:**
- Started via `SafetyMonitoringService.start(context)` 
- Persists across app kills via foreground notification
- WorkManager watchdog (`SafetyMonitoringWorker`) restarts if killed

**Inactivity → SOS Pipeline:**
```
Inactivity Detected
    ↓
Voice check enabled?
    ↓ Yes                    ↓ No
Start voice prompt:       Direct SOS trigger
"Are you okay?"
    ↓
Wait 60 seconds
    ↓
No response?
    ↓
triggerSOSFromInactivity()
    ├── 1. Get GPS location (FusedLocationProvider)
    ├── 2. Generate Google Maps link
    ├── 3. Build inactivity SOS message
    ├── 4. Load emergency contacts from ContactsRepository
    ├── 5. Send SMS to ALL contacts (silent, background)
    ├── 6. Send WhatsApp to ALL contacts
    ├── 7. Call emergency number (112)
    └── 8. Update notification: "⚠️ SOS sent"
```

---

## 8. Emergency SOS System

### Files
```
sos/
├── SOSManager.kt       → SMS, WhatsApp, call orchestration
└── SosCallManager.kt   → Phone call management
```

### SOSManager Operations

| Operation | Method | Description |
|:--|:--|:--|
| **SMS** | `sendEmergencySMS()` | Uses `SmsManager.sendMultipartTextMessage` — **silent, background, no UI** |
| **WhatsApp** | `sendEmergencyWhatsApp()` | Intent.ACTION_SEND targeted to WhatsApp package |
| **Call** | `callEmergencyNumber()` | `ACTION_CALL` — auto-dials, no confirmation dialog |

### Message Templates

**Manual SOS:**
```
🚨 EMERGENCY ALERT 🚨

This is an automated SOS from [Name]'s SAHAY app.

They need immediate help!

📍 Live Location: [Google Maps Link]

Please respond immediately.
— Sent by SAHAY Safety System
```

**Inactivity SOS:**
```
⚠️ INACTIVITY ALERT ⚠️

[Name]'s SAHAY app has detected no activity for [X hours].
A voice check was attempted but received no response.

This may indicate a fall, medical emergency, or other concern.

📍 Last Known Location: [Google Maps Link]

Please check on them immediately.
— Sent by SAHAY Safety System
```

### Emergency Screen Flow

```
User taps "Emergency Help"
    ↓
┌──────────────────────────────┐
│     EMERGENCY SCREEN         │
│                              │
│    ┌──────────────────┐      │
│    │   🔴 SOS BUTTON  │      │  ← Big red button
│    │     (45 sec)     │      │  ← Countdown timer
│    └──────────────────┘      │
│                              │
│    [Cancel]                  │  ← Can cancel during countdown
│                              │
│    Nearby Hospitals:         │  ← HospitalFinder results
│    🏥 City Hospital (2.1km) │
│    🏥 Apollo (3.5km)        │
└──────────────────────────────┘
    ↓ Timer expires
    ├── Send SMS to all contacts
    ├── Send WhatsApp to all contacts
    ├── Call 112 (India emergency)
    └── Show "SOS Sent" confirmation
```

---

## 9. Medicine Management Module

### Files
```
medicine/
├── data/
│   ├── db/
│   │   ├── MedicineManagerEntity.kt    → Medicine data model
│   │   ├── MedicineManagerDao.kt       → CRUD operations
│   │   ├── MedicineManagerDatabase.kt  → Room database
│   │   ├── IntakeLogEntity.kt          → Daily intake records
│   │   ├── IntakeLogDao.kt             → Intake CRUD
│   │   ├── PrescriptionEntity.kt       → Prescription storage
│   │   └── PrescriptionDao.kt          → Prescription CRUD
│   ├── pharmacy/
│   │   ├── PharmacyProvider.kt         → Base pharmacy interface
│   │   ├── ApolloProvider.kt           → Apollo Pharmacy integration
│   │   ├── Tata1mgProvider.kt          → Tata 1mg integration
│   │   └── PharmacyAppChecker.kt       → Check installed pharmacy apps
│   └── repository/
│       └── MedicineManagerRepository.kt → Repository with stock calculation
├── domain/model/
│   └── PharmacyModels.kt               → Pharmacy domain models
├── ocr/
│   ├── OCRProcessor.kt                 → ML Kit text recognition
│   └── MedicineParser.kt               → Extract medicine names from OCR text
├── reminders/
│   ├── MedicineReminderScheduler.kt    → Schedule notifications
│   └── MedicineReminderWorker.kt       → WorkManager notification worker
├── ui/
│   ├── MedicineListScreen.kt           → Medicine inventory list
│   ├── MedicineDetailScreen.kt         → Single medicine detail view
│   ├── AddEditMedicineScreen.kt        → Add/edit medicine form
│   ├── AddEditMedicineDialog.kt        → Quick add dialog
│   ├── IntakeTrackerScreen.kt          → Daily intake logging
│   ├── PrescriptionVaultScreen.kt      → Prescription gallery
│   ├── OrderStatusScreen.kt            → Pharmacy order tracking
│   ├── ReorderConfirmationScreen.kt    → Reorder confirmation
│   └── ExtractedMedicinesSuggestionDialog.kt → OCR results dialog
├── vault/
│   └── WhatsAppHelper.kt              → Share prescriptions via WhatsApp
├── viewmodel/
│   ├── MedicineManagerViewModel.kt     → Main medicine state
│   ├── MedicineDetailViewModel.kt      → Detail view state
│   ├── PrescriptionVaultViewModel.kt   → Vault state
│   └── ReorderViewModel.kt            → Reorder flow state
└── workers/
    ├── DailyResetScheduler.kt          → Schedule daily intake reset
    ├── DailyResetWorker.kt             → Reset "taken today" flags
    ├── MedicineStockScheduler.kt       → Schedule stock checks
    └── MedicineStockWorker.kt          → Background stock monitoring
```

### Medicine Entity (Data Model)

```kotlin
MedicineManagerEntity(
    id: Long,
    name: String,                    // "Amlodipine"
    dosage: String,                  // "5mg"
    frequency: String,               // "Once daily"
    totalQuantity: Int,              // 30 (pills purchased)
    dosesPerDay: Int,                // 1
    reorderThreshold: Int,           // 5 (alert when ≤5 remaining)
    isTakenToday: Boolean,           // Quick access flag
    lastTakenDate: Long?,            // Timestamp of last intake
    notes: String?,                  // Optional notes
    prescriptionId: Long?,           // Link to scanned prescription
    createdAt: Long,
    updatedAt: Long
)
```

### Stock Calculation

```
remaining = totalQuantity - totalTakenCount
needsReorder = remaining <= reorderThreshold
```

Stock is computed via `MedicineManagerRepository.MedicineWithStock`:
- `totalTaken` from `IntakeLogDao.getTotalTakenCount()`
- `remaining` from entity's `remainingQuantity()` method
- `needsReorder` from entity's `needsReorder()` method

### OCR Pipeline (Prescription Scanning)

```
User captures/selects document
    ↓
┌──────────────────────────────────┐
│          OCRProcessor            │
│                                  │
│  Image? → extractTextFromImage() │
│  PDF?   → extractTextFromPdf()   │
│            ↓                     │
│  Render each page to Bitmap      │
│  (2x resolution for accuracy)    │
│            ↓                     │
│  ML Kit TextRecognition          │
│  .process(InputImage)            │
│            ↓                     │
│  Raw text result                 │
└────────────┬─────────────────────┘
             ↓
┌──────────────────────────────────┐
│        MedicineParser            │
│                                  │
│  Extracts medicine names from    │
│  raw OCR text using patterns     │
│  and medical terminology         │
└────────────┬─────────────────────┘
             ↓
┌──────────────────────────────────┐
│  ExtractedMedicinesSuggestion    │
│  Dialog                          │
│                                  │
│  User confirms/edits detected    │
│  medicines → Auto-add to list    │
└──────────────────────────────────┘
```

### Pharmacy Integration

Deep-linking to pharmacy apps for reordering:

| Provider | Package Name | Action |
|:--|:--|:--|
| Apollo Pharmacy | `in.apollo.android` | Search medicine by name |
| Tata 1mg | `com.tatahealth.consumer` | Search medicine by name |
| Netmeds | `com.netmeds.app` | Open app |
| PharmEasy | `com.pharmeasy.app` | Open app |

`PharmacyAppChecker` verifies which apps are installed before showing options.

### Background Workers

| Worker | Schedule | Purpose |
|:--|:--|:--|
| `MedicineReminderWorker` | Per-medicine schedule | Push notification for each dose |
| `DailyResetWorker` | Daily at midnight | Reset `isTakenToday` flags |
| `MedicineStockWorker` | Daily | Check stock levels, trigger reorder alerts |

---

## 10. Mood Check-In System

### Files
```
mood/
├── MoodCheckInDialog.kt       → Emoji-based mood selector UI
├── MoodCheckInViewModel.kt    → State + pattern detection (241 lines)
├── MoodCheckInWorker.kt       → Scheduled check-in trigger
├── MoodDao.kt                 → Room DAO for mood entries
├── MoodEntity.kt              → Mood data model
└── MoodRepository.kt          → Data access + pattern analysis
```

### Mood Types

| Emoji | MoodType | Numeric Value |
|:--|:--|:--|
| 😊 | `GREAT` | 5 |
| 🙂 | `GOOD` | 4 |
| 😐 | `OKAY` | 3 |
| 😔 | `LOW` | 2 |
| 😢 | `BAD` | 1 |

### Check-In Flow

```
App opens → LaunchedEffect(Unit)
    ↓
MoodCheckInViewModel.showCheckIn()
    ↓
┌──────────────────────────────┐
│  How are you feeling today?  │
│                              │
│  😊  🙂  😐  😔  😢         │  ← User taps emoji
│                              │
└──────────────────────────────┘
    ↓ onMoodSelected(moodType)
    ├── 1. Save to Room DB (MoodEntity)
    ├── 2. Run pattern detection
    │      ├── Rule 1: 3 consecutive LOW days
    │      │    → Gentle SMS alert to caretaker
    │      └── Rule 2: 5 LOW in last 7 entries
    │           → Stronger SMS alert to caretaker
    └── 3. Dismiss dialog
```

### Pattern Detection Rules

| Rule | Condition | Action |
|:--|:--|:--|
| **Consecutive Low** | 3+ days of LOW/BAD mood | Send gentle SMS: *"[Name] has been feeling low for 3 days"* |
| **Frequent Low** | 5+ LOW/BAD in last 7 entries | Send stronger SMS: *"[Name] may need emotional support"* |

**Safety constraints:**
- ❌ **No automatic calling** — only SMS alerts
- ❌ **No WhatsApp auto-message**
- ✅ Each rule has cooldown to prevent alert spam
- ✅ Falls back to local notification if SMS permission denied

---

## 11. Hospital Finder

### Files
```
hospital/
├── HospitalFinder.kt    → Google Places API + Haversine distance (306 lines)
└── HospitalModel.kt     → HospitalInfo data class
```

### Pipeline

```
Emergency triggered
    ↓
Get GPS coordinates (FusedLocationProvider)
    ↓
Google Places Nearby Search API
    query: type=hospital, radius=5000m
    rankby: distance
    ↓
Parse results → HospitalInfo[]
    ↓
For each hospital:
    ├── Get phone number via Place Details API
    ├── Calculate distance (Haversine formula)
    └── Display in Emergency Screen
    ↓
User taps hospital:
    ├── sendHospitalSMS() → Silent ambulance request SMS
    ├── sendHospitalWhatsApp() → WhatsApp message
    └── callHospital() → Auto-dial
```

**Fallback:** If API fails → opens Google Maps with "hospital" search query centered on user's location.

---

## 12. Call Family Module

### Files
```
data/contacts/
├── ContactModel.kt       → SavedContact data class
└── ContactsRepository.kt → Contact CRUD (SharedPreferences)

ui/callfamily/
├── CallFamilyScreen.kt   → Contact list + call buttons
└── CallFamilyViewModel.kt → State management

screens/
└── CallFamilyScreen.kt   → Alternative screen implementation
```

### Features

- **One-tap phone call** → `ACTION_CALL` intent
- **WhatsApp video call** → WhatsApp deep link with contact number
- **Contact management** → Add, edit, delete emergency contacts
- **Favorites** → Priority contacts shown at top

---

## 13. Memories & Photos Module

### Files
```
photos/
└── PhotosManager.kt       → Device photo gallery access

ui/memories/
├── MemoriesScreenNew.kt   → Photo grid with albums
└── MemoriesViewModel.kt   → Photo loading state

screens/
└── MemoriesScreen.kt      → Alternative memories view
```

### Features

- Access device photo gallery via `MediaStore`
- EXIF-based photo organization (location, date)
- Photo albums with grid view
- Image loading via Coil (efficient, cached)
- Fullscreen photo viewer

---

## 14. Food & Cabs Ordering

### Files
```
screens/
├── FoodOrderScreen.kt      → Food ordering shortcuts
└── MedicineOrderScreen.kt  → Medicine ordering shortcuts
```

### Supported Apps

| Category | Apps | Integration |
|:--|:--|:--|
| **Food** | Swiggy, Zomato, Domino's | Deep link + Genie automation |
| **Cabs** | Uber, Ola | Deep link to destination |
| **Medicine** | Apollo, 1mg, Netmeds, PharmEasy | Deep link to search |
| **E-commerce** | Amazon, Flipkart | Deep link |

---

## 15. DataHaven — Decentralized Medical Storage

### Backend Architecture

```
saathi-datahaven-backend/
├── src/
│   ├── index.ts                    → Express server entry point
│   ├── config/
│   │   ├── networks.ts             → DataHaven testnet endpoints
│   │   └── logger.ts               → Winston logging
│   ├── services/
│   │   ├── clientService.ts        → Wallet + Viem + StorageHub + Polkadot
│   │   └── mspService.ts           → MSP client + SIWE authentication
│   ├── operations/
│   │   ├── bucketOperations.ts     → Create, verify, wait for buckets
│   │   └── fileOperations.ts       → Upload, download, verify files
│   └── routes/
│       └── prescriptionRoutes.ts   → Express REST endpoints
```

### Upload Pipeline (Detailed)

```
Step 1: FileManager Setup
    ├── Read file from disk
    ├── Compute file size (BigInt)
    └── Create stream factory for chunked reading

Step 2: File Fingerprint
    └── FileManager.getFingerprint() → Content hash (H256)

Step 3: MSP Details
    ├── getMspInfo() → MSP ID, multiaddresses
    └── Extract libp2p peer IDs from multiaddresses

Step 4: Issue Storage Request (ON-CHAIN)
    ├── storageHubClient.issueStorageRequest()
    │   Parameters: bucketId, fileName, fingerprint,
    │               fileSize, mspId, peerIds,
    │               replicationLevel, replicas
    ├── Submit transaction
    └── Wait for transaction receipt (on-chain confirmation)

Step 5: Verify Storage Request (ON-CHAIN)
    ├── Compute file key: FileManager.computeFileKey(owner, bucketId, name)
    ├── Query: polkadotApi.query.fileSystem.storageRequests(fileKey)
    ├── Verify bucketId matches
    └── Verify fingerprint matches

Step 6: Authenticate with MSP (SIWE)
    ├── mspClient.auth.SIWE(walletClient, domain, uri)
    ├── Store session token
    └── Get user profile

Step 7: Upload to MSP
    ├── mspClient.files.uploadFile(bucketId, fileKey, blob, address, name)
    └── Verify status === "upload_successful"

Step 8: Wait for Confirmations
    ├── waitForMSPConfirmOnChain(fileKey) → Poll until MSP confirms
    └── waitForBackendFileReady(bucketId, fileKey) → Poll until indexer ready

Step 9: Return Result
    └── { fileId, fileKey, bucketId, txHash }
```

### API Endpoints

| Method | Path | Body | Response |
|:--|:--|:--|:--|
| `GET` | `/api/health` | — | `{status, mspHealth, bucketInitialized, filesStored}` |
| `POST` | `/api/initBucket` | `{bucketName?}` | `{success, bucketId, alreadyExists}` |
| `POST` | `/api/uploadPrescription` | `multipart: file` | `{success, fileId, fileKey, size}` |
| `GET` | `/api/getPrescription/:id` | — | Binary file stream |
| `GET` | `/api/prescriptions` | — | `{files: [{fileId, fileName, size, uploadedAt}]}` |
| `GET` | `/api/prescription/:id/info` | — | `{file: {fileId, fileKey, bucketId, ...}}` |

---

## 16. Database Architecture

### Room Database: `SahayDatabase`

```
sahay_database
├── medicines                    → MedicineEntity (legacy)
├── medicine_manager             → MedicineManagerEntity
├── intake_logs                  → IntakeLogEntity
├── prescriptions                → PrescriptionEntity
├── moods                        → MoodEntity
└── daos                         → Daos.kt (legacy DAOs)
```

### Entity Relationships

```
MedicineManagerEntity (1) ←──→ (N) IntakeLogEntity
       ↑                              (daily intake records)
       │
       └── prescriptionId? ←──→ PrescriptionEntity
                                    (scanned document reference)

MoodEntity (standalone)
    └── userId, moodType, timestamp, notes
```

---

## 17. Background Workers

### WorkManager Workers

| Worker | Trigger | Period | Purpose |
|:--|:--|:--|:--|
| `SafetyMonitoringWorker` | Boot + manual | Periodic (15 min) | Watchdog for safety service |
| `MedicineReminderWorker` | Per-medicine | One-time scheduled | Dose notification |
| `DailyResetWorker` | Daily midnight | Periodic (24h) | Reset `isTakenToday` flags |
| `MedicineStockWorker` | Daily | Periodic (24h) | Check stock levels |
| `MoodCheckInWorker` | Scheduled | Periodic | Trigger mood check-in |

### Boot Receiver

`MoodBootReceiver` re-schedules mood check-in alarms after device reboot:
```
BOOT_COMPLETED → Re-schedule AlarmManager for mood check-ins
```

---

## 18. Permissions Model

### Required Permissions

| Permission | Justification | Runtime Request |
|:--|:--|:--|
| `INTERNET` | API calls, DataHaven | Auto-granted |
| `READ_CONTACTS` | Call Family contacts | Yes |
| `CALL_PHONE` | SOS auto-dial | Yes |
| `ANSWER_PHONE_CALLS` | Scam detection | Yes |
| `RECORD_AUDIO` | Genie voice input | Yes |
| `ACCESS_FINE_LOCATION` | SOS GPS, hospital finder | Yes |
| `ACCESS_COARSE_LOCATION` | Fallback location | Yes |
| `SEND_SMS` | SOS silent SMS | Yes |
| `CAMERA` | Prescription scanning | Yes |
| `READ_MEDIA_IMAGES` | Memories gallery | Yes |
| `POST_NOTIFICATIONS` | Medicine reminders | Yes |
| `FOREGROUND_SERVICE` | Safety monitoring | Auto-granted |
| `HIGH_SAMPLING_RATE_SENSORS` | Fall detection | Auto-granted |
| `WAKE_LOCK` | Keep workers alive | Auto-granted |
| `RECEIVE_BOOT_COMPLETED` | Re-schedule workers | Auto-granted |
| `SCHEDULE_EXACT_ALARM` | Precise reminders | Yes (API 31+) |

### Permission Handler

`PermissionsHandler.kt` manages runtime permission requests with:
- Rationale dialogs explaining why each permission is needed
- Graceful degradation when permissions denied
- Batch permission requests for related features

---

## 19. UI Design System

### Files
```
ui/theme/
├── Color.kt   → Color palette
├── Theme.kt   → Material 3 theme definition
└── Type.kt    → Typography scale
```

### Color Palette

| Token | Hex | Usage |
|:--|:--|:--|
| `DarkNavy` | Deep navy | App background |
| `TealAccent` | Bright teal | Primary accent |
| `PurpleAccent` | Vibrant purple | Genie button, highlights |
| `BlueAccent` | Electric blue | Secondary accent |
| `CallGreen` | Forest green | Call Family card |
| `MemoriesTeal` | Deep teal | Memories card |
| `FoodOrange` | Warm orange | Food & Cabs card |
| `HelpRed` | Alert red | Emergency card |
| `TextWhite` | Pure white | Primary text |
| `TextGray` | Light gray | Secondary text |
| `TextMuted` | Muted gray | Tertiary text |
| `BottomBarDark` | Dark shade | Bottom nav background |

### Design Principles

1. **High contrast** — White text on dark backgrounds
2. **Large touch targets** — Minimum 110dp card height
3. **Gradient cards** — Visual hierarchy through color
4. **Animated interactions** — Genie button pulse, transitions
5. **Minimal cognitive load** — Maximum 6 options per screen
6. **Dark theme only** — Reduces eye strain for elderly users

---

## 20. Security Architecture

### Threat Model

| Threat | Mitigation |
|:--|:--|
| Private key exposure | Keys stored ONLY on backend, never in APK |
| Voice data collection | On-device SpeechRecognizer, no cloud recording |
| Location tracking | GPS accessed only during active SOS events |
| Unauthorized access | OTP-based authentication with attempt limits |
| Payment fraud | Genie NEVER taps payment buttons |
| Data tampering | DataHaven on-chain verification |
| SMS spoofing | SmsManager sends directly, no third-party |
| Scam calls | Truecaller integration (planned) |

### Data Flow Security

```
Android App ──HTTPS──→ Backend ──On-chain──→ DataHaven
     ↑                    ↑                      ↑
  No keys             Private key           Cryptographic
  No blockchain       held here             verification
  Only file IDs       Signs all txs         Immutable storage
```

---

## 21. End-to-End User Workflows

### Workflow 1: First-Time Setup

```
1. Install SAHAY → Open app
2. Email Screen → Enter email → Receive OTP
3. OTP Screen → Enter 6-digit code → Verified
4. Name Setup → Enter "Grandpa" → Complete
5. Home Screen loads with "Good Morning, Grandpa"
6. Permission requests appear one by one
7. Safety monitoring auto-starts (if enabled in settings)
```

### Workflow 2: Voice-Powered Food Ordering

```
1. Grandpa taps Genie button on Home Screen
2. "I want to eat biryani" (speaks in Hindi/English)
3. SpeechRecognizer → "mujhe biryani khana hai"
4. GenieIntentParser:
   - normalizeHinglish() → "me biryani food want"
   - detectIntentType() → FOOD
   - detectPlatform() → null → default SWIGGY
   - extractItem() → "biryani"
5. Confirmation dialog: "Order biryani from Swiggy?"
6. User confirms → PlatformLauncher opens Swiggy
7. AccessibilityService executes flow:
   - Finds search bar → Types "biryani" → Submits
   - Waits for results → Clicks first restaurant
   - STOPS BEFORE PAYMENT → "Ready! Review and place order."
8. Grandpa makes final payment tap himself
```

### Workflow 3: Inactivity Emergency

```
1. Grandpa falls asleep at 2 PM (unusual)
2. 8 PM — SafetyMonitoringService detects:
   - No screen touch for 6 hours ✓
   - No accelerometer motion for 6 hours ✓
   - Not in sleep hours window ✓
3. VoiceCheckManager triggers:
   - Device speaks: "Are you feeling alright?"
   - Waits 60 seconds for any touch/voice response
4. No response after 60 seconds
5. triggerSOSFromInactivity():
   - Gets GPS location → [12.9716, 77.5946]
   - Generates Maps link → https://maps.google.com/?q=12.97,77.59
   - Sends SMS to daughter, son, neighbor
   - Sends WhatsApp to daughter
   - Calls 112
6. Family receives: "⚠️ INACTIVITY ALERT — No activity for 6 hours..."
```

### Workflow 4: Prescription Management

```
1. Grandpa taps "Medicine Manager" on Home Screen
2. Taps camera icon → Captures prescription photo
3. OCRProcessor runs ML Kit on image
4. MedicineParser extracts: "Amlodipine 5mg", "Metformin 500mg"
5. ExtractedMedicinesSuggestionDialog shows results
6. User confirms → Medicines added to Room DB
7. MedicineReminderScheduler sets WorkManager alarms
8. 8:00 AM next day → Notification: "Time for Amlodipine 5mg"
9. User taps notification → Opens IntakeTrackerScreen
10. Marks as taken → Stock updated (29 remaining)
11. Day 26 → Stock drops to 4 → Below reorder threshold (5)
12. Alert: "Running low on Amlodipine. Reorder?"
13. User taps "Reorder" → Opens Apollo Pharmacy with search
```

### Workflow 5: Mood Pattern Alert

```
Day 1: Grandpa selects 😔 LOW
Day 2: Grandpa selects 😢 BAD
Day 3: Grandpa selects 😔 LOW  → Rule 1 triggers!
  ↓
checkMoodPatterns():
  - 3 consecutive LOW/BAD detected
  - Cooldown check: last alert > 24h ago ✓
  - Send SMS to emergency contacts:
    "[Grandpa] has been feeling low for 3 consecutive days.
     They might need some extra love and attention."
  - Show local notification on device:
    "We've noticed you've been feeling down. Your family cares about you ❤️"
```

---

*This documentation covers the complete SAHAY application architecture, every module's internal workflow, data flows, security model, and real-world user scenarios. For API-level details, refer to the KDoc comments in individual source files.*
