package com.example.healthpro.medicine.domain.model

/**
 * Domain models for pharmacy integration.
 */

data class MedicineResult(
    val success: Boolean,
    val medicines: List<PharmacyMedicineItem> = emptyList(),
    val message: String = ""
)

data class PharmacyMedicineItem(
    val name: String,
    val dosage: String?,
    val price: Double?,
    val url: String?
)

data class OrderRequest(
    val medicineName: String,
    val dosage: String,
    val quantity: Int
)

data class OrderResponse(
    val success: Boolean,
    val orderId: String? = null,
    val message: String,
    val redirectUrl: String? = null
)
