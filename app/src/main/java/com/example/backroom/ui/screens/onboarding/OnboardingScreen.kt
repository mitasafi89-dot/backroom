package com.example.backroom.ui.screens.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Onboarding Screen
 *
 * Encapsulates all onboarding steps with animated transitions:
 * 1. Welcome
 * 2. How It Works
 * 3. Safety
 * 4. Microphone Permission
 * 5. Notification Permission
 *
 * Transitions: Slide left + fade (300ms ease-out) between steps.
 */
@Composable
fun OnboardingScreen(
    onOnboardingComplete: () -> Unit
) {
    var currentStep by remember { mutableStateOf(OnboardingStep.WELCOME) }

    AnimatedContent(
        targetState = currentStep,
        transitionSpec = {
            val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
            (slideInHorizontally(
                initialOffsetX = { fullWidth -> direction * fullWidth / 4 },
                animationSpec = tween(300)
            ) + fadeIn(tween(300)))
                .togetherWith(
                    slideOutHorizontally(
                        targetOffsetX = { fullWidth -> -direction * fullWidth / 4 },
                        animationSpec = tween(300)
                    ) + fadeOut(tween(200))
                )
        },
        label = "onboarding_transition"
    ) { step ->
        when (step) {
            OnboardingStep.WELCOME -> {
                WelcomeScreen(
                    onGetStarted = { currentStep = OnboardingStep.HOW_IT_WORKS }
                )
            }

            OnboardingStep.HOW_IT_WORKS -> {
                HowItWorksScreen(
                    onContinue = { currentStep = OnboardingStep.SAFETY }
                )
            }

            OnboardingStep.SAFETY -> {
                SafetyScreen(
                    onContinue = { currentStep = OnboardingStep.PERMISSION_MIC }
                )
            }

            OnboardingStep.PERMISSION_MIC -> {
                MicrophonePermissionScreen(
                    onPermissionGranted = { currentStep = OnboardingStep.PERMISSION_NOTIFICATION },
                    onPermissionDenied = { currentStep = OnboardingStep.PERMISSION_NOTIFICATION }
                )
            }

            OnboardingStep.PERMISSION_NOTIFICATION -> {
                NotificationPermissionScreen(
                    onContinue = { onOnboardingComplete() }
                )
            }
        }
    }
}

/**
 * Steps in the onboarding flow
 */
private enum class OnboardingStep {
    WELCOME,
    HOW_IT_WORKS,
    SAFETY,
    PERMISSION_MIC,
    PERMISSION_NOTIFICATION
}
