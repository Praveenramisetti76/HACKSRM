package com.example.healthpro.screens

import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.healthpro.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun EmergencyScreen(navController: NavController) {
    var countdown by remember { mutableIntStateOf(45) }
    val infiniteTransition = rememberInfiniteTransition(label = "sos")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ring"
    )
    LaunchedEffect(Unit) { while (countdown > 0) { delay(1000); countdown-- } }

    Box(
        modifier = Modifier.fillMaxSize().background(
            brush = Brush.verticalGradient(listOf(Color(0xFF4A0E0E), Color(0xFF2D0808), Color(0xFF1A0505)))
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(40.dp))
            Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF6B1A1A)) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF4ADE80)))
                    Text("EMERGENCY PROTOCOL", style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
            Spacer(Modifier.height(32.dp))
            Text("Grandpa,", style = MaterialTheme.typography.titleLarge, color = Color.White.copy(alpha = 0.8f))
            Text("Are you okay?", style = MaterialTheme.typography.headlineLarge, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(0.3f))
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp).scale(pulseScale)) {
                Box(Modifier.size(200.dp).clip(CircleShape).background(brush = Brush.radialGradient(listOf(Color.Transparent, SosRed.copy(alpha = ringAlpha * 0.4f), SosRed.copy(alpha = ringAlpha * 0.6f)))))
                Box(Modifier.size(160.dp).clip(CircleShape).background(SosRed.copy(alpha = 0.3f)))
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp).clip(CircleShape).background(brush = Brush.radialGradient(listOf(SosRed, Color(0xFFB91C1C))))) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("SOS", style = MaterialTheme.typography.headlineLarge, color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                        Text("HELP IN ${countdown}S", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f), letterSpacing = 1.sp)
                    }
                }
            }
            Spacer(Modifier.weight(0.4f))
            Button(onClick = { navController.popBackStack() }, modifier = Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A2A3A))) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column { Text("I AM OKAY", style = MaterialTheme.typography.labelLarge, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp); Text("Cancel emergency alert", style = MaterialTheme.typography.labelSmall, color = TextMuted) }
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(36.dp).clip(CircleShape).background(TealAccent.copy(alpha = 0.2f))) { Icon(Icons.Default.Check, null, tint = TealAccent, modifier = Modifier.size(20.dp)) }
                }
            }
            Spacer(Modifier.height(14.dp))
            Button(onClick = { }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = SosRed)) {
                Icon(Icons.Default.NotificationsActive, null, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(8.dp))
                Text("SEND HELP NOW", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Mic, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                Text("Or say \"I'm okay\"", style = MaterialTheme.typography.labelMedium, color = TextMuted)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
