package com.example.healthpro.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.healthpro.ble.BleConnectionState
import com.example.healthpro.ble.BleStateHolder
import com.example.healthpro.navigation.Screen
import com.example.healthpro.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HomeScreen(navController: NavController) {
    val scrollState = rememberScrollState()
    val calendar = Calendar.getInstance()
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val dayFormat = SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
    val currentTime = timeFormat.format(calendar.time)
    val currentDay = dayFormat.format(calendar.time).uppercase()

    // Read preferred name from auth preferences
    val context = LocalContext.current
    val authPreferences = remember { com.example.healthpro.auth.AuthPreferences(context) }
    val userName = authPreferences.preferredName.ifEmpty { "there" }

    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    val greeting = when {
        hour < 12 -> "Good Morning, $userName"
        hour < 17 -> "Good Afternoon, $userName"
        else -> "Good Evening, $userName"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavy)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
    // BLE connection state for status dot
    val bleState by BleStateHolder.connectionState.collectAsState()
    val bleDotColor = when (bleState) {
        BleConnectionState.CONNECTED   -> Color(0xFF22C55E)   // green
        BleConnectionState.SCANNING,
        BleConnectionState.CONNECTING  -> Color(0xFFF59E0B)   // amber
        else                           -> Color(0xFFEF4444)   // red
    }

        // Status bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SAHAY",
                style = MaterialTheme.typography.labelLarge,
                color = TextWhite,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // BLE Nano status dot — tap goes to pairing screen
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(bleDotColor)
                        .clickable { navController.navigate(Screen.BlePairing.route) }
                )
                Icon(Icons.Default.SignalCellularAlt, "Signal", tint = TextGray, modifier = Modifier.size(18.dp))
                Icon(Icons.Default.Wifi, "WiFi", tint = TextGray, modifier = Modifier.size(18.dp))
                Icon(Icons.Default.BatteryFull, "Battery", tint = TextGray, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Greeting
        Text(
            text = greeting,
            style = MaterialTheme.typography.bodyLarge,
            color = TextGray
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Time
        Text(
            text = currentTime,
            style = MaterialTheme.typography.displayLarge,
            color = TextWhite,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = currentDay,
            style = MaterialTheme.typography.labelMedium,
            color = TextMuted,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Genie Button
        GenieButton(onClick = { navController.navigate(Screen.Genie.route) })
        
        Spacer(modifier = Modifier.height(28.dp))
        
        // ─── Feature Grid ───
        
        // Row 1: Call Family | Memories
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            FeatureCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Call,
                label = "Call\nFamily",
                gradientColors = listOf(CallGreen, Color(0xFF145A48)),
                onClick = { navController.navigate(Screen.CallFamily.route) }
            )
            FeatureCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.PhotoLibrary,
                label = "Memories",
                gradientColors = listOf(MemoriesTeal, Color(0xFF134E3A)),
                onClick = { navController.navigate(Screen.Memories.route) }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Row 2: Medicine | Reports
        // Row 2: Medicine | Food & Cabs
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            FeatureCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Medication,
                label = "Medicine\nManager",
                gradientColors = listOf(Color(0xFF3B82F6), Color(0xFF1E3A8A)), // Blue
                onClick = { navController.navigate(Screen.Medicine.route) }
            )
            FeatureCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Fastfood,
                label = "Food &\nCabs",
                gradientColors = listOf(FoodOrange, Color(0xFFA85E1E)),
                onClick = { navController.navigate(Screen.FoodOrder.route) }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Row 3: Emergency Help | Safety Monitor
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            FeatureCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.LocalHospital,
                label = "Emergency\nHelp",
                gradientColors = listOf(HelpRed, Color(0xFFB91C1C)),
                iconColor = Color.White,
                labelColor = Color.White,
                onClick = { navController.navigate(Screen.Emergency.route) }
            )
            FeatureCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Shield,
                label = "Safety\nMonitor",
                gradientColors = listOf(TealAccent, Color(0xFF0E4434)),
                onClick = { navController.navigate(Screen.Inactivity.route) }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Row 4: Fall Detector (BLE Wearable)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            FeatureCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.MonitorHeart,
                label = "Fall\nDetector",
                gradientColors = listOf(Color(0xFFDC2626), Color(0xFF450A0A)),
                iconColor = Color.White,
                labelColor = Color.White,
                onClick = { navController.navigate(Screen.BlePairing.route) }
            )
            // Empty placeholder to keep grid balanced
            Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun GenieButton(onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "genie")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size((120 * scale).dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            PurpleAccent.copy(alpha = glowAlpha),
                            PurpleGlow.copy(alpha = glowAlpha * 0.5f),
                            BlueAccent.copy(alpha = glowAlpha * 0.3f),
                            Color.Transparent
                        )
                    )
                )
                .clickable(onClick = onClick)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(PurpleAccent, BlueAccent)
                        )
                    )
                    .shadow(8.dp, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Genie",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "GENIE",
            style = MaterialTheme.typography.labelLarge,
            color = TextWhite,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )

        Text(
            text = "Tap to ask Genie anything",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
        )
    }
}

@Composable
fun FeatureCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    gradientColors: List<Color>,
    iconColor: Color = Color.White,
    labelColor: Color = Color.White,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(110.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(gradientColors)
                )
                .padding(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier
                    .size(32.dp)
                    .align(Alignment.TopStart)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = labelColor,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.BottomStart)
            )
        }
    }
}
