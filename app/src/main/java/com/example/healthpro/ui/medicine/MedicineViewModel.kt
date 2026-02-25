package com.example.healthpro.ui.medicine

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthpro.database.MedicineEntity
import com.example.healthpro.database.SahayDatabase
import com.example.healthpro.notifications.ReminderScheduler
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * ViewModel for Medicine Management feature.
 *
 * Manages:
 *   - Medicine dashboard (list, stock, refill status)
 *   - Take medicine (decrement stock)
 *   - Smart purchase deep-linking (Apollo, 1mg, PharmEasy)
 *   - Reminder scheduling
 */
class MedicineViewModel : ViewModel() {

    companion object {
        private const val TAG = "MedicineViewModel"

        // Deep link templates for Indian pharmacy platforms
        val PHARMACY_LINKS = listOf(
            PharmacyPlatform("Apollo Pharmacy", "com.apollo.pharmacy",
                "https://www.apollopharmacy.in/search?q="),
            PharmacyPlatform("Tata 1mg", "com.healthkart.onemg",
                "https://www.1mg.com/search/all?name="),
            PharmacyPlatform("PharmEasy", "com.pharmeasy.app",
                "https://pharmeasy.in/search/all?name="),
            PharmacyPlatform("Netmeds", "com.netmeds.app",
                "https://www.netmeds.com/catalogsearch/result?q=")
        )
    }

    data class PharmacyPlatform(
        val name: String,
        val packageName: String,
        val searchUrl: String
    )

    private var db: SahayDatabase? = null

    var medicines by mutableStateOf<List<MedicineEntity>>(emptyList())
        private set

    var refillNeeded by mutableStateOf<List<MedicineEntity>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var showBuyDialog by mutableStateOf(false)
    var selectedMedicineForPurchase by mutableStateOf<MedicineEntity?>(null)

    fun init(context: Context) {
        if (db == null) {
            db = SahayDatabase.getInstance(context.applicationContext)
            observeMedicines()
            ReminderScheduler.scheduleAllReminders(context)
        }
    }

    private fun observeMedicines() {
        viewModelScope.launch {
            db?.medicineDao()?.getAllMedicines()?.collectLatest { list ->
                medicines = list
                refillNeeded = list.filter { it.needsRefill() }
            }
        }
    }

    /**
     * Record taking a medicine (decrement stock by 1).
     */
    fun takeMedicine(medicine: MedicineEntity) {
        viewModelScope.launch {
            try {
                if (medicine.tabletsRemaining > 0) {
                    db?.medicineDao()?.takeMedicine(medicine.id)
                    Log.d(TAG, "Took medicine: ${medicine.name}, remaining: ${medicine.tabletsRemaining - 1}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error taking medicine: ${e.message}")
            }
        }
    }

    /**
     * Open pharmacy app or website to buy medicine.
     */
    fun buyMedicine(context: Context, medicine: MedicineEntity, pharmacy: PharmacyPlatform) {
        val query = Uri.encode(medicine.searchQuery())
        try {
            // Try to open the pharmacy app first
            val appIntent = context.packageManager.getLaunchIntentForPackage(pharmacy.packageName)
            if (appIntent != null) {
                context.startActivity(appIntent)
                Log.d(TAG, "Opened ${pharmacy.name} app")
            } else {
                // Fallback: open web search
                val webUrl = "${pharmacy.searchUrl}$query"
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
                Log.d(TAG, "Opened ${pharmacy.name} web: $webUrl")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open ${pharmacy.name}: ${e.message}")
            // Last resort: Google Play Store
            try {
                val storeIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=${pharmacy.packageName}")
                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(storeIntent)
            } catch (ex: Exception) {
                val browserIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=${pharmacy.packageName}")
                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(browserIntent)
            }
        }
    }

    /**
     * Add a new medicine manually.
     */
    fun addMedicine(medicine: MedicineEntity) {
        viewModelScope.launch {
            db?.medicineDao()?.insertMedicine(medicine)
        }
    }

    /**
     * Delete a medicine.
     */
    fun deleteMedicine(medicine: MedicineEntity) {
        viewModelScope.launch {
            db?.medicineDao()?.deleteMedicine(medicine)
        }
    }

    /**
     * Update medicine details.
     */
    fun updateMedicine(medicine: MedicineEntity) {
        viewModelScope.launch {
            db?.medicineDao()?.updateMedicine(medicine)
        }
    }

    fun showPurchaseOptions(medicine: MedicineEntity) {
        selectedMedicineForPurchase = medicine
        showBuyDialog = true
    }

    fun dismissPurchaseDialog() {
        showBuyDialog = false
        selectedMedicineForPurchase = null
    }
}
