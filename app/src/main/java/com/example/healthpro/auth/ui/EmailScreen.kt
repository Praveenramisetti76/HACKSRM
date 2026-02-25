package com.example.healthpro.auth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthpro.auth.AuthScreenState
import com.example.healthpro.ui.theme.*

/**
 * Step 1: Email Input Screen
 *
 * Elder-friendly design:
 *  - Large fonts (20sp+ for inputs)
 *  - High contrast
 *  - Clear error messages
 *  - Full-width CTA button
 */
@Composable
fun EmailScreen(
    authState: AuthScreenState,
    onSubmitEmail: (String) -> Unit,
    onClearError: () -> Unit
) {
    var email by remember { mutableStateOf("") }
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
        // ─── Logo / Icon ───
        Text("👋", fontSize = 64.sp)

        Spacer(Modifier.height(16.dp))

        Text(
            "Welcome to SAHAY",
            style = MaterialTheme.typography.headlineMedium,
            color = TextWhite,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Enter your email to get started",
            style = MaterialTheme.typography.bodyLarge,
            color = TextMuted,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(40.dp))

        // ─── Email Input ───
        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                if (errorMessage != null) onClearError()
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Email Address", fontSize = 16.sp) },
            placeholder = { Text("your.email@example.com") },
            leadingIcon = {
                Icon(Icons.Default.Email, contentDescription = "Email", tint = TealAccent)
            },
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 20.sp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    if (email.isNotBlank() && !isLoading) onSubmitEmail(email)
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
            shape = RoundedCornerShape(14.dp)
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
                onSubmitEmail(email)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = !isLoading && email.isNotBlank(),
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
                    "Continue",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkNavy
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "We'll send a verification code to your email",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            fontSize = 14.sp
        )
    }
}
