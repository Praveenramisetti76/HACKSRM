package com.example.healthpro.medicine.viewmodel

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthpro.medicine.data.db.MedicineManagerDatabase
import com.example.healthpro.medicine.data.repository.MedicineManagerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class MedicineDetailViewModel(
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private var repository: MedicineManagerRepository? = null
    private val medicineId: Long = savedStateHandle.get<Long>("medicineId") ?: 0L

    private val _medicineWithStock = MutableStateFlow<MedicineManagerRepository.MedicineWithStock?>(null)
    val medicineWithStock: StateFlow<MedicineManagerRepository.MedicineWithStock?> = _medicineWithStock.asStateFlow()

    fun init(context: Context) {
        if (repository == null) {
            val db = MedicineManagerDatabase.getInstance(context.applicationContext)
            repository = MedicineManagerRepository(db.medicineManagerDao(), db.intakeLogDao())
            observeMedicine()
        }
    }

    private fun observeMedicine() {
        viewModelScope.launch {
            if (medicineId <= 0) return@launch
            repository?.getMedicineWithStock(medicineId)
                ?.catch { }
                ?.collect { _medicineWithStock.value = it }
        }
    }

    fun markTaken() {
        viewModelScope.launch {
            if (medicineId > 0) repository?.markTaken(medicineId)
        }
    }

    fun updateMedicine(medicine: com.example.healthpro.medicine.data.db.MedicineManagerEntity) {
        viewModelScope.launch {
            repository?.updateMedicine(medicine)
        }
    }

    fun deleteMedicine(medicine: com.example.healthpro.medicine.data.db.MedicineManagerEntity) {
        viewModelScope.launch {
            repository?.deleteMedicine(medicine)
        }
    }
}
