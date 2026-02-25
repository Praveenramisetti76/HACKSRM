package com.example.healthpro.medicine.ui

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.healthpro.medicine.data.db.MedicineManagerEntity
import com.example.healthpro.medicine.data.db.PrescriptionEntity
import com.example.healthpro.medicine.viewmodel.PrescriptionVaultViewModel
import com.example.healthpro.medicine.viewmodel.PrescriptionVaultViewModel.VaultScreen
import com.example.healthpro.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

// ── Module Colors ──
private val VaultBlue = Color(0xFF3B82F6)
private val VaultGreen = Color(0xFF10B981)
private val VaultRed = Color(0xFFEF4444)
private val VaultOrange = Color(0xFFF97316)
private val VaultPurple = Color(0xFF8B5CF6)
private val VaultTeal = Color(0xFF14B8A6)

/**
 * Main entry point for Prescription Vault + Medicine Tracker.
 * Replaces the old MedicineListScreen with a vault-first approach.
 */
@Composable
fun PrescriptionVaultScreen(
    navController: NavController,
    viewModel: PrescriptionVaultViewModel = viewModel()
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.init(context) }

    val currentScreen by viewModel.currentScreen.collectAsState()
    val prescriptions by viewModel.prescriptions.collectAsState()
    val medicines by viewModel.medicines.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMsg by viewModel.errorMessage.collectAsState()
    val extractedMeds by viewModel.extractedMedicines.collectAsState()
    val showPurchaseDialog by viewModel.showPurchaseDialog.collectAsState()
    val showMedicineOverDialog by viewModel.showMedicineOverDialog.collectAsState()

    // ── Launchers ──
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val mime = context.contentResolver.getType(it)
            viewModel.storePrescription(it, mime)
        }
    }

    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            cameraUri?.let { viewModel.storePrescription(it) }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = viewModel.createCameraUri(context)
            if (uri != null) {
                cameraUri = uri
                cameraLauncher.launch(uri)
            }
        }
    }

    var showCaptureOptions by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(DarkNavy)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Top Bar ──
            VaultTopBar(
                currentScreen = currentScreen,
                onBack = {
                    if (currentScreen == VaultScreen.HOME) {
                        navController.popBackStack()
                    } else {
                        viewModel.navigateTo(VaultScreen.HOME)
                    }
                },
                title = when (currentScreen) {
                    VaultScreen.HOME -> "Prescription Vault"
                    VaultScreen.PRESCRIPTIONS -> "My Prescriptions"
                    VaultScreen.MEDICINES -> "My Medicines"
                    VaultScreen.ADD_MEDICINE -> "Add Medicines"
                }
            )

            // ── Content ──
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    slideInHorizontally { it } + fadeIn() togetherWith
                            slideOutHorizontally { -it } + fadeOut()
                },
                label = "vault_screen"
            ) { screen ->
                when (screen) {
                    VaultScreen.HOME -> VaultHomeContent(
                        prescriptionCount = prescriptions.size,
                        medicineCount = medicines.size,
                        medicines = medicines,
                        onUploadCapture = { showCaptureOptions = true },
                        onViewPrescriptions = { viewModel.navigateTo(VaultScreen.PRESCRIPTIONS) },
                        onViewMedicines = { viewModel.navigateTo(VaultScreen.MEDICINES) },
                        onMedicineOver = { viewModel.showMedicineOverConfirmation() },
                        onMarkTaken = { viewModel.markTaken(it) }
                    )

                    VaultScreen.PRESCRIPTIONS -> PrescriptionListContent(
                        prescriptions = prescriptions,
                        onDelete = { viewModel.deletePrescription(it) }
                    )

                    VaultScreen.MEDICINES -> MedicineListContent(
                        medicines = medicines,
                        onDelete = { viewModel.deleteMedicine(it) },
                        onMarkTaken = { viewModel.markTaken(it) }
                    )

                    VaultScreen.ADD_MEDICINE -> AddMedicineContent(
                        extractedMedicines = extractedMeds,
                        onSaveAll = { meds, times -> viewModel.saveAllExtractedMedicines(meds, times) },
                        onSaveSingle = { viewModel.saveMedicine(it) },
                        onDone = { viewModel.navigateTo(VaultScreen.HOME) }
                    )
                }
            }
        }

        // ── Capture Options Bottom Sheet ──
        if (showCaptureOptions) {
            CaptureOptionsSheet(
                onDismiss = { showCaptureOptions = false },
                onCamera = {
                    showCaptureOptions = false
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                },
                onGallery = {
                    showCaptureOptions = false
                    galleryLauncher.launch("image/*")
                }
            )
        }

        // ── Purchase Confirmation Dialog ──
        if (showPurchaseDialog) {
            PurchaseConfirmationDialog(
                onYes = { viewModel.confirmPurchased() },
                onNo = { viewModel.confirmNotPurchased() }
            )
        }

        // ── Medicine Over Dialog ──
        if (showMedicineOverDialog) {
            MedicineOverDialog(
                onConfirm = { viewModel.confirmMedicineOver() },
                onDismiss = { viewModel.dismissMedicineOverDialog() }
            )
        }

        // ── Loading Overlay ──
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DarkNavy.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = CardDark)
                ) {
                    Column(
                        modifier = Modifier.padding(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = VaultBlue,
                            strokeWidth = 4.dp,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(20.dp))
                        Text(
                            "Analyzing prescription...",
                            fontSize = 20.sp,
                            color = TextWhite,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Extracting medicine details",
                            fontSize = 16.sp,
                            color = TextMuted
                        )
                    }
                }
            }
        }

        // ── Error Snackbar ──
        if (!errorMsg.isNullOrBlank()) {
            Snackbar(
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.BottomCenter),
                containerColor = VaultRed,
                contentColor = Color.White,
                action = {
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text("OK", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            ) {
                Text(errorMsg ?: "", fontSize = 16.sp)
            }
        }
    }
}

// ═══════════════════════════════════════════
// TOP BAR
// ═══════════════════════════════════════════

@Composable
private fun VaultTopBar(currentScreen: VaultScreen, onBack: () -> Unit, title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.Default.ArrowBack, "Back",
                tint = TextWhite,
                modifier = Modifier.size(32.dp)
            )
        }
        Text(
            title,
            fontSize = 22.sp,
            color = TextWhite,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.size(48.dp)) // Balance the back button
    }
}

// ═══════════════════════════════════════════
// HOME SCREEN
// ═══════════════════════════════════════════

@Composable
private fun VaultHomeContent(
    prescriptionCount: Int,
    medicineCount: Int,
    medicines: List<MedicineManagerEntity>,
    onUploadCapture: () -> Unit,
    onViewPrescriptions: () -> Unit,
    onViewMedicines: () -> Unit,
    onMedicineOver: () -> Unit,
    onMarkTaken: (Long) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Main Action: Upload / Capture ──
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onUploadCapture),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(VaultBlue, VaultPurple)
                            ),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .padding(28.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(Modifier.width(20.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Upload / Capture",
                                fontSize = 22.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Prescription",
                                fontSize = 22.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Camera or gallery",
                                fontSize = 16.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }

        // ── Stats Row ──
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Description,
                    value = "$prescriptionCount",
                    label = "Prescriptions",
                    color = VaultTeal,
                    onClick = onViewPrescriptions
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Medication,
                    value = "$medicineCount",
                    label = "Medicines",
                    color = VaultBlue,
                    onClick = onViewMedicines
                )
            }
        }

        // ── View Stored Prescriptions ──
        item {
            ActionCard(
                icon = Icons.Default.FolderOpen,
                title = "View Stored Prescriptions",
                subtitle = "$prescriptionCount prescriptions saved",
                color = VaultTeal,
                onClick = onViewPrescriptions
            )
        }

        // ── Medicine Over Button ──
        if (medicineCount > 0) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onMedicineOver),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(VaultRed, Color(0xFFB91C1C))
                                ),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(24.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.ErrorOutline,
                                    null,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Medicine Over?",
                                    fontSize = 22.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Alert family via WhatsApp",
                                    fontSize = 15.sp,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                            Icon(
                                Icons.Default.Send,
                                null,
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }

        }

        // ── Today's Medicines ──
        if (medicines.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Today's Medicines",
                    fontSize = 20.sp,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
            }

            items(medicines) { med ->
                MedicineTakeCard(
                    medicine = med,
                    onTake = { onMarkTaken(med.id) }
                )
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ═══════════════════════════════════════════
// STAT CARD
// ═══════════════════════════════════════════

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    value: String,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(8.dp))
            Text(value, fontSize = 32.sp, color = TextWhite, fontWeight = FontWeight.Bold)
            Text(label, fontSize = 15.sp, color = TextMuted)
        }
    }
}

// ═══════════════════════════════════════════
// ACTION CARD
// ═══════════════════════════════════════════

@Composable
private fun ActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 18.sp, color = TextWhite, fontWeight = FontWeight.Bold)
                Text(subtitle, fontSize = 14.sp, color = TextMuted)
            }
            Icon(Icons.Default.ChevronRight, null, tint = TextMuted)
        }
    }
}

// ═══════════════════════════════════════════
// MEDICINE TAKE CARD (Today's view)
// ═══════════════════════════════════════════

@Composable
private fun MedicineTakeCard(
    medicine: MedicineManagerEntity,
    onTake: () -> Unit
) {
    // Read isTakenToday from the entity (persisted in DB), NOT local remember state!
    val taken = medicine.isTakenToday

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (taken) VaultGreen.copy(alpha = 0.1f) else CardDark
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (taken) VaultGreen.copy(alpha = 0.2f)
                        else VaultBlue.copy(alpha = 0.15f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (taken) Icons.Default.CheckCircle else Icons.Default.Medication,
                    null,
                    tint = if (taken) VaultGreen else VaultBlue,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    medicine.name,
                    fontSize = 18.sp,
                    color = if (taken) TextMuted else TextWhite,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${medicine.dosage} · ${medicine.frequencyPerDay}x/day",
                    fontSize = 14.sp,
                    color = TextMuted
                )
                if (medicine.reminderTimes.isNotBlank()) {
                    Text(
                        "⏰ ${medicine.reminderTimes.replace(",", " · ")}",
                        fontSize = 13.sp,
                        color = if (taken) TextMuted else VaultTeal
                    )
                }
            }
            if (!taken) {
                Button(
                    onClick = { onTake() },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VaultGreen),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text("Take", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                // Show DONE indicator — greyed out, disabled
                Box(
                    modifier = Modifier
                        .background(
                            VaultGreen.copy(alpha = 0.15f),
                            RoundedCornerShape(14.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CheckCircle,
                            null,
                            tint = VaultGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Done",
                            fontSize = 16.sp,
                            color = VaultGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════
// PRESCRIPTION LIST
// ═══════════════════════════════════════════

@Composable
private fun PrescriptionListContent(
    prescriptions: List<PrescriptionEntity>,
    onDelete: (PrescriptionEntity) -> Unit
) {
    if (prescriptions.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Description,
                null,
                tint = VaultTeal,
                modifier = Modifier.size(80.dp)
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "No prescriptions yet",
                fontSize = 24.sp,
                color = TextWhite,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Upload or capture a prescription\nto get started",
                fontSize = 16.sp,
                color = TextMuted,
                textAlign = TextAlign.Center
            )
        }
    } else {
        val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(prescriptions) { rx ->
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(VaultTeal.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Description,
                                null,
                                tint = VaultTeal,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Prescription #${rx.id}",
                                fontSize = 18.sp,
                                color = TextWhite,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                dateFormat.format(Date(rx.dateAdded)),
                                fontSize = 14.sp,
                                color = TextMuted
                            )
                            Text(
                                if (rx.analyzed) "✓ Analyzed" else "⏳ Pending",
                                fontSize = 13.sp,
                                color = if (rx.analyzed) VaultGreen else VaultOrange
                            )
                        }
                        IconButton(onClick = { onDelete(rx) }) {
                            Icon(
                                Icons.Default.Delete,
                                "Delete",
                                tint = VaultRed.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

// ═══════════════════════════════════════════
// MEDICINE LIST
// ═══════════════════════════════════════════

@Composable
private fun MedicineListContent(
    medicines: List<MedicineManagerEntity>,
    onDelete: (MedicineManagerEntity) -> Unit,
    onMarkTaken: (Long) -> Unit
) {
    if (medicines.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Medication,
                null,
                tint = VaultBlue,
                modifier = Modifier.size(80.dp)
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "No medicines added",
                fontSize = 24.sp,
                color = TextWhite,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Upload a prescription to\nauto-extract medicines",
                fontSize = 16.sp,
                color = TextMuted,
                textAlign = TextAlign.Center
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(medicines) { med ->
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(VaultBlue.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Medication,
                                null,
                                tint = VaultBlue,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                med.name,
                                fontSize = 18.sp,
                                color = TextWhite,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${med.dosage} · ${med.frequencyPerDay}x/day · ${med.durationDays} days",
                                fontSize = 14.sp,
                                color = TextMuted
                            )
                            if (med.reminderTimes.isNotBlank()) {
                                Text(
                                    "⏰ ${med.reminderTimes.replace(",", " · ")}",
                                    fontSize = 13.sp,
                                    color = VaultTeal
                                )
                            }
                        }
                        IconButton(onClick = { onDelete(med) }) {
                            Icon(
                                Icons.Default.Delete,
                                "Delete",
                                tint = VaultRed.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

// ═══════════════════════════════════════════
// ADD MEDICINE SCREEN (from extracted)
// ═══════════════════════════════════════════

@Composable
private fun AddMedicineContent(
    extractedMedicines: List<PrescriptionVaultViewModel.ExtractedMedicine>,
    onSaveAll: (List<PrescriptionVaultViewModel.ExtractedMedicine>, Map<Int, String>) -> Unit,
    onSaveSingle: (MedicineManagerEntity) -> Unit,
    onDone: () -> Unit
) {
    val editableMeds = remember(extractedMedicines) {
        extractedMedicines.map { med ->
            mutableStateOf(med)
        }.toMutableList()
    }

    // Reminder times per medicine index
    val reminderTimesMap = remember { mutableStateMapOf<Int, String>() }

    // Initialize default times
    LaunchedEffect(extractedMedicines) {
        extractedMedicines.forEachIndexed { index, med ->
            val default = when (med.frequencyPerDay) {
                1 -> "08:00"
                2 -> "08:00,20:00"
                3 -> "08:00,14:00,21:00"
                else -> "08:00"
            }
            reminderTimesMap[index] = default
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (extractedMedicines.isEmpty()) {
            // Manual add
            ManualAddMedicineForm(
                onSave = onSaveSingle,
                onDone = onDone
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        "${extractedMedicines.size} medicines found",
                        fontSize = 20.sp,
                        color = VaultGreen,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Review and set reminder times",
                        fontSize = 15.sp,
                        color = TextMuted
                    )
                }

                itemsIndexed(extractedMedicines) { index, med ->
                    ExtractedMedicineCard(
                        medicine = med,
                        reminderTimes = reminderTimesMap[index] ?: "",
                        onReminderTimesChange = { reminderTimesMap[index] = it }
                    )
                }

                item { Spacer(Modifier.height(80.dp)) }
            }

            // Save All Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkNavy)
                    .padding(20.dp)
            ) {
                Button(
                    onClick = { onSaveAll(extractedMedicines, reminderTimesMap.toMap()) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VaultGreen)
                ) {
                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Save All & Set Reminders",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ExtractedMedicineCard(
    medicine: PrescriptionVaultViewModel.ExtractedMedicine,
    reminderTimes: String,
    onReminderTimesChange: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(VaultBlue.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Medication,
                        null,
                        tint = VaultBlue,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        medicine.name,
                        fontSize = 20.sp,
                        color = TextWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${medicine.dosage} · ${medicine.frequencyPerDay}x/day · ${medicine.durationDays} days",
                        fontSize = 14.sp,
                        color = TextMuted
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = TextMuted.copy(alpha = 0.2f))
            Spacer(Modifier.height(12.dp))

            Text(
                "⏰ Reminder Times",
                fontSize = 16.sp,
                color = VaultTeal,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = reminderTimes,
                onValueChange = onReminderTimesChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 18.sp,
                    color = TextWhite
                ),
                placeholder = {
                    Text(
                        "e.g. 08:00,14:00,21:00",
                        color = TextMuted.copy(alpha = 0.5f),
                        fontSize = 16.sp
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VaultTeal,
                    unfocusedBorderColor = TextMuted.copy(alpha = 0.3f),
                    cursorColor = VaultTeal
                ),
                singleLine = true
            )

            Text(
                "Separate times with commas (HH:MM format)",
                fontSize = 13.sp,
                color = TextMuted,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

// ═══════════════════════════════════════════
// MANUAL ADD MEDICINE FORM
// ═══════════════════════════════════════════

@Composable
private fun ManualAddMedicineForm(
    onSave: (MedicineManagerEntity) -> Unit,
    onDone: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var dosage by remember { mutableStateOf("") }
    var frequency by remember { mutableStateOf("1") }
    var duration by remember { mutableStateOf("30") }
    var reminderTimes by remember { mutableStateOf("08:00") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Add Medicine Manually",
            fontSize = 22.sp,
            color = TextWhite,
            fontWeight = FontWeight.Bold
        )

        VaultTextField(value = name, onValueChange = { name = it }, label = "Medicine Name")
        VaultTextField(value = dosage, onValueChange = { dosage = it }, label = "Dosage (e.g. 500mg)")
        VaultTextField(
            value = frequency,
            onValueChange = { frequency = it.filter { c -> c.isDigit() } },
            label = "Times per day"
        )
        VaultTextField(
            value = duration,
            onValueChange = { duration = it.filter { c -> c.isDigit() } },
            label = "Duration (days)"
        )
        VaultTextField(
            value = reminderTimes,
            onValueChange = { reminderTimes = it },
            label = "Reminder times (e.g. 08:00,20:00)"
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                if (name.isNotBlank()) {
                    val freq = frequency.toIntOrNull()?.coerceIn(1, 10) ?: 1
                    val dur = duration.toIntOrNull()?.coerceAtLeast(1) ?: 30
                    onSave(
                        MedicineManagerEntity(
                            name = name.trim(),
                            dosage = dosage.ifBlank { "as prescribed" },
                            frequencyPerDay = freq,
                            totalQuantity = freq * dur,
                            startDate = System.currentTimeMillis(),
                            reorderThreshold = 7,
                            durationDays = dur,
                            reminderTimes = reminderTimes
                        )
                    )
                    // Reset form
                    name = ""
                    dosage = ""
                    frequency = "1"
                    duration = "30"
                    reminderTimes = "08:00"
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = VaultGreen),
            enabled = name.isNotBlank()
        ) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text("Save Medicine", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        OutlinedButton(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, TextMuted)
        ) {
            Text("Done", fontSize = 18.sp, color = TextMuted, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun VaultTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = TextMuted, fontSize = 16.sp) },
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        textStyle = LocalTextStyle.current.copy(fontSize = 18.sp, color = TextWhite),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = VaultBlue,
            unfocusedBorderColor = TextMuted.copy(alpha = 0.3f),
            cursorColor = VaultBlue
        ),
        singleLine = true
    )
}

// ═══════════════════════════════════════════
// CAPTURE OPTIONS BOTTOM SHEET
// ═══════════════════════════════════════════

@Composable
private fun CaptureOptionsSheet(
    onDismiss: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardDark,
        shape = RoundedCornerShape(28.dp),
        title = {
            Text(
                "Add Prescription",
                fontSize = 24.sp,
                color = TextWhite,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onCamera),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = VaultBlue.copy(alpha = 0.1f))
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CameraAlt,
                            null,
                            tint = VaultBlue,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                "📷 Take Photo",
                                fontSize = 20.sp,
                                color = TextWhite,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Use camera to capture prescription",
                                fontSize = 14.sp,
                                color = TextMuted
                            )
                        }
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onGallery),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = VaultPurple.copy(alpha = 0.1f))
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.PhotoLibrary,
                            null,
                            tint = VaultPurple,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                "📂 From Gallery",
                                fontSize = 20.sp,
                                color = TextWhite,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Select an existing image",
                                fontSize = 14.sp,
                                color = TextMuted
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted, fontSize = 18.sp)
            }
        }
    )
}

// ═══════════════════════════════════════════
// PURCHASE CONFIRMATION DIALOG
// ═══════════════════════════════════════════

@Composable
private fun PurchaseConfirmationDialog(
    onYes: () -> Unit,
    onNo: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        containerColor = CardDark,
        shape = RoundedCornerShape(28.dp),
        icon = {
            Icon(
                Icons.Default.ShoppingCart,
                null,
                tint = VaultOrange,
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text(
                "Medicines Purchased?",
                fontSize = 24.sp,
                color = TextWhite,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Text(
                "Have these medicines been purchased already?",
                fontSize = 18.sp,
                color = TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = onYes,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VaultGreen),
                modifier = Modifier.height(52.dp)
            ) {
                Text("✅ Yes", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Button(
                onClick = onNo,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VaultRed),
                modifier = Modifier.height(52.dp)
            ) {
                Text("❌ No", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    )
}

// ═══════════════════════════════════════════
// MEDICINE OVER DIALOG
// ═══════════════════════════════════════════

@Composable
private fun MedicineOverDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardDark,
        shape = RoundedCornerShape(28.dp),
        icon = {
            Icon(
                Icons.Default.ErrorOutline,
                null,
                tint = VaultRed,
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text(
                "Send to Family?",
                fontSize = 24.sp,
                color = TextWhite,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Text(
                "This will send your prescription to family via WhatsApp with a message that medicines are over.",
                fontSize = 17.sp,
                color = TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VaultRed),
                modifier = Modifier.height(52.dp)
            ) {
                Icon(Icons.Default.Send, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Send", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted, fontSize = 18.sp)
            }
        }
    )
}
