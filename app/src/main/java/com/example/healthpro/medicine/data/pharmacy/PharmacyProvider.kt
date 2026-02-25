package com.example.healthpro.medicine.data.pharmacy

import com.example.healthpro.medicine.domain.model.OrderRequest
import com.example.healthpro.medicine.domain.model.OrderResponse
import com.example.healthpro.medicine.domain.model.MedicineResult

/**
 * Abstraction for pharmacy integration.
 * Use deep linking when official APIs unavailable.
 */
interface PharmacyProvider {

    suspend fun searchMedicine(name: String): MedicineResult

    suspend fun orderMedicine(orderRequest: OrderRequest): OrderResponse

    fun getPackageName(): String

    fun getDisplayName(): String

    fun getPlayStoreUrl(): String
}
