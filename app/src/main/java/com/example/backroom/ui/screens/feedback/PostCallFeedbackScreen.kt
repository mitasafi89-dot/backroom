package com.example.backroom.ui.screens.feedback

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.SentimentDissatisfied
import androidx.compose.material.icons.outlined.SentimentNeutral
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.backroom.ui.theme.BackroomTheme

/**
 * Feedback sentiment options
 */
enum class FeedbackSentiment(
    val label: String,
    val icon: ImageVector,
    val color: Color
) {
    HELPED(
        label = "Helped",
        icon = Icons.Outlined.SentimentSatisfied,
        color = Color(0xFF4CAF50) // Green
    ),
    NEUTRAL(
        label = "Neutral",
        icon = Icons.Outlined.SentimentNeutral,
        color = Color(0xFFFFC107) // Amber
    ),
    UNCOMFORTABLE(
        label = "Uncomfortable",
        icon = Icons.Outlined.SentimentDissatisfied,
        color = Color(0xFFF44336) // Red
    )
}

/**
 * Post-Call Feedback Screen
 *
 * Shown after a call ends to collect:
 * - Sentiment feedback (Helped / Neutral / Uncomfortable)
 * - Optional anonymous thanks
 * - Report option
 */
@Composable
fun PostCallFeedbackScreen(
    callDurationSeconds: Int,
    onSendThanks: () -> Unit = {},
    onReportProblem: () -> Unit = {},
    onDone: (sentiment: FeedbackSentiment?) -> Unit = {}
) {
    var selectedSentiment by remember { mutableStateOf<FeedbackSentiment?>(null) }
    var thanksSent by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Call Ended",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = formatDuration(callDurationSeconds),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HorizontalDivider()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Sentiment Question
            Text(
                text = "How did that feel?",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Sentiment Options
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                FeedbackSentiment.entries.forEach { sentiment ->
                    SentimentButton(
                        sentiment = sentiment,
                        isSelected = selectedSentiment == sentiment,
                        onClick = { selectedSentiment = sentiment }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            HorizontalDivider()

            Spacer(modifier = Modifier.height(24.dp))

            // Send Anonymous Thanks
            OutlinedButton(
                onClick = {
                    thanksSent = true
                    onSendThanks()
                },
                enabled = !thanksSent,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (thanksSent) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Sent",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Thanks Sent!")
                } else {
                    Text("Send Anonymous Thanks")
                }
            }

            Text(
                text = "(optional)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Report a Problem
            TextButton(
                onClick = onReportProblem,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Report a Problem",
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Done Button
            Button(
                onClick = { onDone(selectedSentiment) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Done")
            }
        }
    }
}

@Composable
private fun SentimentButton(
    sentiment: FeedbackSentiment,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) sentiment.color else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .background(
                if (isSelected) sentiment.color.copy(alpha = 0.1f)
                else Color.Transparent
            )
            .padding(16.dp)
    ) {
        Icon(
            imageVector = sentiment.icon,
            contentDescription = sentiment.label,
            modifier = Modifier.size(40.dp),
            tint = if (isSelected) sentiment.color else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = sentiment.label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            ),
            color = if (isSelected) sentiment.color else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return "%d:%02d".format(minutes, secs)
}

// ============================================
// PREVIEWS
// ============================================

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun PostCallFeedbackScreenPreview() {
    BackroomTheme {
        PostCallFeedbackScreen(
            callDurationSeconds = 600 // 10 minutes
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PostCallFeedbackScreenDarkPreview() {
    BackroomTheme(darkTheme = true) {
        PostCallFeedbackScreen(
            callDurationSeconds = 432 // 7:12
        )
    }
}

