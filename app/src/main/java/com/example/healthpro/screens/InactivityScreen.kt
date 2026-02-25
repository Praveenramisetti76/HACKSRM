package com.example.healthpro.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.healthpro.safety.SafetyTimelineEvent
import com.example.healthpro.safety.SafetyViewModel
import com.example.healthpro.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

/**
 * Safety Dashboard Screen — Full inactivity monitoring UI.
 *
 * Features:
 *  - Status card (Active / Monitoring / Inactive)
 *  - Last activity timestamp
 *  - Inactivity threshold display
 *  - Toggle enable/disable
 *  - Safety timeline (event log)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InactivityScreen(navController: NavController) {
    val viewModel: SafetyViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()

    // Auto-refresh every 30 seconds
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            viewModel.refreshState()
        }
    }

    val scrollState = rememberScrollState()

    // Pulse animation for status indicator
    val infiniteTransition = rememberInfiniteTransition(label = "status")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        Color(0xFF0A1628),
                        Color(0xFF0D1B2A),
                        Color(0xFF0F1F38)
                    )
                )
            )
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // ─── Header ───
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, "Back", tint = TextWhite)
            }
            Text(
                "Safety Monitor",
                style = MaterialTheme.typography.titleLarge,
                color = TextWhite,
                fontWeight = FontWeight.Bold
            )
            // Badge showing status
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (uiState.isServiceRunning) TealAccent.copy(alpha = 0.15f)
                else CardDark
            ) {
                Text(
                    text = if (uiState.isServiceRunning) "ACTIVE" else "OFF",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (uiState.isServiceRunning) TealAccent else TextMuted,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // ═══════════════════════════════════════════════════════════
        // STATUS CARD
        // ═══════════════════════════════════════════════════════════

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.linearGradient(
                            if (uiState.isServiceRunning)
                                listOf(Color(0xFF0E4434), Color(0xFF0A2D22))
                            else
                                listOf(CardDark, Color(0xFF152238))
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .padding(24.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Animated status dot
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(
                                    if (uiState.isServiceRunning)
                                        TealAccent.copy(alpha = pulseAlpha)
                                    else
                                        TextMuted.copy(alpha = 0.5f)
                                )
                        )
                        Text(
                            text = uiState.statusText.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (uiState.isServiceRunning) TealAccent else TextMuted,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // Shield icon
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(
                                if (uiState.isServiceRunning)
                                    Brush.radialGradient(listOf(TealAccent.copy(alpha = 0.3f), Color.Transparent))
                                else
                                    Brush.radialGradient(listOf(TextMuted.copy(alpha = 0.15f), Color.Transparent))
                            )
                            .align(Alignment.CenterHorizontally)
                    ) {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = "Safety Status",
                            tint = if (uiState.isServiceRunning) TealAccent else TextMuted,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // Last Activity
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Last Activity", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            Text(
                                uiState.lastActivityText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextWhite,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Threshold", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            Text(
                                uiState.thresholdDisplayText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = PurpleAccent,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ═══════════════════════════════════════════════════════════
        // MAIN TOGGLE
        // ═══════════════════════════════════════════════════════════

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardDark)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (uiState.isMonitoringEnabled) TealAccent.copy(alpha = 0.15f)
                                else Color(0xFF1A2A44)
                            )
                    ) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            tint = if (uiState.isMonitoringEnabled) TealAccent else TextMuted,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            "Passive Monitoring",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextWhite,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (uiState.isMonitoringEnabled) "Monitoring your safety" else "Tap to enable",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                }
                Switch(
                    checked = uiState.isMonitoringEnabled,
                    onCheckedChange = { viewModel.toggleMonitoring() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TealAccent,
                        checkedTrackColor = TealAccent.copy(alpha = 0.3f),
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = Color(0xFF1A2A44)
                    )
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // ═══════════════════════════════════════════════════════════
        // SETTINGS CARDS
        // ═══════════════════════════════════════════════════════════

        // Threshold Slider
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardDark)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Timer, null, tint = PurpleAccent, modifier = Modifier.size(22.dp))
                    Text(
                        "Inactivity Threshold",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextWhite,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("10s", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Text(
                        uiState.thresholdDisplayText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = PurpleAccent,
                        fontWeight = FontWeight.Bold
                    )
                    Text("120 min", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }

                Slider(
                    value = uiState.inactivityThresholdSeconds.toFloat(),
                    onValueChange = { viewModel.setThreshold(it.toInt()) },
                    valueRange = 10f..7200f,
                    colors = SliderDefaults.colors(
                        thumbColor = PurpleAccent,
                        activeTrackColor = PurpleAccent,
                        inactiveTrackColor = Color(0xFF1A2A44)
                    )
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Voice Confirmation Toggle
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardDark)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(BlueAccent.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = null,
                            tint = BlueAccent,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            "Voice Confirmation",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextWhite,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Genie asks \"Are you safe?\" before SOS",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                }
                Switch(
                    checked = uiState.isVoiceConfirmationEnabled,
                    onCheckedChange = { viewModel.toggleVoiceConfirmation() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BlueAccent,
                        checkedTrackColor = BlueAccent.copy(alpha = 0.3f),
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = Color(0xFF1A2A44)
                    )
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // ═══════════════════════════════════════════════════════════
        // HOW IT WORKS
        // ═══════════════════════════════════════════════════════════

        Text(
            "How It Works",
            style = MaterialTheme.typography.titleMedium,
            color = TextWhite,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(12.dp))

        HowItWorksStep(
            step = "1",
            icon = Icons.Default.Sensors,
            title = "Detect Inactivity",
            description = "Monitors screen touch and motion sensors",
            color = TealAccent
        )
        HowItWorksStep(
            step = "2",
            icon = Icons.Default.RecordVoiceOver,
            title = "Voice Confirmation",
            description = "Genie asks: \"Are you safe?\"",
            color = BlueAccent
        )
        HowItWorksStep(
            step = "3",
            icon = Icons.Default.NotificationsActive,
            title = "Auto SOS",
            description = "If no response, sends emergency alerts",
            color = SosRed
        )

        Spacer(Modifier.height(24.dp))

        // ═══════════════════════════════════════════════════════════
        // SAFETY TIMELINE
        // ═══════════════════════════════════════════════════════════

        if (uiState.timelineEvents.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Safety Timeline",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = { viewModel.clearTimeline() }) {
                    Text("Clear", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(Modifier.height(8.dp))

            uiState.timelineEvents.take(10).forEach { event ->
                TimelineEventCard(event)
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

// ═══════════════════════════════════════════════════════════════════
// SUBCOMPONENTS
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun HowItWorksStep(
    step: String,
    icon: ImageVector,
    title: String,
    description: String,
    color: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark.copy(alpha = 0.7f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f))
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = TextWhite, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
            Surface(
                shape = CircleShape,
                color = color.copy(alpha = 0.1f),
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(step, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun TimelineEventCard(event: SafetyTimelineEvent) {
    val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

    val (icon, color) = when (event.type) {
        "inactivity_detected" -> Icons.Default.Warning to Color(0xFFFBBF24)
        "voice_check_ok" -> Icons.Default.CheckCircle to TealAccent
        "voice_check_help" -> Icons.Default.Error to SosRed
        "voice_check_timeout" -> Icons.Default.TimerOff to Color(0xFFFF6B35)
        "sos_triggered" -> Icons.Default.NotificationsActive to SosRed
        "monitoring_started" -> Icons.Default.PlayArrow to TealAccent
        "monitoring_stopped" -> Icons.Default.Stop to TextMuted
        else -> Icons.Default.Info to BlueAccent
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    event.description,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextWhite
                )
                Text(
                    dateFormat.format(Date(event.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
        }
    }
}
