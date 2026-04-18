package com.example.backroom.data.network

import android.content.Context
import android.util.Log
import com.example.backroom.data.webrtc.WebRTCManager
import com.example.backroom.data.webrtc.WebRTCState
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import java.util.UUID

/**
 * Call Manager - Singleton that manages the signaling client and call state
 * across the entire app lifecycle.
 */
object CallManager {

    private const val TAG = "CallManager"

    // Exception handler to catch and log any uncaught exceptions in coroutines
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "❌ Uncaught exception in CallManager coroutine: ${throwable.message}", throwable)
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob() + exceptionHandler)

    private var _signalingClient: SignalingClient? = null
    private val signalingClientLock = Any()
    val signalingClient: SignalingClient
        get() {
            synchronized(signalingClientLock) {
                if (_signalingClient == null) {
                    _signalingClient = SignalingClient()
                }
                return _signalingClient!!
            }
        }

    // WebRTC Manager
    private var _webRTCManager: WebRTCManager? = null
    val webRTCManager: WebRTCManager?
        get() = _webRTCManager

    // Track if WebRTC initialization is in progress to prevent duplicates
    private var isWebRTCInitializing = false
    private var lastInitializedCallId: String? = null

    // Job to track WebRTC-related coroutines so they can be cancelled when call ends
    private var webRTCJob: kotlinx.coroutines.Job? = null

    // Track if auto-registration has happened to prevent duplicates
    private var hasAutoRegistered = false

    // Track if FCM token has been registered to prevent duplicates
    private var hasFcmTokenRegistered = false

    // Track if CallManager has been initialized to prevent duplicate coroutine launches
    private var isInitialized = false

    // Application context
    private var appContext: Context? = null

    // User ID (anonymous, device-based) - set by initialize()
    private var _userId: String? = null
    val userId: String
        get() = _userId ?: throw IllegalStateException("CallManager.userId accessed before initialize() was called")

    // Current role
    private val _currentRole = MutableStateFlow<String?>(null)
    val currentRole: StateFlow<String?> = _currentRole.asStateFlow()

    // Listener availability - default TRUE (everyone can be a listener)
    private val _isListenerAvailable = MutableStateFlow(true)
    val isListenerAvailable: StateFlow<Boolean> = _isListenerAvailable.asStateFlow()

    // Current call state
    private val _currentCallId = MutableStateFlow<String?>(null)
    val currentCallId: StateFlow<String?> = _currentCallId.asStateFlow()

    // Pending share ID (for sharer waiting for match)
    private val _pendingShareId = MutableStateFlow<String?>(null)
    val pendingShareId: StateFlow<String?> = _pendingShareId.asStateFlow()

    // Share expired reason (set when shareExpired is received, cleared on new share)
    private val _shareExpiredReason = MutableStateFlow<String?>(null)
    val shareExpiredReason: StateFlow<String?> = _shareExpiredReason.asStateFlow()

    // Current call duration (minutes) - set when share is submitted or preview accepted
    private val _currentCallDuration = MutableStateFlow(10)
    val currentCallDuration: StateFlow<Int> = _currentCallDuration.asStateFlow()

    // Current call topic
    private val _currentCallTopic = MutableStateFlow<String?>(null)
    val currentCallTopic: StateFlow<String?> = _currentCallTopic.asStateFlow()

    // Current call intent
    private val _currentCallIntent = MutableStateFlow<String?>(null)
    val currentCallIntent: StateFlow<String?> = _currentCallIntent.asStateFlow()

    // Incoming preview (for listener)
    private val _incomingPreview = MutableStateFlow<SignalingMessage.IncomingPreview?>(null)
    val incomingPreview: StateFlow<SignalingMessage.IncomingPreview?> = _incomingPreview.asStateFlow()

    // Match info
    private val _matchInfo = MutableStateFlow<SignalingMessage.MatchMade?>(null)
    val matchInfo: StateFlow<SignalingMessage.MatchMade?> = _matchInfo.asStateFlow()

    // Flag to track if match has been consumed (to prevent duplicate navigation)
    private val _matchConsumed = MutableStateFlow(false)
    val matchConsumed: StateFlow<Boolean> = _matchConsumed.asStateFlow()

    // Lock object for atomic consume operation
    private val consumeLock = Any()

    /**
     * Atomically consume the match to prevent duplicate navigation.
     * @return true if this call was the first to consume (and should navigate), false if already consumed
     */
    fun tryConsumeMatch(): Boolean {
        Log.d(TAG, "═══════════════════════════════════════════════════")
        Log.d(TAG, "🔒 tryConsumeMatch() CALLED")
        Log.d(TAG, "   Thread: ${Thread.currentThread().name}")
        Log.d(TAG, "   Current _matchConsumed.value: ${_matchConsumed.value}")
        Log.d(TAG, "   Current matchInfo: ${_matchInfo.value?.callId ?: "null"}")

        synchronized(consumeLock) {
            Log.d(TAG, "   [INSIDE LOCK] Checking consumed state...")
            if (_matchConsumed.value) {
                Log.d(TAG, "   ⚠️ Match already consumed - returning false")
                Log.d(TAG, "═══════════════════════════════════════════════════")
                return false
            }
            _matchConsumed.value = true
            Log.d(TAG, "   ✓ Match consumed successfully - returning true")
            Log.d(TAG, "═══════════════════════════════════════════════════")
            return true
        }
    }

    /**
     * Mark match as consumed (legacy method, prefer tryConsumeMatch for race-safe consumption)
     */
    fun consumeMatch() {
        Log.d(TAG, "consumeMatch() called - setting _matchConsumed to true")
        _matchConsumed.value = true
    }

    // WebRTC state
    private val _webRTCState = MutableStateFlow(WebRTCState.IDLE)
    val webRTCState: StateFlow<WebRTCState> = _webRTCState.asStateFlow()

    // Queue for ICE candidates and offers that arrive before WebRTC is ready
    private val pendingIceCandidates = mutableListOf<SignalingMessage.IceCandidate>()
    private var pendingOffer: SignalingMessage.WebRtcOffer? = null

    // Mute state
    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    // Remote mute state (other party's mute status)
    private val _isRemoteMuted = MutableStateFlow(false)
    val isRemoteMuted: StateFlow<Boolean> = _isRemoteMuted.asStateFlow()

    // Voice anonymization state (default 50%)
    private val _anonymizationLevel = MutableStateFlow(30f)  // Reduced from 50 to minimize echo
    val anonymizationLevel: StateFlow<Float> = _anonymizationLevel.asStateFlow()

    private val _isAnonymizationEnabled = MutableStateFlow(true)
    val isAnonymizationEnabled: StateFlow<Boolean> = _isAnonymizationEnabled.asStateFlow()

    /**
     * Initialize and connect to signaling server
     * This method is idempotent - calling it multiple times is safe
     */
    fun initialize(context: Context) {
        Log.d(TAG, "═══════════════════════════════════════════════════")
        Log.d(TAG, "🚀 initialize() CALLED")
        Log.d(TAG, "   Thread: ${Thread.currentThread().name}")
        Log.d(TAG, "   isInitialized: $isInitialized")

        // Guard: Prevent duplicate initialization which would launch duplicate coroutines
        if (isInitialized) {
            Log.d(TAG, "   ⚠️ Already initialized - returning early")
            Log.d(TAG, "═══════════════════════════════════════════════════")
            return
        }
        isInitialized = true
        Log.d(TAG, "   ✓ Set isInitialized = true")

        appContext = context.applicationContext
        Log.d(TAG, "   ✓ appContext set")

        // Load or generate user ID
        val prefs = context.getSharedPreferences("backroom_prefs", Context.MODE_PRIVATE)
        val existingUserId = prefs.getString("anonymous_user_id", null)
        Log.d(TAG, "   Existing userId from prefs: $existingUserId")

        _userId = existingUserId ?: run {
            val newId = UUID.randomUUID().toString()
            prefs.edit().putString("anonymous_user_id", newId).apply()
            Log.d(TAG, "   Generated new userId: $newId")
            newId
        }
        Log.d(TAG, "   📱 Final User ID: $_userId")

        // Connect to signaling server
        Log.d(TAG, "   🔌 Connecting to signaling server...")
        signalingClient.connect()
        Log.d(TAG, "   ✓ signalingClient.connect() called")

        // Listen for messages
        Log.d(TAG, "   📨 Starting message collector coroutine...")
        scope.launch {
            Log.d(TAG, "   [MessageCollector] Coroutine started")
            signalingClient.messages.collect { message ->
                Log.d(TAG, "📨 [MessageCollector] Received: ${message::class.simpleName}")
                handleMessage(message)
            }
        }

        // Register when connected - auto-register as listener with availability ON
        // BUT DON'T re-register if we're already in a call!
        Log.d(TAG, "   🔌 Starting connection state collector coroutine...")
        scope.launch {
            Log.d(TAG, "   [ConnectionStateCollector] Coroutine started")
            signalingClient.connectionState.collect { state ->
                Log.d(TAG, "🔌 [ConnectionStateCollector] State: $state")
                Log.d(TAG, "   currentCallId: ${_currentCallId.value}")
                Log.d(TAG, "   currentRole: ${_currentRole.value}")
                Log.d(TAG, "   hasAutoRegistered: $hasAutoRegistered")

                if (state == ConnectionState.CONNECTED) {
                    // Check if we're in an active call - don't re-register as listener if so
                    val activeCallId = _currentCallId.value
                    if (activeCallId != null) {
                        Log.d(TAG, "   ⚠️ Already in call $activeCallId - skipping auto-registration")
                        return@collect
                    }

                    // Skip if already registered in this session (prevents duplicates on reconnect)
                    if (hasAutoRegistered && _currentRole.value == "listener") {
                        Log.d(TAG, "   ⚠️ Already auto-registered as listener - skipping duplicate")
                        return@collect
                    }

                    // Register as listener first (so we can receive calls)
                    Log.d(TAG, "   📝 Registering as listener...")
                    signalingClient.register(userId, "listener")
                    Log.d(TAG, "   ✓ register() called")

                    // Set availability to true (default: everyone can receive calls)
                    Log.d(TAG, "   📡 Setting availability to true...")
                    signalingClient.setAvailability(true, emptyList())
                    _isListenerAvailable.value = true
                    _currentRole.value = "listener"
                    hasAutoRegistered = true
                    Log.d(TAG, "   ✅ Auto-registered as listener with availability ON")

                    // Register FCM token for push notifications
                    Log.d(TAG, "   🔑 Registering FCM token...")
                    registerFcmToken()
                }
            }
        }

        Log.d(TAG, "   ✅ All collector coroutines launched")
        Log.d(TAG, "═══════════════════════════════════════════════════")
    }

    /**
     * Initialize WebRTC for a call
     * IMPORTANT: WebRTC must be initialized on the main thread
     */
    private fun initializeWebRTC(turnServers: List<TurnServer>, isInitiator: Boolean) {
        val callId = _currentCallId.value
        Log.d(TAG, "═══════════════════════════════════════════════════")
        Log.d(TAG, "🎬 initializeWebRTC CALLED")
        Log.d(TAG, "   Thread: ${Thread.currentThread().name}")
        Log.d(TAG, "   isInitiator: $isInitiator")
        Log.d(TAG, "   turnServers: ${turnServers.size}")
        Log.d(TAG, "   callId: $callId")
        Log.d(TAG, "   isWebRTCInitializing: $isWebRTCInitializing")
        Log.d(TAG, "   lastInitializedCallId: $lastInitializedCallId")
        Log.d(TAG, "═══════════════════════════════════════════════════")

        // Guard: Prevent duplicate initialization for the same call
        if (isWebRTCInitializing) {
            Log.w(TAG, "⚠️ WebRTC initialization already in progress - skipping duplicate call")
            return
        }
        if (callId != null && callId == lastInitializedCallId && _webRTCManager != null) {
            Log.w(TAG, "⚠️ WebRTC already initialized for call $callId - skipping duplicate")
            return
        }

        val context = appContext
        if (context == null) {
            Log.e(TAG, "❌ initializeWebRTC FAILED - appContext is null!")
            return
        }

        isWebRTCInitializing = true
        Log.d(TAG, "   Set isWebRTCInitializing = true")

        // Cancel any previous WebRTC-related coroutines
        webRTCJob?.cancel()
        Log.d(TAG, "   Cancelled previous WebRTC job")

        // Launch on Main thread - WebRTC requires main thread initialization
        Log.d(TAG, "   Launching coroutine on Main thread...")
        webRTCJob = scope.launch(Dispatchers.Main) {
            Log.d(TAG, "🔧 initializeWebRTC - NOW ON MAIN THREAD")
            Log.d(TAG, "   Thread: ${Thread.currentThread().name}")
            try {
                // Clean up any existing WebRTC manager
                _webRTCManager?.let {
                    Log.d(TAG, "🧹 Disposing existing WebRTC manager")
                    it.dispose()
                }

                // Create new WebRTC manager on main thread
                Log.d(TAG, "🆕 Creating new WebRTCManager...")
                _webRTCManager = WebRTCManager(context)

                // Observe WebRTC state
                launch {
                    _webRTCManager?.state?.collect { state ->
                        Log.d(TAG, "🔊 WebRTC state changed: $state")
                        _webRTCState.value = state

                        // When connected, verify audio setup
                        if (state == WebRTCState.CONNECTED) {
                            Log.d(TAG, "🔊 WebRTC CONNECTED - verifying audio setup...")
                            kotlinx.coroutines.delay(500) // Wait a bit for audio to initialize
                            _webRTCManager?.verifyAudioSetup()
                            _webRTCManager?.logStats()
                        }
                    }
                }

                // Forward local ICE candidates to signaling server
                launch(Dispatchers.IO) {
                    _webRTCManager?.localIceCandidates?.collect { candidate ->
                        val callId = _currentCallId.value
                        if (callId == null) {
                            Log.w(TAG, "⚠️ ICE candidate but no callId!")
                            return@collect
                        }
                        Log.d(TAG, "📤 Sending ICE candidate for call $callId")
                        signalingClient.sendIceCandidate(
                            callId = callId,
                            candidate = candidate.sdp,
                            sdpMid = candidate.sdpMid,
                            sdpMLineIndex = candidate.sdpMLineIndex
                        )
                    }
                }

                // Forward local SDP to signaling server
                launch(Dispatchers.IO) {
                    _webRTCManager?.localSdp?.collect { sdp ->
                        val callId = _currentCallId.value
                        if (callId == null) {
                            Log.w(TAG, "⚠️ SDP but no callId!")
                            return@collect
                        }
                        if (sdp.type == SessionDescription.Type.OFFER) {
                            Log.d(TAG, "📤 Sending OFFER for call $callId")
                            signalingClient.sendOffer(callId, sdp.description)
                        } else {
                            Log.d(TAG, "📤 Sending ANSWER for call $callId")
                            signalingClient.sendAnswer(callId, sdp.description)
                        }
                    }
                }

                // Create peer connection on main thread
                Log.d(TAG, "🔌 Creating peer connection...")
                _webRTCManager?.createPeerConnection(turnServers, isInitiator)

                // Mark initialization complete
                lastInitializedCallId = _currentCallId.value
                isWebRTCInitializing = false

                // Process any queued signaling messages
                processPendingSignalingMessages()

                Log.d(TAG, "✅ WebRTC initialized successfully, isInitiator: $isInitiator")
            } catch (e: Exception) {
                Log.e(TAG, "❌ FAILED to initialize WebRTC: ${e.message}", e)
                isWebRTCInitializing = false
                _webRTCState.value = WebRTCState.FAILED
            }
        }
    }

    /**
     * Process any signaling messages that arrived before WebRTC was ready
     */
    private fun processPendingSignalingMessages() {
        // Process pending offer
        pendingOffer?.let { offer ->
            Log.d(TAG, "📥 Processing queued offer for call ${offer.callId}")
            if (offer.callId == _currentCallId.value) {
                val sdp = SessionDescription(SessionDescription.Type.OFFER, offer.sdp)
                _webRTCManager?.setRemoteDescription(sdp, isAnswer = false)
            }
            pendingOffer = null
        }

        // Process pending ICE candidates
        val candidates = synchronized(pendingIceCandidates) {
            val copy = pendingIceCandidates.toList()
            pendingIceCandidates.clear()
            copy
        }

        if (candidates.isNotEmpty()) {
            Log.d(TAG, "📥 Processing ${candidates.size} queued ICE candidates")
            candidates.forEach { message ->
                if (message.callId == _currentCallId.value) {
                    val candidate = IceCandidate(
                        message.sdpMid ?: "",
                        message.sdpMLineIndex ?: 0,
                        message.candidate
                    )
                    _webRTCManager?.addIceCandidate(candidate)
                }
            }
        }
    }

    /**
     * Set role to sharer
     */
    fun setRoleSharer() {
        Log.d(TAG, "═══════════════════════════════════════════════════")
        Log.d(TAG, "📢 setRoleSharer() CALLED")
        Log.d(TAG, "   Thread: ${Thread.currentThread().name}")
        Log.d(TAG, "   Current role: ${_currentRole.value}")
        Log.d(TAG, "   Connection state: ${signalingClient.connectionState.value}")

        // Skip if already sharer
        if (_currentRole.value == "sharer") {
            Log.d(TAG, "   ⚠️ Already sharer - returning early")
            Log.d(TAG, "═══════════════════════════════════════════════════")
            return
        }

        _currentRole.value = "sharer"
        Log.d(TAG, "   ✓ Set _currentRole = 'sharer'")

        _isListenerAvailable.value = false
        Log.d(TAG, "   ✓ Set _isListenerAvailable = false")

        if (signalingClient.connectionState.value == ConnectionState.CONNECTED) {
            Log.d(TAG, "   📝 Registering as sharer...")
            signalingClient.register(userId, "sharer")
            Log.d(TAG, "   ✓ register() called")
        } else {
            Log.d(TAG, "   ⚠️ Not connected - skipping registration")
        }
        Log.d(TAG, "═══════════════════════════════════════════════════")
    }

    /**
     * Set role to listener and toggle availability
     */
    fun setRoleListener(available: Boolean, topics: List<String> = emptyList()) {
        Log.d(TAG, "═══════════════════════════════════════════════════")
        Log.d(TAG, "👂 setRoleListener() CALLED")
        Log.d(TAG, "   Thread: ${Thread.currentThread().name}")
        Log.d(TAG, "   available: $available")
        Log.d(TAG, "   topics: $topics")
        Log.d(TAG, "   Current role: ${_currentRole.value}")
        Log.d(TAG, "   Current availability: ${_isListenerAvailable.value}")
        Log.d(TAG, "   Connection state: ${signalingClient.connectionState.value}")

        // Skip if no change
        val roleChanged = _currentRole.value != "listener"
        val availabilityChanged = _isListenerAvailable.value != available

        Log.d(TAG, "   roleChanged: $roleChanged")
        Log.d(TAG, "   availabilityChanged: $availabilityChanged")

        if (!roleChanged && !availabilityChanged) {
            Log.d(TAG, "   ⚠️ No change needed - returning early")
            Log.d(TAG, "═══════════════════════════════════════════════════")
            return
        }

        _currentRole.value = "listener"
        Log.d(TAG, "   ✓ Set _currentRole = 'listener'")

        _isListenerAvailable.value = available
        Log.d(TAG, "   ✓ Set _isListenerAvailable = $available")

        if (signalingClient.connectionState.value == ConnectionState.CONNECTED) {
            if (roleChanged) {
                Log.d(TAG, "   📝 Registering as listener...")
                signalingClient.register(userId, "listener")
                Log.d(TAG, "   ✓ register() called")
            }
            if (availabilityChanged || roleChanged) {
                Log.d(TAG, "   📡 Setting availability to $available...")
                signalingClient.setAvailability(available, topics)
                Log.d(TAG, "   ✓ setAvailability() called")
            }
        } else {
            Log.d(TAG, "   ⚠️ Not connected - skipping server calls")
        }
        Log.d(TAG, "═══════════════════════════════════════════════════")
    }

    /**
     * Submit a share request (sharer looking for listener)
     */
    fun submitShare(topic: String, tone: String, intent: String, duration: Int) {
        Log.d(TAG, "Submitting share: $topic, $tone, $duration min")

        // Clear any previous expiration reason
        _shareExpiredReason.value = null

        // IMPORTANT: Set role to sharer when submitting a share
        // This ensures HomeScreen doesn't try to navigate (it only handles listener matches)
        _currentRole.value = "sharer"

        // Store the call parameters for when the call starts
        _currentCallDuration.value = duration
        _currentCallTopic.value = topic
        _currentCallIntent.value = intent

        signalingClient.submitShareRequest(topic, tone, intent, duration)
    }

    /**
     * Cancel pending share request
     */
    fun cancelShare() {
        Log.d(TAG, "Cancelling share")
        signalingClient.cancelShare()
        _pendingShareId.value = null
        _shareExpiredReason.value = null  // Clear any previous expiration
    }

    /**
     * Accept incoming preview (listener accepts call)
     */
    fun acceptPreview() {
        val preview = _incomingPreview.value
        if (preview != null) {
            Log.d(TAG, "👆 acceptPreview() called - shareId: ${preview.shareId}")
            Log.d(TAG, "   Current thread: ${Thread.currentThread().name}")

            // Store the call parameters from the preview
            _currentCallDuration.value = preview.durationMinutes
            _currentCallTopic.value = preview.topic
            _currentCallIntent.value = preview.previewText
            Log.d(TAG, "   Stored call params: duration=${preview.durationMinutes}, topic=${preview.topic}")

            signalingClient.acceptPreview(preview.shareId)
            Log.d(TAG, "   Sent preview_accept to server")

            _incomingPreview.value = null
            Log.d(TAG, "   Cleared incomingPreview")
        } else {
            Log.w(TAG, "⚠️ acceptPreview() called but no preview available!")
        }
    }

    /**
     * Decline incoming preview
     */
    fun declinePreview() {
        val preview = _incomingPreview.value
        if (preview != null) {
            Log.d(TAG, "Declining preview: ${preview.shareId}")
            signalingClient.declinePreview(preview.shareId)
            _incomingPreview.value = null
        }
    }


    /**
     * Handle incoming messages
     */
    private fun handleMessage(message: SignalingMessage) {
        Log.d(TAG, "📩 Processing message: ${message::class.simpleName}")

        when (message) {
            is SignalingMessage.Connected -> {
                Log.d(TAG, "🔗 Connected with client ID: ${message.clientId}")
            }

            is SignalingMessage.ShareSubmitted -> {
                _pendingShareId.value = message.shareId
                _shareExpiredReason.value = null  // Clear any previous expiration
                Log.d(TAG, "📝 Share submitted: ${message.shareId}, status: ${message.status}")
            }

            is SignalingMessage.ShareExpired -> {
                _pendingShareId.value = null
                _shareExpiredReason.value = message.reason
                Log.d(TAG, "⏰ Share expired: ${message.shareId}, reason: ${message.reason}")
            }

            is SignalingMessage.IncomingPreview -> {
                _incomingPreview.value = message
                Log.d(TAG, "📬 Incoming preview: topic=${message.topic}, text=${message.previewText}")
            }

            is SignalingMessage.MatchMade -> {
                Log.d(TAG, "═══════════════════════════════════════════════════")
                Log.d(TAG, "🎯 MATCH MADE RECEIVED!")
                Log.d(TAG, "   Thread: ${Thread.currentThread().name}")
                Log.d(TAG, "   Call ID: ${message.callId}")
                Log.d(TAG, "   Role: ${message.role}")
                Log.d(TAG, "   Duration: ${message.durationMinutes} min")
                Log.d(TAG, "   Topic: ${message.topic}")
                Log.d(TAG, "   Intent: ${message.intent}")
                Log.d(TAG, "   TURN servers: ${message.turnServers.size}")
                Log.d(TAG, "═══════════════════════════════════════════════════")

                // CRITICAL: Update role from server's match_made message FIRST
                // This must happen before setting matchInfo to avoid race condition
                // in HomeScreen's LaunchedEffect which checks currentRole
                Log.d(TAG, "   Step 1: Setting currentRole to '${message.role}'")
                _currentRole.value = message.role
                Log.d(TAG, "   ✓ currentRole is now: ${_currentRole.value}")

                // Now set the match-related state
                Log.d(TAG, "   Step 2: Setting matchConsumed to false")
                _matchConsumed.value = false

                Log.d(TAG, "   Step 3: Setting currentCallId to '${message.callId}'")
                _currentCallId.value = message.callId

                Log.d(TAG, "   Step 4: Clearing pendingShareId and incomingPreview")
                _pendingShareId.value = null
                _incomingPreview.value = null

                // Update call parameters from match - these override any previously set values
                Log.d(TAG, "   Step 5: Updating call parameters")
                _currentCallDuration.value = message.durationMinutes
                if (!message.topic.isNullOrEmpty()) {
                    _currentCallTopic.value = message.topic
                }
                if (!message.intent.isNullOrEmpty()) {
                    _currentCallIntent.value = message.intent
                }

                // Set matchInfo LAST - this triggers the navigation in HomeScreen/WaitingScreen
                Log.d(TAG, "   Step 6: Setting matchInfo (THIS TRIGGERS NAVIGATION)")
                _matchInfo.value = message
                Log.d(TAG, "   ✓ matchInfo is now set")

                Log.d(TAG, "📞 All state updated, now initializing WebRTC...")

                // Convert TurnServer types
                val turnServers = message.turnServers.map { ts ->
                    Log.d(TAG, "   TURN: ${ts.urls}")
                    TurnServer(
                        urls = ts.urls,
                        username = ts.username,
                        credential = ts.credential
                    )
                }

                // Initialize WebRTC - sharer initiates, listener waits
                val isInitiator = message.role == "sharer"
                Log.d(TAG, "🎬 Step 7: Calling initializeWebRTC(isInitiator=$isInitiator)")
                initializeWebRTC(turnServers, isInitiator)
                Log.d(TAG, "   ✓ initializeWebRTC launched")
            }

            is SignalingMessage.WebRtcOffer -> {
                Log.d(TAG, "📥 Received WebRTC OFFER, sdp length: ${message.sdp.length}")
                // Validate call ID to prevent stale/malicious messages
                if (message.callId != _currentCallId.value) {
                    Log.w(TAG, "⚠️ Ignoring offer for wrong call: ${message.callId} vs ${_currentCallId.value}")
                    return
                }
                // Queue if WebRTC not ready yet (either manager is null OR peerConnection not created)
                if (_webRTCManager == null || _webRTCManager?.isPeerConnectionReady() != true) {
                    Log.w(TAG, "⏳ Queuing offer - WebRTC not ready yet (manager=${_webRTCManager != null}, peerConnection=${_webRTCManager?.isPeerConnectionReady()})")
                    pendingOffer = message
                    return
                }
                val sdp = SessionDescription(SessionDescription.Type.OFFER, message.sdp)
                _webRTCManager?.setRemoteDescription(sdp, isAnswer = false)
            }

            is SignalingMessage.WebRtcAnswer -> {
                Log.d(TAG, "📥 Received WebRTC ANSWER, sdp length: ${message.sdp.length}")
                // Validate call ID
                if (message.callId != _currentCallId.value) {
                    Log.w(TAG, "⚠️ Ignoring answer for wrong call: ${message.callId} vs ${_currentCallId.value}")
                    return
                }
                if (_webRTCManager == null) {
                    Log.e(TAG, "❌ WebRTCManager is NULL when receiving answer! This shouldn't happen for sharer.")
                    return
                }
                val sdp = SessionDescription(SessionDescription.Type.ANSWER, message.sdp)
                _webRTCManager?.setRemoteDescription(sdp, isAnswer = true)
            }

            is SignalingMessage.IceCandidate -> {
                Log.d(TAG, "📥 Received ICE candidate: ${message.candidate.take(50)}...")
                // Validate call ID
                if (message.callId != _currentCallId.value) {
                    Log.w(TAG, "⚠️ Ignoring ICE for wrong call: ${message.callId} vs ${_currentCallId.value}")
                    return
                }
                // Queue if WebRTC not ready yet (either manager is null OR peerConnection not created)
                if (_webRTCManager == null || _webRTCManager?.isPeerConnectionReady() != true) {
                    Log.w(TAG, "⏳ Queuing ICE candidate - WebRTC not ready yet (manager=${_webRTCManager != null}, peerConnection=${_webRTCManager?.isPeerConnectionReady()})")
                    synchronized(pendingIceCandidates) {
                        pendingIceCandidates.add(message)
                    }
                    return
                }
                val candidate = IceCandidate(
                    message.sdpMid ?: "",
                    message.sdpMLineIndex ?: 0,
                    message.candidate
                )
                _webRTCManager?.addIceCandidate(candidate)
            }

            is SignalingMessage.CallEnded -> {
                Log.d(TAG, "📞 Call ended: ${message.callId}, reason: ${message.reason}")

                // Cancel WebRTC-related coroutines
                webRTCJob?.cancel()
                webRTCJob = null

                _currentCallId.value = null
                _matchInfo.value = null
                _webRTCManager?.close()
                _webRTCState.value = WebRTCState.IDLE

                // Reset initialization tracking
                isWebRTCInitializing = false
                lastInitializedCallId = null

                // Clear queued messages
                pendingOffer = null
                synchronized(pendingIceCandidates) {
                    pendingIceCandidates.clear()
                }
            }

            is SignalingMessage.RemoteMuteState -> {
                _isRemoteMuted.value = message.muted
                Log.d(TAG, "🔇 Remote mute state: ${message.muted}")
            }

            is SignalingMessage.Error -> {
                Log.e(TAG, "❌ Error from server: ${message.message}")
            }

            else -> {
                Log.d(TAG, "❓ Unhandled message type: ${message::class.simpleName}")
            }
        }
    }

    /**
     * Set mute state and notify remote party
     */
    fun setMuted(muted: Boolean) {
        _isMuted.value = muted
        _webRTCManager?.setMuted(muted)

        // Send mute state to remote party via signaling
        val callId = _currentCallId.value ?: return
        scope.launch {
            signalingClient.sendMuteState(callId, muted)
        }
        Log.d(TAG, "Mute set to: $muted")
    }

    /**
     * Set voice anonymization level (0-100%)
     * Default is 50%
     */
    fun setAnonymizationLevel(level: Float) {
        val clampedLevel = level.coerceIn(0f, 100f)
        _anonymizationLevel.value = clampedLevel

        // Update WebRTC manager if available, otherwise update the static anonymizer
        // Run on IO thread to avoid UI thread issues
        scope.launch(Dispatchers.IO) {
            try {
                val webrtc = _webRTCManager
                if (webrtc != null) {
                    // Convert percentage to AnonymizationLevel enum
                    val anonymizationLevel = when {
                        clampedLevel <= 0f -> com.example.backroom.data.audio.AnonymizationLevel.NONE
                        clampedLevel <= 25f -> com.example.backroom.data.audio.AnonymizationLevel.LIGHT
                        clampedLevel <= 50f -> com.example.backroom.data.audio.AnonymizationLevel.MEDIUM
                        clampedLevel <= 75f -> com.example.backroom.data.audio.AnonymizationLevel.STRONG
                        else -> com.example.backroom.data.audio.AnonymizationLevel.MAXIMUM
                    }
                    webrtc.setAnonymizationLevel(anonymizationLevel)
                } else {
                    // Fallback: update ProductionVoiceAnonymizer directly
                    com.example.backroom.data.audio.ProductionVoiceAnonymizer.setAnonymizationLevel(clampedLevel.toInt())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error setting anonymization level: ${e.message}")
            }
        }

        Log.d(TAG, "Anonymization level: $clampedLevel%")
    }

    /**
     * Enable/disable voice anonymization
     */
    fun setAnonymizationEnabled(enabled: Boolean) {
        _isAnonymizationEnabled.value = enabled

        try {
            _webRTCManager?.setVoiceAnonymizationEnabled(enabled)
                ?: com.example.backroom.data.audio.ProductionVoiceAnonymizer.setEnabled(enabled)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting anonymization enabled: ${e.message}")
        }

        Log.d(TAG, "Anonymization enabled: $enabled")
    }

    /**
     * Set voice modification direction (deeper or higher)
     * @deprecated Voice direction is now controlled via AnonymizationLevel
     */
    @Deprecated("Use setAnonymizationLevel instead")
    fun setVoiceDirection(direction: com.example.backroom.data.audio.AnonymizationDirection) {
        // Voice direction is now controlled via pitch factor in VoiceAnonymizer
        // Lower pitch (< 1.0) = deeper voice, which is the default for anonymization
        Log.d(TAG, "Voice direction: $direction (deprecated - use setAnonymizationLevel)")
    }

    /**
     * End current call and clean up WebRTC
     */
    fun endCall() {
        Log.d(TAG, "═══════════════════════════════════════════════════")
        Log.d(TAG, "📞 endCall() CALLED")
        Log.d(TAG, "   Thread: ${Thread.currentThread().name}")
        Log.d(TAG, "   Current callId: ${_currentCallId.value}")
        Log.d(TAG, "   Current role: ${_currentRole.value}")
        Log.d(TAG, "   WebRTC state: ${_webRTCState.value}")

        val callId = _currentCallId.value
        if (callId != null) {
            Log.d(TAG, "   📤 Sending endCall to server for: $callId")
            signalingClient.endCall(callId)
            Log.d(TAG, "   ✓ endCall sent to server")
        } else {
            Log.d(TAG, "   ⚠️ No active call to end")
        }

        // Cancel WebRTC-related coroutines first
        Log.d(TAG, "   Cancelling WebRTC job...")
        webRTCJob?.cancel()
        webRTCJob = null
        Log.d(TAG, "   ✓ WebRTC job cancelled")

        // Clean up WebRTC
        Log.d(TAG, "   Closing WebRTC manager...")
        _webRTCManager?.close()
        Log.d(TAG, "   ✓ WebRTC manager closed")

        Log.d(TAG, "   Resetting state...")
        _webRTCState.value = WebRTCState.IDLE
        _currentCallId.value = null
        _matchInfo.value = null
        _isMuted.value = false
        _isRemoteMuted.value = false
        Log.d(TAG, "   ✓ State reset")

        // Reset initialization tracking
        isWebRTCInitializing = false
        lastInitializedCallId = null
        Log.d(TAG, "   ✓ Initialization tracking reset")
        Log.d(TAG, "═══════════════════════════════════════════════════")
        Log.d(TAG, "✅ Call ended successfully")
    }

    /**
     * Disconnect from signaling server
     */
    fun disconnect() {
        _webRTCManager?.dispose()
        _webRTCManager = null
        signalingClient.disconnect()
        _currentCallId.value = null
        _matchInfo.value = null
        _pendingShareId.value = null
        _incomingPreview.value = null
        _webRTCState.value = WebRTCState.IDLE
    }

    /**
     * Submit a report about a call/user
     */
    fun submitReport(callId: String, reason: String, details: String, blockUser: Boolean) {
        Log.d(TAG, "📝 submitReport() called - callId=$callId, reason=$reason, blockUser=$blockUser")
        signalingClient.submitReport(callId, reason, details, blockUser)
    }

    /**
     * Block a user by their ID
     */
    fun blockUser(userId: String) {
        Log.d(TAG, "🚫 blockUser() called - userId=$userId")
        signalingClient.blockUser(userId)
    }

    /**
     * Update FCM token for push notifications
     * Called when a new FCM token is generated
     */
    fun updateFcmToken(token: String) {
        Log.d(TAG, "🔑 updateFcmToken() called - token=${token.take(20)}...")
        signalingClient.updateFcmToken(token)
    }

    /**
     * Register FCM token on app startup
     * Retrieves the current FCM token and sends it to the server
     * Uses hasFcmTokenRegistered flag to prevent duplicate registrations
     */
    fun registerFcmToken() {
        Log.d(TAG, "🔑 registerFcmToken() called")
        Log.d(TAG, "   hasFcmTokenRegistered: $hasFcmTokenRegistered")

        // Guard: Prevent duplicate FCM token registration
        if (hasFcmTokenRegistered) {
            Log.d(TAG, "   ⚠️ FCM token already registered - skipping duplicate")
            return
        }
        hasFcmTokenRegistered = true
        Log.d(TAG, "   ✓ Set hasFcmTokenRegistered = true")

        scope.launch {
            try {
                com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                    .addOnSuccessListener { token ->
                        Log.d(TAG, "   ✓ Got FCM token: ${token.take(20)}...")
                        signalingClient.updateFcmToken(token)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "   ❌ Failed to get FCM token", e)
                        // Reset flag on failure so it can be retried
                        hasFcmTokenRegistered = false
                    }
            } catch (e: Exception) {
                Log.e(TAG, "   ❌ Error getting FCM token", e)
                // Reset flag on exception so it can be retried
                hasFcmTokenRegistered = false
            }
        }
    }
}
