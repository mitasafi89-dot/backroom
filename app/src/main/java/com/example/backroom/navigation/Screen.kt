package com.example.backroom.navigation

/**
 * Navigation destinations for Backroom app
 */
sealed class Screen(val route: String) {
    // Splash & Onboarding
    object Splash : Screen("splash")
    object Onboarding : Screen("onboarding")

    // Main
    object Home : Screen("home")

    // Sharer Flow
    object SharerTopic : Screen("sharer/topic")
    object SharerTone : Screen("sharer/tone")
    object SharerDuration : Screen("sharer/duration")
    object SharerPreview : Screen("sharer/preview")
    object SharerMatching : Screen("sharer/matching")

    // Listener Flow
    object ListenerBoundaries : Screen("listener/boundaries")
    object ListenerAvailable : Screen("listener/available")
    object ListenerPreview : Screen("listener/preview/{shareId}") {
        fun createRoute(shareId: String) = "listener/preview/$shareId"
    }

    // Call
    object InCall : Screen("call/{callId}") {
        fun createRoute(callId: String) = "call/$callId"
    }

    // Post-Call
    object PostCallFeedback : Screen("feedback/{callId}/{durationSeconds}") {
        fun createRoute(callId: String, durationSeconds: Int) = "feedback/$callId/$durationSeconds"
    }
    object RecognitionCheck : Screen("feedback/recognition")
    object ReportProblem : Screen("report/{callId}") {
        fun createRoute(callId: String) = "report/$callId"
    }

    // Settings
    object Settings : Screen("settings")
    object BlockedUsers : Screen("settings/blocked")
    object Subscription : Screen("settings/subscription")
    object Boundaries : Screen("settings/boundaries")

    // Help
    object Help : Screen("help")
}

