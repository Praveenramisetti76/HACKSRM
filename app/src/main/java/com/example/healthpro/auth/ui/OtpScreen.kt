package com.example.healthpro.auth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthpro.auth.AuthScreenState
import com.example.healthpro.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Step 2: OTP Verification Screen
 *
 * Features:
 *  - 6-digit input boxes (elder-friendly, large)
 *  - Auto-focus
 *  - Shows the test OTP prominently (remove in production)
 *  - Resend button with 30-second timer
 *  - Clear error messages
 */
@Composable
fun OtpScreen(
    authState: AuthScreenState,
    testOtp: String,
    onVerifyOtp: (String) -> Unit,
    onResendOtp: () -> Boolean,
    getResendCooldown: () -> Long,
    onClearError: () -> Unit
) {
    var otpValue by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val isLoading = authState is AuthScreenState.Loading
    val errorMessage = (authState as? AuthScreenState.Error)?.message

    // Resend cooldown timer
    var resendCooldown by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            resendCooldown = getResendCooldown()
            delay(1000)
        }
    }

    // Auto-focus on mount
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavy)
            .padding(horizontal = 28.dp)
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🔐", fontSize = 64.sp)

        Spacer(Modifier.height(16.dp))

        Text(
            "Verify Your Email",
            style = MaterialTheme.typography.headlineMedium,
            color = TextWhite,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Enter the 6-digit code we sent",
            style = MaterialTheme.typography.bodyLarge,
            color = TextMuted,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(16.dp))

        // ─── Test OTP Display (REMOVE IN PRODUCTION) ───
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A3A2A))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "🧪 Test Mode — Your OTP:",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGreenOnline,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    testOtp,
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextGreenOnline,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 8.sp
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        // ─── OTP Input Boxes ───
        BasicTextField(
            value = otpValue,
            onValueChange = {
                if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                    otpValue = it
                    if (errorMessage != null) onClearError()
                }
            },
            modifier = Modifier.focusRequester(focusRequester),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = TextStyle(color = Color.Transparent),
            decorationBox = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    repeat(6) { index ->
                        val char = otpValue.getOrNull(index)?.toString() ?: ""
                        val isFocused = otpValue.length == index

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(60.dp)
                                .border(
                                    width = 2.dp,
                                    color = when {
                                        errorMessage != null -> SosRed
                                        isFocused -> TealAccent
                                        char.isNotEmpty() -> TealAccent.copy(alpha = 0.5f)
                                        else -> TextMuted.copy(alpha = 0.3f)
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .background(
                                    CardDark,
                                    RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = char,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        )

        // ─── Error Message ───
        if (errorMessage != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                errorMessage,
                color = SosRed,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(28.dp))

        // ─── Verify Button ───
        Button(
            onClick = {
                focusManager.clearFocus()
                onVerifyOtp(otpValue)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = !isLoading && otpValue.length == 6,
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
                    "Verify",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkNavy
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // ─── Resend OTP ───
        if (resendCooldown > 0) {
            Text(
                "Resend OTP in ${resendCooldown / 1000}s",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
                fontSize = 15.sp
            )
        } else {
            TextButton(onClick = {
                val sent = onResendOtp()
                if (sent) otpValue = "" // Clear input for new OTP
            }) {
                Text(
                    "Resend OTP",
                    fontSize = 16.sp,
                    color = TealAccent,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
