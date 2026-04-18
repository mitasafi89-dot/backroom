package com.example.backroom.ui.screens.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.backroom.ui.theme.BackroomTheme
import kotlinx.coroutines.delay

@Composable
fun HowItWorksScreen(
    onContinue: () -> Unit = {}
) {
    var showTitle by remember { mutableStateOf(false) }
    var showStep1 by remember { mutableStateOf(false) }
    var showStep2 by remember { mutableStateOf(false) }
    var showStep3 by remember { mutableStateOf(false) }
    var showButton by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100)
        showTitle = true
        delay(250)
        showStep1 = true
        delay(200)
        showStep2 = true
        delay(200)
        showStep3 = true
        delay(200)
        showButton = true
    }

    val gradientBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.1f),
            MaterialTheme.colorScheme.background
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBrush)
            .padding(horizontal = 24.dp)
            .semantics { contentDescription = "How Backroom works" },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(64.dp))

        // ── Title ──────────────────────────────────────────────
        AnimatedVisibility(
            visible = showTitle,
            enter = fadeIn(tween(400)) + slideInVertically(
                initialOffsetY = { 30 },
                animationSpec = tween(400, easing = FastOutSlowInEasing)
            )
        ) {
            Text(
                text = "How It Works",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        // ── Steps ──────────────────────────────────────────────
        AnimatedStep(
            visible = showStep1,
            stepNumber = "1",
            title = "Share what you need to say",
            subtitle = "Choose a topic, set the tone, write a few words"
        )

        StepConnector(visible = showStep2)

        AnimatedStep(
            visible = showStep2,
            stepNumber = "2",
            title = "Someone chooses to listen",
            subtitle = "They see your words first — no surprises"
        )

        StepConnector(visible = showStep3)

        AnimatedStep(
            visible = showStep3,
            stepNumber = "3",
            title = "Talk freely. Stay anonymous.",
            subtitle = "Voice-only, nothing saved, ever"
        )

        Spacer(modifier = Modifier.weight(1f))

        // ── Continue Button ────────────────────────────────────
        AnimatedVisibility(
            visible = showButton,
            enter = fadeIn(tween(300)) + slideInVertically(
                initialOffsetY = { 40 },
                animationSpec = tween(400, easing = FastOutSlowInEasing)
            )
        ) {
            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 2.dp,
                    pressedElevation = 0.dp
                )
            ) {
                Text(
                    text = "Continue",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        PageIndicator(totalPages = 5, currentPage = 1)

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun AnimatedStep(
    visible: Boolean,
    stepNumber: String,
    title: String,
    subtitle: String
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400)) + slideInVertically(
            initialOffsetY = { 30 },
            animationSpec = tween(400, easing = FastOutSlowInEasing)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Numbered circle
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stepNumber,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StepConnector(visible: Boolean) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        if (visible) alpha.animateTo(1f, tween(300))
    }
    Box(
        modifier = Modifier
            .padding(start = 42.dp) // Aligned with center of circle (24dp padding + 18dp)
            .width(2.dp)
            .height(20.dp)
            .alpha(alpha.value)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun HowItWorksScreenPreview() {
    BackroomTheme {
        HowItWorksScreen()
    }
}

@Preview(showBackground = true, showSystemUi = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun HowItWorksScreenDarkPreview() {
    BackroomTheme(darkTheme = true) {
        HowItWorksScreen()
    }
}

