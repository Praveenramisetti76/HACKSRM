package com.example.healthpro.medicine.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.healthpro.medicine.viewmodel.ReorderViewModel
import com.example.healthpro.ui.theme.*

private val MedGreen = Color(0xFF10B981)
private val MedRed = Color(0xFFEF4444)
private val MedBlue = Color(0xFF3B82F6)

@Composable
fun OrderStatusScreen(
    navController: NavController,
    medicineId: Long,
    reorderViewModel: ReorderViewModel = viewModel()
) {
    val orderStatus by reorderViewModel.orderStatus.collectAsState()

    LaunchedEffect(orderStatus) {
        if (orderStatus is ReorderViewModel.OrderStatus.Idle) {
            reorderViewModel.resetOrderStatus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavy)
            .padding(24.dp)
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
                "Order Status",
                fontSize = 24.sp,
                color = TextWhite,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(56.dp))
        }

        Spacer(Modifier.height(48.dp))

        when (val status = orderStatus) {
            is ReorderViewModel.OrderStatus.Processing -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = MedBlue, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(24.dp))
                    Text("Processing order...", fontSize = 20.sp, color = TextMuted)
                }
            }
            is ReorderViewModel.OrderStatus.Success -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(MedGreen.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CheckCircle, null, tint = MedGreen, modifier = Modifier.size(64.dp))
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Order Initiated",
                        fontSize = 26.sp,
                        color = TextWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        status.message,
                        fontSize = 18.sp,
                        color = TextMuted,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(48.dp))
                    Button(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MedGreen)
                    ) {
                        Text("Back to Medicine", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            is ReorderViewModel.OrderStatus.Error -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(MedRed.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Error, null, tint = MedRed, modifier = Modifier.size(64.dp))
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Order Failed",
                        fontSize = 26.sp,
                        color = TextWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        status.message,
                        fontSize = 18.sp,
                        color = TextMuted,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(48.dp))
                    Button(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MedBlue)
                    ) {
                        Text("Try Again", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            else -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Redirecting...", fontSize = 20.sp, color = TextMuted)
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { navController.popBackStack() },
                        colors = ButtonDefaults.buttonColors(containerColor = MedBlue)
                    ) {
                        Text("Back to Medicine", fontSize = 18.sp)
                    }
                }
            }
        }
    }
}
