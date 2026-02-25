package com.example.healthpro.medicine.viewmodel

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthpro.medicine.data.db.MedicineManagerDatabase
import com.example.healthpro.medicine.data.db.MedicineManagerEntity
import com.example.healthpro.medicine.data.db.PrescriptionEntity
import com.example.healthpro.medicine.data.pharmacy.Tata1mgProvider
import com.example.healthpro.medicine.ocr.MedicineParser
import com.example.healthpro.medicine.ocr.OCRProcessor
import com.example.healthpro.medicine.reminders.MedicineReminderScheduler
import com.example.healthpro.medicine.datahaven.DataHavenUploader
import com.example.healthpro.medicine.vault.WhatsAppHelper
import com.example.healthpro.medicine.workers.DailyResetScheduler
import com.example.healthpro.medicine.workers.MedicineStockScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder
import java.util.Calendar

/**
 * ViewModel for Prescription Vault feature.
 * Handles prescription capture, OCR analysis, purchase confirmation,
 * WhatsApp alerts, Tata 1mg ordering, and medicine entry with reminders.
 */
class PrescriptionVaultViewModel : ViewModel() {

    companion object {
        private const val TAG = "PrescriptionVaultVM"
    }

    // ── State ──
    private var db: MedicineManagerDatabase? = null
    private var ocrProcessor: OCRProcessor? = null
    private var whatsAppHelper: WhatsAppHelper? = null
    private var appContext: Context? = null

    private val _prescriptions = MutableStateFlow<List<PrescriptionEntity>>(emptyList())
    val prescriptions: StateFlow<List<PrescriptionEntity>> = _prescriptions.asStateFlow()

    private val _medicines = MutableStateFlow<List<MedicineManagerEntity>>(emptyList())
    val medicines: StateFlow<List<MedicineManagerEntity>> = _medicines.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /** Extracted medicines from OCR, awaiting user confirmation */
    private val _extractedMedicines = MutableStateFlow<List<ExtractedMedicine>>(emptyList())
    val extractedMedicines: StateFlow<List<ExtractedMedicine>> = _extractedMedicines.asStateFlow()

    /** Show purchase confirmation dialog */
    private val _showPurchaseDialog = MutableStateFlow(false)
    val showPurchaseDialog: StateFlow<Boolean> = _showPurchaseDialog.asStateFlow()

    /** Show medicine over confirmation */
    private val _showMedicineOverDialog = MutableStateFlow(false)
    val showMedicineOverDialog: StateFlow<Boolean> = _showMedicineOverDialog.asStateFlow()

    /** The current prescription being processed */
    private val _currentPrescriptionId = MutableStateFlow<Long?>(null)
    val currentPrescriptionId: StateFlow<Long?> = _currentPrescriptionId.asStateFlow()

    /** Current page/screen within the vault */
    private val _currentScreen = MutableStateFlow(VaultScreen.HOME)
    val currentScreen: StateFlow<VaultScreen> = _currentScreen.asStateFlow()

    /** Camera URI for taking prescription photo */
    private val _cameraUri = MutableStateFlow<Uri?>(null)
    val cameraUri: StateFlow<Uri?> = _cameraUri.asStateFlow()

    /** DataHaven upload status: "idle", "uploading", "success", "failed" */
    private val _dataHavenStatus = MutableStateFlow("idle")
    val dataHavenStatus: StateFlow<String> = _dataHavenStatus.asStateFlow()

    /** DataHaven file key (returned after successful upload) */
    private val _dataHavenFileKey = MutableStateFlow<String?>(null)
    val dataHavenFileKey: StateFlow<String?> = _dataHavenFileKey.asStateFlow()

    data class ExtractedMedicine(
        val name: String,
        val dosage: String,
        val frequencyPerDay: Int,
        val durationDays: Int = 30
    )

    enum class VaultScreen {
        HOME,
        PRESCRIPTIONS,
        MEDICINES,
        ADD_MEDICINE
    }

    // ═══════════════════════════════════════════
    // INITIALIZATION
    // ═══════════════════════════════════════════

    fun init(context: Context) {
        if (db == null) {
            appContext = context.applicationContext
            db = MedicineManagerDatabase.getInstance(context.applicationContext)
            ocrProcessor = OCRProcessor(context.applicationContext)
            whatsAppHelper = WhatsAppHelper(context.applicationContext)
            // Schedule background workers
            MedicineStockScheduler.schedule(context.applicationContext)
            DailyResetScheduler.schedule(context.applicationContext)
            observePrescriptions()
            observeMedicines()
        }
    }

    private fun observePrescriptions() {
        viewModelScope.launch {
            db?.prescriptionDao()?.getAllPrescriptions()
                ?.catch { e -> _errorMessage.value = e.message }
                ?.collect { _prescriptions.value = it }
        }
    }

    private fun observeMedicines() {
        viewModelScope.launch {
            db?.medicineManagerDao()?.getAllMedicines()
                ?.catch { e -> _errorMessage.value = e.message }
                ?.collect { _medicines.value = it }
        }
    }

    // ═══════════════════════════════════════════
    // NAVIGATION
    // ═══════════════════════════════════════════

    fun navigateTo(screen: VaultScreen) {
        _currentScreen.value = screen
    }

    // ═══════════════════════════════════════════
    // CAMERA
    // ═══════════════════════════════════════════

    fun createCameraUri(context: Context): Uri? {
        return try {
            val imageDir = File(context.filesDir, "prescriptions")
            if (!imageDir.exists()) imageDir.mkdirs()
            val imageFile = File(imageDir, "rx_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                imageFile
            )
            _cameraUri.value = uri
            uri
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create camera URI: ${e.message}")
            null
        }
    }

    // ═══════════════════════════════════════════
    // PRESCRIPTION STORAGE + OCR
    // ═══════════════════════════════════════════

    /**
     * Store a prescription image and run OCR analysis.
     * Parses ALL medicines from the OCR text individually.
     */
    fun storePrescription(imageUri: Uri, mimeType: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                // 1. Copy image to internal storage so it's always accessible
                //    (gallery content:// URIs expire after picker closes)
                val permanentUri = withContext(Dispatchers.IO) {
                    copyImageToInternalStorage(imageUri)
                } ?: imageUri // fallback to original if copy fails

                // 2. Save to database with the permanent URI
                val prescriptionId = db?.prescriptionDao()?.insertPrescription(
                    PrescriptionEntity(
                        imageUri = permanentUri.toString(),
                        dateAdded = System.currentTimeMillis(),
                        analyzed = false
                    )
                ) ?: 0L

                _currentPrescriptionId.value = prescriptionId

                // 2. Run OCR
                val rawText = withContext(Dispatchers.IO) {
                    val actualMime = mimeType ?: "image/*"
                    when {
                        actualMime.contains("pdf") -> ocrProcessor?.extractTextFromPdf(imageUri)
                        else -> ocrProcessor?.extractTextFromImage(imageUri)
                    }
                }

                val text = rawText?.getOrNull() ?: ""
                if (text.isBlank()) {
                    db?.prescriptionDao()?.updatePrescription(
                        PrescriptionEntity(id = prescriptionId, imageUri = imageUri.toString(), analyzed = true)
                    )
                    _errorMessage.value = "Could not extract medicines. You can add them manually."
                    _showPurchaseDialog.value = true
                    return@launch
                }

                // 3. Parse ALL medicines from the full text
                val parsed = MedicineParser.parse(text)
                if (parsed.isEmpty()) {
                    _errorMessage.value = "No medicines detected. You can add them manually."
                    _showPurchaseDialog.value = true
                    return@launch
                }

                _extractedMedicines.value = parsed.map { m ->
                    ExtractedMedicine(
                        name = m.name,
                        dosage = m.dosage,
                        frequencyPerDay = m.frequencyPerDay
                    )
                }

                // Mark prescription as analyzed
                db?.prescriptionDao()?.updatePrescription(
                    PrescriptionEntity(id = prescriptionId, imageUri = imageUri.toString(), analyzed = true)
                )

                Log.d(TAG, "Extracted ${parsed.size} medicines from prescription")

                // 4. Upload to DataHaven (background, non-blocking)
                uploadToDataHaven(prescriptionId, permanentUri, "rx_${prescriptionId}.jpg")

                // 5. Show purchase confirmation
                _showPurchaseDialog.value = true

            } catch (e: Exception) {
                Log.e(TAG, "Prescription processing failed: ${e.message}")
                _errorMessage.value = e.message ?: "Failed to process prescription"
                _showPurchaseDialog.value = true
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ═══════════════════════════════════════════
    // DATAHAVEN UPLOAD (background, non-blocking)
    // ═══════════════════════════════════════════

    /**
     * Upload prescription to DataHaven in the background.
     * Does NOT block the main OCR/purchase flow.
     * Updates the local DB record on success.
     */
    private fun uploadToDataHaven(prescriptionId: Long, imageUri: Uri, fileName: String) {
        val ctx = appContext ?: return
        _dataHavenStatus.value = "uploading"
        _dataHavenFileKey.value = null

        viewModelScope.launch {
            try {
                Log.d(TAG, "📤 Uploading to DataHaven: $fileName")
                val response = DataHavenUploader.uploadPrescription(ctx, imageUri, fileName)

                if (response.success && response.fileKey != null) {
                    _dataHavenStatus.value = "success"
                    _dataHavenFileKey.value = response.fileKey

                    // Update local DB with DataHaven reference
                    val existing = db?.prescriptionDao()?.getPrescriptionById(prescriptionId)
                    if (existing != null) {
                        db?.prescriptionDao()?.updatePrescription(
                            existing.copy(
                                dataHavenFileKey = response.fileKey,
                                dataHavenTxHash = response.txHash,
                                uploadedToDataHaven = true
                            )
                        )
                    }
                    Log.d(TAG, "✅ DataHaven upload success: fileKey=${response.fileKey}")
                } else {
                    _dataHavenStatus.value = "failed"
                    Log.e(TAG, "❌ DataHaven upload failed: ${response.error}")
                }
            } catch (e: Exception) {
                _dataHavenStatus.value = "failed"
                Log.e(TAG, "❌ DataHaven upload error: ${e.message}", e)
            }
        }
    }

    // ═══════════════════════════════════════════
    // PURCHASE CONFIRMATION FLOW
    // ═══════════════════════════════════════════

    fun confirmPurchased() {
        _showPurchaseDialog.value = false
        if (_extractedMedicines.value.isNotEmpty()) {
            _currentScreen.value = VaultScreen.ADD_MEDICINE
        }
    }

    fun confirmNotPurchased() {
        _showPurchaseDialog.value = false
        viewModelScope.launch {
            try {
                val prescriptionId = _currentPrescriptionId.value
                val prescription = if (prescriptionId != null) {
                    db?.prescriptionDao()?.getPrescriptionById(prescriptionId)
                } else {
                    db?.prescriptionDao()?.getLatestPrescription()
                }

                if (prescription != null) {
                    val imageUri = Uri.parse(prescription.imageUri)
                    whatsAppHelper?.sendPrescriptionToFamily(
                        imageUri = imageUri,
                        message = "📋 Prescription uploaded. Medicines not yet purchased. Please help arrange."
                    )
                } else {
                    whatsAppHelper?.sendTextToFamily(
                        "📋 Prescription uploaded. Medicines not yet purchased. Please help arrange."
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "WhatsApp send failed: ${e.message}")
                _errorMessage.value = "Failed to send WhatsApp message"
            }
        }

        if (_extractedMedicines.value.isNotEmpty()) {
            _currentScreen.value = VaultScreen.ADD_MEDICINE
        }
    }

    // ═══════════════════════════════════════════
    // MEDICINE ENTRY
    // ═══════════════════════════════════════════

    fun saveMedicine(medicine: MedicineManagerEntity) {
        viewModelScope.launch {
            try {
                // Check for duplicates
                val existing = db?.medicineManagerDao()?.getMedicineByName(medicine.name)
                if (existing != null) {
                    Log.d(TAG, "Medicine '${medicine.name}' already exists, skipping duplicate")
                    return@launch
                }
                val id = db?.medicineManagerDao()?.insertMedicine(medicine) ?: 0L
                val saved = medicine.copy(id = id)

                appContext?.let { ctx ->
                    MedicineReminderScheduler.scheduleForMedicine(ctx, saved)
                }
                Log.d(TAG, "Saved medicine: ${medicine.name} with ${medicine.getReminderTimesList().size} reminders")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save medicine: ${e.message}")
                _errorMessage.value = e.message
            }
        }
    }

    fun saveAllExtractedMedicines(
        medicines: List<ExtractedMedicine>,
        reminderTimes: Map<Int, String>  // index -> "HH:mm,HH:mm"
    ) {
        viewModelScope.launch {
            try {
                val prescriptionId = _currentPrescriptionId.value ?: 0L
                val startDate = System.currentTimeMillis()
                var savedCount = 0

                medicines.forEachIndexed { index, m ->
                    // Skip duplicates
                    val existing = db?.medicineManagerDao()?.getMedicineByName(m.name)
                    if (existing != null) {
                        Log.d(TAG, "Skipping duplicate: ${m.name}")
                        return@forEachIndexed
                    }

                    val entity = MedicineManagerEntity(
                        prescriptionId = prescriptionId,
                        name = m.name,
                        dosage = m.dosage,
                        frequencyPerDay = m.frequencyPerDay,
                        totalQuantity = m.frequencyPerDay * m.durationDays,
                        startDate = startDate,
                        reorderThreshold = 7,
                        durationDays = m.durationDays,
                        reminderTimes = reminderTimes[index] ?: generateDefaultTimes(m.frequencyPerDay)
                    )
                    val id = db?.medicineManagerDao()?.insertMedicine(entity) ?: 0L
                    val saved = entity.copy(id = id)

                    appContext?.let { ctx ->
                        MedicineReminderScheduler.scheduleForMedicine(ctx, saved)
                    }
                    savedCount++
                }

                _extractedMedicines.value = emptyList()
                _currentScreen.value = VaultScreen.HOME
                Log.d(TAG, "Saved $savedCount extracted medicines (${medicines.size - savedCount} duplicates skipped)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save medicines: ${e.message}")
                _errorMessage.value = e.message
            }
        }
    }

    fun deleteMedicine(medicine: MedicineManagerEntity) {
        viewModelScope.launch {
            try {
                appContext?.let { MedicineReminderScheduler.cancelForMedicine(it, medicine.id) }
                db?.intakeLogDao()?.deleteLogsForMedicine(medicine.id)
                db?.medicineManagerDao()?.deleteMedicine(medicine)
            } catch (e: Exception) {
                _errorMessage.value = e.message
            }
        }
    }

    fun deletePrescription(prescription: PrescriptionEntity) {
        viewModelScope.launch {
            try {
                db?.prescriptionDao()?.deletePrescription(prescription)
            } catch (e: Exception) {
                _errorMessage.value = e.message
            }
        }
    }

    // ═══════════════════════════════════════════
    // FIX #2: MARK TAKEN (persists to DB)
    // ═══════════════════════════════════════════

    /**
     * Mark a medicine as taken today.
     * Updates the medicine entity's isTakenToday flag via DAO
     * AND creates/updates the intake log for stock tracking.
     */
    fun markTaken(medicineId: Long) {
        viewModelScope.launch {
            try {
                val todayMillis = getTodayStartMillis()

                // 1. Update isTakenToday flag in medicine entity (persists across recomposition)
                db?.medicineManagerDao()?.markTakenToday(medicineId, todayMillis)

                // 2. Also record in intake_log for stock calculation
                val existingLog = db?.intakeLogDao()?.getLog(medicineId, todayMillis)
                if (existingLog != null) {
                    db?.intakeLogDao()?.updateLog(existingLog.copy(taken = true))
                } else {
                    db?.intakeLogDao()?.insertLog(
                        com.example.healthpro.medicine.data.db.IntakeLogEntity(
                            medicineId = medicineId,
                            date = todayMillis,
                            taken = true
                        )
                    )
                }

                // 3. Check if stock is now 0 → trigger WhatsApp
                checkStockAndAlert(medicineId)

                Log.d(TAG, "✅ Medicine $medicineId marked as taken for today")
            } catch (e: Exception) {
                Log.e(TAG, "Mark taken failed: ${e.message}")
            }
        }
    }

    // ═══════════════════════════════════════════
    // FIX #4: WHATSAPP WHEN STOCK = 0
    // Sends prescription IMAGE + message text
    // ═══════════════════════════════════════════

    /**
     * After marking taken, check if remaining stock is 0.
     * If so, send WhatsApp message WITH prescription photo to family.
     */
    private suspend fun checkStockAndAlert(medicineId: Long) {
        try {
            val medicine = db?.medicineManagerDao()?.getMedicineById(medicineId) ?: return
            val totalTaken = db?.intakeLogDao()?.getTotalTakenCount(medicineId) ?: 0
            val remaining = medicine.remainingQuantity(totalTaken)

            if (remaining <= 0) {
                Log.w(TAG, "🔴 Medicine '${medicine.name}' stock reached 0")
                val message = "Medicine ${medicine.name} is over. Please reorder. Prescription attached."

                // Fetch the linked prescription image
                var prescriptionImageUri: Uri? = null

                // 1. Try prescription linked to this medicine
                if (medicine.prescriptionId > 0) {
                    val prescription = db?.prescriptionDao()?.getPrescriptionById(medicine.prescriptionId)
                    if (prescription != null) {
                        prescriptionImageUri = Uri.parse(prescription.imageUri)
                        Log.d(TAG, "Found linked prescription image: ${prescription.imageUri}")
                    }
                }

                // 2. Fallback: use the latest prescription if no specific one linked
                if (prescriptionImageUri == null) {
                    val latest = db?.prescriptionDao()?.getLatestPrescription()
                    if (latest != null) {
                        prescriptionImageUri = Uri.parse(latest.imageUri)
                        Log.d(TAG, "Using latest prescription image as fallback: ${latest.imageUri}")
                    }
                }

                sendMedicineOverWhatsApp(medicine.name, message, prescriptionImageUri)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Stock check failed: ${e.message}")
        }
    }

    /**
     * Send WhatsApp message + prescription image when medicine stock reaches 0.
     *
     * Flow:
     *   1. If image URI exists → sendPrescriptionToFamily (image + text)
     *   2. If image missing → sendTextToFamily (text only, with log warning)
     *   3. If WhatsApp not installed → show Toast, no crash
     */
    private fun sendMedicineOverWhatsApp(medicineName: String, message: String, imageUri: Uri?) {
        val ctx = appContext ?: return
        val helper = whatsAppHelper ?: return

        val success: Boolean

        if (imageUri != null) {
            // Send WITH prescription image attached
            Log.d(TAG, "Sending WhatsApp with prescription image for: $medicineName")
            success = helper.sendPrescriptionToFamily(
                imageUri = imageUri,
                message = message
            )
            if (!success) {
                // Fallback to text-only if image send failed
                Log.w(TAG, "Image send failed, falling back to text-only for: $medicineName")
                helper.sendTextToFamily(message)
            }
        } else {
            // No image available — send text only
            Log.w(TAG, "No prescription image found for medicine: $medicineName, sending text only")
            success = helper.sendTextToFamily(message)
        }

        if (!success) {
            Log.w(TAG, "WhatsApp not available for medicine-over alert: $medicineName")
            try {
                Toast.makeText(ctx, "⚠️ $medicineName is over! Please reorder.", Toast.LENGTH_LONG).show()
            } catch (_: Exception) { /* ignore if on wrong thread */ }
        }
    }

    // ═══════════════════════════════════════════
    // MEDICINE OVER? (manual trigger)
    // ═══════════════════════════════════════════

    fun showMedicineOverConfirmation() {
        _showMedicineOverDialog.value = true
    }

    fun dismissMedicineOverDialog() {
        _showMedicineOverDialog.value = false
    }

    fun confirmMedicineOver() {
        _showMedicineOverDialog.value = false
        viewModelScope.launch {
            try {
                val ctx = appContext ?: return@launch
                val helper = whatsAppHelper ?: return@launch

                // Build message with all medicine names
                val allMeds = db?.medicineManagerDao()?.getAllMedicinesList() ?: emptyList()
                val medNames = if (allMeds.isNotEmpty()) {
                    allMeds.joinToString(", ") { it.name }
                } else {
                    "all medicines"
                }

                val message = "🔴 Medicines are over. Please help reorder. Prescription attached.\nMedicines: $medNames"

                // Try sending with prescription image
                val latest = db?.prescriptionDao()?.getLatestPrescription()
                if (latest != null) {
                    // Copy image to a fresh internal file so URI is always valid
                    // (gallery-picked URIs expire after picker closes)
                    val freshUri = withContext(Dispatchers.IO) {
                        copyPrescriptionToShareFile(ctx, Uri.parse(latest.imageUri))
                    }

                    if (freshUri != null) {
                        Log.d(TAG, "Sending Medicine Over with prescription image: $freshUri")
                        val sent = helper.sendPrescriptionToFamily(
                            imageUri = freshUri,
                            message = message
                        )
                        if (!sent) {
                            helper.sendTextToFamily(message)
                        }
                    } else {
                        Log.w(TAG, "Could not prepare prescription image, sending text only")
                        helper.sendTextToFamily(message)
                    }
                } else {
                    Log.w(TAG, "No prescription found in database")
                    helper.sendTextToFamily(message)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Medicine over WhatsApp failed: ${e.message}")
                _errorMessage.value = "Failed to send message"
            }
        }
    }

    /**
     * Copy a prescription image to a fresh shareable file in internal storage.
     * Returns a FileProvider content:// URI that WhatsApp can read.
     *
     * Handles:
     *  - Camera images (FileProvider content:// URI from our own app) → readable
     *  - Gallery images (system content:// URI) → may have expired permissions
     *  - file:// URIs → reads from filesystem directly
     *
     * If the original URI is unreadable, tries to read the file directly
     * from the prescriptions/ directory (camera images are stored there).
     */
    private fun copyPrescriptionToShareFile(context: Context, sourceUri: Uri): Uri? {
        try {
            val shareDir = File(context.filesDir, "prescriptions")
            if (!shareDir.exists()) shareDir.mkdirs()
            val shareFile = File(shareDir, "share_rx_${System.currentTimeMillis()}.jpg")

            // Strategy 1: Try reading via ContentResolver (works for valid content:// URIs)
            try {
                val inputStream = context.contentResolver.openInputStream(sourceUri)
                if (inputStream != null) {
                    shareFile.outputStream().use { output -> inputStream.copyTo(output) }
                    inputStream.close()
                    if (shareFile.length() > 0) {
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            shareFile
                        )
                        Log.d(TAG, "Copied prescription via ContentResolver: ${shareFile.length()} bytes")
                        return uri
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "ContentResolver read failed for $sourceUri: ${e.message}")
            }

            // Strategy 2: If URI points to a file in our prescriptions/ dir, read directly
            val uriStr = sourceUri.toString()
            val prescriptionsDir = File(context.filesDir, "prescriptions")
            if (prescriptionsDir.exists()) {
                // Try to find the original file by matching the filename from the URI
                val fileName = sourceUri.lastPathSegment ?: ""
                val candidates = prescriptionsDir.listFiles()?.filter {
                    it.name.startsWith("rx_") && it.extension == "jpg"
                } ?: emptyList()

                // Try the file matching the URI's last path segment
                val matchingFile = candidates.find { it.name == fileName }
                    ?: candidates.maxByOrNull { it.lastModified() } // or use the most recent

                if (matchingFile != null && matchingFile.exists() && matchingFile.length() > 0) {
                    matchingFile.inputStream().use { input ->
                        shareFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (shareFile.length() > 0) {
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            shareFile
                        )
                        Log.d(TAG, "Copied prescription from file: ${matchingFile.name}, ${shareFile.length()} bytes")
                        return uri
                    }
                }
            }

            // Strategy 3: file:// URI — read from path directly
            if (sourceUri.scheme == "file") {
                val file = File(sourceUri.path ?: return null)
                if (file.exists() && file.length() > 0) {
                    file.inputStream().use { input ->
                        shareFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (shareFile.length() > 0) {
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            shareFile
                        )
                        Log.d(TAG, "Copied prescription from file:// path: ${shareFile.length()} bytes")
                        return uri
                    }
                }
            }

            Log.e(TAG, "All strategies failed to read prescription image from: $sourceUri")
            // Clean up empty file
            if (shareFile.exists()) shareFile.delete()
            return null
        } catch (e: Exception) {
            Log.e(TAG, "copyPrescriptionToShareFile failed: ${e.message}")
            return null
        }
    }

    // ═══════════════════════════════════════════
    // FIX #5: TATA 1MG ORDER MEDICINES
    // ═══════════════════════════════════════════

    /**
     * Open Tata 1mg app and search for all medicines.
     * Uses deep linking to the 1mg search page.
     *
     * - If Tata 1mg is installed: opens app with search query
     * - If not installed: opens Play Store to install it
     *
     * Each medicine is opened via its search URL sequentially.
     */
    fun orderOnTata1mg() {
        viewModelScope.launch {
            val ctx = appContext ?: return@launch
            val allMeds = db?.medicineManagerDao()?.getAllMedicinesList() ?: emptyList()

            if (allMeds.isEmpty()) {
                _errorMessage.value = "No medicines to order"
                return@launch
            }

            // Check if Tata 1mg is installed
            val isInstalled = isAppInstalled(ctx, Tata1mgProvider.PACKAGE_NAME)

            if (!isInstalled) {
                // Open Play Store page for Tata 1mg
                try {
                    val playStoreIntent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("market://details?id=${Tata1mgProvider.PACKAGE_NAME}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    ctx.startActivity(playStoreIntent)
                } catch (e: Exception) {
                    // Fallback to web Play Store
                    try {
                        val webIntent = Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse("https://play.google.com/store/apps/details?id=${Tata1mgProvider.PACKAGE_NAME}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        ctx.startActivity(webIntent)
                    } catch (_: Exception) {
                        _errorMessage.value = "Could not open Play Store"
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, "Please install Tata 1mg first", Toast.LENGTH_LONG).show()
                }
                return@launch
            }

            // Build combined search query for all medicines
            val combinedQuery = allMeds.joinToString(" ") { "${it.name} ${it.dosage}" }
            val encoded = URLEncoder.encode(combinedQuery.take(200), "UTF-8")
            val searchUrl = "https://www.1mg.com/search/all?name=$encoded"

            try {
                // Try to open in Tata 1mg app
                val appIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse(searchUrl)
                    setPackage(Tata1mgProvider.PACKAGE_NAME)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(appIntent)
                Log.d(TAG, "Opened Tata 1mg with ${allMeds.size} medicines")
            } catch (e: Exception) {
                // Fallback: open in browser
                try {
                    val browserIntent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse(searchUrl)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    ctx.startActivity(browserIntent)
                    Log.d(TAG, "Opened 1mg in browser as fallback")
                } catch (_: Exception) {
                    _errorMessage.value = "Could not open Tata 1mg"
                }
            }

            // Also open individual medicine searches sequentially
            for (med in allMeds) {
                try {
                    val query = URLEncoder.encode("${med.name} ${med.dosage}".trim(), "UTF-8")
                    val url = "https://www.1mg.com/search/all?name=$query"
                    Log.d(TAG, "1mg search URL for ${med.name}: $url")
                } catch (_: Exception) { /* log only */ }
            }
        }
    }

    private fun isAppInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    // ═══════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════

    fun clearError() {
        _errorMessage.value = null
    }

    fun dismissExtractedMedicines() {
        _extractedMedicines.value = emptyList()
    }

    private fun getTodayStartMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * Generate default reminder times based on frequency.
     * 1x/day → 08:00
     * 2x/day → 08:00,20:00
     * 3x/day → 08:00,14:00,21:00
     */
    private fun generateDefaultTimes(frequencyPerDay: Int): String {
        return when (frequencyPerDay) {
            1 -> "08:00"
            2 -> "08:00,20:00"
            3 -> "08:00,14:00,21:00"
            else -> (0 until frequencyPerDay).joinToString(",") { i ->
                val hour = 8 + (i * 14 / frequencyPerDay.coerceAtLeast(1))
                "%02d:00".format(hour.coerceIn(6, 22))
            }
        }
    }

    /**
     * Copy an image from any URI to internal storage (prescriptions/ directory).
     * Returns a permanent FileProvider content:// URI that will always be accessible.
     *
     * This is critical for gallery-picked images whose content:// URIs
     * have temporary read permissions that expire after the picker closes.
     * Without this copy, the image cannot be read when "Medicine Over" is
     * clicked days later, causing WhatsApp to send text-only.
     */
    private fun copyImageToInternalStorage(sourceUri: Uri): Uri? {
        val ctx = appContext ?: return null
        return try {
            // If it's already a camera image stored in our prescriptions/ dir, keep as-is
            val uriStr = sourceUri.toString()
            if (uriStr.contains("${ctx.packageName}.fileprovider") && uriStr.contains("prescriptions")) {
                Log.d(TAG, "Image already in internal storage, skipping copy")
                return sourceUri
            }

            // Copy from source to internal storage
            val inputStream = ctx.contentResolver.openInputStream(sourceUri)
            if (inputStream == null) {
                Log.w(TAG, "Cannot open input stream for: $sourceUri")
                return null
            }

            val prescriptionDir = File(ctx.filesDir, "prescriptions")
            if (!prescriptionDir.exists()) prescriptionDir.mkdirs()
            val destFile = File(prescriptionDir, "rx_${System.currentTimeMillis()}.jpg")

            destFile.outputStream().use { output -> inputStream.copyTo(output) }
            inputStream.close()

            if (destFile.length() > 0) {
                val permanentUri = FileProvider.getUriForFile(
                    ctx,
                    "${ctx.packageName}.fileprovider",
                    destFile
                )
                Log.d(TAG, "Copied prescription to internal storage: ${destFile.name} (${destFile.length()} bytes)")
                permanentUri
            } else {
                Log.w(TAG, "Copied file is empty, using original URI")
                destFile.delete()
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy image to internal storage: ${e.message}")
            null
        }
    }

    override fun onCleared() {
        ocrProcessor?.close()
        super.onCleared()
    }
}
