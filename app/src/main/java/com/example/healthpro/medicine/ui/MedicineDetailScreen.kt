package com.example.healthpro.medicine.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.example.healthpro.medicine.data.repository.MedicineManagerRepository
import com.example.healthpro.medicine.viewmodel.MedicineDetailViewModel
import com.example.healthpro.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

private val MedBlue = Color(0xFF3B82F6)
private val MedGreen = Color(0xFF10B981)
private val MedRed = Color(0xFFEF4444)
private val MedOrange = Color(0xFFF97316)

@Composable
fun MedicineDetailScreen(
    navController: NavController,
    medicineId: Long,
    viewModel: MedicineDetailViewModel = viewModel()
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.init(context) }

    val mws by viewModel.medicineWithStock.collectAsState()
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(DarkNavy)) {
        val currentMws = mws
        if (currentMws == null) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = MedBlue)
        } else {
            val data = currentMws
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
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
                        data.medicine.name,
                        fontSize = 22.sp,
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Row {
                        IconButton(onClick = { showEditDialog = true }) {
                            Icon(Icons.Default.Edit, "Edit", tint = MedBlue, modifier = Modifier.size(28.dp))
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, "Delete", tint = MedRed, modifier = Modifier.size(28.dp))
                        }
                    }
                }

                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = CardDark)
                    ) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(MedBlue.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Medication, null, tint = MedBlue, modifier = Modifier.size(32.dp))
                                }
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    Text(
                                        data.medicine.dosage,
                                        fontSize = 20.sp,
                                        color = TextWhite,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "${data.medicine.frequencyPerDay}x per day",
                                        fontSize = 16.sp,
                                        color = TextMuted
                                    )
                                }
                            }
                            Spacer(Modifier.height(20.dp))
                            HorizontalDivider(color = DarkNavyLight)
                            Spacer(Modifier.height(20.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                DetailItem("Remaining", "${data.remaining}", if (data.needsReorder) MedRed else MedGreen)
                                DetailItem("Total", "${data.medicine.totalQuantity}", TextWhite)
                                DetailItem("Taken", "${data.totalTaken}", TextMuted)
                            }
                        }
                    }

                    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                    Text(
                        "Started: ${sdf.format(Date(data.medicine.startDate))}",
                        fontSize = 16.sp,
                        color = TextMuted
                    )

                    Button(
                        onClick = { viewModel.markTaken() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MedGreen)
                    ) {
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Mark as Taken Today", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }

                    if (data.needsReorder) {
                        Button(
                            onClick = { navController.navigate("medicine_reorder/${data.medicine.id}") },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MedRed)
                        ) {
                            Icon(Icons.Default.ShoppingCart, null, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Reorder Now", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    val currentMwsForDialogs = mws
    if (showEditDialog && currentMwsForDialogs != null) {
        AddEditMedicineDialog(
            medicine = currentMwsForDialogs.medicine,
            onDismiss = { showEditDialog = false },
            onSave = { med ->
                viewModel.updateMedicine(med)
                showEditDialog = false
            }
        )
    }

    if (showDeleteConfirm && currentMwsForDialogs != null) {
        val medToDelete = currentMwsForDialogs.medicine
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = CardDark,
            shape = RoundedCornerShape(20.dp),
            title = { Text("Delete ${medToDelete.name}?", fontSize = 20.sp, color = TextWhite) },
            text = { Text("This cannot be undone.", fontSize = 18.sp, color = TextMuted) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMedicine(medToDelete)
                    showDeleteConfirm = false
                    navController.popBackStack()
                }) {
                    Text("Delete", color = MedRed, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = TextMuted, fontSize = 18.sp)
                }
            }
        )
    }
}

@Composable
private fun DetailItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 24.sp, color = color, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 14.sp, color = TextMuted)
    }
}
