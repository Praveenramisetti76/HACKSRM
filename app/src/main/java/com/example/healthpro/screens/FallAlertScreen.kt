package com.example.healthpro.screens

import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.healthpro.ble.BleStateHolder
import com.example.healthpro.data.contacts.ContactsRepository
import com.example.healthpro.database.FallEventEntity
import com.example.healthpro.database.SahayDatabase
import com.example.healthpro.location.LocationHelper
import com.example.healthpro.sos.SOSManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val COUNTDOWN_SECONDS = 45

/**
 * FallAlertScreen — Full-screen emergency countdown triggered by FALL_DETECTED BLE message.
 *
 * Behaviour:
 *  - 45-second countdown ring (animated)
 *  - Vibrates every 5 seconds
 *  - "I'm Okay" → log false alarm, dismiss
 *  - Countdown expires → send SMS + WhatsApp + call, log confirmed fall
 */
@Composable
fun FallAlertScreen(navController: NavController) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    // ─── Timer State ────────────────────────────────────────
    var secondsLeft by remember { mutableIntStateOf(COUNTDOWN_SECONDS) }
    var alertHandled by remember { mutableStateOf(false) }
    val alertStartTime = remember { System.currentTimeMillis() }

    // ─── Ring animation ─────────────────────────────────────
    val progress = secondsLeft / COUNTDOWN_SECONDS.toFloat()
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    // ─── Vibrator helper ────────────────────────────────────
    fun vibrateAlert() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(VibratorManager::class.java)
                vm?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(400L, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Vibrator::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v?.vibrate(VibrationEffect.createOneShot(400L, VibrationEffect.DEFAULT_AMPLITUDE))
                }
            }
        } catch (_: Exception) {}
    }

    // ─── Dismiss helper ─────────────────────────────────────
    fun dismiss() {
        alertHandled = true
        BleStateHolder.clearFallAlert()
        navController.popBackStack()
    }

    // ─── Trigger full SOS ───────────────────────────────────
    fun triggerSOS(elapsed: Int) {
        scope.launch {
            try {
                val contactsRepo = ContactsRepository(context)
                val contacts = contactsRepo.getEmergencyContacts()
                    .ifEmpty { contactsRepo.getFamilyContacts() }

                val locationHelper = LocationHelper(context)
                val location = locationHelper.getCurrentLocation()
                    ?: locationHelper.getLastKnownLocation()

                val mapsLink = if (location != null) {
                    LocationHelper.generateMapsLink(location.latitude, location.longitude)
                } else {
                    "Location unavailable — please call immediately"
                }

                // Build fall-specific SOS message
                val sosMessage = """
🚨 FALL ALERT — SAHAY DETECTED A FALL 🚨
The SAHAY wearable has detected that the user may have fallen and did not respond.
Please check on them immediately.
Last known location: $mapsLink
                """.trimIndent()

                val sosManager = SOSManager(context)
                // Send SMS to all emergency contacts
                if (contacts.isNotEmpty()) {
                    sosManager.sendEmergencySMSCustom(contacts, sosMessage)
                    // Send WhatsApp to first contact
                    sosManager.sendEmergencyWhatsApp(contacts, mapsLink)
                    // Emergency call
                    sosManager.callEmergencyNumber("112")
                }

                // Log confirmed fall
                SahayDatabase.getInstance(context).fallEventDao().insert(
                    FallEventEntity(
                        wasConfirmedFall    = true,
                        responseTimeSeconds = elapsed
                    )
                )
            } catch (e: Exception) {
                android.util.Log.e("FallAlertScreen", "SOS dispatch failed: ${e.message}", e)
            }
        }
    }

    // ─── Countdown coroutine ────────────────────────────────
    LaunchedEffect(Unit) {
        while (secondsLeft > 0 && !alertHandled) {
            delay(1_000)
            secondsLeft--
            // Vibrate every 5s as a reminder
            if (secondsLeft % 5 == 0) vibrateAlert()
        }
        if (!alertHandled) {
            // Countdown expired — nobody responded
            val elapsed = ((System.currentTimeMillis() - alertStartTime) / 1000).toInt()
            triggerSOS(elapsed)
            dismiss()
        }
    }

    // ═══════════════════════════════════════════════════════
    // UI
    // ═══════════════════════════════════════════════════════

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF7F0000),   // Deep dark red top
                        Color(0xFFB71C1C),   // Mid red
                        Color(0xFF1A0000)    // Near-black bottom
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {

            // ─── Warning Icon (pulsing) ──────────────────────
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Fall Detected",
                tint = Color(0xFFFF5252),
                modifier = Modifier
                    .size(64.dp)
                    .scale(pulseScale)
            )

            // ─── Header ─────────────────────────────────────
            Text(
                text = "FALL DETECTED",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = 3.sp
            )

            Text(
                text = "Emergency SOS will be sent automatically\nif you don't respond",
                fontSize = 15.sp,
                color = Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ─── Countdown Ring ──────────────────────────────
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(180.dp)
            ) {
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    // Background track
                    drawArc(
                        color = Color.White.copy(alpha = 0.15f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
                    )
                    // Progress arc
                    drawArc(
                        color = when {
                            secondsLeft > 20 -> Color(0xFFFF5252)
                            secondsLeft > 10 -> Color(0xFFFF9800)
                            else             -> Color(0xFFFFFF00)   // Yellow urgency
                        },
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$secondsLeft",
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "seconds",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ─── "I'M OKAY" Button ───────────────────────────
            Button(
                onClick = {
                    if (!alertHandled) {
                        alertHandled = true
                        val elapsed = ((System.currentTimeMillis() - alertStartTime) / 1000).toInt()
                        scope.launch {
                            SahayDatabase.getInstance(context).fallEventDao().insert(
                                FallEventEntity(
                                    wasConfirmedFall    = false,
                                    responseTimeSeconds = elapsed
                                )
                            )
                        }
                        dismiss()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(30.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50),   // Green — safe
                    contentColor   = Color.White
                ),
                elevation = ButtonDefaults.buttonElevation(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "I'M OKAY",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }

            Text(
                text = "Tap above to cancel emergency alert",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.45f),
                textAlign = TextAlign.Center
            )
        }
    }
}
