package com.example.healthpro.auth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthpro.auth.AuthScreenState
import com.example.healthpro.ui.theme.*

/**
 * Step 3: Preferred Name Setup Screen
 *
 * After OTP verification, ask the user:
 * "What should we call you in the app?"
 *
 * Validation:
 *  - Not empty
 *  - 2–30 characters
 *  - Letters and spaces only
 */
@Composable
fun NameSetupScreen(
    authState: AuthScreenState,
    onSubmitName: (String) -> Unit,
    onClearError: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val isLoading = authState is AuthScreenState.Loading
    val errorMessage = (authState as? AuthScreenState.Error)?.message

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavy)
            .padding(horizontal = 28.dp)
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🎉", fontSize = 64.sp)

        Spacer(Modifier.height(16.dp))

        Text(
            "Almost There!",
            style = MaterialTheme.typography.headlineMedium,
            color = TextWhite,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "What should we call you\nin the app?",
            style = MaterialTheme.typography.bodyLarge,
            color = TextMuted,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )

        Spacer(Modifier.height(40.dp))

        // ─── Name Input ───
        OutlinedTextField(
            value = name,
            onValueChange = {
                if (it.length <= 30) {
                    name = it
                    if (errorMessage != null) onClearError()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Your Preferred Name", fontSize = 16.sp) },
            placeholder = { Text("e.g., Grandpa, Dadu, Papa") },
            leadingIcon = {
                Icon(Icons.Default.Person, contentDescription = "Name", tint = TealAccent)
            },
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 22.sp),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    if (name.isNotBlank() && !isLoading) onSubmitName(name)
                }
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextWhite,
                unfocusedTextColor = TextGray,
                cursorColor = TealAccent,
                focusedBorderColor = TealAccent,
                unfocusedBorderColor = TextMuted.copy(alpha = 0.4f),
                focusedLabelColor = TealAccent,
                unfocusedLabelColor = TextMuted,
                errorBorderColor = SosRed,
                errorLabelColor = SosRed
            ),
            isError = errorMessage != null,
            enabled = !isLoading,
            shape = RoundedCornerShape(14.dp),
            supportingText = {
                Text(
                    "${name.length}/30",
                    color = if (name.length > 28) TextYellowAvailable else TextMuted.copy(alpha = 0.5f),
                    fontSize = 13.sp
                )
            }
        )

        // ─── Error Message ───
        if (errorMessage != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                errorMessage,
                color = SosRed,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 15.sp
            )
        }

        Spacer(Modifier.height(28.dp))

        // ─── Continue Button ───
        Button(
            onClick = {
                focusManager.clearFocus()
                onSubmitName(name)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = !isLoading && name.isNotBlank(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = TealAccent,
                disabledContainerColor = TealAccent.copy(alpha = 0.3f)
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = DarkNavy,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    "Let's Go! 🚀",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkNavy
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "This name will be used to greet you throughout the app",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            fontSize = 14.sp
        )
    }
}
