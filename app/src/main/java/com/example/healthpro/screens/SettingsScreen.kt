package com.example.healthpro.screens

import android.app.TimePickerDialog
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.healthpro.safety.SafetyViewModel
import com.example.healthpro.ui.theme.*

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val safetyViewModel: SafetyViewModel = viewModel()
    val safetyState by safetyViewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavy)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineLarge, color = TextWhite, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))

        // ─── Safety Monitoring Section ───
        Text(
            "SAFETY",
            style = MaterialTheme.typography.labelMedium,
            color = TealAccent,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Enable Passive Monitoring
        SettingsToggleItem(
            icon = Icons.Default.Security,
            title = "Passive Monitoring",
            subtitle = if (safetyState.isMonitoringEnabled) "Active — Monitoring safety" else "Disabled",
            isChecked = safetyState.isMonitoringEnabled,
            accentColor = TealAccent,
            onToggle = { safetyViewModel.toggleMonitoring() }
        )

        // Voice Confirmation
        SettingsToggleItem(
            icon = Icons.Default.Mic,
            title = "Voice Confirmation",
            subtitle = "Ask \"Are you safe?\" before SOS",
            isChecked = safetyState.isVoiceConfirmationEnabled,
            accentColor = BlueAccent,
            onToggle = { safetyViewModel.toggleVoiceConfirmation() }
        )

        // Inactivity Duration
        SettingsItem(
            Icons.Default.Timer,
            "Inactivity Duration",
            safetyState.thresholdDisplayText
        )

        Spacer(Modifier.height(20.dp))

        // ═══════════════════════════════════════════════════════════
        // 💤 SLEEP HOURS SECTION
        // ═══════════════════════════════════════════════════════════

        Text(
            "SLEEP HOURS",
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF9B8FFF),  // Soft purple for sleep theme
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Sleep Mode Toggle
        SettingsToggleItem(
            icon = Icons.Default.Bedtime,
            title = "Disable Monitoring During Sleep",
            subtitle = if (safetyState.isSleepModeEnabled) {
                if (safetyState.isSleepActive) "💤 Sleep Mode Active — monitoring paused"
                else "Enabled — ${safetyState.sleepStartTime} to ${safetyState.sleepEndTime}"
            } else {
                "Off — Monitoring works 24/7"
            },
            isChecked = safetyState.isSleepModeEnabled,
            accentColor = Color(0xFF9B8FFF),
            onToggle = { safetyViewModel.toggleSleepMode() }
        )

        // Time Pickers (only shown when sleep mode is enabled)
        if (safetyState.isSleepModeEnabled) {
            // Start Time
            SleepTimePickerItem(
                icon = Icons.Default.AccessTime,
                title = "Sleep Start Time",
                time = safetyState.sleepStartTime,
                accentColor = Color(0xFF9B8FFF),
                onTimeSelected = { safetyViewModel.setSleepStartTime(it) }
            )

            // End Time
            SleepTimePickerItem(
                icon = Icons.Default.WbSunny,
                title = "Wake Up Time",
                time = safetyState.sleepEndTime,
                accentColor = Color(0xFFFFA726),  // Warm orange for wake-up
                onTimeSelected = { safetyViewModel.setSleepEndTime(it) }
            )

            // Active indicator
            if (safetyState.isSleepActive) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A3A))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("💤", fontSize = 20.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Sleep Mode Active — Monitoring Paused",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF9B8FFF),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ─── General Section ───
        Text(
            "GENERAL",
            style = MaterialTheme.typography.labelMedium,
            color = TextMuted,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        SettingsItem(Icons.Default.Person, "Profile", "Manage your profile")
        SettingsItem(Icons.Default.Notifications, "Notifications", "Alert preferences")
        SettingsItem(Icons.Default.ContactPhone, "Emergency Contacts", "Manage contacts")
        SettingsItem(Icons.Default.VolumeUp, "Accessibility", "Font size, voice settings")
        SettingsItem(Icons.Default.Info, "About SAHAY", "Version 1.0")

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun SettingsItem(icon: ImageVector, title: String, subtitle: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp).clip(CircleShape).background(TealAccent.copy(alpha = 0.1f))) {
                Icon(icon, null, tint = TealAccent, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = TextWhite, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
            Icon(Icons.Default.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isChecked: Boolean,
    accentColor: Color = TealAccent,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isChecked) accentColor.copy(alpha = 0.15f)
                        else Color(0xFF1A2A44)
                    )
            ) {
                Icon(icon, null, tint = if (isChecked) accentColor else TextMuted, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = TextWhite, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
            Switch(
                checked = isChecked,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = accentColor,
                    checkedTrackColor = accentColor.copy(alpha = 0.3f),
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = Color(0xFF1A2A44)
                )
            )
        }
    }
}

@Composable
fun SleepTimePickerItem(
    icon: ImageVector,
    title: String,
    time: String,
    accentColor: Color = TealAccent,
    onTimeSelected: (String) -> Unit
) {
    val context = LocalContext.current

    // Parse current time for dialog initial values
    val parts = time.split(":")
    val initialHour = parts.getOrNull(0)?.toIntOrNull() ?: 22
    val initialMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable {
                TimePickerDialog(
                    context,
                    { _, hourOfDay, minute ->
                        val formatted = String.format("%02d:%02d", hourOfDay, minute)
                        onTimeSelected(formatted)
                    },
                    initialHour,
                    initialMinute,
                    true  // 24-hour format
                ).show()
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f))
            ) {
                Icon(icon, null, tint = accentColor, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    color = TextWhite,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    time,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    fontSize = 14.sp
                )
            }
            Text(
                "Change",
                style = MaterialTheme.typography.labelMedium,
                color = accentColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
