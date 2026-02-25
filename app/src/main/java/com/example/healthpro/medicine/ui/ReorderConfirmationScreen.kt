package com.example.healthpro.medicine.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.healthpro.medicine.data.db.MedicineManagerDatabase
import com.example.healthpro.medicine.data.pharmacy.PharmacyAppChecker
import com.example.healthpro.medicine.data.pharmacy.PharmacyProvider
import com.example.healthpro.medicine.data.repository.MedicineManagerRepository
import com.example.healthpro.medicine.viewmodel.MedicineManagerViewModel
import com.example.healthpro.medicine.viewmodel.ReorderViewModel
import com.example.healthpro.ui.theme.*

private val MedBlue = Color(0xFF3B82F6)
private val MedGreen = Color(0xFF10B981)
private val MedRed = Color(0xFFEF4444)

@Composable
fun ReorderConfirmationScreen(
    navController: NavController,
    medicineId: Long,
    medicineViewModel: MedicineManagerViewModel = viewModel(),
    reorderViewModel: ReorderViewModel = viewModel()
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { medicineViewModel.init(context) }

    val medicines by medicineViewModel.medicinesWithStock.collectAsState()
    val medicineWithStock = medicines.find { it.medicine.id == medicineId }
    var quantity by remember { mutableStateOf("30") }
    var selectedProvider by remember { mutableStateOf<PharmacyProvider?>(null) }
    var showPharmacyPicker by remember { mutableStateOf(false) }
    val showInstallDialog by reorderViewModel.showInstallDialog.collectAsState()
    val orderStatus by reorderViewModel.orderStatus.collectAsState()
    val providers = medicineViewModel.getPharmacyProviders()

    Box(modifier = Modifier.fillMaxSize().background(DarkNavy)) {
        if (medicineWithStock == null) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = MedBlue)
        } else {
            val m = medicineWithStock.medicine
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = TextWhite, modifier = Modifier.size(32.dp))
                    }
                    Text(
                        "Reorder ${m.name}",
                        fontSize = 22.sp,
                        color = TextWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(56.dp))
                }

                Spacer(Modifier.height(24.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CardDark)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text("Edit quantity to order:", fontSize = 18.sp, color = TextMuted)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = quantity,
                            onValueChange = { quantity = it.filter { c -> c.isDigit() } },
                            modifier = Modifier.fillMaxWidth().height(64.dp),
                            textStyle = LocalTextStyle.current.copy(fontSize = 20.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MedBlue,
                                unfocusedBorderColor = TextMuted.copy(alpha = 0.5f),
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                cursorColor = MedBlue
                            ),
                            singleLine = true
                        )
                        Spacer(Modifier.height(20.dp))
                        Text("Select pharmacy:", fontSize = 18.sp, color = TextMuted)
                        Spacer(Modifier.height(12.dp))
                        providers.forEach { provider ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        selectedProvider = provider
                                    },
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selectedProvider == provider) MedBlue.copy(alpha = 0.2f) else DarkNavyLight
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.LocalPharmacy, null, tint = MedBlue, modifier = Modifier.size(28.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        provider.getDisplayName(),
                                        fontSize = 20.sp,
                                        color = TextWhite,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (selectedProvider == provider) {
                                        Spacer(Modifier.weight(1f))
                                        Icon(Icons.Default.Check, null, tint = MedGreen, modifier = Modifier.size(24.dp))
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(32.dp))
                        Button(
                            onClick = {
                                val prov = selectedProvider ?: providers.firstOrNull()
                                val qty = quantity.toIntOrNull() ?: 30
                                if (prov != null) {
                                    val isInstalled = PharmacyAppChecker.isPharmacyInstalled(context, prov)
                                    reorderViewModel.checkAndOrder(context, m, prov, qty)
                                    if (isInstalled) navController.navigate("medicine_order_status/${m.id}")
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(60.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MedGreen)
                        ) {
                            Icon(Icons.Default.ShoppingCart, null, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Confirm Order", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (showInstallDialog) {
            InstallPharmacyDialog(
                providerName = selectedProvider?.getDisplayName() ?: "Pharmacy",
                onInstall = { reorderViewModel.onInstallConfirmed() },
                onDismiss = { reorderViewModel.onInstallDismissed() }
            )
        }
    }
}

@Composable
private fun InstallPharmacyDialog(
    providerName: String,
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardDark,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                "Pharmacy App Required",
                fontSize = 22.sp,
                color = TextWhite,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                "Pharmacy app required to complete order. Install $providerName now?",
                fontSize = 18.sp,
                color = TextMuted
            )
        },
        confirmButton = {
            Button(
                onClick = onInstall,
                colors = ButtonDefaults.buttonColors(containerColor = MedGreen)
            ) {
                Text("Install Now", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted, fontSize = 18.sp)
            }
        }
    )
}
