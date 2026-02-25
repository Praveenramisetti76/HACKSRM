package com.example.healthpro.medicine.data.pharmacy

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.healthpro.medicine.domain.model.MedicineResult
import com.example.healthpro.medicine.domain.model.OrderRequest
import com.example.healthpro.medicine.domain.model.OrderResponse
import com.example.healthpro.medicine.domain.model.PharmacyMedicineItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

/**
 * Tata 1mg provider using deep linking.
 * Package: com.tatahealth.consumer (correct Play Store package)
 */
class Tata1mgProvider(private val context: Context) : PharmacyProvider {

    companion object {
        const val PACKAGE_NAME = "com.tatahealth.consumer"
        private const val SEARCH_URL = "https://www.1mg.com/search/all?name="
        private const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=$PACKAGE_NAME"
    }

    override suspend fun searchMedicine(name: String): MedicineResult = withContext(Dispatchers.IO) {
        val query = URLEncoder.encode(name, "UTF-8")
        MedicineResult(
            success = true,
            medicines = listOf(
                PharmacyMedicineItem(
                    name = name,
                    dosage = null,
                    price = null,
                    url = "$SEARCH_URL$query"
                )
            )
        )
    }

    override suspend fun orderMedicine(orderRequest: OrderRequest): OrderResponse =
        withContext(Dispatchers.Main) {
            val query = "${orderRequest.medicineName} ${orderRequest.dosage}".trim()
            val encoded = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "$SEARCH_URL$encoded"
            OrderResponse(
                success = true,
                message = "Opening Tata 1mg to search: $query",
                redirectUrl = searchUrl
            )
        }

    override fun getPackageName(): String = PACKAGE_NAME

    override fun getDisplayName(): String = "Tata 1mg"

    override fun getPlayStoreUrl(): String = PLAY_STORE_URL

    fun isInstalled(): Boolean =
        try {
            context.packageManager.getPackageInfo(PACKAGE_NAME, 0)
            true
        } catch (_: Exception) {
            false
        }
}
