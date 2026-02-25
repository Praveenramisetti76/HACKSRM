package com.example.healthpro.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.healthpro.genie.MedicineEntry
import com.example.healthpro.genie.MedicineRepository
import com.example.healthpro.ui.theme.*

@Composable
fun MedicineOrderScreen(navController: NavController) {
    val medicines = remember { MedicineRepository.getMedicineHistory() }
    val selectedMeds = remember { mutableStateMapOf<Int, Boolean>() }
    val scrollState = rememberScrollState()

    // Initialize all as selected
    LaunchedEffect(Unit) {
        medicines.forEach { selectedMeds[it.id] = true }
    }

    val selectedList = medicines.filter { selectedMeds[it.id] == true }
    val totalPrice = selectedList.sumOf { it.estimatedPrice }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavy)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Back button
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, "Back", tint = TextWhite)
            }
            Text("Back", style = MaterialTheme.typography.bodyLarge, color = TextWhite)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Header
        Text("Medicine Reorder", style = MaterialTheme.typography.headlineMedium,
            color = TextWhite, fontWeight = FontWeight.Bold)
        Text("Based on your prescription history", style = MaterialTheme.typography.bodyMedium,
            color = TextMuted)

        Spacer(modifier = Modifier.height(8.dp))

        // Simulation banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2D1A0A))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Info, null, tint = FoodOrange, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("This is a simulation. No real orders will be placed.",
                    style = MaterialTheme.typography.bodySmall, color = FoodOrange)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Medicine list
        Text("YOUR PRESCRIPTIONS", style = MaterialTheme.typography.labelMedium,
            color = TextMuted, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(8.dp))

        medicines.forEach { med ->
            MedicineCard(
                medicine = med,
                isSelected = selectedMeds[med.id] == true,
                onToggle = { selectedMeds[med.id] = !(selectedMeds[med.id] ?: false) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Checkout Preview
        Text("SIMULATED CHECKOUT", style = MaterialTheme.typography.labelMedium,
            color = TextMuted, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardDark)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Selected items summary
                Text("${selectedList.size} medicine(s) selected",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextWhite, fontWeight = FontWeight.Bold)

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = DarkNavyLight)
                Spacer(modifier = Modifier.height(12.dp))

                // Delivery info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("DELIVERY TO", style = MaterialTheme.typography.labelSmall,
                            color = TextMuted, letterSpacing = 1.sp)
                        Text("Home", style = MaterialTheme.typography.bodyLarge,
                            color = TextWhite, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("PAYMENT", style = MaterialTheme.typography.labelSmall,
                            color = TextMuted, letterSpacing = 1.sp)
                        Text("COD", style = MaterialTheme.typography.bodyLarge,
                            color = TealAccent, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = DarkNavyLight)
                Spacer(modifier = Modifier.height(12.dp))

                // Price breakdown
                selectedList.forEach { med ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${med.name} ${med.dosage}", style = MaterialTheme.typography.bodySmall,
                            color = TextGray)
                        Text("₹${med.estimatedPrice.toInt()}", style = MaterialTheme.typography.bodySmall,
                            color = TextGray)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = DarkNavyLight)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total", style = MaterialTheme.typography.titleMedium,
                        color = TextWhite, fontWeight = FontWeight.Bold)
                    Text("₹${totalPrice.toInt()}", style = MaterialTheme.typography.titleMedium,
                        color = TealAccent, fontWeight = FontWeight.Bold)
                }

                if (selectedList.any { it.requiresPrescription }) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Description, null, tint = FoodOrange,
                            modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Prescription upload required for some items",
                            style = MaterialTheme.typography.bodySmall, color = FoodOrange)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Confirm button (simulated)
        Button(
            onClick = { navController.popBackStack() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TealAccent),
            enabled = selectedList.isNotEmpty()
        ) {
            Icon(Icons.Default.CheckCircle, null, tint = Color.Black, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Simulated Order (Demo Only)", fontWeight = FontWeight.Bold,
                fontSize = 16.sp, color = Color.Black)
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancel", style = MaterialTheme.typography.bodyLarge, color = TextGray)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun MedicineCard(medicine: MedicineEntry, isSelected: Boolean, onToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) CardDark else DarkNavySurface
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(checkedColor = TealAccent)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(medicine.name, style = MaterialTheme.typography.titleSmall,
                    color = TextWhite, fontWeight = FontWeight.Bold)
                Text("${medicine.dosage} • ${medicine.frequency}",
                    style = MaterialTheme.typography.bodySmall, color = TextGray)
                Text("Last ordered: ${medicine.lastOrdered}",
                    style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("₹${medicine.estimatedPrice.toInt()}", style = MaterialTheme.typography.titleSmall,
                    color = TealAccent, fontWeight = FontWeight.Bold)
                if (medicine.requiresPrescription) {
                    Text("Rx", style = MaterialTheme.typography.labelSmall,
                        color = FoodOrange, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
