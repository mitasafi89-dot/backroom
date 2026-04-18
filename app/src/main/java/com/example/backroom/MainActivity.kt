package com.example.backroom

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController
import com.example.backroom.data.PreferencesManager
import com.example.backroom.data.network.CallManager
import com.example.backroom.navigation.BackroomNavGraph
import com.example.backroom.navigation.Screen
import com.example.backroom.service.ListenerService
import com.example.backroom.ui.screens.home.HomeScreen
import com.example.backroom.ui.theme.BackroomTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {

    companion object {
        // State flow to communicate pending call navigation to composables
        private val _pendingCallNavigation = MutableStateFlow<String?>(null)
        val pendingCallNavigation: StateFlow<String?> = _pendingCallNavigation.asStateFlow()

        fun clearPendingNavigation() {
            _pendingCallNavigation.value = null
        }
    }

    private lateinit var preferencesManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "🚀 onCreate called - savedInstanceState: ${savedInstanceState != null}")

        // Initialize preferences
        preferencesManager = PreferencesManager(this)
        Log.d(TAG, "📋 PreferencesManager initialized, onboardingCompleted: ${preferencesManager.isOnboardingCompleted}")

        // Initialize WebRTC early (must be done once per process)
        try {
            com.example.backroom.data.webrtc.WebRTCManager.initializeOnce(this)
            Log.d(TAG, "✅ WebRTC initialized")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize WebRTC: ${e.message}", e)
        }

        // Initialize CallManager and connect to signaling server
        try {
            CallManager.initialize(this)
            Log.d(TAG, "✅ CallManager initialized")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize CallManager: ${e.message}", e)
        }

        // Start listener service if listener mode is enabled (default: true)
        if (preferencesManager.isListenerModeEnabled) {
            ListenerService.startListening(this)
            Log.d(TAG, "✅ ListenerService started")
        }

        // Handle intent extras for navigation
        handleIntent(intent)

        enableEdgeToEdge()
        setContent {
            BackroomTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BackroomApp()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "📥 onNewIntent called with action: ${intent.action}")
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        Log.d(TAG, "═══════════════════════════════════════════════════")
        Log.d(TAG, "🎯 handleIntent CALLED")
        Log.d(TAG, "   intent: ${intent != null}")

        if (intent == null) {
            Log.d(TAG, "   ⚠️ Intent is null - returning")
            Log.d(TAG, "═══════════════════════════════════════════════════")
            return
        }

        Log.d(TAG, "   action: ${intent.action}")
        Log.d(TAG, "   flags: ${intent.flags}")
        Log.d(TAG, "   extras: ${intent.extras?.keySet()?.joinToString()}")

        val callId = intent.getStringExtra("callId")
        val navigateToCall = intent.getBooleanExtra("navigateToCall", false)
        val incomingPreview = intent.getBooleanExtra("incomingPreview", false)

        Log.d(TAG, "   callId: $callId")
        Log.d(TAG, "   navigateToCall: $navigateToCall")
        Log.d(TAG, "   incomingPreview: $incomingPreview")

        if (navigateToCall && !callId.isNullOrEmpty()) {
            Log.d(TAG, "   ✅ Setting pending call navigation to: $callId")
            _pendingCallNavigation.value = callId
            Log.d(TAG, "   ✓ _pendingCallNavigation set")
        } else {
            Log.d(TAG, "   ❌ Not setting pending navigation - navigateToCall=$navigateToCall, callId=$callId")
        }
        Log.d(TAG, "═══════════════════════════════════════════════════")
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "📱 onStart called")
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "📱 onResume called")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "📱 onPause called")
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "📱 onStop called")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "📱 onDestroy called")
        // Note: Don't disconnect or stop service here - let it run in background
        // Only stop if user explicitly turns off listener mode
    }
}

@Composable
fun BackroomApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val preferencesManager = androidx.compose.runtime.remember {
        com.example.backroom.data.PreferencesManager(context)
    }

    // Determine start destination based on onboarding status
    // Skip splash if onboarding is complete to avoid unnecessary delay
    val startDestination = if (preferencesManager.isOnboardingCompleted) {
        com.example.backroom.navigation.Screen.Home.route
    } else {
        com.example.backroom.navigation.Screen.Splash.route
    }

    Log.d(TAG, "🎨 BackroomApp composing - startDestination=$startDestination")

    val navController = rememberNavController()

    // Observe pending call navigation from MainActivity
    val pendingCallId by MainActivity.pendingCallNavigation.collectAsState()
    val matchConsumed by CallManager.matchConsumed.collectAsState()
    val currentRole by CallManager.currentRole.collectAsState()
    val currentCallId by CallManager.currentCallId.collectAsState()

    // Handle pending call navigation (from ListenerService when app is in background)
    // This should only trigger if the match hasn't been consumed by HomeScreen already
    LaunchedEffect(pendingCallId, matchConsumed) {
        Log.d(TAG, "═══════════════════════════════════════════════════")
        Log.d(TAG, "🔄 BackroomApp NAVIGATION LaunchedEffect TRIGGERED")
        Log.d(TAG, "   pendingCallId: $pendingCallId")
        Log.d(TAG, "   matchConsumed: $matchConsumed")
        Log.d(TAG, "   currentRole: $currentRole")
        Log.d(TAG, "   currentCallId: $currentCallId")

        val callId = pendingCallId
        if (!callId.isNullOrEmpty()) {
            Log.d(TAG, "   ✅ pendingCallId is not empty: $callId")

            // Clear the pending navigation first to prevent re-triggering
            Log.d(TAG, "   Clearing pending navigation...")
            MainActivity.clearPendingNavigation()
            Log.d(TAG, "   ✓ Pending navigation cleared")

            // Use atomic tryConsumeMatch to prevent race condition with HomeScreen
            Log.d(TAG, "   Attempting to consume match...")
            if (CallManager.tryConsumeMatch()) {
                Log.d(TAG, "   ✓ Match consumed by BackroomApp")
                Log.d(TAG, "   🚀 Navigating to InCallScreen for call: $callId")
                navController.navigate(Screen.InCall.createRoute(callId)) {
                    popUpTo(Screen.Home.route) { inclusive = false }
                }
                Log.d(TAG, "   ✓ Navigation completed")
            } else {
                Log.d(TAG, "   ⚠️ Match already consumed by another component")
            }
        } else {
            Log.d(TAG, "   ❌ pendingCallId is null or empty - nothing to do")
        }
        Log.d(TAG, "═══════════════════════════════════════════════════")
    }

    // Log when BackroomApp is composed/recomposed
    LaunchedEffect(Unit) {
        Log.d(TAG, "🎯 BackroomApp LaunchedEffect(Unit) - navController created, startDestination=$startDestination")
    }

    DisposableEffect(Unit) {
        Log.d(TAG, "🎯 BackroomApp DisposableEffect - entering composition")
        onDispose {
            Log.d(TAG, "🎯 BackroomApp DisposableEffect - leaving composition")
        }
    }

    BackroomNavGraph(navController = navController, startDestination = startDestination)
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    BackroomTheme {
        HomeScreen()
    }
}

