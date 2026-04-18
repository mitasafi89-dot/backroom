package com.example.backroom.ui.screens.call

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.backroom.data.network.CallManager
import com.example.backroom.data.webrtc.WebRTCState
import com.example.backroom.ui.screens.sharer.Topic
import com.example.backroom.ui.theme.BackroomTheme
import kotlinx.coroutines.delay

/**
 * Call information for the In-Call screen
 */
data class CallInfo(
    val callId: String = "",
    val participantId: String,
    val topic: Topic,
    val intent: String,
    val durationMinutes: Int,
    val isListener: Boolean
)

/**
 * Call state for tracking connection status
 */
enum class CallState {
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ENDING,
    ENDED
}

/**
 * In-Call Screen - displays during an active voice call
 *
 * Features:
 * - Countdown timer with wrapping up warning
 * - Voice wave animation (active when not muted and connected)
 * - Mute/Unmute toggle
 * - End call button (normal termination → feedback screen)
 * - Emergency exit (uncomfortable/unsafe → report screen)
 * - Anonymous status reminder
 *
 * Button Logic:
 * - END CALL (red phone icon): Normal end → Post-Call Feedback screen
 * - EMERGENCY EXIT (warning icon): Immediate end → Report screen (for unsafe situations)
 */
@Composable
fun InCallScreen(
    callInfo: CallInfo,
    onEndCall: (elapsedSeconds: Int) -> Unit = {},          // Normal end → Feedback
    onEmergencyExit: (elapsedSeconds: Int) -> Unit = {}     // Emergency → Report screen
) {
    var remainingSeconds by remember { mutableIntStateOf(callInfo.durationMinutes * 60) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var showExitDialog by remember { mutableStateOf(false) }

    // Observe WebRTC state from CallManager
    val webRTCState by CallManager.webRTCState.collectAsState()
    val isMuted by CallManager.isMuted.collectAsState()
    val isRemoteMuted by CallManager.isRemoteMuted.collectAsState()

    // Voice anonymization state
    val anonymizationLevel by CallManager.anonymizationLevel.collectAsState()
    val isAnonymizationEnabled by CallManager.isAnonymizationEnabled.collectAsState()

    // Map WebRTC state to call state
    val callState = when (webRTCState) {
        WebRTCState.IDLE, WebRTCState.CONNECTING -> CallState.CONNECTING
        WebRTCState.CONNECTED -> CallState.CONNECTED
        WebRTCState.DISCONNECTED -> CallState.RECONNECTING
        WebRTCState.FAILED -> CallState.ENDED
    }

    val isWrappingUp = remainingSeconds <= 30
    val isConnected = callState == CallState.CONNECTED

    // Observe call ID to detect when remote peer ends the call
    val currentCallId by CallManager.currentCallId.collectAsState()
    val currentRole by CallManager.currentRole.collectAsState()

    // Log initial state
    Log.d("InCallScreen", "═══════════════════════════════════════════════════")
    Log.d("InCallScreen", "🖥️ InCallScreen COMPOSING")
    Log.d("InCallScreen", "   callInfo.callId: ${callInfo.callId}")
    Log.d("InCallScreen", "   callInfo.isListener: ${callInfo.isListener}")
    Log.d("InCallScreen", "   callInfo.topic: ${callInfo.topic}")
    Log.d("InCallScreen", "   callInfo.durationMinutes: ${callInfo.durationMinutes}")
    Log.d("InCallScreen", "   webRTCState: $webRTCState")
    Log.d("InCallScreen", "   callState: $callState")
    Log.d("InCallScreen", "   currentCallId: $currentCallId")
    Log.d("InCallScreen", "   currentRole: $currentRole")
    Log.d("InCallScreen", "═══════════════════════════════════════════════════")

    // Track if we've already handled the call end to prevent multiple navigations
    var hasHandledCallEnd by remember { mutableStateOf(false) }

    // Track if we've seen a valid call ID (to prevent false trigger on initial composition)
    var hasSeenValidCallId by remember { mutableStateOf(currentCallId != null) }

    // Handle remote call end or connection failure
    LaunchedEffect(currentCallId) {
        Log.d("InCallScreen", "═══════════════════════════════════════════════════")
        Log.d("InCallScreen", "🔄 currentCallId LaunchedEffect TRIGGERED")
        Log.d("InCallScreen", "   currentCallId: $currentCallId")
        Log.d("InCallScreen", "   hasSeenValidCallId: $hasSeenValidCallId")
        Log.d("InCallScreen", "   hasHandledCallEnd: $hasHandledCallEnd")

        if (currentCallId != null) {
            hasSeenValidCallId = true
            Log.d("InCallScreen", "   ✓ Set hasSeenValidCallId = true")
        }
        // If call ID becomes null AFTER we've seen a valid one, and we haven't already handled it
        if (currentCallId == null && hasSeenValidCallId && !hasHandledCallEnd) {
            Log.d("InCallScreen", "   ⚠️ Call ended by remote peer! (callId became null)")
            hasHandledCallEnd = true
            Log.d("InCallScreen", "   Calling onEndCall(elapsedSeconds=$elapsedSeconds)")
            onEndCall(elapsedSeconds)
            Log.d("InCallScreen", "   ✓ onEndCall returned")
        }
        Log.d("InCallScreen", "═══════════════════════════════════════════════════")
    }

    // Also watch for call state changes
    LaunchedEffect(callState) {
        Log.d("InCallScreen", "🔄 callState LaunchedEffect: callState=$callState, hasSeenValidCallId=$hasSeenValidCallId, hasHandledCallEnd=$hasHandledCallEnd")
        if (callState == CallState.ENDED && hasSeenValidCallId && !hasHandledCallEnd) {
            Log.d("InCallScreen", "   ⚠️ Call connection failed/ended!")
            hasHandledCallEnd = true
            Log.d("InCallScreen", "   Calling onEndCall(elapsedSeconds=$elapsedSeconds)")
            onEndCall(elapsedSeconds)
        }
    }

    // Track if timer is already running to prevent duplicates
    var timerStarted by remember { mutableStateOf(false) }

    // Countdown timer - only start once when connected
    LaunchedEffect(callState) {
        if (callState == CallState.CONNECTED && !timerStarted) {
            timerStarted = true
            Log.d("InCallScreen", "⏱️ Starting call timer - connected!")
            while (remainingSeconds > 0 && !hasHandledCallEnd) {
                delay(1000)
                remainingSeconds--
                elapsedSeconds++
                if (elapsedSeconds % 30 == 0) {
                    Log.d("InCallScreen", "⏱️ Timer update: elapsed=${elapsedSeconds}s, remaining=${remainingSeconds}s")
                }
            }
            // Call ended due to timer - end the call (only if not already ended)
            if (!hasHandledCallEnd) {
                Log.d("InCallScreen", "⏱️ Timer expired - ending call")
                hasHandledCallEnd = true
                CallManager.endCall()
                onEndCall(elapsedSeconds)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            // Connection status indicator
            AnimatedVisibility(
                visible = callState == CallState.CONNECTING || callState == CallState.RECONNECTING,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                ConnectionStatusBanner(
                    isReconnecting = callState == CallState.RECONNECTING
                )
            }

            // Participant ID
            Text(
                text = if (callInfo.isListener) "Caller #${callInfo.participantId}"
                       else "Listener #${callInfo.participantId}",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Voice Wave Animation
            VoiceWaveAnimation(
                isMuted = isMuted,
                isConnected = isConnected
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Timer
            TimerDisplay(
                remainingSeconds = remainingSeconds,
                isWrappingUp = isWrappingUp,
                isConnected = isConnected
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Topic Info Card
            TopicInfoCard(
                topic = callInfo.topic,
                intent = callInfo.intent
            )

            // Show if remote party is muted
            if (isRemoteMuted) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Other party is muted",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Call Controls
            val currentlyMuted = isMuted
            CallControls(
                isMuted = currentlyMuted,
                onToggleMute = { CallManager.setMuted(!currentlyMuted) },
                onEndCall = {
                    CallManager.endCall()
                    onEndCall(elapsedSeconds)
                },
                onEmergencyExit = { showExitDialog = true }
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Anonymous reminder with voice control
            AnonymousReminderWithControl(
                anonymizationLevel = anonymizationLevel,
                isEnabled = isAnonymizationEnabled,
                onLevelChange = { CallManager.setAnonymizationLevel(it) }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Emergency Exit Dialog
        if (showExitDialog) {
            EmergencyExitDialog(
                onDismiss = { showExitDialog = false },
                onConfirm = {
                    showExitDialog = false
                    CallManager.endCall()
                    onEmergencyExit(elapsedSeconds)
                }
            )
        }
    }
}

@Composable
private fun ConnectionStatusBanner(isReconnecting: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (isReconnecting) "Reconnecting..." else "Connecting...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun VoiceWaveAnimation(
    isMuted: Boolean,
    isConnected: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")

    val wave1 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave1"
    )

    val wave2 by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave2"
    )

    val wave3 by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave3"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(80.dp)
    ) {
        val shouldAnimate = isConnected && !isMuted
        val heights = if (shouldAnimate) {
            listOf(wave1, wave2, wave3, wave2, wave1)
        } else {
            listOf(0.2f, 0.2f, 0.2f, 0.2f, 0.2f)
        }

        heights.forEach { height ->
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .height((80 * height).dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        when {
                            isMuted -> MaterialTheme.colorScheme.outlineVariant
                            !isConnected -> MaterialTheme.colorScheme.outline
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
            )
        }
    }
}

@Composable
private fun TimerDisplay(
    remainingSeconds: Int,
    isWrappingUp: Boolean,
    isConnected: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = formatTime(remainingSeconds),
            style = MaterialTheme.typography.displayMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 48.sp
            ),
            color = when {
                isWrappingUp -> MaterialTheme.colorScheme.error
                !isConnected -> MaterialTheme.colorScheme.outline
                else -> MaterialTheme.colorScheme.onBackground
            }
        )

        Text(
            text = when {
                isWrappingUp -> "⚠️ Wrapping up"
                !isConnected -> "connecting..."
                else -> "remaining"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = when {
                isWrappingUp -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun TopicInfoCard(
    topic: Topic,
    intent: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Topic: ${topic.title}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "\"$intent\"",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CallControls(
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    onEndCall: () -> Unit,
    onEmergencyExit: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Mute Button
        CallControlButton(
            icon = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
            label = if (isMuted) "Unmute" else "Mute",
            onClick = onToggleMute,
            isActive = isMuted
        )

        // End Call Button
        IconButton(
            onClick = onEndCall,
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(
                imageVector = Icons.Filled.CallEnd,
                contentDescription = "End Call",
                modifier = Modifier.size(32.dp),
                tint = Color.White
            )
        }

        // Emergency Exit Button - for reporting unsafe/uncomfortable situations
        CallControlButton(
            icon = Icons.Outlined.Warning,
            label = "Report",
            onClick = onEmergencyExit,
            isActive = false,
            tint = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun CallControlButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    isActive: Boolean,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(
                    if (isActive) MaterialTheme.colorScheme.surfaceVariant
                    else Color.Transparent
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape
                )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(24.dp),
                tint = if (isActive) MaterialTheme.colorScheme.primary else tint
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (isActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AnonymousReminderWithControl(
    anonymizationLevel: Float,
    isEnabled: Boolean,
    onLevelChange: (Float) -> Unit
) {
    // Local state for smooth slider movement
    var localLevel by remember { mutableStateOf(anonymizationLevel) }

    // Sync local state with external state when it changes
    LaunchedEffect(anonymizationLevel) {
        localLevel = anonymizationLevel
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Status text
        Text(
            text = if (isEnabled) "🔒 Voice anonymized at ${localLevel.toInt()}%"
                   else "⚠️ Voice anonymization disabled",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium
            ),
            color = if (isEnabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Anonymization level slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Voice Mask",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.width(12.dp))

            Slider(
                value = localLevel,
                onValueChange = { newValue ->
                    // Update local state for smooth visual feedback
                    localLevel = newValue
                },
                onValueChangeFinished = {
                    // Only update the actual anonymization level when user stops dragging
                    onLevelChange(localLevel)
                },
                valueRange = 0f..100f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "${localLevel.toInt()}%",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(40.dp)
            )
        }

        Text(
            text = "Your identity is protected",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AnonymousReminder() {
    Text(
        text = "You're anonymous.\nYour voice is modified.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun EmergencyExitDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Feeling uncomfortable?",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold
                )
            )
        },
        text = {
            Text(
                text = "This will end the call immediately and take you to report this conversation if needed. Use this if you feel unsafe or the other person is being inappropriate.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("End & Report")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Continue Call")
            }
        }
    )
}

private fun formatTime(seconds: Int): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return "%02d:%02d".format(minutes, secs)
}

// ============================================
// PREVIEWS
// ============================================

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun InCallScreenPreview() {
    BackroomTheme {
        InCallScreen(
            callInfo = CallInfo(
                callId = "test-call-123",
                participantId = "4829",
                topic = Topic.GRIEF,
                intent = "Lost someone close, need to talk through the pain",
                durationMinutes = 10,
                isListener = false
            )
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun InCallScreenDarkPreview() {
    BackroomTheme(darkTheme = true) {
        InCallScreen(
            callInfo = CallInfo(
                callId = "test-call-123",
                participantId = "4829",
                topic = Topic.GRIEF,
                intent = "Lost someone close, need to talk through the pain",
                durationMinutes = 10,
                isListener = false
            )
        )
    }
}

@Preview(showBackground = true, name = "Listener View")
@Composable
fun InCallScreenListenerPreview() {
    BackroomTheme {
        InCallScreen(
            callInfo = CallInfo(
                callId = "test-call-456",
                participantId = "7392",
                topic = Topic.CONFESSION,
                intent = "Something I've never told anyone",
                durationMinutes = 15,
                isListener = true
            )
        )
    }
}

@Preview(showBackground = true, name = "Wrapping Up State")
@Composable
fun InCallScreenWrappingUpPreview() {
    BackroomTheme {
        // Shows the wrapping up state (last 30 seconds)
        InCallScreen(
            callInfo = CallInfo(
                callId = "test-call-789",
                participantId = "1234",
                topic = Topic.LETTING_OUT,
                intent = "Need to vent about work stress",
                durationMinutes = 1, // 1 minute = starts in wrapping up zone quickly
                isListener = false
            )
        )
    }
}

