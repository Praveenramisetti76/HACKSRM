package com.example.healthpro.medicine.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthpro.medicine.data.db.MedicineManagerEntity
import com.example.healthpro.medicine.data.pharmacy.PharmacyAppChecker
import com.example.healthpro.medicine.data.pharmacy.PharmacyProvider
import com.example.healthpro.medicine.domain.model.OrderRequest
import com.example.healthpro.medicine.domain.model.OrderResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for reorder flow: confirm order, call pharmacy, show status.
 */
class ReorderViewModel : ViewModel() {

    private val _orderStatus = MutableStateFlow<OrderStatus>(OrderStatus.Idle)
    val orderStatus: StateFlow<OrderStatus> = _orderStatus.asStateFlow()

    private val _showInstallDialog = MutableStateFlow(false)
    val showInstallDialog: StateFlow<Boolean> = _showInstallDialog.asStateFlow()

    private var pendingProvider: PharmacyProvider? = null
    private var pendingContext: Context? = null

    sealed class OrderStatus {
        object Idle : OrderStatus()
        object Processing : OrderStatus()
        data class Success(val message: String) : OrderStatus()
        data class Error(val message: String) : OrderStatus()
    }

    fun checkAndOrder(
        context: Context,
        medicine: MedicineManagerEntity,
        provider: PharmacyProvider,
        quantity: Int
    ) {
        if (!PharmacyAppChecker.isPharmacyInstalled(context, provider)) {
            pendingProvider = provider
            pendingContext = context
            _showInstallDialog.value = true
            return
        }
        executeOrder(context, medicine, provider, quantity)
    }

    fun onInstallConfirmed() {
        val provider = pendingProvider
        val ctx = pendingContext
        _showInstallDialog.value = false
        pendingProvider = null
        pendingContext = null
        if (provider != null && ctx != null) {
            PharmacyAppChecker.openPlayStore(ctx, provider)
        }
    }

    fun onInstallDismissed() {
        _showInstallDialog.value = false
        pendingProvider = null
        pendingContext = null
    }

    fun executeOrder(
        context: Context,
        medicine: MedicineManagerEntity,
        provider: PharmacyProvider,
        quantity: Int
    ) {
        viewModelScope.launch {
            _orderStatus.value = OrderStatus.Processing
            val response = withContext(Dispatchers.IO) {
                provider.orderMedicine(
                    OrderRequest(
                        medicineName = medicine.name,
                        dosage = medicine.dosage,
                        quantity = quantity
                    )
                )
            }
            if (response.success && response.redirectUrl != null) {
                PharmacyAppChecker.openPharmacyOrPlayStore(context, provider, response.redirectUrl)
                _orderStatus.value = OrderStatus.Success(response.message)
            } else {
                _orderStatus.value = OrderStatus.Error(response.message)
            }
        }
    }

    fun resetOrderStatus() {
        _orderStatus.value = OrderStatus.Idle
    }
}
