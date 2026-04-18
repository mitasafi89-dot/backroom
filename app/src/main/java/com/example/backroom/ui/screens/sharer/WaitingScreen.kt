package com.example.backroom.ui.screens.sharer

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.backroom.data.network.CallManager
import com.example.backroom.data.network.ConnectionState
import com.example.backroom.ui.theme.BackroomTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaitingScreen(
    onCancel: () -> Unit = {},
    onMatchFound: (callId: String) -> Unit = {},
    onShareExpired: (reason: String) -> Unit = {}  // Called when share expires
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Observe connection state and match info
    val connectionState by CallManager.signalingClient.connectionState.collectAsState()
    val pendingShareId by CallManager.pendingShareId.collectAsState()
    val matchInfo by CallManager.matchInfo.collectAsState()
    val matchConsumed by CallManager.matchConsumed.collectAsState()
    val currentRole by CallManager.currentRole.collectAsState()
    val shareExpiredReason by CallManager.shareExpiredReason.collectAsState()

    // Handle share expiration
    LaunchedEffect(shareExpiredReason) {
        if (shareExpiredReason != null) {
            android.util.Log.d("WaitingScreen", "⏰ Share expired with reason: $shareExpiredReason")
            onShareExpired(shareExpiredReason!!)
        }
    }

    // When match is found, call onMatchFound (only for sharer role, not already consumed)
    LaunchedEffect(matchInfo, matchConsumed, currentRole) {
        android.util.Log.d("WaitingScreen", "═══════════════════════════════════════════════════")
        android.util.Log.d("WaitingScreen", "🔄 NAVIGATION LaunchedEffect TRIGGERED")
        android.util.Log.d("WaitingScreen", "   Thread: ${Thread.currentThread().name}")
        android.util.Log.d("WaitingScreen", "   matchInfo: ${matchInfo?.callId ?: "null"}")
        android.util.Log.d("WaitingScreen", "   matchConsumed: $matchConsumed")
        android.util.Log.d("WaitingScreen", "   currentRole: '$currentRole'")
        android.util.Log.d("WaitingScreen", "   pendingShareId: $pendingShareId")
        android.util.Log.d("WaitingScreen", "   connectionState: $connectionState")
        android.util.Log.d("WaitingScreen", "═══════════════════════════════════════════════════")

        matchInfo?.let { match ->
            android.util.Log.d("WaitingScreen", "📋 Match exists!")
            android.util.Log.d("WaitingScreen", "   callId: ${match.callId}")
            android.util.Log.d("WaitingScreen", "   match.role: ${match.role}")

            // Only handle if we're the sharer and match is not consumed
            val shouldNavigate = !matchConsumed && currentRole == "sharer"
            android.util.Log.d("WaitingScreen", "   Should navigate? $shouldNavigate")
            android.util.Log.d("WaitingScreen", "     - !matchConsumed = ${!matchConsumed}")
            android.util.Log.d("WaitingScreen", "     - currentRole == 'sharer' = ${currentRole == "sharer"}")

            if (shouldNavigate) {
                android.util.Log.d("WaitingScreen", "✅ CONDITIONS MET! Trying to consume match...")
                // Use atomic tryConsumeMatch to prevent race conditions
                if (CallManager.tryConsumeMatch()) {
                    android.util.Log.d("WaitingScreen", "   ✓ Match consumed by WaitingScreen")
                    android.util.Log.d("WaitingScreen", "   ✓ Calling onMatchFound(${match.callId})")
                    onMatchFound(match.callId)
                    android.util.Log.d("WaitingScreen", "   ✓ onMatchFound returned")
                } else {
                    android.util.Log.d("WaitingScreen", "   ⚠️ Match already consumed by another component")
                }
            } else {
                android.util.Log.d("WaitingScreen", "❌ CONDITIONS NOT MET - skipping navigation")
            }
        } ?: run {
            android.util.Log.d("WaitingScreen", "   matchInfo is null - nothing to do")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top App Bar
        TopAppBar(
            title = {
                Text(
                    text = "Finding...",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(0.3f))

            // Connection status
            Text(
                text = when (connectionState) {
                    ConnectionState.CONNECTED -> if (pendingShareId != null) "Looking for a listener..." else "Submitting..."
                    ConnectionState.CONNECTING -> "Connecting to server..."
                    ConnectionState.RECONNECTING -> "Reconnecting..."
                    ConnectionState.DISCONNECTED -> "Disconnected - Please check your connection"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Animated Pulse
            Box(
                modifier = Modifier
                    .size(120.dp),
                contentAlignment = Alignment.Center
            ) {
                // Outer pulsing circle
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(scale)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                )
                // Inner circle
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Main text
            Text(
                text = "Finding someone for you...",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "This usually takes less than a minute.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.weight(0.3f))

            // Divider
            HorizontalDivider()

            Spacer(modifier = Modifier.height(24.dp))

            // Tip text (no icon or prefix)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Take a breath. You're about to be heard.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.weight(0.3f))

            // Cancel Button
            TextButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Cancel",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun WaitingScreenPreview() {
    BackroomTheme {
        WaitingScreen()
    }
}

@Preview(showBackground = true, showSystemUi = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun WaitingScreenDarkPreview() {
    BackroomTheme(darkTheme = true) {
        WaitingScreen()
    }
}

