package com.example.backroom.ui.screens.sharer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.backroom.data.network.CallManager

/**
 * Sharer Flow Screen
 *
 * Encapsulates the entire sharer flow:
 * 1. Select Topic
 * 2. Select Tone
 * 3. Write Intent
 * 4. Select Duration
 * 5. Review
 * 6. Waiting for match
 */
@Composable
fun SharerFlowScreen(
    onBack: () -> Unit,
    onCallStarted: (callId: String) -> Unit
) {
    var currentStep by remember { mutableStateOf(SharerStep.TOPIC) }

    // Flow state
    var selectedTopic by remember { mutableStateOf<Topic?>(null) }
    var selectedTone by remember { mutableStateOf<Tone?>(null) }
    var intentText by remember { mutableStateOf("") }
    var selectedDuration by remember { mutableStateOf<Duration?>(null) }

    // NOTE: Match handling is done in WaitingScreen when currentStep == WAITING
    // We don't observe matchInfo here to avoid duplicate navigation

    when (currentStep) {
        SharerStep.TOPIC -> {
            SelectTopicScreen(
                onBackClick = onBack,
                onTopicSelected = { topic ->
                    selectedTopic = topic
                    currentStep = SharerStep.TONE
                }
            )
        }

        SharerStep.TONE -> {
            SelectToneScreen(
                topic = selectedTopic ?: Topic.JUST_TALKING,
                onBackClick = { currentStep = SharerStep.TOPIC },
                onContinue = { tone ->
                    selectedTone = tone
                    currentStep = SharerStep.INTENT
                }
            )
        }

        SharerStep.INTENT -> {
            WriteIntentScreen(
                topic = selectedTopic ?: Topic.JUST_TALKING,
                tone = selectedTone ?: Tone.LIGHT,
                onBackClick = { currentStep = SharerStep.TONE },
                onContinue = { intent ->
                    intentText = intent
                    currentStep = SharerStep.DURATION
                }
            )
        }

        SharerStep.DURATION -> {
            SelectDurationScreen(
                onBackClick = { currentStep = SharerStep.INTENT },
                onContinue = { duration ->
                    selectedDuration = duration
                    currentStep = SharerStep.REVIEW
                }
            )
        }

        SharerStep.REVIEW -> {
            ReviewScreen(
                callRequest = CallRequest(
                    topic = selectedTopic ?: Topic.JUST_TALKING,
                    tone = selectedTone ?: Tone.LIGHT,
                    intent = intentText,
                    duration = selectedDuration ?: Duration.ENOUGH
                ),
                onBackClick = { currentStep = SharerStep.DURATION },
                onEditClick = { currentStep = SharerStep.TOPIC },
                onFindListener = {
                    // Submit share request to server
                    CallManager.submitShare(
                        topic = selectedTopic?.title ?: "Just Talking",
                        tone = selectedTone?.title ?: "Light",
                        intent = intentText,
                        duration = selectedDuration?.minutes ?: 10
                    )
                    currentStep = SharerStep.WAITING
                }
            )
        }

        SharerStep.WAITING -> {
            WaitingScreen(
                onCancel = {
                    CallManager.cancelShare()
                    onBack()
                },
                onMatchFound = { callId ->
                    onCallStarted(callId)
                },
                onShareExpired = { reason ->
                    // Navigate back when share expires
                    // TODO: Show a snackbar or dialog with appropriate message
                    android.util.Log.d("SharerFlowScreen", "Share expired with reason: $reason")
                    onBack()
                }
            )
        }
    }
}

/**
 * Steps in the sharer flow
 */
private enum class SharerStep {
    TOPIC,
    TONE,
    INTENT,
    DURATION,
    REVIEW,
    WAITING
}

