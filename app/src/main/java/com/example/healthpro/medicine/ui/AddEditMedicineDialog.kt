package com.example.healthpro.medicine.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthpro.medicine.data.db.MedicineManagerEntity
import com.example.healthpro.ui.theme.*

private val MedBlue = Color(0xFF3B82F6)
private val MedGreen = Color(0xFF10B981)

@Composable
fun AddEditMedicineDialog(
    medicine: MedicineManagerEntity? = null,
    onDismiss: () -> Unit,
    onSave: (MedicineManagerEntity) -> Unit
) {
    var name by remember { mutableStateOf(medicine?.name ?: "") }
    var dosage by remember { mutableStateOf(medicine?.dosage ?: "") }
    var frequencyPerDay by remember { mutableStateOf(medicine?.frequencyPerDay?.toString() ?: "1") }
    var totalQuantity by remember { mutableStateOf(medicine?.totalQuantity?.toString() ?: "30") }
    var reorderThreshold by remember { mutableStateOf(medicine?.reorderThreshold?.toString() ?: "7") }
    val startDate = remember { medicine?.startDate ?: System.currentTimeMillis() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardDark,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                if (medicine != null) "Edit Medicine" else "Add Medicine",
                fontSize = 22.sp,
                color = TextWhite,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Medicine Name", color = TextMuted, fontSize = 16.sp) },
                    modifier = Modifier.fillMaxWidth().height(64.dp),
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
                OutlinedTextField(
                    value = dosage,
                    onValueChange = { dosage = it },
                    label = { Text("Dosage", color = TextMuted, fontSize = 16.sp) },
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    textStyle = LocalTextStyle.current.copy(fontSize = 18.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MedBlue,
                        unfocusedBorderColor = TextMuted.copy(alpha = 0.5f),
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        cursorColor = MedBlue
                    ),
                    singleLine = true,
                    placeholder = { Text("e.g., 500mg", color = TextMuted.copy(alpha = 0.5f)) }
                )
                OutlinedTextField(
                    value = frequencyPerDay,
                    onValueChange = { frequencyPerDay = it.filter { c -> c.isDigit() } },
                    label = { Text("Frequency per day", color = TextMuted, fontSize = 16.sp) },
                    modifier = Modifier.fillMaxWidth().height(64.dp),
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
                OutlinedTextField(
                    value = totalQuantity,
                    onValueChange = { totalQuantity = it.filter { c -> c.isDigit() } },
                    label = { Text("Total quantity", color = TextMuted, fontSize = 16.sp) },
                    modifier = Modifier.fillMaxWidth().height(64.dp),
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
                OutlinedTextField(
                    value = reorderThreshold,
                    onValueChange = { reorderThreshold = it.filter { c -> c.isDigit() } },
                    label = { Text("Reorder threshold (days)", color = TextMuted, fontSize = 16.sp) },
                    modifier = Modifier.fillMaxWidth().height(64.dp),
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
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(
                            MedicineManagerEntity(
                                id = medicine?.id ?: 0L,
                                name = name.trim(),
                                dosage = dosage.ifBlank { "as prescribed" },
                                frequencyPerDay = frequencyPerDay.toIntOrNull()?.coerceIn(1, 10) ?: 1,
                                totalQuantity = totalQuantity.toIntOrNull()?.coerceAtLeast(1) ?: 30,
                                startDate = startDate,
                                reorderThreshold = reorderThreshold.toIntOrNull()?.coerceAtLeast(1) ?: 7
                            )
                        )
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MedGreen),
                enabled = name.isNotBlank()
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("Save", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted, fontSize = 18.sp)
            }
        }
    )
}
