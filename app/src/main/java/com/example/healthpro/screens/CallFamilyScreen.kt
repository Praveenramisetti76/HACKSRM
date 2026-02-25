package com.example.healthpro.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.healthpro.ui.theme.*

data class FamilyContact(
    val name: String,
    val status: String,
    val statusColor: Color,
    val avatarColor: Color
)

@Composable
fun CallFamilyScreen(navController: NavController) {
    val contacts = listOf(
        FamilyContact("Son", "Online", TextGreenOnline, BlueAccent),
        FamilyContact("Daughter", "Available", TextYellowAvailable, TealAccent),
        FamilyContact("Grandson", "Busy", TextRedBusy, PurpleAccent)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavy)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Back button
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
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

        // Header
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

        // Contact cards
        contacts.forEach { contact ->
            ContactCard(contact = contact)
            Spacer(modifier = Modifier.height(14.dp))
        }

        Spacer(modifier = Modifier.weight(1f))

        // Dial Number Button
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clickable { },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardDark)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(TealAccent.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            Icons.Default.Dialpad,
                            contentDescription = "Dial",
                            tint = TealAccent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Text(
                        text = "DIAL NUMBER",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }

                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun ContactCard(contact: FamilyContact) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Avatar
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(contact.avatarColor.copy(alpha = 0.2f))
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = contact.avatarColor,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Name and status
                Column {
                    Text(
                        text = contact.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(contact.statusColor)
                        )
                        Text(
                            text = contact.status,
                            style = MaterialTheme.typography.labelMedium,
                            color = contact.statusColor
                        )
                    }
                }
            }

            // Call button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(TealAccent, CallGreen)
                        )
                    )
            ) {
                Icon(
                    Icons.Default.Call,
                    contentDescription = "Call ${contact.name}",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
