package com.example.healthpro.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.healthpro.ui.theme.*

/**
 * All permissions required by Call Family + Help features.
 */
val requiredPermissions = arrayOf(
    Manifest.permission.READ_CONTACTS,
    Manifest.permission.CALL_PHONE,
    Manifest.permission.SEND_SMS,
    Manifest.permission.ACCESS_FINE_LOCATION
)

fun hasAllPermissions(context: Context): Boolean {
    return requiredPermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

fun hasPermission(context: Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

data class PermissionItem(
    val permission: String,
    val label: String,
    val description: String,
    val icon: ImageVector
)

val permissionItems = listOf(
    PermissionItem(
        Manifest.permission.READ_CONTACTS,
        "Contacts",
        "Access your phone contacts to save family members",
        Icons.Default.Contacts
    ),
    PermissionItem(
        Manifest.permission.CALL_PHONE,
        "Phone Calls",
        "Make direct calls to family and hospitals",
        Icons.Default.Call
    ),
    PermissionItem(
        Manifest.permission.SEND_SMS,
        "SMS Messages",
        "Send emergency alerts via text message",
        Icons.Default.Sms
    ),
    PermissionItem(
        Manifest.permission.ACCESS_FINE_LOCATION,
        "Location",
        "Share your live location during emergencies",
        Icons.Default.MyLocation
    )
)

/**
 * Full-screen permission request UI with explanations and retry flow.
 */
@Composable
fun PermissionsGate(
    onAllGranted: @Composable () -> Unit
) {
    val context = LocalContext.current
    var allGranted by remember { mutableStateOf(hasAllPermissions(context)) }
    var permissionResults by remember {
        mutableStateOf(
            requiredPermissions.associateWith { hasPermission(context, it) }
        )
    }
    var showDeniedDialog by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionResults = requiredPermissions.associateWith {
            results[it] ?: hasPermission(context, it)
        }
        allGranted = permissionResults.values.all { it }
        if (!allGranted) {
            showDeniedDialog = true
        }
    }

    if (allGranted) {
        onAllGranted()
    } else {
        // Permission Request Screen
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkNavy)
                .padding(horizontal = 24.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // Shield icon
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(
                            listOf(TealAccent, BlueAccent)
                        )
                    )
            ) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Permissions Required",
                style = MaterialTheme.typography.headlineMedium,
                color = TextWhite,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "SAHAY needs these permissions to keep you safe and connected with your family.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Permission items list
            permissionItems.forEach { item ->
                val granted = permissionResults[item.permission] ?: false
                PermissionRow(item = item, granted = granted)
                Spacer(modifier = Modifier.height(12.dp))
            }

            Spacer(modifier = Modifier.weight(1f))

            // Grant button
            Button(
                onClick = {
                    val ungrantedPermissions = requiredPermissions.filter { !hasPermission(context, it) }
                    if (ungrantedPermissions.isNotEmpty()) {
                        launcher.launch(ungrantedPermissions.toTypedArray())
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TealAccent)
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = DarkNavy
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "GRANT PERMISSIONS",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = DarkNavy,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Denied dialog
        if (showDeniedDialog) {
            AlertDialog(
                onDismissRequest = { showDeniedDialog = false },
                containerColor = CardDark,
                title = {
                    Text(
                        "Permissions Needed",
                        color = TextWhite,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        "Some permissions were denied. SAHAY needs all permissions to function properly. " +
                                "You can grant them from Settings > Apps > SAHAY > Permissions.",
                        color = TextMuted
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeniedDialog = false
                            val ungrantedPermissions = requiredPermissions.filter { !hasPermission(context, it) }
                            if (ungrantedPermissions.isNotEmpty()) {
                                launcher.launch(ungrantedPermissions.toTypedArray())
                            }
                        }
                    ) {
                        Text("RETRY", color = TealAccent, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeniedDialog = false }) {
                        Text("LATER", color = TextMuted)
                    }
                }
            )
        }
    }
}

@Composable
fun PermissionRow(item: PermissionItem, granted: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (granted) CardDark.copy(alpha = 0.6f) else CardDark
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
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (granted) TealAccent.copy(alpha = 0.15f)
                        else BlueAccent.copy(alpha = 0.15f)
                    )
            ) {
                Icon(
                    item.icon,
                    contentDescription = null,
                    tint = if (granted) TealAccent else BlueAccent,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }

            if (granted) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Granted",
                    tint = TealAccent,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
