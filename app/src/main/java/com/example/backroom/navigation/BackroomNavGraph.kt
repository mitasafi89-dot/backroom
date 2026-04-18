package com.example.backroom.navigation

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.backroom.data.PreferencesManager
import com.example.backroom.data.network.CallManager
import com.example.backroom.ui.screens.call.CallInfo
import com.example.backroom.ui.screens.call.InCallScreen
import com.example.backroom.ui.screens.feedback.PostCallFeedbackScreen
import com.example.backroom.ui.screens.feedback.RecognitionCheckScreen
import com.example.backroom.ui.screens.feedback.ReportProblemScreen
import com.example.backroom.ui.screens.help.HelpResourcesScreen
import com.example.backroom.ui.screens.home.HomeScreen
import com.example.backroom.ui.screens.listener.SetBoundariesScreen
import com.example.backroom.ui.screens.onboarding.OnboardingScreen
import com.example.backroom.ui.screens.settings.BlockedUsersScreen
import com.example.backroom.ui.screens.settings.SettingsScreen
import com.example.backroom.ui.screens.settings.SubscriptionScreen
import com.example.backroom.ui.screens.sharer.SharerFlowScreen
import com.example.backroom.ui.screens.sharer.Topic
import com.example.backroom.ui.screens.splash.SplashScreen

private const val TAG = "BackroomNavGraph"

/**
 * Main Navigation Graph for Backroom app
 */
@Composable
fun BackroomNavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Splash.route
) {
    val context = LocalContext.current
    val preferencesManager = PreferencesManager(context)

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // ============================================
        // SPLASH & ONBOARDING
        // ============================================

        composable(Screen.Splash.route) {
            SplashScreen(
                onSplashComplete = {
                    val destination = if (preferencesManager.isOnboardingCompleted) {
                        Screen.Home.route
                    } else {
                        Screen.Onboarding.route
                    }
                    navController.navigate(destination) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onOnboardingComplete = {
                    preferencesManager.isOnboardingCompleted = true
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        // ============================================
        // HOME
        // ============================================

        composable(Screen.Home.route) {
            HomeScreen(
                onStartCall = {
                    navController.navigate(Screen.SharerTopic.route)
                },
                onSetBoundaries = {
                    navController.navigate(Screen.Boundaries.route)
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                },
                onHelpClick = {
                    navController.navigate(Screen.Help.route)
                },
                onCallStarted = { callId ->
                    Log.d(TAG, "═══════════════════════════════════════════════════")
                    Log.d(TAG, "🚀 HomeScreen.onCallStarted CALLBACK INVOKED")
                    Log.d(TAG, "   Thread: ${Thread.currentThread().name}")
                    Log.d(TAG, "   callId: $callId")
                    Log.d(TAG, "   Current route: ${navController.currentDestination?.route}")
                    Log.d(TAG, "   Navigating to: ${Screen.InCall.createRoute(callId)}")
                    Log.d(TAG, "═══════════════════════════════════════════════════")
                    try {
                        navController.navigate(Screen.InCall.createRoute(callId)) {
                            popUpTo(Screen.Home.route)
                        }
                        Log.d(TAG, "   ✓ Navigation call completed")
                        Log.d(TAG, "   New route: ${navController.currentDestination?.route}")
                    } catch (e: Exception) {
                        Log.e(TAG, "   ❌ NAVIGATION FAILED: ${e.message}", e)
                    }
                }
            )
        }

        // ============================================
        // SHARER FLOW
        // ============================================

        composable(Screen.SharerTopic.route) {
            SharerFlowScreen(
                onBack = { navController.popBackStack() },
                onCallStarted = { callId ->
                    Log.d(TAG, "🚀 SharerFlowScreen.onCallStarted called with callId=$callId - navigating to InCall")
                    navController.navigate(Screen.InCall.createRoute(callId)) {
                        popUpTo(Screen.Home.route)
                    }
                }
            )
        }

        // ============================================
        // LISTENER FLOW
        // ============================================

        composable(Screen.ListenerBoundaries.route) {
            SetBoundariesScreen(
                onBackClick = { navController.popBackStack() },
                onSave = { navController.popBackStack() }
            )
        }

        composable(Screen.Boundaries.route) {
            SetBoundariesScreen(
                onBackClick = { navController.popBackStack() },
                onSave = { navController.popBackStack() }
            )
        }

        // ============================================
        // IN-CALL
        // ============================================

        composable(
            route = Screen.InCall.route,
            arguments = listOf(navArgument("callId") { type = NavType.StringType })
        ) { backStackEntry ->
            val callId = backStackEntry.arguments?.getString("callId") ?: ""
            Log.d(TAG, "📞 InCallScreen composable entered with callId=$callId")

            // Get call info from CallManager
            val currentRole by CallManager.currentRole.collectAsState()
            val callDuration by CallManager.currentCallDuration.collectAsState()
            val callTopic by CallManager.currentCallTopic.collectAsState()
            val callIntent by CallManager.currentCallIntent.collectAsState()
            val matchInfo by CallManager.matchInfo.collectAsState()

            Log.d(TAG, "📞 InCallScreen: role=$currentRole, duration=$callDuration, topic=$callTopic")

            // Determine topic enum from string
            val topic = try {
                Topic.valueOf(callTopic?.uppercase()?.replace(" ", "_") ?: "JUST_TALKING")
            } catch (e: Exception) {
                Topic.JUST_TALKING
            }

            InCallScreen(
                callInfo = CallInfo(
                    callId = callId,
                    participantId = matchInfo?.callId?.takeLast(4) ?: "0000",
                    topic = topic,
                    intent = callIntent ?: "Just need someone to talk to",
                    durationMinutes = callDuration,
                    isListener = currentRole == "listener"
                ),
                onEndCall = { elapsedSeconds ->
                    navController.navigate(Screen.PostCallFeedback.createRoute(callId, elapsedSeconds)) {
                        popUpTo(Screen.Home.route)
                    }
                },
                onEmergencyExit = { elapsedSeconds ->
                    // CRITICAL: End the call first before navigating
                    CallManager.endCall()
                    navController.navigate(Screen.ReportProblem.createRoute(callId)) {
                        popUpTo(Screen.Home.route)
                    }
                }
            )
        }

        // ============================================
        // POST-CALL FEEDBACK
        // ============================================

        composable(
            route = Screen.PostCallFeedback.route,
            arguments = listOf(
                navArgument("callId") { type = NavType.StringType },
                navArgument("durationSeconds") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val callId = backStackEntry.arguments?.getString("callId") ?: ""
            val durationSeconds = backStackEntry.arguments?.getInt("durationSeconds") ?: 0

            PostCallFeedbackScreen(
                callDurationSeconds = durationSeconds,
                onSendThanks = {
                    // Send thanks via API
                },
                onReportProblem = {
                    navController.navigate(Screen.ReportProblem.createRoute(callId))
                },
                onDone = { sentiment ->
                    // Optionally show recognition check
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.RecognitionCheck.route) {
            RecognitionCheckScreen(
                onResponse = { response ->
                    // Log response for analytics
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.ReportProblem.route,
            arguments = listOf(navArgument("callId") { type = NavType.StringType })
        ) { backStackEntry ->
            val callId = backStackEntry.arguments?.getString("callId") ?: ""

            ReportProblemScreen(
                onBack = {
                    // Navigate to home since the call has already ended
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onSubmit = { reportData ->
                    // Submit report via WebSocket signaling
                    CallManager.submitReport(
                        callId = callId,
                        reason = reportData.reason.name,
                        details = reportData.details,
                        blockUser = reportData.blockUser
                    )

                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }

        // ============================================
        // SETTINGS
        // ============================================

        composable(Screen.Settings.route) {
            SettingsScreen(
                preferencesManager = preferencesManager,
                onBack = { navController.popBackStack() },
                onNavigateToBoundaries = {
                    navController.navigate(Screen.Boundaries.route)
                },
                onNavigateToBlockedUsers = {
                    navController.navigate(Screen.BlockedUsers.route)
                },
                onNavigateToSubscription = {
                    navController.navigate(Screen.Subscription.route)
                }
            )
        }

        composable(Screen.BlockedUsers.route) {
            BlockedUsersScreen(
                preferencesManager = preferencesManager,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Subscription.route) {
            SubscriptionScreen(
                preferencesManager = preferencesManager,
                onBack = { navController.popBackStack() },
                onSubscribe = { plan ->
                    // Initiate M-Pesa payment
                }
            )
        }

        // ============================================
        // HELP
        // ============================================

        composable(Screen.Help.route) {
            HelpResourcesScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}

