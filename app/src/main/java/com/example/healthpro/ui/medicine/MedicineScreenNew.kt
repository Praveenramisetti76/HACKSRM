package com.example.healthpro.ui.medicine

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.healthpro.database.MedicineEntity
import com.example.healthpro.ui.theme.*

// ═══════════════════════════════════════════════════════════════
//  Premium colors for Medicine
// ═══════════════════════════════════════════════════════════════
private val MedBlue = Color(0xFF3B82F6)
private val MedGreen = Color(0xFF10B981)
private val MedOrange = Color(0xFFF97316)
private val MedRed = Color(0xFFEF4444)
private val MedPurple = Color(0xFF8B5CF6)
private val MedAmber = Color(0xFFF59E0B)
private val MedCyan = Color(0xFF06B6D4)

private val MedColors = listOf(MedBlue, MedGreen, MedPurple, MedOrange, MedCyan, MedAmber)

// ═══════════════════════════════════════════════════════════════
//  Main Entry Point
// ═══════════════════════════════════════════════════════════════
@Composable
fun MedicineScreenNew(navController: NavController) {
    val context = LocalContext.current
    val viewModel: MedicineViewModel = viewModel()

    LaunchedEffect(Unit) { viewModel.init(context) }

    // Add medicine dialog state
    var showAddDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(DarkNavy)) {
        if (viewModel.medicines.isEmpty()) {
            EmptyMedicineScreen(
                navController = navController,
                onAddManually = { showAddDialog = true }
            )
        } else {
            MedicineDashboard(
                viewModel = viewModel,
                navController = navController,
                onAddNew = { showAddDialog = true }
            )
        }

        // Buy dialog
        if (viewModel.showBuyDialog) {
            viewModel.selectedMedicineForPurchase?.let { medicineToBuy ->
                BuyMedicineDialog(
                    medicine = medicineToBuy,
                    onDismiss = { viewModel.dismissPurchaseDialog() },
                    onSelectPharmacy = { pharmacy ->
                        viewModel.buyMedicine(context, medicineToBuy, pharmacy)
                        viewModel.dismissPurchaseDialog()
                    }
                )
            }
        }

        // Add medicine dialog
        if (showAddDialog) {
            AddMedicineDialog(
                onDismiss = { showAddDialog = false },
                onAdd = { medicine ->
                    viewModel.addMedicine(medicine)
                    showAddDialog = false
                }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  Empty State
// ═══════════════════════════════════════════════════════════════
@Composable
private fun EmptyMedicineScreen(navController: NavController, onAddManually: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, "Back", tint = TextWhite, modifier = Modifier.size(28.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text("MEDICINE", style = MaterialTheme.typography.labelMedium,
                    color = MedBlue, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                Text("Manager", fontSize = 26.sp, color = TextWhite, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(80.dp))

        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(MedBlue.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Medication, null, tint = MedBlue, modifier = Modifier.size(48.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("No Medicines Added", fontSize = 24.sp, color = TextWhite, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Add your medicines manually to track\nstock and get refill alerts",
            color = TextMuted, textAlign = TextAlign.Center, fontSize = 16.sp, lineHeight = 24.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedButton(
            onClick = onAddManually,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.5.dp, MedBlue),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MedBlue)
        ) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text("Add Manually", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  Medicine Dashboard
// ═══════════════════════════════════════════════════════════════
@Composable
private fun MedicineDashboard(
    viewModel: MedicineViewModel,
    navController: NavController,
    onAddNew: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Header ──
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("MEDICINE", style = MaterialTheme.typography.labelMedium,
                        color = MedBlue, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                    Text("Dashboard", fontSize = 28.sp, color = TextWhite, fontWeight = FontWeight.Bold)
                }
                Row {
                    IconButton(onClick = onAddNew) {
                        Icon(Icons.Default.AddCircle, "Add", tint = MedGreen, modifier = Modifier.size(32.dp))
                    }
                }
            }
        }

        // ── Summary Stats ──
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MiniStatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Medication,
                    label = "Active",
                    value = "${viewModel.medicines.size}",
                    color = MedBlue
                )
                MiniStatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Warning,
                    label = "Refill",
                    value = "${viewModel.refillNeeded.size}",
                    color = if (viewModel.refillNeeded.isNotEmpty()) MedRed else MedGreen
                )
            }
        }

        // ── Refill Alert ──
        if (viewModel.refillNeeded.isNotEmpty()) {
            item {
                val pulsate = rememberInfiniteTransition(label = "refill")
                val alpha by pulsate.animateFloat(
                    initialValue = 0.6f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
                    label = "alpha"
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MedRed.copy(alpha = 0.12f)),
                    border = BorderStroke(1.dp, MedRed.copy(alpha = alpha * 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.NotificationsActive, null, tint = MedRed.copy(alpha = alpha),
                            modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("⚠️ Refill Needed", fontSize = 16.sp, color = MedRed, fontWeight = FontWeight.Bold)
                            Text(
                                viewModel.refillNeeded.joinToString(", ") { it.name },
                                fontSize = 13.sp, color = MedRed.copy(alpha = 0.7f),
                                maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // ── Medicine Cards ──
        item {
            Text("YOUR MEDICINES", style = MaterialTheme.typography.labelMedium,
                color = TextMuted, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
        }

        items(viewModel.medicines) { medicine ->
            MedicineCard(
                medicine = medicine,
                color = MedColors[medicine.id.toInt() % MedColors.size],
                onTake = { viewModel.takeMedicine(medicine) },
                onBuy = { viewModel.showPurchaseOptions(medicine) },
                onDelete = { viewModel.deleteMedicine(medicine) }
            )
        }



        item { Spacer(modifier = Modifier.height(12.dp)) }
    }
}

// ═══════════════════════════════════════════════════════════════
//  Mini Stat Card
// ═══════════════════════════════════════════════════════════════
@Composable
private fun MiniStatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(value, fontSize = 24.sp, color = color, fontWeight = FontWeight.Bold)
                Text(label, fontSize = 13.sp, color = TextMuted)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  Medicine Card
// ═══════════════════════════════════════════════════════════════
@Composable
private fun MedicineCard(
    medicine: MedicineEntity,
    color: Color,
    onTake: () -> Unit,
    onBuy: () -> Unit,
    onDelete: () -> Unit
) {
    val remainDays = medicine.remainingDays()
    val isLow = medicine.needsRefill()

    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isLow) MedRed.copy(alpha = 0.06f) else CardDark
        ),
        border = if (isLow) BorderStroke(1.dp, MedRed.copy(alpha = 0.2f)) else null
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pill icon
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Medication, null, tint = color, modifier = Modifier.size(26.dp))
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        medicine.name,
                        fontSize = 18.sp,
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${medicine.dosage} • ${medicine.frequency}",
                        fontSize = 13.sp,
                        color = TextMuted
                    )
                }

                // Stock indicator
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${medicine.tabletsRemaining}",
                        fontSize = 22.sp,
                        color = if (isLow) MedRed else color,
                        fontWeight = FontWeight.Bold
                    )
                    Text("left", fontSize = 11.sp, color = TextMuted)
                }
            }

            // Progress bar for stock
            Spacer(modifier = Modifier.height(12.dp))
            val stockPercent = if (medicine.totalTablets > 0)
                medicine.tabletsRemaining.toFloat() / medicine.totalTablets else 0f
            LinearProgressIndicator(
                progress = { stockPercent.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = when {
                    stockPercent <= 0.1f -> MedRed
                    stockPercent <= 0.3f -> MedOrange
                    else -> color
                },
                trackColor = DarkNavy
            )

            // Info line
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${medicine.tabletsPerDay}/day • $remainDays days supply",
                    fontSize = 12.sp, color = TextMuted
                )
                // Dose schedule icons
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (medicine.morningDose) DoseTimeChip("AM", Color(0xFFFBBF24))
                    if (medicine.afternoonDose) DoseTimeChip("PM", MedOrange)
                    if (medicine.nightDose) DoseTimeChip("Night", MedPurple)
                }
            }

            // Action buttons
            AnimatedVisibility(visible = expanded || isLow) {
                Column {
                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = DarkNavyLight)
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Take medicine button
                        Button(
                            onClick = onTake,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MedGreen),
                            enabled = medicine.tabletsRemaining > 0
                        ) {
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Take", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }

                        // Buy button
                        Button(
                            onClick = onBuy,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isLow) MedRed else MedBlue
                            )
                        ) {
                            Icon(Icons.Default.ShoppingCart, null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Buy", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }

                        // Delete
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MedRed.copy(alpha = 0.1f))
                        ) {
                            Icon(Icons.Default.Delete, null, tint = MedRed, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DoseTimeChip(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(label, fontSize = 10.sp, color = color, fontWeight = FontWeight.Bold)
    }
}

// ═══════════════════════════════════════════════════════════════
//  Buy Medicine Dialog (Pharmacy Selection)
// ═══════════════════════════════════════════════════════════════
@Composable
private fun BuyMedicineDialog(
    medicine: MedicineEntity,
    onDismiss: () -> Unit,
    onSelectPharmacy: (MedicineViewModel.PharmacyPlatform) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardDark,
        shape = RoundedCornerShape(24.dp),
        title = {
            Column {
                Text("Buy ${medicine.name}", fontSize = 22.sp, color = TextWhite, fontWeight = FontWeight.Bold)
                Text("${medicine.dosage}", fontSize = 14.sp, color = TextMuted)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Select pharmacy to order:", fontSize = 15.sp, color = TextMuted)

                MedicineViewModel.PHARMACY_LINKS.forEachIndexed { idx, pharmacy ->
                    val colors = listOf(MedGreen, MedBlue, MedOrange, MedPurple)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectPharmacy(pharmacy) },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkNavy)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(colors[idx % colors.size].copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.LocalPharmacy, null,
                                    tint = colors[idx % colors.size], modifier = Modifier.size(22.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(pharmacy.name, fontSize = 16.sp, color = TextWhite, fontWeight = FontWeight.Bold)
                                Text("Search: ${medicine.searchQuery()}", fontSize = 12.sp, color = TextMuted)
                            }
                            Icon(Icons.Default.OpenInNew, null, tint = TextMuted, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

// ═══════════════════════════════════════════════════════════════
//  Add Medicine Dialog
// ═══════════════════════════════════════════════════════════════
@Composable
private fun AddMedicineDialog(
    onDismiss: () -> Unit,
    onAdd: (MedicineEntity) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var dosage by remember { mutableStateOf("") }
    var frequency by remember { mutableStateOf("Once daily") }
    var totalTablets by remember { mutableStateOf("30") }
    var morning by remember { mutableStateOf(true) }
    var afternoon by remember { mutableStateOf(false) }
    var night by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardDark,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text("Add Medicine", fontSize = 22.sp, color = TextWhite, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Medicine Name", color = TextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MedBlue,
                        unfocusedBorderColor = TextMuted.copy(alpha = 0.3f),
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        cursorColor = MedBlue
                    ),
                    singleLine = true
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = dosage,
                        onValueChange = { dosage = it },
                        label = { Text("Dosage", color = TextMuted) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MedBlue,
                            unfocusedBorderColor = TextMuted.copy(alpha = 0.3f),
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            cursorColor = MedBlue
                        ),
                        singleLine = true,
                        placeholder = { Text("e.g., 500mg", color = TextMuted.copy(alpha = 0.5f)) }
                    )
                    OutlinedTextField(
                        value = totalTablets,
                        onValueChange = { totalTablets = it },
                        label = { Text("Stock", color = TextMuted) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MedBlue,
                            unfocusedBorderColor = TextMuted.copy(alpha = 0.3f),
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            cursorColor = MedBlue
                        ),
                        singleLine = true,
                        placeholder = { Text("30", color = TextMuted.copy(alpha = 0.5f)) }
                    )
                }

                Text("Dose Schedule", fontSize = 14.sp, color = TextMuted, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DoseToggle("Morning", morning, Color(0xFFFBBF24), Modifier.weight(1f)) { morning = it }
                    DoseToggle("Afternoon", afternoon, MedOrange, Modifier.weight(1f)) { afternoon = it }
                    DoseToggle("Night", night, MedPurple, Modifier.weight(1f)) { night = it }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val tabs = totalTablets.toIntOrNull() ?: 30
                        val perDay = listOf(morning, afternoon, night).count { it }.coerceAtLeast(1)
                        onAdd(
                            MedicineEntity(
                                name = name.trim(),
                                dosage = dosage.ifBlank { "as prescribed" },
                                frequency = frequency,
                                tabletsPerDay = perDay,
                                durationDays = if (perDay > 0) tabs / perDay else 30,
                                totalTablets = tabs,
                                tabletsRemaining = tabs,
                                morningDose = morning,
                                afternoonDose = afternoon,
                                nightDose = night
                            )
                        )
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MedGreen),
                enabled = name.isNotBlank()
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Medicine", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        }
    )
}

@Composable
private fun DoseToggle(label: String, checked: Boolean, color: Color, modifier: Modifier, onToggle: (Boolean) -> Unit) {
    Card(
        modifier = modifier
            .clickable { onToggle(!checked) },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (checked) color.copy(alpha = 0.15f) else DarkNavy
        ),
        border = if (checked) BorderStroke(1.dp, color.copy(alpha = 0.4f)) else null
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onToggle,
                colors = CheckboxDefaults.colors(checkedColor = color, uncheckedColor = TextMuted),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(label, fontSize = 10.sp, color = if (checked) color else TextMuted, fontWeight = FontWeight.SemiBold)
        }
    }
}
