package com.example.healthpro.screens

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.healthpro.ble.BleConnectionState
import com.example.healthpro.ble.BleStateHolder
import com.example.healthpro.ble.FallDetectionService
import com.example.healthpro.ui.theme.DarkNavy
import com.example.healthpro.ui.theme.TealAccent
import com.example.healthpro.ui.theme.TextGray
import com.example.healthpro.ui.theme.TextMuted
import com.example.healthpro.ui.theme.TextWhite
import java.text.SimpleDateFormat
import java.util.*

/**
 * BleDevicePairingScreen — Scan, connect, and monitor the Sahay-Nano wearable.
 *
 * Responsibilities:
 *  - Request runtime BLE permissions (BLUETOOTH_SCAN, BLUETOOTH_CONNECT)
 *  - Start / Stop FallDetectionService
 *  - Display live connection state from BleStateHolder
 *  - Show last heartbeat time and pairing tips
 */
@Composable
fun BleDevicePairingScreen(navController: NavController) {
    val context = LocalContext.current

    // Observe live BLE state from BleStateHolder
    val connectionState by BleStateHolder.connectionState.collectAsState()
    val deviceName      by BleStateHolder.deviceName.collectAsState()
    val lastHeartbeat   by BleStateHolder.lastHeartbeatTime.collectAsState()
    val lastMsg         by BleStateHolder.lastRawMessage.collectAsState()

    // ─── Permission launcher ─────────────────────────────────────────
    var permissionsGranted by remember { mutableStateOf(false) }
    var permissionDenied   by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = results.values.all { it }
        permissionDenied   = !permissionsGranted
    }

    // ─── Helpers ─────────────────────────────────────────────────────
    fun requestPermissionsAndStart() {
        val needed = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            arrayOf(
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }
        // Check if already granted
        val allGranted = needed.all {
            context.checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            permissionsGranted = true
            FallDetectionService.start(context)
        } else {
            permissionLauncher.launch(needed)
        }
    }

    LaunchedEffect(permissionsGranted) {
        if (permissionsGranted) {
            FallDetectionService.start(context)
        }
    }

    fun formatHeartbeat(ts: Long?): String {
        if (ts == null) return "Never"
        val diff = System.currentTimeMillis() - ts
        return when {
            diff < 5_000  -> "Just now"
            diff < 60_000 -> "${diff / 1000}s ago"
            else          -> SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ts))
        }
    }

    // ─── Pulse animation for scanning state ─────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "scan_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    val dotColor = when (connectionState) {
        BleConnectionState.CONNECTED    -> Color(0xFF22C55E)
        BleConnectionState.SCANNING,
        BleConnectionState.CONNECTING  -> Color(0xFFF59E0B)
        else                           -> Color(0xFFEF4444)
    }

    val statusLabel = when (connectionState) {
        BleConnectionState.CONNECTED   -> "Connected"
        BleConnectionState.SCANNING    -> "Scanning..."
        BleConnectionState.CONNECTING  -> "Connecting..."
        BleConnectionState.DISCONNECTED -> "Disconnected"
    }

    // ═══════════════════════════════════════════════════════════════
    // UI
    // ═══════════════════════════════════════════════════════════════

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavy)
            .padding(20.dp)
    ) {

        // ─── Top Bar ────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextWhite)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Fall Detector",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextWhite
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ─── Status Card ────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2235))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Big pulsing status dot
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(
                            dotColor.copy(
                                alpha = if (connectionState == BleConnectionState.SCANNING ||
                                    connectionState == BleConnectionState.CONNECTING
                                ) pulseAlpha * 0.25f else 0.15f
                            )
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                if (connectionState == BleConnectionState.SCANNING ||
                                    connectionState == BleConnectionState.CONNECTING
                                ) dotColor.copy(alpha = pulseAlpha)
                                else dotColor
                            )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = statusLabel,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = dotColor
                )

                if (deviceName != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = deviceName ?: "",
                        fontSize = 14.sp,
                        color = TextGray
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Last heartbeat
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Favorite,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Last heartbeat: ${formatHeartbeat(lastHeartbeat)}",
                        fontSize = 13.sp,
                        color = TextMuted
                    )
                }

                // Last BLE message (debug)
                if (lastMsg != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "BLE MSG: $lastMsg",
                        fontSize = 11.sp,
                        color = TealAccent.copy(alpha = 0.7f),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ─── Action Buttons ─────────────────────────────────────────
        if (connectionState == BleConnectionState.DISCONNECTED) {
            Button(
                onClick = { requestPermissionsAndStart() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TealAccent)
            ) {
                Icon(Icons.Default.Bluetooth, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Scan & Connect", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        } else {
            OutlinedButton(
                onClick = { FallDetectionService.stop(context) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFEF4444)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
            ) {
                Icon(Icons.Default.BluetoothDisabled, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Disconnect", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // ─── Info / Tips Card ────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111827))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "How it works",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = TextWhite
                )
                Spacer(Modifier.height(12.dp))
                TipRow("🔵", "Power on Sahay-Nano — it auto-advertises via BLE")
                TipRow("🔗", "Tap Scan & Connect — app finds the device automatically")
                TipRow("💓", "Heartbeat pings every 5s confirm the link is live")
                TipRow("⚡", "IMPACT detected → short buzz, no alert")
                TipRow("🚨", "FALL DETECTED → 45s countdown → SOS if no response")

                if (permissionDenied) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "⚠️ BLE permissions denied. Please enable them in Settings → Apps → SAHAY → Permissions",
                        fontSize = 12.sp,
                        color = Color(0xFFFF5252),
                        textAlign = TextAlign.Start
                    )
                }
            }
        }
    }
}

@Composable
private fun TipRow(emoji: String, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 16.sp)
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 13.sp, color = TextMuted)
    }
}
