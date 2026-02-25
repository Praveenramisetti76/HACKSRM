package com.example.healthpro.medicine.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.healthpro.medicine.data.repository.MedicineManagerRepository
import com.example.healthpro.medicine.viewmodel.MedicineManagerViewModel
import com.example.healthpro.ui.theme.*

private val MedBlue = Color(0xFF3B82F6)
private val MedGreen = Color(0xFF10B981)
private val MedRed = Color(0xFFEF4444)
private val MedOrange = Color(0xFFF97316)

@Composable
fun MedicineListScreen(
    navController: NavController,
    viewModel: MedicineManagerViewModel = viewModel()
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.init(context) }

    val medicines by viewModel.medicinesWithStock.collectAsState()
    val extracted by viewModel.extractedMedicines.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMsg by viewModel.errorMessage.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    val reportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val mime = context.contentResolver.getType(it)
            viewModel.processReport(it, mime)
        }
    }


    Box(modifier = Modifier.fillMaxSize().background(DarkNavy)) {
        Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = TextWhite, modifier = Modifier.size(32.dp))
                    }
                    Text(
                        "Medicine Manager",
                        fontSize = 24.sp,
                        color = TextWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Row {
                        IconButton(onClick = { navController.navigate("medicine_intake") }) {
                            Icon(Icons.Default.CheckCircle, "Intake", tint = MedBlue, modifier = Modifier.size(28.dp))
                        }
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, "Add", tint = MedGreen, modifier = Modifier.size(32.dp))
                        }
                    }
                }

            if (medicines.isEmpty() && extracted.isEmpty()) {
                EmptyState(
                    onAddClick = { showAddDialog = true },
                    onImportReport = { reportLauncher.launch("*/*") }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (medicines.any { it.needsReorder }) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MedRed.copy(alpha = 0.15f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Warning, null, tint = MedRed, modifier = Modifier.size(28.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        "Some medicines need reorder",
                                        fontSize = 18.sp,
                                        color = MedRed,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    items(medicines) { mws ->
                        MedicineListCard(
                            medicineWithStock = mws,
                            onClick = { navController.navigate("medicine_detail/${mws.medicine.id}") },
                            onReorder = { navController.navigate("medicine_reorder/${mws.medicine.id}") }
                        )
                    }
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { reportLauncher.launch("*/*") },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = CardDark)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.PictureAsPdf, null, tint = MedOrange, modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Import from Report", fontSize = 18.sp, color = TextWhite, fontWeight = FontWeight.Bold)
                                    Text("Upload PDF or image to extract medicines", fontSize = 14.sp, color = TextMuted)
                                }
                                Icon(Icons.Default.ChevronRight, null, tint = TextMuted)
                            }
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            AddEditMedicineDialog(
                onDismiss = { showAddDialog = false },
                onSave = { medicine ->
                    viewModel.addMedicine(medicine)
                    showAddDialog = false
                }
            )
        }

        if (extracted.isNotEmpty()) {
            ExtractedMedicinesSuggestionDialog(
                medicines = extracted,
                onConfirm = {
                    viewModel.confirmExtractedMedicines(it)
                },
                onDismiss = { viewModel.dismissExtractedMedicines() }
            )
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DarkNavy.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CardDark)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = MedBlue)
                        Spacer(Modifier.height(16.dp))
                        Text("Extracting medicines from report...", fontSize = 18.sp, color = TextWhite)
                    }
                }
            }
        }

        if (!errorMsg.isNullOrBlank()) {
            Snackbar(
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.BottomCenter),
                containerColor = MedRed,
                contentColor = Color.White
            ) {
                Text(errorMsg ?: "")
            }
        }
    }
}

@Composable
private fun EmptyState(onAddClick: () -> Unit, onImportReport: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Medication, null, tint = MedBlue, modifier = Modifier.size(80.dp))
        Spacer(Modifier.height(24.dp))
        Text("No medicines added", fontSize = 24.sp, color = TextWhite, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Import from report or add manually",
            fontSize = 18.sp,
            color = TextMuted
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onImportReport,
            modifier = Modifier.height(56.dp).fillMaxWidth(0.9f),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MedOrange)
        ) {
            Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text("Import from Report (PDF/Image)", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onAddClick,
            modifier = Modifier.height(56.dp).fillMaxWidth(0.9f),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, MedBlue)
        ) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add Manually", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MedicineListCard(
    medicineWithStock: MedicineManagerRepository.MedicineWithStock,
    onClick: () -> Unit,
    onReorder: () -> Unit
) {
    val m = medicineWithStock.medicine
    val isLow = medicineWithStock.needsReorder

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isLow) MedRed.copy(alpha = 0.08f) else CardDark
        )
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MedBlue.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Medication, null, tint = MedBlue, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    m.name,
                    fontSize = 20.sp,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${m.dosage} • ${m.frequencyPerDay}x/day",
                    fontSize = 16.sp,
                    color = TextMuted
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${medicineWithStock.remaining}",
                    fontSize = 24.sp,
                    color = if (isLow) MedRed else TextWhite,
                    fontWeight = FontWeight.Bold
                )
                Text("left", fontSize = 14.sp, color = TextMuted)
            }
            if (isLow) {
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onReorder) {
                    Icon(Icons.Default.ShoppingCart, "Reorder", tint = MedRed, modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}
