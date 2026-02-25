package com.example.healthpro.medicine.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.healthpro.medicine.data.db.MedicineManagerEntity
import com.example.healthpro.ui.theme.*
import java.util.Calendar

private val MedBlue = Color(0xFF3B82F6)
private val MedGreen = Color(0xFF10B981)

@Composable
fun AddEditMedicineScreen(
    navController: NavController,
    medicineId: Long?,
    onSave: (MedicineManagerEntity) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var dosage by remember { mutableStateOf("") }
    var frequencyPerDay by remember { mutableStateOf("1") }
    var totalQuantity by remember { mutableStateOf("30") }
    var reorderThreshold by remember { mutableStateOf("7") }
    val startDate = remember { System.currentTimeMillis() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavy)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, "Back", tint = TextWhite, modifier = Modifier.size(32.dp))
            }
            Text(
                if (medicineId != null) "Edit Medicine" else "Add Medicine",
                fontSize = 24.sp,
                color = TextWhite,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp)
            )
            Spacer(Modifier.width(56.dp))
        }

        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Medicine Name", color = TextMuted, fontSize = 18.sp) },
                modifier = Modifier.fillMaxWidth().height(72.dp),
                textStyle = LocalTextStyle.current.copy(fontSize = 20.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MedBlue,
                    unfocusedBorderColor = TextMuted.copy(alpha = 0.5f),
                    focusedTextColor = TextWhite,
                    unfocusedTextColor = TextWhite,
                    cursorColor = MedBlue,
                    focusedLabelColor = MedBlue
                ),
                singleLine = true
            )

            OutlinedTextField(
                value = dosage,
                onValueChange = { dosage = it },
                label = { Text("Dosage (e.g., 500mg)", color = TextMuted, fontSize = 18.sp) },
                modifier = Modifier.fillMaxWidth().height(72.dp),
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

            OutlinedTextField(
                value = frequencyPerDay,
                onValueChange = { frequencyPerDay = it.filter { c -> c.isDigit() } },
                label = { Text("Frequency per day", color = TextMuted, fontSize = 18.sp) },
                modifier = Modifier.fillMaxWidth().height(72.dp),
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

            OutlinedTextField(
                value = totalQuantity,
                onValueChange = { totalQuantity = it.filter { c -> c.isDigit() } },
                label = { Text("Total quantity", color = TextMuted, fontSize = 18.sp) },
                modifier = Modifier.fillMaxWidth().height(72.dp),
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

            OutlinedTextField(
                value = reorderThreshold,
                onValueChange = { reorderThreshold = it.filter { c -> c.isDigit() } },
                label = { Text("Reorder threshold (days remaining)", color = TextMuted, fontSize = 18.sp) },
                modifier = Modifier.fillMaxWidth().height(72.dp),
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

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(
                            MedicineManagerEntity(
                                id = medicineId ?: 0L,
                                name = name.trim(),
                                dosage = dosage.ifBlank { "as prescribed" },
                                frequencyPerDay = frequencyPerDay.toIntOrNull()?.coerceIn(1, 10) ?: 1,
                                totalQuantity = totalQuantity.toIntOrNull()?.coerceAtLeast(1) ?: 30,
                                startDate = startDate,
                                reorderThreshold = reorderThreshold.toIntOrNull()?.coerceAtLeast(1) ?: 7
                            )
                        )
                        navController.popBackStack()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MedGreen)
            ) {
                Icon(Icons.Default.Save, null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save Medicine", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
