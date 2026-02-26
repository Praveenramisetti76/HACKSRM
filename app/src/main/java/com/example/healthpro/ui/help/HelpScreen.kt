package com.example.healthpro.ui.help

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.healthpro.ui.theme.*

// ════════════════════════════════════════════════════════════════════
//  HELP SCREEN — MAIN ENTRY POINT
// ════════════════════════════════════════════════════════════════════

@Composable
fun HelpScreenNew(navController: NavController, autoTrigger: Boolean = false) {
    val viewModel: HelpViewModel = viewModel()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.init(context)
    }

    // ── Auto-trigger SOS from voice detection ──
    LaunchedEffect(autoTrigger) {
        if (autoTrigger && viewModel.phase == SOSPhase.IDLE) {
            // Small delay to allow viewModel.init() to complete
            kotlinx.coroutines.delay(500)
            viewModel.triggerSOS()
        }
    }

    // ── Permission gate ──
    val requiredPermissions = arrayOf(
        Manifest.permission.SEND_SMS,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.ANSWER_PHONE_CALLS
    )

    var allPermissionsGranted by remember {
        mutableStateOf(
            requiredPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        allPermissionsGranted = results.values.all { it }
    }

    // Request permissions on first load if not granted
    LaunchedEffect(Unit) {
        if (!allPermissionsGranted) {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    when {
        !allPermissionsGranted -> {
            PermissionScreen(
                onRequestPermissions = { permissionLauncher.launch(requiredPermissions) }
            )
        }
        viewModel.showContactPicker -> {
            EmergencyContactPickerScreen(
                viewModel = viewModel,
                onDismiss = { viewModel.showContactPicker = false }
            )
        }
        viewModel.phase != SOSPhase.IDLE -> {
            EmergencyActiveScreen(
                viewModel = viewModel
            )
        }
        else -> {
            HelpMainScreen(
                navController = navController,
                viewModel = viewModel
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  PERMISSION SCREEN — Explain + request required permissions
// ════════════════════════════════════════════════════════════════════

@Composable
fun PermissionScreen(onRequestPermissions: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    listOf(SosBackground, Color(0xFF1A0404), DarkNavy)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(SosRed.copy(alpha = 0.2f))
            ) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    tint = SosRed,
                    modifier = Modifier.size(50.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Permissions Required",
                style = MaterialTheme.typography.headlineMedium,
                color = TextWhite,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "SAHAY Emergency needs permission to:\n\n" +
                        "📍 Access your location (for live location sharing)\n" +
                        "📨 Send SMS (auto-send emergency alerts)\n" +
                        "📞 Make calls (auto-call hospital + missed call alerts)",
                style = MaterialTheme.typography.bodyLarge,
                color = TextMuted,
                textAlign = TextAlign.Center,
                lineHeight = 26.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ── Permission item cards ──
            PermissionExplainCard(
                icon = Icons.Default.MyLocation,
                label = "Location",
                description = "Share live GPS location in emergency alerts",
                color = Color(0xFF4ADE80)
            )
            Spacer(modifier = Modifier.height(8.dp))
            PermissionExplainCard(
                icon = Icons.Default.Sms,
                label = "SMS",
                description = "Auto-send emergency SMS without pressing send",
                color = Color(0xFF60A5FA)
            )
            Spacer(modifier = Modifier.height(8.dp))
            PermissionExplainCard(
                icon = Icons.Default.Call,
                label = "Phone",
                description = "Auto-call nearest hospital immediately",
                color = Color(0xFFFBBF24)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onRequestPermissions,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SosRed)
            ) {
                Icon(
                    Icons.Default.Shield,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "GRANT PERMISSIONS",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}

@Composable
fun PermissionExplainCard(
    icon: ImageVector,
    label: String,
    description: String,
    color: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f))
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            }
            Column {
                Text(label, style = MaterialTheme.typography.labelLarge, color = TextWhite, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  HELP MAIN SCREEN — 2 MASSIVE BUTTONS (SOS + HOSPITAL)
// ════════════════════════════════════════════════════════════════════

@Composable
fun HelpMainScreen(
    navController: NavController,
    viewModel: HelpViewModel
) {
    val scrollState = rememberScrollState()

    // Pulsing animation for SOS button
    val infiniteTransition = rememberInfiniteTransition(label = "sos_pulse")
    val sosPulse by infiniteTransition.animateFloat(
        initialValue = 0.94f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse
        ), label = "sosPulse"
    )
    val ringGlow by infiniteTransition.animateFloat(
        initialValue = 0.15f, targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse
        ), label = "ringGlow"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        SosBackground,
                        Color(0xFF1A0404),
                        DarkNavy
                    )
                )
            )
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Back button ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, "Back", tint = SosRed.copy(alpha = 0.8f))
            }
            Text("Back", style = MaterialTheme.typography.bodyLarge, color = SosRed.copy(alpha = 0.8f))
        }

        Spacer(modifier = Modifier.height(4.dp))

        // ── Emergency badge ──
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = SosRed.copy(alpha = 0.18f)
        ) {
            Row(
                Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(SosRed)
                )
                Text(
                    "EMERGENCY SYSTEM",
                    style = MaterialTheme.typography.labelMedium,
                    color = SosRed,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Emergency Help",
            style = MaterialTheme.typography.headlineLarge,
            color = TextWhite,
            fontWeight = FontWeight.Bold,
            fontSize = 34.sp
        )
        Text(
            text = "One tap to send alerts instantly",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ════════════════════════════════════
        //  🚨 BUTTON 1 — SOS
        // ════════════════════════════════════

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(220.dp)
                .scale(sosPulse)
        ) {
            // Outer glow ring
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            listOf(
                                Color.Transparent,
                                SosRed.copy(alpha = ringGlow * 0.4f),
                                SosRed.copy(alpha = ringGlow * 0.6f)
                            )
                        )
                    )
            )
            // Middle ring
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .clip(CircleShape)
                    .background(SosRed.copy(alpha = 0.2f))
            )
            // Inner ring
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .clip(CircleShape)
                    .background(SosRed.copy(alpha = 0.35f))
            )
            // Core button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(130.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            listOf(
                                Color(0xFFEF4444),
                                SosRed,
                                Color(0xFFB91C1C)
                            )
                        )
                    )
                    .clickable { viewModel.triggerSOS() }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "SOS",
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 6.sp,
                        fontSize = 42.sp
                    )
                    Text(
                        "TAP FOR HELP",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f),
                        letterSpacing = 2.sp,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Auto-sends SMS + WhatsApp\nwith live location to all contacts",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            textAlign = TextAlign.Center,
            lineHeight = 16.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ════════════════════════════════════
        //  🏥 BUTTON 2 — NEAREST HOSPITAL
        // ════════════════════════════════════

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .clickable { viewModel.triggerHospitalSearch() },
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.linearGradient(
                            listOf(
                                Color(0xFF1E3A5F),
                                Color(0xFF0F2847),
                                Color(0xFF0C1F3A)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            listOf(
                                Color(0xFF3B82F6).copy(alpha = 0.4f),
                                Color(0xFF1E40AF).copy(alpha = 0.2f)
                            )
                        ),
                        shape = RoundedCornerShape(22.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    // Hospital icon
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.radialGradient(
                                    listOf(
                                        Color(0xFF3B82F6).copy(alpha = 0.3f),
                                        Color(0xFF1E40AF).copy(alpha = 0.1f)
                                    )
                                )
                            )
                    ) {
                        Icon(
                            Icons.Default.LocalHospital,
                            contentDescription = null,
                            tint = Color(0xFF60A5FA),
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "NEAREST HOSPITAL",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Find → Alert → Auto-Call",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF60A5FA).copy(alpha = 0.8f),
                            letterSpacing = 1.sp
                        )
                    }

                    Icon(
                        Icons.Default.ChevronRight,
                        null,
                        tint = Color(0xFF60A5FA).copy(alpha = 0.6f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ════════════════════════════════════
        //  EMERGENCY CONTACTS SECTION
        // ════════════════════════════════════

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Emergency Contacts",
                style = MaterialTheme.typography.titleMedium,
                color = TextWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            TextButton(
                onClick = {
                    viewModel.showContactPicker = true
                    viewModel.loadDeviceContacts()
                }
            ) {
                Icon(
                    Icons.Default.PersonAdd,
                    contentDescription = null,
                    tint = SosRed,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "Add",
                    color = SosRed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (viewModel.emergencyContacts.isEmpty()) {
            // ── Empty state warning ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = FoodOrange,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "No Emergency Contacts",
                        style = MaterialTheme.typography.titleMedium,
                        color = FoodOrange,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Add contacts to receive your SOS alerts.\nPress '+Add' above to get started.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }
        } else {
            viewModel.emergencyContacts.forEach { contact ->
                EmergencyContactRow(
                    contact = contact,
                    onRemove = { viewModel.removeEmergencyContact(contact) }
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
        }

        // ── Error message display ──
        viewModel.errorMessage?.let { error ->
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SosRed.copy(alpha = 0.12f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        null,
                        tint = SosRed,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SosRed,
                        lineHeight = 20.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Quick emergency numbers ──
        Text(
            text = "Quick Emergency Numbers",
            style = MaterialTheme.typography.labelLarge,
            color = TextMuted.copy(alpha = 0.7f),
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickCallCard(
                modifier = Modifier.weight(1f),
                number = "112",
                label = "Emergency",
                icon = Icons.Default.LocalPolice,
                color = SosRed
            )
            QuickCallCard(
                modifier = Modifier.weight(1f),
                number = "108",
                label = "Ambulance",
                icon = Icons.Default.LocalHospital,
                color = Color(0xFFE85D4A)
            )
            QuickCallCard(
                modifier = Modifier.weight(1f),
                number = "100",
                label = "Police",
                icon = Icons.Default.Shield,
                color = Color(0xFF3B82F6)
            )
        }

        Spacer(modifier = Modifier.height(28.dp))
    }
}

// ════════════════════════════════════════════════════════════════════
//  EMERGENCY CONTACT ROW
// ════════════════════════════════════════════════════════════════════

@Composable
fun EmergencyContactRow(
    contact: com.example.healthpro.data.contacts.SavedContact,
    onRemove: () -> Unit
) {
    val avatarColors = listOf(SosRed, FoodOrange, Color(0xFFE85D4A), BlueAccent, PurpleAccent)
    val colorIndex = kotlin.math.abs(contact.name.hashCode()) % avatarColors.size

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(avatarColors[colorIndex].copy(alpha = 0.2f))
            ) {
                Text(
                    text = contact.name.firstOrNull()?.uppercase()?.toString() ?: "#",
                    style = MaterialTheme.typography.titleMedium,
                    color = avatarColors[colorIndex],
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 16.sp
                )
                Text(
                    text = contact.phoneNumber,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.Sms, null, tint = Color(0xFF60A5FA).copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                Icon(Icons.Default.Call, null, tint = CallGreen.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
            }

            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = TextMuted.copy(alpha = 0.4f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  QUICK CALL CARD — Small emergency number cards
// ════════════════════════════════════════════════════════════════════

@Composable
fun QuickCallCard(
    modifier: Modifier = Modifier,
    number: String,
    label: String,
    icon: ImageVector,
    color: Color
) {
    val context = LocalContext.current

    Card(
        modifier = modifier
            .height(82.dp)
            .clickable {
                try {
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_CALL,
                        android.net.Uri.parse("tel:$number")
                    ).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    try {
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_DIAL,
                            android.net.Uri.parse("tel:$number")
                        ).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
                        context.startActivity(intent)
                    } catch (_: Exception) { }
                }
            },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = number,
                style = MaterialTheme.typography.titleMedium,
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                fontSize = 9.sp
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  EMERGENCY ACTIVE SCREEN — Full-screen red feedback
// ════════════════════════════════════════════════════════════════════

@Composable
fun EmergencyActiveScreen(viewModel: HelpViewModel) {
    val infiniteTransition = rememberInfiniteTransition(label = "alert")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.93f, targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse
        ), label = "alertPulse"
    )
    val flash by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            tween(450), RepeatMode.Reverse
        ), label = "flash"
    )

    // Determine if this is the hospital flow based on phase
    val isHospitalFlow = viewModel.phase in listOf(
        SOSPhase.HOSPITAL_FETCHING_LOCATION,
        SOSPhase.HOSPITAL_SEARCHING,
        SOSPhase.HOSPITAL_SENDING_ALERT,
        SOSPhase.HOSPITAL_CALLING,
        SOSPhase.HOSPITAL_COMPLETED
    )

    val isCompleted = viewModel.phase in listOf(
        SOSPhase.SOS_COMPLETED,
        SOSPhase.HOSPITAL_COMPLETED
    )
    val isError = viewModel.phase == SOSPhase.ERROR

    // Background gradient — red for SOS, blue-dark for hospital
    val bgGradient = if (isHospitalFlow) {
        Brush.verticalGradient(
            listOf(Color(0xFF0C1F3A), Color(0xFF0A1628), Color(0xFF060E1A))
        )
    } else {
        Brush.verticalGradient(
            listOf(Color(0xFF5C0A0A), Color(0xFF3B0808), Color(0xFF1A0404))
        )
    }

    val accentColor = if (isHospitalFlow) Color(0xFF3B82F6) else SosRed

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = bgGradient),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // ── Status badge ──
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = accentColor.copy(alpha = 0.2f)
            ) {
                Row(
                    Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isCompleted -> Color(0xFF4ADE80)
                                    isError -> Color(0xFFEF4444)
                                    else -> Color(0xFFFBBF24)
                                }
                            )
                    )
                    Text(
                        text = when (viewModel.phase) {
                            SOSPhase.SOS_FETCHING_LOCATION -> "LOCATING..."
                            SOSPhase.SOS_SENDING_SMS -> "SENDING SMS"
                            SOSPhase.SOS_SENDING_WHATSAPP -> "SENDING WHATSAPP"
                            SOSPhase.SOS_COMPLETED -> "ALERT SENT ✓"
                            SOSPhase.HOSPITAL_FETCHING_LOCATION -> "LOCATING..."
                            SOSPhase.HOSPITAL_SEARCHING -> "FINDING HOSPITAL"
                            SOSPhase.HOSPITAL_SENDING_ALERT -> "ALERTING HOSPITAL"
                            SOSPhase.HOSPITAL_CALLING -> "CALLING HOSPITAL"
                            SOSPhase.HOSPITAL_COMPLETED -> "HOSPITAL ALERTED ✓"
                            SOSPhase.ERROR -> "ERROR"
                            else -> "PROCESSING"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // ── Animated alert icon ──
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(150.dp)
                    .scale(if (!isCompleted && !isError) pulse else 1f)
            ) {
                if (!isCompleted && !isError) {
                    Box(
                        Modifier
                            .size(150.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = flash * 0.2f))
                    )
                    Box(
                        Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = flash * 0.35f))
                    )
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isCompleted -> Color(0xFF166534)
                                isError -> Color(0xFF7F1D1D)
                                else -> accentColor
                            }
                        )
                ) {
                    Icon(
                        imageVector = when {
                            isCompleted -> Icons.Default.Check
                            isError -> Icons.Default.ErrorOutline
                            isHospitalFlow -> Icons.Default.LocalHospital
                            else -> Icons.Default.NotificationsActive
                        },
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── Title ──
            Text(
                text = when {
                    isCompleted && isHospitalFlow -> "🏥 Hospital Alerted"
                    isCompleted -> "🚨 Emergency Alert Sent"
                    isError -> "⚠️ Alert Error"
                    isHospitalFlow -> "🏥 Finding Hospital..."
                    else -> "🚨 Sending Emergency Alert..."
                },
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                fontSize = 26.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = viewModel.statusMessage,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ── Location card ──
            if (viewModel.mapsLink.isNotBlank()) {
                StatusCard(
                    icon = Icons.Default.LocationOn,
                    iconColor = Color(0xFF4ADE80),
                    title = "Live Location Shared",
                    subtitle = viewModel.currentLocation?.let { loc ->
                        "Lat: ${String.format("%.5f", loc.latitude)}, " +
                                "Lng: ${String.format("%.5f", loc.longitude)}"
                    } ?: "Fetching..."
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // ── SMS status card ──
            if (viewModel.smsSentCount > 0) {
                StatusCard(
                    icon = Icons.Default.Sms,
                    iconColor = Color(0xFF60A5FA),
                    title = "SMS Sent",
                    subtitle = "${viewModel.smsSentCount} contacts alerted via SMS"
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // ── WhatsApp status card ──
            if (viewModel.whatsAppSent) {
                StatusCard(
                    icon = Icons.Default.Message,
                    iconColor = Color(0xFF25D366),
                    title = "WhatsApp Sent",
                    subtitle = "Emergency message launched via WhatsApp"
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // ── Hospital info card ──
            viewModel.foundHospital?.let { hospital ->
                StatusCard(
                    icon = Icons.Default.LocalHospital,
                    iconColor = Color(0xFF60A5FA),
                    title = hospital.name,
                    subtitle = buildString {
                        if (hospital.address.isNotBlank()) append(hospital.address)
                        if (hospital.phoneNumber.isNotBlank()) {
                            if (isNotBlank()) append("\n")
                            append("📞 ${hospital.phoneNumber}")
                        }
                        if (hospital.distanceMeters > 0) {
                            if (isNotBlank()) append("\n")
                            val km = hospital.distanceMeters / 1000.0
                            append("📍 ${String.format("%.1f", km)} km away")
                        }
                    }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // ── Contacts notified list ──
            if (isCompleted && !isHospitalFlow && viewModel.emergencyContacts.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.08f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            "Contacts Notified:",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        viewModel.emergencyContacts.forEach { contact ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    null,
                                    tint = Color(0xFF4ADE80),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    "${contact.name}  •  ${contact.phoneNumber}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // ── Bottom action button ──
            if (isCompleted || isError) {
                Button(
                    onClick = { viewModel.resetToIdle() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCompleted) Color(0xFF166534) else CardDark
                    )
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                if (isCompleted) "I AM SAFE" else "TRY AGAIN",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                            Text(
                                "Return to help screen",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f))
                        ) {
                            Icon(
                                if (isCompleted) Icons.Default.Check else Icons.Default.Refresh,
                                null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            } else {
                // ── In-progress loading ──
                CircularProgressIndicator(
                    color = accentColor,
                    modifier = Modifier.size(52.dp),
                    strokeWidth = 5.dp
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Please wait...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  STATUS CARD — Reusable info card for emergency screen
// ════════════════════════════════════════════════════════════════════

@Composable
fun StatusCard(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.08f)
        )
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
                    .background(iconColor.copy(alpha = 0.15f))
            ) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f),
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  EMERGENCY CONTACT PICKER
// ════════════════════════════════════════════════════════════════════

@Composable
fun EmergencyContactPickerScreen(
    viewModel: HelpViewModel,
    onDismiss: () -> Unit
) {
    val filteredContacts = viewModel.getFilteredDeviceContacts()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavy)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // ── Header ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, "Close", tint = TextMuted)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Emergency Contacts",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "These contacts will receive SOS alerts",
                    style = MaterialTheme.typography.labelSmall,
                    color = SosRed
                )
            }

            if (viewModel.selectedContacts.isNotEmpty()) {
                Badge(containerColor = SosRed) {
                    Text(
                        text = "${viewModel.selectedContacts.size}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Search bar ──
        OutlinedTextField(
            value = viewModel.searchQuery,
            onValueChange = { viewModel.searchQuery = it },
            placeholder = { Text("Search contacts...", color = TextMuted) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = TextMuted) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SosRed,
                unfocusedBorderColor = CardDark,
                focusedContainerColor = CardDark,
                unfocusedContainerColor = CardDark,
                cursorColor = SosRed,
                focusedTextColor = TextWhite,
                unfocusedTextColor = TextWhite
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (viewModel.isLoadingContacts) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(40.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = SosRed)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredContacts) { contact ->
                    val isSelected = contact.id in viewModel.selectedContacts
                    val avatarColors = listOf(SosRed, FoodOrange, BlueAccent, PurpleAccent, CallGreen)
                    val colorIndex = kotlin.math.abs(contact.name.hashCode()) % avatarColors.size

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleContactSelection(contact.id) },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) SosRed.copy(alpha = 0.1f) else Color.Transparent
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(avatarColors[colorIndex].copy(alpha = 0.2f))
                            ) {
                                if (contact.photoUri != null) {
                                    AsyncImage(
                                        model = contact.photoUri,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text(
                                        text = contact.name.firstOrNull()?.uppercase()?.toString() ?: "#",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = avatarColors[colorIndex],
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = contact.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextWhite,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 18.sp
                                )
                                Text(
                                    text = contact.phoneNumber,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextMuted
                                )
                            }

                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { viewModel.toggleContactSelection(contact.id) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = SosRed,
                                    uncheckedColor = TextMuted,
                                    checkmarkColor = Color.White
                                )
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Save button ──
        Button(
            onClick = { viewModel.saveSelectedAsEmergencyContacts() },
            enabled = viewModel.selectedContacts.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SosRed,
                disabledContainerColor = CardDark
            )
        ) {
            Icon(
                Icons.Default.Shield,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (viewModel.selectedContacts.isNotEmpty()) Color.White else TextMuted
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (viewModel.selectedContacts.isNotEmpty())
                    "SAVE ${viewModel.selectedContacts.size} EMERGENCY CONTACTS"
                else "SELECT CONTACTS",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (viewModel.selectedContacts.isNotEmpty()) Color.White else TextMuted,
                letterSpacing = 1.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
