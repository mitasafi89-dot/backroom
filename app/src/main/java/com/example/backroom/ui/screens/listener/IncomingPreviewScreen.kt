package com.example.backroom.ui.screens.listener

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.backroom.ui.screens.sharer.Tone
import com.example.backroom.ui.screens.sharer.Topic
import com.example.backroom.ui.theme.BackroomTheme
import kotlinx.coroutines.delay

data class IncomingCallPreview(
    val topic: Topic,
    val tone: Tone,
    val durationMinutes: Int,
    val intent: String
)

private const val PREVIEW_TIMEOUT_SECONDS = 30

@Composable
fun IncomingPreviewScreen(
    preview: IncomingCallPreview,
    onAccept: () -> Unit = {},
    onSkip: () -> Unit = {},
    onPauseAvailability: () -> Unit = {}
) {
    var remainingSeconds by remember { mutableIntStateOf(PREVIEW_TIMEOUT_SECONDS) }

    // Countdown timer
    LaunchedEffect(Unit) {
        while (remainingSeconds > 0) {
            delay(1000)
            remainingSeconds--
        }
        // Auto-skip when timer expires
        onSkip()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        // Header
        Text(
            text = "Someone wants to talk",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Preview Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(20.dp)
        ) {
            // Left accent bar + content
            Row {
                // Accent bar
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(180.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    // Topic
                    PreviewItem(label = "Topic", value = preview.topic.title)

                    Spacer(modifier = Modifier.height(12.dp))

                    // Tone with dots
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tone: ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        ToneIndicator(tone = preview.tone)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = preview.tone.title,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Duration
                    PreviewItem(label = "Time", value = "~${preview.durationMinutes} minutes")

                    Spacer(modifier = Modifier.height(16.dp))

                    // Intent
                    Text(
                        text = "Their words:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "\"${preview.intent}\"",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Timer
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LinearProgressIndicator(
                progress = { remainingSeconds.toFloat() / PREVIEW_TIMEOUT_SECONDS },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "$remainingSeconds seconds",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = onSkip,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
            ) {
                Text(
                    text = "Skip",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Button(
                onClick = onAccept,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
            ) {
                Text(
                    text = "Accept",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Pause Availability
        TextButton(
            onClick = onPauseAvailability,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Pause My Availability",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun PreviewItem(
    label: String,
    value: String
) {
    Row {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun ToneIndicator(tone: Tone) {
    val filledDots = when (tone) {
        Tone.LIGHT -> 1
        Tone.HEAVY -> 3
        Tone.VERY_HEAVY -> 5
    }

    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(5) { index ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (index < filledDots) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun IncomingPreviewScreenPreview() {
    BackroomTheme {
        IncomingPreviewScreen(
            preview = IncomingCallPreview(
                topic = Topic.GRIEF,
                tone = Tone.HEAVY,
                durationMinutes = 10,
                intent = "Lost someone close, need to talk through the pain"
            )
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun IncomingPreviewScreenDarkPreview() {
    BackroomTheme(darkTheme = true) {
        IncomingPreviewScreen(
            preview = IncomingCallPreview(
                topic = Topic.GRIEF,
                tone = Tone.HEAVY,
                durationMinutes = 10,
                intent = "Lost someone close, need to talk through the pain"
            )
        )
    }
}

