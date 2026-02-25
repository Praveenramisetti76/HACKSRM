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
import com.example.healthpro.medicine.data.repository.MedicineManagerRepository
import com.example.healthpro.medicine.viewmodel.MedicineManagerViewModel
import com.example.healthpro.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

private val MedBlue = Color(0xFF3B82F6)
private val MedGreen = Color(0xFF10B981)

@Composable
fun IntakeTrackerScreen(
    navController: NavController,
    viewModel: MedicineManagerViewModel = viewModel()
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.init(context) }

    val medicines by viewModel.medicinesWithStock.collectAsState()
    val today = remember {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.timeInMillis
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavy)
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
            Column {
                Text(
                    "Today's Intake",
                    fontSize = 24.sp,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date()),
                    fontSize = 16.sp,
                    color = TextMuted
                )
            }
            Spacer(Modifier.width(56.dp))
        }

        if (medicines.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Medication, null, tint = MedBlue.copy(alpha = 0.5f), modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("No medicines to track", fontSize = 20.sp, color = TextMuted)
                    Spacer(Modifier.height(8.dp))
                    Text("Add medicines first", fontSize = 16.sp, color = TextMuted)
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { navController.popBackStack() },
                        colors = ButtonDefaults.buttonColors(containerColor = MedBlue)
                    ) {
                        Text("Back to List", fontSize = 18.sp)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(medicines) { mws ->
                    IntakeTrackerCard(
                        medicineWithStock = mws,
                        onMarkTaken = { viewModel.markTaken(mws.medicine.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun IntakeTrackerCard(
    medicineWithStock: MedicineManagerRepository.MedicineWithStock,
    onMarkTaken: () -> Unit
) {
    val m = medicineWithStock.medicine
    var isTaken by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
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
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${m.dosage} • ${m.frequencyPerDay}x/day • ${medicineWithStock.remaining} left",
                    fontSize = 16.sp,
                    color = TextMuted
                )
            }
            FilledTonalButton(
                onClick = {
                    if (!isTaken) {
                        onMarkTaken()
                        isTaken = true
                    }
                },
                modifier = Modifier.height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (isTaken) MedGreen.copy(alpha = 0.3f) else MedGreen
                ),
                enabled = !isTaken && medicineWithStock.remaining > 0
            ) {
                if (isTaken) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Taken", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                } else {
                    Text("Take", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
