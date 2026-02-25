package com.example.healthpro.mood

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.healthpro.ui.theme.*

/**
 * Full-screen mood check-in dialog.
 *
 * Properties:
 *  - NOT dismissible without selection (no back, no outside click)
 *  - Shows 4 mood options with emoji buttons
 *  - Calls [onMoodSelected] when user taps one
 *
 * Design: Dark glassmorphism style matching app theme.
 */
@Composable
fun MoodCheckInDialog(
    isProcessing: Boolean,
    onMoodSelected: (MoodType) -> Unit
) {
    Dialog(
        onDismissRequest = { /* Cannot dismiss without selection */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false  // Full-width dialog
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { /* Absorb clicks */ },
            contentAlignment = Alignment.Center
        ) {
            // Animated entrance
            val scale by animateFloatAsState(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "dialog_scale"
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .scale(scale),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color(0xFF152238),
                                    Color(0xFF0D1B2A),
                                    Color(0xFF0A1628)
                                )
                            ),
                            shape = RoundedCornerShape(28.dp)
                        )
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // ─── Header ───
                    Text(
                        "💭",
                        fontSize = 48.sp
                    )

                    Spacer(Modifier.height(16.dp))

                    Text(
                        "How are you feeling\ntoday?",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        lineHeight = 32.sp
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        "Your daily emotional check-in",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(32.dp))

                    // ─── Mood Buttons ───
                    if (isProcessing) {
                        CircularProgressIndicator(
                            color = TealAccent,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Saving...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                    } else {
                        // Row 1: Good | Okay
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            MoodButton(
                                modifier = Modifier.weight(1f),
                                emoji = "🙂",
                                label = "Good",
                                gradientColors = listOf(Color(0xFF0E4434), Color(0xFF1B8A6B)),
                                onClick = { onMoodSelected(MoodType.GOOD) }
                            )
                            MoodButton(
                                modifier = Modifier.weight(1f),
                                emoji = "😐",
                                label = "Okay",
                                gradientColors = listOf(Color(0xFF2A3A5C), Color(0xFF4A6FA5)),
                                onClick = { onMoodSelected(MoodType.OKAY) }
                            )
                        }

                        Spacer(Modifier.height(14.dp))

                        // Row 2: Not Good | Unwell
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            MoodButton(
                                modifier = Modifier.weight(1f),
                                emoji = "🙁",
                                label = "Not Good",
                                gradientColors = listOf(Color(0xFF5C3A1E), Color(0xFFD97B2A)),
                                onClick = { onMoodSelected(MoodType.NOT_GOOD) }
                            )
                            MoodButton(
                                modifier = Modifier.weight(1f),
                                emoji = "🤒",
                                label = "Unwell",
                                gradientColors = listOf(Color(0xFF5C1010), Color(0xFFDC2626)),
                                onClick = { onMoodSelected(MoodType.UNWELL) }
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    Text(
                        "Tap how you feel • We care about you ❤️",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * Individual mood selection button with emoji, gradient, and press animation.
 */
@Composable
private fun MoodButton(
    modifier: Modifier = Modifier,
    emoji: String,
    label: String,
    gradientColors: List<Color>,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val buttonScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "button_press"
    )

    Card(
        modifier = modifier
            .scale(buttonScale)
            .height(100.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        onClick = {
            isPressed = true
            onClick()
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(gradientColors),
                    shape = RoundedCornerShape(20.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(emoji, fontSize = 32.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
