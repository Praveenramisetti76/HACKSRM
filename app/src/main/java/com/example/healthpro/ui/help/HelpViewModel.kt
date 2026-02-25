package com.example.healthpro.ui.help

import android.content.Context
import android.location.Location
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthpro.data.contacts.ContactsRepository
import com.example.healthpro.data.contacts.DeviceContact
import com.example.healthpro.data.contacts.SavedContact
import com.example.healthpro.hospital.HospitalFinder
import com.example.healthpro.hospital.HospitalInfo
import com.example.healthpro.location.LocationHelper
import com.example.healthpro.sos.SOSManager
import com.example.healthpro.sos.SosCallManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════════════════════════
// SOS State Machine
// ═══════════════════════════════════════════════════════════════

enum class SOSPhase {
    IDLE,

    // SOS Button flow
    SOS_FETCHING_LOCATION,
    SOS_SENDING_SMS,
    SOS_SENDING_WHATSAPP,
    SOS_CALLING_CONTACTS,
    SOS_COMPLETED,

    // Hospital Button flow
    HOSPITAL_FETCHING_LOCATION,
    HOSPITAL_SEARCHING,
    HOSPITAL_SENDING_ALERT,
    HOSPITAL_CALLING,
    HOSPITAL_COMPLETED,

    // Error
    ERROR
}

/**
 * ViewModel for the HELP (Emergency SOS) screen.
 *
 * Manages two independent flows:
 *   1. SOS → auto-send SMS + WhatsApp to all emergency contacts
 *   2. Hospital → find nearest, send ambulance request, auto-call
 */
class HelpViewModel : ViewModel() {

    companion object {
        private const val TAG = "HelpViewModel"
    }

    // ─── Dependencies (lazy init) ────────────────────────────────
    private var repository: ContactsRepository? = null
    private var locationHelper: LocationHelper? = null
    private var sosManager: SOSManager? = null
    private var sosCallManager: SosCallManager? = null
    private var hospitalFinder: HospitalFinder? = null

    // ─── Observable State ────────────────────────────────────────
    var phase by mutableStateOf(SOSPhase.IDLE)
        private set

    var currentLocation by mutableStateOf<Location?>(null)
        private set

    var mapsLink by mutableStateOf("")
        private set

    var statusMessage by mutableStateOf("Ready")
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var smsSentCount by mutableStateOf(0)
        private set

    var whatsAppSent by mutableStateOf(false)
        private set

    var foundHospital by mutableStateOf<HospitalInfo?>(null)
        private set

    var missedCallResult by mutableStateOf<SosCallManager.CallResult?>(null)
        private set

    // ─── Emergency ContactsManagement ────────────────────────────
    var emergencyContacts by mutableStateOf<List<SavedContact>>(emptyList())
        private set

    var showContactPicker by mutableStateOf(false)

    var deviceContacts by mutableStateOf<List<DeviceContact>>(emptyList())
        private set

    var selectedContacts by mutableStateOf<Set<String>>(emptySet())
        private set

    var searchQuery by mutableStateOf("")

    var isLoadingContacts by mutableStateOf(false)
        private set

    // ═══════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ═══════════════════════════════════════════════════════════════

    fun init(context: Context) {
        if (repository == null) {
            val appContext = context.applicationContext
            repository = ContactsRepository(appContext)
            locationHelper = LocationHelper(appContext)
            sosManager = SOSManager(appContext)
            sosCallManager = SosCallManager(appContext)
            hospitalFinder = HospitalFinder(appContext)
            loadEmergencyContacts()
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // FLOW 1: SOS — Auto-send to ALL emergency contacts
    // ═══════════════════════════════════════════════════════════════

    /**
     * MAIN SOS TRIGGER.
     *
     * Executes the FULL emergency protocol automatically:
     *   Step 1: Fetch GPS location (with retry)
     *   Step 2: Auto-send SMS to ALL emergency contacts (SmsManager, silent)
     *   Step 3: Auto-launch WhatsApp for contacts (deep link)
     *
     * ❗ ZERO user interaction after button press.
     */
    fun triggerSOS() {
        if (emergencyContacts.isEmpty()) {
            errorMessage = "No emergency contacts saved.\nAdd contacts first, then press SOS."
            return
        }

        viewModelScope.launch {
            try {
                // ── Step 1: Fetch Location ──
                phase = SOSPhase.SOS_FETCHING_LOCATION
                statusMessage = "📍 Fetching your location..."
                errorMessage = null

                val location = locationHelper?.getCurrentLocation()
                currentLocation = location

                if (location != null) {
                    mapsLink = LocationHelper.generateMapsLink(
                        location.latitude, location.longitude
                    )
                    Log.d(TAG, "Location: ${location.latitude}, ${location.longitude}")
                } else {
                    mapsLink = "Location unavailable — please call for help"
                    Log.w(TAG, "Location fetch failed, proceeding with fallback message")
                }

                // ── Step 2: Auto-Send SMS (SILENT — SmsManager) ──
                phase = SOSPhase.SOS_SENDING_SMS
                statusMessage = "📨 Sending SMS alerts..."

                smsSentCount = sosManager?.sendEmergencySMS(emergencyContacts, mapsLink) ?: 0
                Log.d(TAG, "SMS sent to $smsSentCount contacts")

                // Small delay to prevent intent collision
                delay(800)

                // ── Step 3: Auto-Send WhatsApp ──
                phase = SOSPhase.SOS_SENDING_WHATSAPP
                statusMessage = "💬 Sending WhatsApp alerts..."

                whatsAppSent = sosManager?.sendEmergencyWhatsApp(emergencyContacts, mapsLink) ?: false

                if (!whatsAppSent) {
                    Log.w(TAG, "WhatsApp not installed — SMS only mode")
                }

                // Small delay before starting call sequence
                delay(1000)

                // ── Step 4: Auto Missed Calls (20s each, background) ──
                phase = SOSPhase.SOS_CALLING_CONTACTS
                statusMessage = "📞 Placing missed calls to contacts..."

                missedCallResult = sosCallManager?.placeSequentialMissedCalls(emergencyContacts)
                Log.d(TAG, "Missed calls: ${missedCallResult?.callsPlaced}/${missedCallResult?.totalContacts} placed")

                // ── DONE ──
                phase = SOSPhase.SOS_COMPLETED
                val callInfo = missedCallResult?.let { " + ${it.callsPlaced} missed calls" } ?: ""
                statusMessage = if (whatsAppSent) {
                    "✅ Emergency alerts sent via SMS + WhatsApp$callInfo!"
                } else {
                    "✅ Emergency SMS alerts sent$callInfo! (WhatsApp not available)"
                }
                Log.d(TAG, "SOS flow completed successfully")

            } catch (e: Exception) {
                Log.e(TAG, "SOS flow error: ${e.message}")
                phase = SOSPhase.ERROR
                errorMessage = e.message ?: "An unexpected error occurred"
                statusMessage = "❌ Error: ${e.message}"
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // FLOW 2: NEAREST HOSPITAL — Find, alert, auto-call
    // ═══════════════════════════════════════════════════════════════

    /**
     * HOSPITAL TRIGGER.
     *
     * Executes the full hospital emergency protocol:
     *   Step 1: Fetch GPS location
     *   Step 2: Find nearest hospital (Google Places API)
     *   Step 3: Auto-send ambulance SMS + WhatsApp to hospital
     *   Step 4: Auto-call the hospital
     *
     * Fallback: If API fails → opens Google Maps hospital search
     */
    fun triggerHospitalSearch() {
        viewModelScope.launch {
            try {
                // ── Step 1: Fetch Location ──
                phase = SOSPhase.HOSPITAL_FETCHING_LOCATION
                statusMessage = "📍 Fetching your location..."
                errorMessage = null
                foundHospital = null

                val location = locationHelper?.getCurrentLocation()
                currentLocation = location

                if (location == null) {
                    phase = SOSPhase.ERROR
                    errorMessage = "Unable to fetch location. Please try again."
                    statusMessage = "❌ Location unavailable"
                    return@launch
                }

                mapsLink = LocationHelper.generateMapsLink(
                    location.latitude, location.longitude
                )

                // ── Step 2: Find Nearest Hospital ──
                phase = SOSPhase.HOSPITAL_SEARCHING
                statusMessage = "🏥 Searching nearest hospital..."

                val hospital = hospitalFinder?.findNearestHospital(
                    location.latitude, location.longitude
                )

                if (hospital != null) {
                    foundHospital = hospital
                    Log.d(TAG, "Found: ${hospital.name}, phone: ${hospital.phoneNumber}")

                    // ── Step 3: Auto-Send Ambulance Request ──
                    phase = SOSPhase.HOSPITAL_SENDING_ALERT
                    statusMessage = "🚑 Sending ambulance request to ${hospital.name}..."

                    if (hospital.phoneNumber.isNotBlank()) {
                        hospitalFinder?.sendHospitalSMS(hospital.phoneNumber, mapsLink)
                        delay(500)
                        hospitalFinder?.sendHospitalWhatsApp(hospital.phoneNumber, mapsLink)
                    }

                    delay(500)

                    // ── Step 4: Auto-Call Hospital ──
                    phase = SOSPhase.HOSPITAL_CALLING
                    statusMessage = "📞 Calling ${hospital.name}..."

                    hospitalFinder?.callHospital(hospital.phoneNumber)

                    // ── DONE ──
                    phase = SOSPhase.HOSPITAL_COMPLETED
                    statusMessage = "✅ Hospital alerted: ${hospital.name}"

                } else {
                    // API failed → fallback to Google Maps search
                    Log.w(TAG, "Places API failed, using Maps fallback")
                    statusMessage = "🗺️ Opening Google Maps to find hospital..."

                    hospitalFinder?.openMapsHospitalSearch(
                        location.latitude, location.longitude
                    )

                    delay(500)

                    // Also call 112 as fallback
                    sosManager?.callEmergencyNumber("112")

                    phase = SOSPhase.HOSPITAL_COMPLETED
                    statusMessage = "🗺️ Google Maps opened — also calling 112"
                }

            } catch (e: Exception) {
                Log.e(TAG, "Hospital flow error: ${e.message}")
                phase = SOSPhase.ERROR
                errorMessage = e.message ?: "An unexpected error occurred"
                statusMessage = "❌ Error: ${e.message}"
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // CONTACTS MANAGEMENT
    // ═══════════════════════════════════════════════════════════════

    fun loadEmergencyContacts() {
        repository?.let {
            emergencyContacts = it.getEmergencyContacts()
        }
    }

    fun loadDeviceContacts() {
        isLoadingContacts = true
        repository?.let {
            deviceContacts = it.fetchDeviceContacts()
            isLoadingContacts = false
        }
    }

    fun toggleContactSelection(contactId: String) {
        selectedContacts = if (contactId in selectedContacts) {
            selectedContacts - contactId
        } else {
            selectedContacts + contactId
        }
    }

    fun saveSelectedAsEmergencyContacts() {
        repository?.let { repo ->
            val selected = deviceContacts.filter { it.id in selectedContacts }
            selected.forEach { device ->
                repo.addEmergencyContact(
                    SavedContact(
                        name = device.name,
                        phoneNumber = device.phoneNumber,
                        photoUri = device.photoUri
                    )
                )
            }
            emergencyContacts = repo.getEmergencyContacts()
            selectedContacts = emptySet()
            searchQuery = ""
            showContactPicker = false
        }
    }

    fun removeEmergencyContact(contact: SavedContact) {
        repository?.let { repo ->
            repo.removeEmergencyContact(contact)
            emergencyContacts = repo.getEmergencyContacts()
        }
    }

    fun getFilteredDeviceContacts(): List<DeviceContact> {
        if (searchQuery.isBlank()) return deviceContacts
        return deviceContacts.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.phoneNumber.contains(searchQuery)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // RESET
    // ═══════════════════════════════════════════════════════════════

    fun resetToIdle() {
        phase = SOSPhase.IDLE
        statusMessage = "Ready"
        errorMessage = null
        smsSentCount = 0
        whatsAppSent = false
        foundHospital = null
        missedCallResult = null
    }
}
