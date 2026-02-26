package com.example.healthpro.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.healthpro.genie.*
import com.example.healthpro.navigation.Screen
import com.example.healthpro.ui.theme.*
import kotlin.math.sin

@Composable
fun GenieScreen(navController: NavController, viewModel: GenieViewModel = viewModel()) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val recognizedText by viewModel.recognizedText.collectAsState()
    val partialText by viewModel.partialText.collectAsState()
    val parsedIntent by viewModel.parsedIntent.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val statusText by viewModel.statusText.collectAsState()
    val failedStep by viewModel.failedStep.collectAsState()

    // Microphone permission
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) viewModel.startListening()
    }

    // Animations
    val infiniteTransition = rememberInfiniteTransition(label = "genie_orb")
    val orbScale by infiniteTransition.animateFloat(
        initialValue = 0.9f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(2500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "orb_scale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow_alpha"
    )
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
        label = "wave_phase"
    )

    // Navigate to medicine screen when done with medicine intent
    LaunchedEffect(state, parsedIntent) {
        if (state == GenieViewModel.GenieState.DONE && parsedIntent?.type == IntentType.MEDICINE) {
            navController.navigate(Screen.MedicineOrder.route)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF0A1628), Color(0xFF0D1F3C), Color(0xFF0A1628))
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Back button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            IconButton(onClick = {
                viewModel.cancelOrder()
                navController.popBackStack()
            }) {
                Icon(Icons.Default.ArrowBack, "Back", tint = TextWhite)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ─── State-dependent content ─────────────────────────

        when (state) {
            GenieViewModel.GenieState.IDLE -> {
                IdleContent(orbScale, glowAlpha) {
                    if (hasPermission) viewModel.startListening()
                    else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }

            GenieViewModel.GenieState.LISTENING -> {
                ListeningContent(orbScale, glowAlpha, wavePhase, partialText)
            }

            GenieViewModel.GenieState.PROCESSING -> {
                ProcessingContent()
            }

            GenieViewModel.GenieState.CONFIRMING -> {
                parsedIntent?.let { intent ->
                    ConfirmingContent(
                        intent = intent,
                        onConfirm = { viewModel.confirmOrder() },
                        onCancel = { viewModel.reset() }
                    )
                }
            }

            GenieViewModel.GenieState.SOS_TRIGGERED -> {
                // When emergency is triggered, tell the user, wait briefly, then navigate to SOS screen
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(1000)
                    navController.navigate(Screen.Emergency.createRoute(true))
                }
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Emergency Protocol Activated\nTransferring to SOS...", 
                         style = MaterialTheme.typography.titleLarge, 
                         color = HelpRed, 
                         textAlign = TextAlign.Center)
                }
            }

            GenieViewModel.GenieState.CONSENT_REQUIRED -> {
                ConsentContent(
                    hasConsent = ConsentManager.hasConsent(context),
                    onGrantConsent = { viewModel.grantConsent() },
                    onConfirmSession = { viewModel.confirmSessionAndProceed() },
                    onCancel = { viewModel.reset() }
                )
            }

            GenieViewModel.GenieState.LAUNCHING, GenieViewModel.GenieState.AUTOMATING -> {
                AutomatingContent(statusText) {
                    viewModel.cancelOrder()
                }
            }

            GenieViewModel.GenieState.DONE -> {
                DoneContent(statusText) {
                    viewModel.reset()
                }
            }

            GenieViewModel.GenieState.ERROR -> {
                ErrorContent(
                    errorMessage = errorMessage,
                    failedStep = failedStep,
                    onRetry = {
                        if (failedStep != null) viewModel.retryStep()
                        else viewModel.reset()
                    },
                    onRetryAll = { viewModel.retryAll() },
                    onCancel = { viewModel.reset() }
                )
            }
        }
    }
}

// ── IDLE ──────────────────────────────────────────────────────

@Composable
private fun IdleContent(orbScale: Float, glowAlpha: Float, onTap: () -> Unit) {
    Spacer(modifier = Modifier.height(40.dp))

    Text("Hey, I'm Genie", style = MaterialTheme.typography.headlineMedium,
        color = TextWhite, fontWeight = FontWeight.Bold)
    Text("Tap the orb to start speaking", style = MaterialTheme.typography.bodyMedium,
        color = TextMuted)

    Spacer(modifier = Modifier.height(60.dp))

    // Glowing Orb (tappable)
    GlowingOrb(orbScale, glowAlpha, onClick = onTap)

    Spacer(modifier = Modifier.height(40.dp))

    Text("Try saying:", style = MaterialTheme.typography.bodySmall, color = TextMuted)
    Spacer(modifier = Modifier.height(8.dp))

    listOf(
        "\"Order me a sandwich from Swiggy\"",
        "\"Buy a phone charger from Amazon\"",
        "\"Order my medicines\""
    ).forEach { example ->
        Text(example, style = MaterialTheme.typography.bodyMedium,
            color = TealAccent.copy(alpha = 0.7f), textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(4.dp))
    }
}

// ── LISTENING ────────────────────────────────────────────────

@Composable
private fun ListeningContent(orbScale: Float, glowAlpha: Float, wavePhase: Float, partialText: String) {
    Spacer(modifier = Modifier.height(40.dp))

    Text("I am listening...", style = MaterialTheme.typography.headlineMedium,
        color = TextWhite, fontWeight = FontWeight.Bold)
    Text("Go ahead, tell me what you need", style = MaterialTheme.typography.bodyMedium,
        color = TextMuted)

    Spacer(modifier = Modifier.height(40.dp))

    GlowingOrb(orbScale, glowAlpha)

    Spacer(modifier = Modifier.height(24.dp))

    // Partial recognized text
    if (partialText.isNotBlank()) {
        Text("\"$partialText\"", style = MaterialTheme.typography.titleMedium,
            color = TextWhite.copy(alpha = 0.7f), textAlign = TextAlign.Center)
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Waveform
    Row(
        modifier = Modifier.height(40.dp).fillMaxWidth(0.6f),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0..15) {
            val barHeight = (16 + 18 * sin(Math.toRadians((wavePhase + i * 25).toDouble()))).toFloat()
            Box(
                modifier = Modifier.width(3.dp).height(barHeight.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(brush = Brush.verticalGradient(listOf(TealAccent, BlueAccent)))
            )
        }
    }
}

// ── PROCESSING ───────────────────────────────────────────────

@Composable
private fun ProcessingContent() {
    Spacer(modifier = Modifier.height(80.dp))

    CircularProgressIndicator(color = TealAccent, modifier = Modifier.size(64.dp))

    Spacer(modifier = Modifier.height(24.dp))

    Text("Understanding your request...", style = MaterialTheme.typography.titleMedium,
        color = TextWhite, fontWeight = FontWeight.Medium)
}

// ── CONFIRMING ───────────────────────────────────────────────

@Composable
private fun ConfirmingContent(intent: GenieIntent, onConfirm: () -> Unit, onCancel: () -> Unit) {
    Spacer(modifier = Modifier.height(24.dp))

    Text("I understood this:", style = MaterialTheme.typography.titleMedium,
        color = TextMuted)

    Spacer(modifier = Modifier.height(16.dp))

    // Intent card
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            // Icon + type
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (icon, color) = when (intent.type) {
                    IntentType.FOOD -> Icons.Default.Fastfood to FoodOrange
                    IntentType.PRODUCT -> Icons.Default.ShoppingCart to BlueAccent
                    IntentType.MEDICINE -> Icons.Default.LocalPharmacy to TealAccent
                }
                Icon(icon, null, tint = color, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    intent.type.name, style = MaterialTheme.typography.labelLarge,
                    color = color, fontWeight = FontWeight.Bold, letterSpacing = 2.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Item
            Text("Item", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Text(
                intent.item.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.headlineSmall,
                color = TextWhite, fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Platform
            Text("Platform", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Text(
                intent.platform.appName,
                style = MaterialTheme.typography.titleLarge,
                color = TealAccent, fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Action
            val actionText = when (intent.type) {
                IntentType.FOOD -> "Search → Add to cart → Checkout"
                IntentType.PRODUCT -> "Search → Add to cart → Checkout"
                IntentType.MEDICINE -> "View prescriptions → Select → Simulated checkout"
            }
            Text("Action", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Text(actionText, style = MaterialTheme.typography.bodyMedium, color = TextGray)
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Accessibility status
    val accessibilityRunning = GenieAccessibilityService.isRunning()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (accessibilityRunning) Color(0xFF0D2818) else Color(0xFF2D1A0A)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (accessibilityRunning) Icons.Default.CheckCircle else Icons.Default.Info,
                null,
                tint = if (accessibilityRunning) TealAccent else FoodOrange,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (accessibilityRunning)
                    "Automation enabled — Genie will handle everything up to payment."
                else
                    "Automation not enabled — Genie will open the search page for you.",
                style = MaterialTheme.typography.bodySmall,
                color = if (accessibilityRunning) TealAccent else FoodOrange
            )
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    Text("Should I proceed?", style = MaterialTheme.typography.titleMedium,
        color = TextWhite, fontWeight = FontWeight.Medium)

    Spacer(modifier = Modifier.height(16.dp))

    // Buttons
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(
                onClick = onCancel,
                modifier = Modifier.size(64.dp).clip(CircleShape).background(CardDark)
            ) {
                Icon(Icons.Default.Close, "Cancel", tint = TextGray, modifier = Modifier.size(28.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Cancel", style = MaterialTheme.typography.labelMedium, color = TextGray)
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(
                onClick = onConfirm,
                modifier = Modifier.size(64.dp).clip(CircleShape)
                    .background(brush = Brush.linearGradient(listOf(TealAccent, BlueAccent)))
            ) {
                Icon(Icons.Default.Check, "Confirm", tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Confirm", style = MaterialTheme.typography.labelMedium, color = TextWhite)
        }
    }
}

// ── CONSENT ──────────────────────────────────────────────────

@Composable
private fun ConsentContent(
    hasConsent: Boolean,
    onGrantConsent: () -> Unit,
    onConfirmSession: () -> Unit,
    onCancel: () -> Unit
) {
    var checkedConsent by remember { mutableStateOf(false) }

    Spacer(modifier = Modifier.height(24.dp))

    Icon(Icons.Default.Security, null, tint = TealAccent, modifier = Modifier.size(48.dp))
    Spacer(modifier = Modifier.height(16.dp))

    if (!hasConsent) {
        // First-time consent
        Text("Genie Automation Consent", style = MaterialTheme.typography.titleLarge,
            color = TextWhite, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardDark)
        ) {
            Text(
                ConsentManager.LEGAL_DISCLAIMER,
                style = MaterialTheme.typography.bodySmall,
                color = TextGray,
                modifier = Modifier.padding(16.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { checkedConsent = !checkedConsent }
        ) {
            Checkbox(
                checked = checkedConsent,
                onCheckedChange = { checkedConsent = it },
                colors = CheckboxDefaults.colors(checkedColor = TealAccent)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("I have read and consent to the above.", style = MaterialTheme.typography.bodyMedium,
                color = TextWhite)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onGrantConsent,
            enabled = checkedConsent,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TealAccent)
        ) {
            Text("Enable & Continue", fontWeight = FontWeight.Bold, color = Color.Black)
        }
    } else {
        // Per-session confirmation
        Text("Confirm Automation", style = MaterialTheme.typography.titleLarge,
            color = TextWhite, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        Text("Genie will automate actions in another app on your behalf.\nYou can cancel anytime.",
            style = MaterialTheme.typography.bodyMedium, color = TextGray, textAlign = TextAlign.Center)

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { checkedConsent = !checkedConsent }
        ) {
            Checkbox(
                checked = checkedConsent,
                onCheckedChange = { checkedConsent = it },
                colors = CheckboxDefaults.colors(checkedColor = TealAccent)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("I understand", style = MaterialTheme.typography.bodyMedium, color = TextWhite)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onConfirmSession,
            enabled = checkedConsent,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TealAccent)
        ) {
            Text("Proceed", fontWeight = FontWeight.Bold, color = Color.Black)
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    TextButton(onClick = onCancel) {
        Text("Cancel", color = TextGray)
    }
}

// ── AUTOMATING ───────────────────────────────────────────────

@Composable
private fun AutomatingContent(statusText: String, onCancel: () -> Unit) {
    Spacer(modifier = Modifier.height(60.dp))

    CircularProgressIndicator(color = TealAccent, modifier = Modifier.size(56.dp), strokeWidth = 4.dp)

    Spacer(modifier = Modifier.height(24.dp))

    Text("Genie is working...", style = MaterialTheme.typography.titleLarge,
        color = TextWhite, fontWeight = FontWeight.Bold)

    Spacer(modifier = Modifier.height(12.dp))

    Text(statusText, style = MaterialTheme.typography.bodyMedium,
        color = TealAccent, textAlign = TextAlign.Center)

    Spacer(modifier = Modifier.height(32.dp))

    OutlinedButton(
        onClick = onCancel,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = HelpRed)
    ) {
        Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Cancel Automation")
    }
}

// ── DONE ─────────────────────────────────────────────────────

@Composable
private fun DoneContent(statusText: String, onReset: () -> Unit) {
    Spacer(modifier = Modifier.height(60.dp))

    Icon(Icons.Default.CheckCircle, null, tint = TealAccent, modifier = Modifier.size(72.dp))

    Spacer(modifier = Modifier.height(24.dp))

    Text("All Done!", style = MaterialTheme.typography.headlineMedium,
        color = TextWhite, fontWeight = FontWeight.Bold)

    Spacer(modifier = Modifier.height(12.dp))

    Text(statusText, style = MaterialTheme.typography.bodyMedium,
        color = TealAccent, textAlign = TextAlign.Center)

    Spacer(modifier = Modifier.height(40.dp))

    Button(
        onClick = onReset,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = CardDark)
    ) {
        Icon(Icons.Default.Mic, null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Ask Genie Again", color = TextWhite, fontWeight = FontWeight.Bold)
    }
}

// ── ERROR / FAILURE ──────────────────────────────────────────

@Composable
private fun ErrorContent(
    errorMessage: String,
    failedStep: AutomationResult.Failed?,
    onRetry: () -> Unit,
    onRetryAll: () -> Unit,
    onCancel: () -> Unit
) {
    Spacer(modifier = Modifier.height(40.dp))

    Icon(Icons.Default.ErrorOutline, null, tint = HelpRed, modifier = Modifier.size(56.dp))

    Spacer(modifier = Modifier.height(16.dp))

    if (failedStep != null) {
        // Failure recovery UI
        Text("Automation Failed", style = MaterialTheme.typography.titleLarge,
            color = TextWhite, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2D0808))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Failed at step:", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                Text(failedStep.stepName, style = MaterialTheme.typography.titleMedium,
                    color = HelpRed, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Reason:", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                Text(failedStep.reason, style = MaterialTheme.typography.bodyMedium, color = TextGray)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Retry buttons
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = FoodOrange)
        ) {
            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Retry Step", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onRetryAll,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("Retry from Beginning", color = TextWhite)
        }
    } else {
        Text("Something went wrong", style = MaterialTheme.typography.titleLarge,
            color = TextWhite, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(12.dp))

        Text(errorMessage, style = MaterialTheme.typography.bodyMedium,
            color = TextGray, textAlign = TextAlign.Center)

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TealAccent)
        ) {
            Icon(Icons.Default.Mic, null, tint = Color.Black, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Try Again", fontWeight = FontWeight.Bold, color = Color.Black)
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    TextButton(onClick = onCancel) {
        Text("Cancel", color = TextGray)
    }
}

// ── Shared: Glowing Orb ──────────────────────────────────────

@Composable
private fun GlowingOrb(orbScale: Float, glowAlpha: Float, onClick: (() -> Unit)? = null) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(200.dp).then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        )
    ) {
        // Outer glow
        Box(
            modifier = Modifier
                .size((180 * orbScale).dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            BlueAccent.copy(alpha = glowAlpha * 0.5f),
                            PurpleAccent.copy(alpha = glowAlpha * 0.3f),
                            Color.Transparent
                        )
                    )
                )
        )
        // Inner orb
        Box(
            modifier = Modifier
                .size((120 * orbScale).dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            BlueAccent.copy(alpha = 0.9f),
                            PurpleAccent.copy(alpha = 0.7f),
                            BlueAccent.copy(alpha = 0.4f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Mic, "Speak", tint = Color.White, modifier = Modifier.size(40.dp))
        }
    }
}
