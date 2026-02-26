package com.example.healthpro.ui.callfamily

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.healthpro.data.contacts.SavedContact
import com.example.healthpro.ui.theme.*

// ═══════════════════════════════════════════════════════════════
// CALL FAMILY SCREEN — Main entry point
// ═══════════════════════════════════════════════════════════════

@Composable
fun CallFamilyScreenNew(navController: NavController) {
    val viewModel: CallFamilyViewModel = viewModel()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.init(context)
    }

    if (viewModel.showContactPicker) {
        ContactPickerScreen(
            viewModel = viewModel,
            onDismiss = { viewModel.showContactPicker = false }
        )
    } else {
        CallFamilyHomeScreen(
            navController = navController,
            viewModel = viewModel
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// CALL FAMILY HOME — Shows saved family contacts with actions
// ═══════════════════════════════════════════════════════════════

@Composable
fun CallFamilyHomeScreen(
    navController: NavController,
    viewModel: CallFamilyViewModel
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavy)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // ── Back button ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = TealAccent
                )
            }
            Text(
                text = "Back",
                style = MaterialTheme.typography.bodyLarge,
                color = TealAccent
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Header ──
        Text(
            text = "Call Family",
            style = MaterialTheme.typography.headlineLarge,
            color = TextWhite,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Who would you like to speak to?",
            style = MaterialTheme.typography.bodyLarge,
            color = TextMuted
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (viewModel.familyContacts.isEmpty()) {
            // ── Empty state ──
            EmptyFamilyState(
                onAddContacts = {
                    viewModel.showContactPicker = true
                    viewModel.loadDeviceContacts()
                }
            )
        } else {
            // ── Family contact cards ──
            viewModel.familyContacts.forEach { contact ->
                FamilyContactCard(
                    contact = contact,
                    onCall = {
                        try {
                            val intent = Intent(
                                Intent.ACTION_CALL,
                                Uri.parse("tel:${contact.phoneNumber}")
                            ).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Cannot make call", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onMessage = {
                        try {
                            val userName = "Grandpa" // Can be made dynamic
                            val message = "Hello, I need help. I am $userName"
                            val intent = Intent(
                                Intent.ACTION_SENDTO,
                                Uri.parse("smsto:${contact.phoneNumber}")
                            ).apply {
                                putExtra("sms_body", message)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Cannot open messages", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onWhatsApp = {
                        try {
                            // Check if WhatsApp is installed
                            context.packageManager.getPackageInfo(
                                "com.whatsapp",
                                PackageManager.GET_ACTIVITIES
                            )
                            val cleanNumber = contact.phoneNumber
                                .replace(Regex("[^\\d+]"), "")
                                .removePrefix("+")
                            val url = "https://wa.me/$cleanNumber"
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: PackageManager.NameNotFoundException) {
                            Toast.makeText(
                                context,
                                "WhatsApp not installed",
                                Toast.LENGTH_SHORT
                            ).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Cannot open WhatsApp", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onRemove = {
                        viewModel.removeFamilyContact(contact)
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Add more contacts button ──
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clickable {
                        viewModel.showContactPicker = true
                        viewModel.loadDeviceContacts()
                    },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.PersonAdd,
                        contentDescription = null,
                        tint = TealAccent,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "ADD MORE FAMILY",
                        style = MaterialTheme.typography.labelLarge,
                        color = TealAccent,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ═══════════════════════════════════════════════════════════════
// FAMILY CONTACT CARD — Large card with Call, Message, WhatsApp
// ═══════════════════════════════════════════════════════════════

@Composable
fun FamilyContactCard(
    contact: SavedContact,
    onCall: () -> Unit,
    onMessage: () -> Unit,
    onWhatsApp: () -> Unit,
    onRemove: () -> Unit
) {
    val avatarColors = listOf(BlueAccent, TealAccent, PurpleAccent, FoodOrange, CallGreen)
    val colorIndex = contact.name.hashCode().let { kotlin.math.abs(it) % avatarColors.size }
    val avatarColor = avatarColors[colorIndex]

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // ── Avatar ──
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(avatarColor.copy(alpha = 0.2f))
                ) {
                    if (contact.photoUri != null) {
                        AsyncImage(
                            model = contact.photoUri,
                            contentDescription = contact.name,
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = contact.name.firstOrNull()?.uppercase()?.toString() ?: "#",
                            style = MaterialTheme.typography.headlineMedium,
                            color = avatarColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // ── Name & Number ──
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = contact.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 22.sp
                    )
                    Text(
                        text = contact.phoneNumber,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }

                // ── Remove button ──
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = TextMuted.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Action buttons row ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 📞 Call Button
                ActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Call,
                    label = "Call",
                    gradientColors = listOf(CallGreen, Color(0xFF145A48)),
                    onClick = onCall
                )

                // 💬 Message Button
                ActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Message,
                    label = "Message",
                    gradientColors = listOf(BlueAccent, Color(0xFF2A5A9D)),
                    onClick = onMessage
                )

                // 🎥 WhatsApp Button
                ActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Videocam,
                    label = "WhatsApp",
                    gradientColors = listOf(Color(0xFF25D366), Color(0xFF128C7E)),
                    onClick = onWhatsApp
                )
            }
        }
    }
}

@Composable
fun ActionButton(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    gradientColors: List<Color>,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(56.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(gradientColors)
                )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// EMPTY STATE — When no family contacts are saved
// ═══════════════════════════════════════════════════════════════

@Composable
fun EmptyFamilyState(onAddContacts: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(TealAccent.copy(alpha = 0.1f))
        ) {
            Icon(
                Icons.Default.FamilyRestroom,
                contentDescription = null,
                tint = TealAccent,
                modifier = Modifier.size(50.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "No Family Contacts Yet",
            style = MaterialTheme.typography.titleLarge,
            color = TextWhite,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Add contacts from your phone to\ncall them quickly.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = onAddContacts,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TealAccent)
        ) {
            Icon(
                Icons.Default.PersonAdd,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = DarkNavy
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "ADD FAMILY CONTACTS",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = DarkNavy,
                letterSpacing = 1.sp
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// CONTACT PICKER — Select contacts from device
// ═══════════════════════════════════════════════════════════════

@Composable
fun ContactPickerScreen(
    viewModel: CallFamilyViewModel,
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
            Text(
                text = "Select Contacts",
                style = MaterialTheme.typography.headlineMedium,
                color = TextWhite,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            if (viewModel.selectedContacts.isNotEmpty()) {
                Badge(containerColor = TealAccent) {
                    Text(
                        text = "${viewModel.selectedContacts.size}",
                        color = DarkNavy,
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
            placeholder = {
                Text("Search contacts...", color = TextMuted)
            },
            leadingIcon = {
                Icon(Icons.Default.Search, null, tint = TextMuted)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TealAccent,
                unfocusedBorderColor = CardDark,
                focusedContainerColor = CardDark,
                unfocusedContainerColor = CardDark,
                cursorColor = TealAccent,
                focusedTextColor = TextWhite,
                unfocusedTextColor = TextWhite
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (viewModel.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(40.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = TealAccent)
            }
        } else {
            // ── Contact list ──
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredContacts) { contact ->
                    val isSelected = contact.id in viewModel.selectedContacts
                    val avatarColors = listOf(BlueAccent, TealAccent, PurpleAccent, FoodOrange, CallGreen)
                    val colorIndex = contact.name.hashCode().let { kotlin.math.abs(it) % avatarColors.size }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleContactSelection(contact.id) },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected)
                                TealAccent.copy(alpha = 0.1f) else Color.Transparent
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Avatar
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
                                        fontWeight = FontWeight.Bold
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

                            // Checkbox
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { viewModel.toggleContactSelection(contact.id) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = TealAccent,
                                    uncheckedColor = TextMuted,
                                    checkmarkColor = DarkNavy
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
            onClick = { viewModel.saveSelectedAsFamilyContacts() },
            enabled = viewModel.selectedContacts.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = TealAccent,
                disabledContainerColor = CardDark
            )
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (viewModel.selectedContacts.isNotEmpty()) DarkNavy else TextMuted
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (viewModel.selectedContacts.isNotEmpty())
                    "SAVE ${viewModel.selectedContacts.size} CONTACTS"
                else "SELECT CONTACTS",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (viewModel.selectedContacts.isNotEmpty()) DarkNavy else TextMuted,
                letterSpacing = 1.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
