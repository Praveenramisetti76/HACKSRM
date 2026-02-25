package com.example.healthpro.medicine.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthpro.medicine.data.db.MedicineManagerDatabase
import com.example.healthpro.medicine.data.db.MedicineManagerEntity
import com.example.healthpro.medicine.data.pharmacy.PharmacyProvider
import com.example.healthpro.medicine.data.repository.MedicineManagerRepository
import com.example.healthpro.medicine.ocr.MedicineParser
import com.example.healthpro.medicine.ocr.OCRProcessor
import com.example.healthpro.medicine.workers.MedicineStockScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Main ViewModel for Medicine Manager module.
 */
class MedicineManagerViewModel : ViewModel() {

    companion object {
        private const val TAG = "MedicineManagerVM"
    }

    private var repository: MedicineManagerRepository? = null
    private var pharmacyProviders: List<PharmacyProvider> = emptyList()
    private var ocrProcessor: OCRProcessor? = null

    private val _medicinesWithStock = MutableStateFlow<List<MedicineManagerRepository.MedicineWithStock>>(emptyList())
    val medicinesWithStock: StateFlow<List<MedicineManagerRepository.MedicineWithStock>> = _medicinesWithStock.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /** Extracted medicines from report - show as auto-suggestion on dashboard */
    private val _extractedMedicines = MutableStateFlow<List<ExtractedMedicineSuggestion>>(emptyList())
    val extractedMedicines: StateFlow<List<ExtractedMedicineSuggestion>> = _extractedMedicines.asStateFlow()

    /** Quantity = frequencyPerDay × 30 for 30-day supply */
    data class ExtractedMedicineSuggestion(
        val name: String,
        val dosage: String,
        val frequencyPerDay: Int,
        val suggestedQuantity: Int
    ) {
        constructor(name: String, dosage: String, frequencyPerDay: Int) : this(
            name = name,
            dosage = dosage,
            frequencyPerDay = frequencyPerDay,
            suggestedQuantity = frequencyPerDay * 30
        )
    }

    fun init(context: Context) {
        if (repository == null) {
            val appContext = context.applicationContext
            val db = MedicineManagerDatabase.getInstance(appContext)
            repository = MedicineManagerRepository(
                db.medicineManagerDao(),
                db.intakeLogDao()
            )
            ocrProcessor = OCRProcessor(appContext)
            pharmacyProviders = listOf(
                com.example.healthpro.medicine.data.pharmacy.Tata1mgProvider(context),
                com.example.healthpro.medicine.data.pharmacy.ApolloProvider(context)
            )
            MedicineStockScheduler.schedule(context)
            observeMedicines()
        }
    }

    private fun observeMedicines() {
        viewModelScope.launch {
            repository?.getAllMedicinesWithStock()
                ?.catch { e -> _errorMessage.value = e.message }
                ?.collect { list -> _medicinesWithStock.value = list }
        }
    }

    fun addMedicine(medicine: MedicineManagerEntity) {
        viewModelScope.launch {
            try {
                repository?.insertMedicine(medicine)
            } catch (e: Exception) {
                _errorMessage.value = e.message
            }
        }
    }

    fun updateMedicine(medicine: MedicineManagerEntity) {
        viewModelScope.launch {
            try {
                repository?.updateMedicine(medicine)
            } catch (e: Exception) {
                _errorMessage.value = e.message
            }
        }
    }

    fun deleteMedicine(medicine: MedicineManagerEntity) {
        viewModelScope.launch {
            try {
                repository?.deleteMedicine(medicine)
            } catch (e: Exception) {
                _errorMessage.value = e.message
            }
        }
    }

    fun markTaken(medicineId: Long) {
        viewModelScope.launch {
            val repo = repository ?: return@launch
            val today = repo.getTodayStartMillis()
            repo.markTaken(medicineId, today)
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun getPharmacyProviders(): List<PharmacyProvider> = pharmacyProviders

    /**
     * Process uploaded report (PDF or image) via OCR and show medicine suggestions.
     */
    fun processReport(uri: Uri, mimeType: String?) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _extractedMedicines.value = emptyList()
            try {
                val rawText = withContext(Dispatchers.IO) {
                    when {
                        mimeType?.contains("pdf") == true -> ocrProcessor?.extractTextFromPdf(uri)
                        else -> ocrProcessor?.extractTextFromImage(uri)
                    }
                }
                val text = rawText?.getOrNull() ?: ""
                if (text.isBlank()) {
                    _errorMessage.value = "Could not extract text from report. Try a clearer image."
                    return@launch
                }
                val parsed = MedicineParser.parse(text)
                _extractedMedicines.value = parsed.map { m ->
                    ExtractedMedicineSuggestion(
                        name = m.name,
                        dosage = m.dosage,
                        frequencyPerDay = m.frequencyPerDay
                    )
                }
                Log.d(TAG, "Extracted ${parsed.size} medicines from report")
            } catch (e: Exception) {
                Log.e(TAG, "OCR failed: ${e.message}")
                _errorMessage.value = e.message ?: "Failed to process report"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Confirm extracted medicines: save to database with user-edited quantities.
     */
    fun confirmExtractedMedicines(medicines: List<ExtractedMedicineSuggestion>) {
        viewModelScope.launch {
            try {
                val startDate = System.currentTimeMillis()
                medicines.forEach { m ->
                    repository?.insertMedicine(
                        MedicineManagerEntity(
                            name = m.name,
                            dosage = m.dosage,
                            frequencyPerDay = m.frequencyPerDay,
                            totalQuantity = m.suggestedQuantity,
                            startDate = startDate,
                            reorderThreshold = 7
                        )
                    )
                }
                _extractedMedicines.value = emptyList()
            } catch (e: Exception) {
                _errorMessage.value = e.message
            }
        }
    }

    fun dismissExtractedMedicines() {
        _extractedMedicines.value = emptyList()
    }

    override fun onCleared() {
        ocrProcessor?.close()
        super.onCleared()
    }
}
