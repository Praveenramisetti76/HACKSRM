package com.example.healthpro.medicine.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthpro.medicine.viewmodel.MedicineManagerViewModel
import com.example.healthpro.ui.theme.*

private val MedBlue = Color(0xFF3B82F6)
private val MedGreen = Color(0xFF10B981)

@Composable
fun ExtractedMedicinesSuggestionDialog(
    medicines: List<MedicineManagerViewModel.ExtractedMedicineSuggestion>,
    onConfirm: (List<MedicineManagerViewModel.ExtractedMedicineSuggestion>) -> Unit,
    onDismiss: () -> Unit
) {
    var quantities by remember(medicines) {
        mutableStateOf(medicines.associate { it.name to it.suggestedQuantity })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardDark,
        shape = RoundedCornerShape(24.dp),
        title = {
            Column {
                Text(
                    "Medicines Found in Report",
                    fontSize = 22.sp,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Confirm quantity for 30 days supply",
                    fontSize = 16.sp,
                    color = TextMuted
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                medicines.forEach { m ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkNavyLight)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        m.name,
                                        fontSize = 18.sp,
                                        color = TextWhite,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "${m.dosage} • ${m.frequencyPerDay}x/day",
                                        fontSize = 14.sp,
                                        color = TextMuted
                                    )
                                }
                                OutlinedTextField(
                                    value = (quantities[m.name] ?: m.suggestedQuantity).toString(),
                                    onValueChange = { v ->
                                        quantities = quantities + (m.name to (v.filter { c -> c.isDigit() }.toIntOrNull() ?: m.suggestedQuantity))
                                    },
                                    modifier = Modifier.width(80.dp).height(52.dp),
                                    textStyle = LocalTextStyle.current.copy(fontSize = 18.sp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MedBlue,
                                        unfocusedBorderColor = TextMuted.copy(alpha = 0.5f),
                                        focusedTextColor = TextWhite,
                                        unfocusedTextColor = TextWhite,
                                        cursorColor = MedBlue
                                    ),
                                    singleLine = true
                                )
                            }
                        }
                    }
                }
            }
        },
                confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        medicines.map { m ->
                            MedicineManagerViewModel.ExtractedMedicineSuggestion(
                                name = m.name,
                                dosage = m.dosage,
                                frequencyPerDay = m.frequencyPerDay,
                                suggestedQuantity = quantities[m.name] ?: m.suggestedQuantity
                            )
                        }
                    )
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MedGreen)
            ) {
                Icon(Icons.Default.Check, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add to Medicine List", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted, fontSize = 18.sp)
            }
        }
    )
}
