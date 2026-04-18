package com.example.backroom.data.webrtc

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.example.backroom.data.audio.AnonymizationLevel
import com.example.backroom.data.audio.AudioDeviceModuleFactory
import com.example.backroom.data.audio.ProductionVoiceAnonymizer
import com.example.backroom.data.network.TurnServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.*

/**
 * WebRTC connection states
 */
enum class WebRTCState {
    IDLE,
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    FAILED
}

/**
 * Manages WebRTC peer connection for voice calls
 *
 * Voice Anonymization:
 * Uses ProductionVoiceAnonymizer which implements:
 * - TD-PSOLA (Pitch Synchronous Overlap-Add) for pitch shifting
 * - Formant shifting for voice character modification
 * - Spectral scrambling for additional disguise
 *
 * Note: Always pass applicationContext to avoid memory leaks.
 */
class WebRTCManager(
    context: Context
) {
    // Store applicationContext to prevent memory leaks
    private val context: Context = context.applicationContext

    private val TAG = "WebRTCManager"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var audioDeviceModule: org.webrtc.audio.AudioDeviceModule? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null
    private var remoteAudioTrack: AudioTrack? = null
    private var audioSource: AudioSource? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    private val _state = MutableStateFlow(WebRTCState.IDLE)
    val state: StateFlow<WebRTCState> = _state.asStateFlow()

    private val _localIceCandidates = MutableSharedFlow<IceCandidate>(replay = 0)
    val localIceCandidates: SharedFlow<IceCandidate> = _localIceCandidates.asSharedFlow()

    private val _localSdp = MutableSharedFlow<SessionDescription>(replay = 0)
    val localSdp: SharedFlow<SessionDescription> = _localSdp.asSharedFlow()

    private var isMuted = false
    private var savedAudioMode: Int = AudioManager.MODE_NORMAL
    private var savedSpeakerphoneState: Boolean = false

    // Track if offer/answer has been sent to prevent duplicates
    private var hasOfferBeenCreated = false
    private var hasAnswerBeenCreated = false

    // Connection timeout job
    private var connectionTimeoutJob: kotlinx.coroutines.Job? = null
    private val CONNECTION_TIMEOUT_MS = 30000L // 30 seconds

    // Voice anonymization - using ProductionVoiceAnonymizer
    // Implements: PSOLA + Formant Shifting + Spectral Scrambling
    private var voiceAnonymizationEnabled = true
    private var currentAnonymizationLevel = AnonymizationLevel.MAXIMUM  // Changed to MAX


    /**
     * Check if WebRTC is ready to handle signaling (offer/answer/ICE)
     */
    fun isPeerConnectionReady(): Boolean = peerConnection != null

    companion object {
        private var isInitialized = false
        private val initLock = Any()

        /**
         * Initialize PeerConnectionFactory once per process
         */
        fun initializeOnce(context: Context) {
            synchronized(initLock) {
                if (isInitialized) {
                    Log.d("WebRTCManager", "PeerConnectionFactory already initialized")
                    return
                }
                Log.d("WebRTCManager", "═══════════════════════════════════════════════════")
                Log.d("WebRTCManager", "🏭 INITIALIZING PeerConnectionFactory (ONCE)")
                Log.d("WebRTCManager", "   Thread: ${Thread.currentThread().name}")
                try {
                    val options = PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                        .setEnableInternalTracer(false)
                        .createInitializationOptions()
                    PeerConnectionFactory.initialize(options)
                    isInitialized = true
                    Log.d("WebRTCManager", "   ✓ PeerConnectionFactory initialized successfully")
                } catch (e: Exception) {
                    Log.e("WebRTCManager", "   ❌ Error initializing PeerConnectionFactory: ${e.message}", e)
                }
                Log.d("WebRTCManager", "═══════════════════════════════════════════════════")
            }
        }
    }

    init {
        Log.d(TAG, "═══════════════════════════════════════════════════")
        Log.d(TAG, "🆕 WebRTCManager CONSTRUCTOR CALLED")
        Log.d(TAG, "   Thread: ${Thread.currentThread().name}")
        Log.d(TAG, "═══════════════════════════════════════════════════")
        // Ensure global initialization happened
        initializeOnce(context)
        // Create the factory for this instance
        initializePeerConnectionFactory()
        Log.d(TAG, "   ✓ WebRTCManager constructor completed")
    }

    private fun initializePeerConnectionFactory() {
        try {
            Log.d(TAG, "🏭 initializePeerConnectionFactory starting...")
            Log.d(TAG, "═══════════════════════════════════════════════════")
            Log.d(TAG, "🎭 INITIALIZING VOICE ANONYMIZER (AudioRecordDataCallback)")
            Log.d(TAG, "   Algorithm: PSOLA + Formant + Spectral + Ring Mod + Noise")
            Log.d(TAG, "   🎭 Voice anonymization: ${if (voiceAnonymizationEnabled) "ENABLED" else "DISABLED"}")
            Log.d(TAG, "   🎭 Anonymization level: ${currentAnonymizationLevel.name}")
            Log.d(TAG, "═══════════════════════════════════════════════════")

            // Build audio device module using ProductionAudioDeviceModule
            // which uses AudioRecordDataCallback for GUARANTEED in-place audio interception
            val adm = AudioDeviceModuleFactory.create(
                context = context,
                enableAnonymization = voiceAnonymizationEnabled,
                anonymizationLevel = currentAnonymizationLevel.intLevel
            )
            audioDeviceModule = adm

            Log.d(TAG, "   ✅ AudioDeviceModule created (AudioRecordDataCallback)")

            peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(PeerConnectionFactory.Options().apply {
                    disableEncryption = false
                    disableNetworkMonitor = false
                })
                .setAudioDeviceModule(adm)
                .setVideoEncoderFactory(null)
                .setVideoDecoderFactory(null)
                .createPeerConnectionFactory()

            Log.d(TAG, "   ✅ PeerConnectionFactory created")

        } catch (e: Exception) {
            Log.e(TAG, "Error creating PeerConnectionFactory: ${e.message}", e)

            // Fallback without voice anonymization
            voiceAnonymizationEnabled = false
            try {
                val adm2 = AudioDeviceModuleFactory.create(
                    context = context,
                    enableAnonymization = false
                )
                audioDeviceModule = adm2

                peerConnectionFactory = PeerConnectionFactory.builder()
                    .setAudioDeviceModule(adm2)
                    .createPeerConnectionFactory()

                Log.d(TAG, "PeerConnectionFactory created without voice anonymization (fallback)")
            } catch (e2: Exception) {
                Log.e(TAG, "Critical error: Could not create PeerConnectionFactory: ${e2.message}", e2)
            }
        }
    }


    // ═══════════════════════════════════════════════════════════════════════════
    // VOICE ANONYMIZATION PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Enable or disable voice anonymization
     */
    fun setVoiceAnonymizationEnabled(enabled: Boolean) {
        voiceAnonymizationEnabled = enabled

        // Update Production Voice Anonymizer
        ProductionVoiceAnonymizer.setEnabled(enabled)

        Log.d(TAG, "🎭 Voice anonymization ${if (enabled) "ENABLED" else "DISABLED"}")
    }

    /**
     * Check if voice anonymization is enabled
     */
    fun isVoiceAnonymizationEnabled(): Boolean = voiceAnonymizationEnabled

    /**
     * Set the voice anonymization level
     */
    fun setAnonymizationLevel(level: AnonymizationLevel) {
        currentAnonymizationLevel = level
        ProductionVoiceAnonymizer.setAnonymizationLevel(level.intLevel)

        Log.d(TAG, "🎭 Anonymization level set to ${level.name} (${level.description})")
        Log.d(TAG, "   Production level: ${level.intLevel}%")
        Log.d(TAG, "   Pitch factor: ${ProductionVoiceAnonymizer.getPitchFactor()}")
    }

    /**
     * Get the current anonymization level
     */
    fun getAnonymizationLevel(): AnonymizationLevel = currentAnonymizationLevel

    /**
     * Set a custom pitch factor for voice anonymization
     *
     * @param factor Pitch multiplier (0.5 = one octave down, 1.0 = normal, 1.5 = one octave up)
     *               For anonymization, values < 1.0 make the voice deeper
     */
    fun setCustomPitchFactor(factor: Float) {
        // Convert pitch factor to level: factor 1.0 = 0%, factor MIN_PITCH_FACTOR(0.30) = 100%
        // Span = 1.0 - 0.30 = 0.70
        val level = ((1.0f - factor) / 0.70f * 100).toInt().coerceIn(0, 100)
        ProductionVoiceAnonymizer.setAnonymizationLevel(level)

        Log.d(TAG, "🎭 Custom pitch factor set to $factor (production level: $level%)")
    }

    /**
     * Get the current pitch factor
     */
    fun getCurrentPitchFactor(): Float = ProductionVoiceAnonymizer.getPitchFactor()

    // ═══════════════════════════════════════════════════════════════════════════

    fun createPeerConnection(
        turnServers: List<TurnServer>,
        isInitiator: Boolean
    ) {
        Log.d(TAG, "═══════════════════════════════════════════════════")
        Log.d(TAG, "🔌 createPeerConnection CALLED")
        Log.d(TAG, "   isInitiator: $isInitiator")
        Log.d(TAG, "   Role: ${if (isInitiator) "SHARER (will create offer)" else "LISTENER (will wait for offer)"}")
        Log.d(TAG, "   TURN servers: ${turnServers.size}")
        turnServers.forEach { ts -> Log.d(TAG, "   - ${ts.urls}") }
        Log.d(TAG, "═══════════════════════════════════════════════════")

        // Guard: Don't create duplicate peer connections
        if (peerConnection != null) {
            Log.w(TAG, "⚠️ PeerConnection already exists - closing old one first")
            peerConnection?.close()
            peerConnection = null
        }

        // Reset offer/answer tracking for new connection
        hasOfferBeenCreated = false
        hasAnswerBeenCreated = false
        hasRemoteDescriptionBeenSet = false

        // Configure audio for voice call
        configureAudioForCall()

        val iceServers = buildIceServers(turnServers)
        Log.d(TAG, "   ICE servers built: ${iceServers.size}")

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        }

        if (peerConnectionFactory == null) {
            Log.e(TAG, "❌ peerConnectionFactory is NULL!")
            _state.value = WebRTCState.FAILED
            return
        }

        peerConnection = peerConnectionFactory?.createPeerConnection(
            rtcConfig,
            createPeerConnectionObserver()
        )

        if (peerConnection == null) {
            Log.e(TAG, "❌ Failed to create peerConnection!")
            _state.value = WebRTCState.FAILED
            return
        }
        Log.d(TAG, "✅ PeerConnection created")

        // Create and add local audio track
        createLocalAudioTrack()

        _state.value = WebRTCState.CONNECTING
        Log.d(TAG, "🔄 State set to CONNECTING")

        // Start connection timeout
        startConnectionTimeout()

        if (isInitiator) {
            Log.d(TAG, "📤 Creating offer (we are initiator)")
            createOffer()
        }

        Log.d(TAG, "PeerConnection created (initiator: $isInitiator)")
    }

    /**
     * Start a timeout that will fail the connection if not established in time
     */
    private fun startConnectionTimeout() {
        // Cancel any existing timeout
        connectionTimeoutJob?.cancel()

        connectionTimeoutJob = scope.launch {
            delay(CONNECTION_TIMEOUT_MS)
            // If still connecting after timeout, fail
            if (_state.value == WebRTCState.CONNECTING) {
                Log.e(TAG, "⏰ Connection timeout - failed to establish WebRTC connection in ${CONNECTION_TIMEOUT_MS/1000}s")
                _state.value = WebRTCState.FAILED
            }
        }
    }

    /**
     * Cancel connection timeout (called when connection succeeds)
     */
    private fun cancelConnectionTimeout() {
        connectionTimeoutJob?.cancel()
        connectionTimeoutJob = null
    }

    /**
     * Configure audio manager for voice call
     * CRITICAL: This setup is essential for WebRTC audio to work properly
     */
    @Suppress("DEPRECATION")
    private fun configureAudioForCall() {
        Log.d(TAG, "═══════════════════════════════════════════════════")
        Log.d(TAG, "🔊 configureAudioForCall CALLED")

        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager?.let { am ->
            // Save current audio state
            savedAudioMode = am.mode
            savedSpeakerphoneState = am.isSpeakerphoneOn
            Log.d(TAG, "   Saved audio mode: $savedAudioMode, speakerphone: $savedSpeakerphoneState")

            // WORKAROUND: First reset to normal mode to release any audio resources
            // This helps on some devices (like Xiaomi) that have trouble with AudioRecord
            if (am.mode != AudioManager.MODE_NORMAL) {
                Log.d(TAG, "   Resetting audio mode to NORMAL first...")
                am.mode = AudioManager.MODE_NORMAL
                // Small delay to let the audio system settle
                try { Thread.sleep(100) } catch (e: InterruptedException) {}
            }

            // CRITICAL: Set to voice communication mode for proper audio routing
            // This mode enables acoustic echo cancellation and proper audio routing
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            Log.d(TAG, "   Set mode to MODE_IN_COMMUNICATION")

            // DISABLE speakerphone to prevent echo
            // Hardware AEC doesn't work well on all devices with speakerphone
            // User should hold phone to ear like a normal phone call
            am.isSpeakerphoneOn = false
            Log.d(TAG, "   Set speakerphone OFF (prevents echo)")

            // Ensure Bluetooth SCO is disabled (we want device speaker/mic)
            if (am.isBluetoothScoOn) {
                am.isBluetoothScoOn = false
                am.stopBluetoothSco()
                Log.d(TAG, "   Disabled Bluetooth SCO")
            }

            // Set volume to reasonable level for voice call stream
            val maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
            val callVolume = (maxVolume * 0.9).toInt() // 90% volume for better audibility
            am.setStreamVolume(AudioManager.STREAM_VOICE_CALL, callVolume, 0)
            Log.d(TAG, "   Set VOICE_CALL volume to $callVolume / $maxVolume")

            // Also set music stream (some devices route WebRTC audio here)
            val maxMusicVolume = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val musicVolume = (maxMusicVolume * 0.9).toInt()
            am.setStreamVolume(AudioManager.STREAM_MUSIC, musicVolume, 0)
            Log.d(TAG, "   Set MUSIC volume to $musicVolume / $maxMusicVolume (fallback)")

            // Request audio focus using modern API (Android O+) or legacy API
            val focusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
                Log.d(TAG, "🔊 Audio focus changed: $focusChange")
                when (focusChange) {
                    AudioManager.AUDIOFOCUS_GAIN -> Log.d(TAG, "   AUDIOFOCUS_GAIN")
                    AudioManager.AUDIOFOCUS_LOSS -> Log.d(TAG, "   AUDIOFOCUS_LOSS")
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> Log.d(TAG, "   AUDIOFOCUS_LOSS_TRANSIENT")
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> Log.d(TAG, "   AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK")
                }
            }

            val focusResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(audioAttributes)
                    .setOnAudioFocusChangeListener(focusListener)
                    .build()

                am.requestAudioFocus(audioFocusRequest!!)
            } else {
                am.requestAudioFocus(
                    focusListener,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
            }
            Log.d(TAG, "   Audio focus request result: $focusResult (1=granted)")

            // Log final audio state
            Log.d(TAG, "   ─────────────────────────────────────────────")
            Log.d(TAG, "   FINAL AUDIO STATE:")
            Log.d(TAG, "   - Mode: ${am.mode} (3=MODE_IN_COMMUNICATION)")
            Log.d(TAG, "   - Speakerphone: ${am.isSpeakerphoneOn}")
            Log.d(TAG, "   - Microphone muted: ${am.isMicrophoneMute}")
            Log.d(TAG, "   - Music active: ${am.isMusicActive}")
            Log.d(TAG, "   - Voice call volume: ${am.getStreamVolume(AudioManager.STREAM_VOICE_CALL)}")
            Log.d(TAG, "═══════════════════════════════════════════════════")
        } ?: run {
            Log.e(TAG, "❌ Failed to get AudioManager!")
        }
    }

    /**
     * Restore audio configuration after call ends
     */
    @Suppress("DEPRECATION")
    private fun restoreAudioConfig() {
        audioManager?.let { am ->
            am.mode = savedAudioMode
            am.isSpeakerphoneOn = savedSpeakerphoneState

            // Abandon audio focus using modern API (Android O+) or legacy API
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
            } else {
                am.abandonAudioFocus(null)
            }
            audioFocusRequest = null
            Log.d(TAG, "Audio configuration restored")
        }
    }

    /**
     * Force audio mode to MODE_IN_COMMUNICATION
     * Called when ICE connects because WebRTC's audio module resets the mode
     */
    @Suppress("DEPRECATION")
    private fun forceAudioModeForCall() {
        audioManager?.let { am ->
            val currentMode = am.mode
            Log.d(TAG, "   forceAudioModeForCall: current mode = $currentMode")

            if (currentMode != AudioManager.MODE_IN_COMMUNICATION) {
                am.mode = AudioManager.MODE_IN_COMMUNICATION
                Log.d(TAG, "   ✅ Forced mode to MODE_IN_COMMUNICATION (was $currentMode)")
            }

            // DISABLE speakerphone to prevent echo
            // Hardware AEC may not work properly with speakerphone on some devices
            // User must hold phone to ear like a normal phone call
            if (am.isSpeakerphoneOn) {
                am.isSpeakerphoneOn = false
                Log.d(TAG, "   ✅ Disabled speakerphone (prevents echo)")
            }

            // Verify the change took effect
            val newMode = am.mode
            if (newMode != AudioManager.MODE_IN_COMMUNICATION) {
                Log.e(TAG, "   ❌ FAILED to set MODE_IN_COMMUNICATION! Mode is still $newMode")
            } else {
                Log.d(TAG, "   ✅ Audio mode confirmed: MODE_IN_COMMUNICATION")
            }
        } ?: Log.e(TAG, "   ❌ AudioManager is null!")
    }

    private fun buildIceServers(turnServers: List<TurnServer>): List<PeerConnection.IceServer> {
        val iceServers = mutableListOf<PeerConnection.IceServer>()

        // Add Google's public STUN server as fallback
        iceServers.add(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302")
                .createIceServer()
        )

        // Add configured TURN servers
        turnServers.forEach { server ->
            server.urls.forEach { url ->
                val builder = PeerConnection.IceServer.builder(url)
                server.username?.let { builder.setUsername(it) }
                server.credential?.let { builder.setPassword(it) }
                iceServers.add(builder.createIceServer())
            }
        }

        return iceServers
    }

    private fun createLocalAudioTrack() {
        Log.d(TAG, "═══════════════════════════════════════════════════")
        Log.d(TAG, "🎤 createLocalAudioTrack CALLED")
        Log.d(TAG, "   Thread: ${Thread.currentThread().name}")
        Log.d(TAG, "   peerConnectionFactory: ${peerConnectionFactory != null}")
        Log.d(TAG, "   peerConnection: ${peerConnection != null}")

        val audioConstraints = MediaConstraints().apply {
            // Echo cancellation - critical for preventing echo
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation2", "true"))

            // Noise suppression
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression2", "true"))

            // Auto gain control
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl2", "true"))

            // High pass filter to remove low frequency noise
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))

            // Typing noise detection
            mandatory.add(MediaConstraints.KeyValuePair("googTypingNoiseDetection", "true"))

            // Audio mirroring (prevent hearing yourself)
            mandatory.add(MediaConstraints.KeyValuePair("googAudioMirroring", "false"))
        }
        Log.d(TAG, "   ✓ Audio constraints created")

        audioSource = peerConnectionFactory?.createAudioSource(audioConstraints)
        Log.d(TAG, "   audioSource created: ${audioSource != null}")

        localAudioTrack = peerConnectionFactory?.createAudioTrack("audio0", audioSource)
        Log.d(TAG, "   localAudioTrack created: ${localAudioTrack != null}")

        localAudioTrack?.setEnabled(true)
        Log.d(TAG, "   localAudioTrack enabled: ${localAudioTrack?.enabled()}")

        val sender = peerConnection?.addTrack(localAudioTrack, listOf("stream0"))
        Log.d(TAG, "   Track added to peerConnection, sender: ${sender != null}")
        Log.d(TAG, "   Sender track: ${sender?.track()?.kind()}")

        // Log all transceivers after adding track
        Log.d(TAG, "   ─────────────────────────────────────────────")
        Log.d(TAG, "   TRANSCEIVERS AFTER ADDING LOCAL TRACK:")
        peerConnection?.transceivers?.forEachIndexed { index, transceiver ->
            Log.d(TAG, "   Transceiver[$index]:")
            Log.d(TAG, "     - mediaType: ${transceiver.mediaType}")
            Log.d(TAG, "     - direction: ${transceiver.direction}")
            Log.d(TAG, "     - currentDirection: ${transceiver.currentDirection}")
            Log.d(TAG, "     - mid: ${transceiver.mid}")
            Log.d(TAG, "     - sender.track: ${transceiver.sender?.track()?.kind()}")
            Log.d(TAG, "     - receiver.track: ${transceiver.receiver?.track()?.kind()}")
        }
        Log.d(TAG, "   ─────────────────────────────────────────────")

        Log.d(TAG, "═══════════════════════════════════════════════════")
        Log.d(TAG, "✅ Local audio track created - THIS DEVICE CAN NOW TRANSMIT AUDIO")
    }

    @Suppress("DEPRECATION")
    private fun createPeerConnectionObserver() = object : PeerConnection.Observer {
        override fun onSignalingChange(state: PeerConnection.SignalingState?) {
            Log.d(TAG, "Signaling state: $state")
        }

        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
            Log.d(TAG, "═══════════════════════════════════════════════════")
            Log.d(TAG, "🔗 ICE connection state: $state")
            when (state) {
                PeerConnection.IceConnectionState.CHECKING -> {
                    Log.d(TAG, "   🔍 ICE checking - attempting to connect...")
                }
                PeerConnection.IceConnectionState.CONNECTED -> {
                    Log.d(TAG, "   ✅ ICE CONNECTED - WebRTC call established!")
                    cancelConnectionTimeout()  // Connection succeeded, cancel timeout
                    _state.value = WebRTCState.CONNECTED

                    // CRITICAL: Re-apply audio configuration NOW
                    // WebRTC's JavaAudioDeviceModule resets the audio mode, so we must set it again
                    Log.d(TAG, "   🔊 Re-applying audio configuration (WebRTC resets it)...")
                    forceAudioModeForCall()

                    // Verify and log audio configuration
                    Log.d(TAG, "   ─────────────────────────────────────────────")
                    Log.d(TAG, "   AUDIO VERIFICATION ON ICE CONNECTED:")
                    Log.d(TAG, "   Local audio track: ${localAudioTrack != null}")
                    Log.d(TAG, "   Local audio enabled: ${localAudioTrack?.enabled()}")
                    Log.d(TAG, "   Remote audio track: ${remoteAudioTrack != null}")
                    Log.d(TAG, "   Remote audio enabled: ${remoteAudioTrack?.enabled()}")

                    // Log peer connection transceivers
                    peerConnection?.transceivers?.forEachIndexed { index, transceiver ->
                        Log.d(TAG, "   Transceiver[$index]:")
                        Log.d(TAG, "     - mediaType: ${transceiver.mediaType}")
                        Log.d(TAG, "     - direction: ${transceiver.direction}")
                        Log.d(TAG, "     - currentDirection: ${transceiver.currentDirection}")
                        Log.d(TAG, "     - mid: ${transceiver.mid}")
                        transceiver.receiver?.track()?.let { track ->
                            Log.d(TAG, "     - Receiver track: kind=${track.kind()}, enabled=${track.enabled()}, state=${track.state()}")
                        } ?: Log.d(TAG, "     - Receiver track: NULL")
                        transceiver.sender?.track()?.let { track ->
                            Log.d(TAG, "     - Sender track: kind=${track.kind()}, enabled=${track.enabled()}, state=${track.state()}")
                        } ?: Log.d(TAG, "     - Sender track: NULL")
                    }

                    // Verify audio manager state AFTER forcing the mode
                    audioManager?.let { am ->
                        Log.d(TAG, "   AudioManager state AFTER force:")
                        Log.d(TAG, "   - Mode: ${am.mode} (3=IN_COMMUNICATION)")
                        Log.d(TAG, "   - Speakerphone: ${am.isSpeakerphoneOn}")
                        Log.d(TAG, "   - Mic muted: ${am.isMicrophoneMute}")
                        Log.d(TAG, "   - Voice volume: ${am.getStreamVolume(AudioManager.STREAM_VOICE_CALL)}")
                    }
                    Log.d(TAG, "   ─────────────────────────────────────────────")
                }
                PeerConnection.IceConnectionState.COMPLETED -> {
                    Log.d(TAG, "   ✅ ICE COMPLETED - all candidates gathered")
                    cancelConnectionTimeout()  // Connection succeeded, cancel timeout
                    _state.value = WebRTCState.CONNECTED
                }
                PeerConnection.IceConnectionState.DISCONNECTED -> {
                    Log.w(TAG, "   ⚠️ ICE DISCONNECTED")
                    _state.value = WebRTCState.DISCONNECTED
                }
                PeerConnection.IceConnectionState.FAILED -> {
                    Log.e(TAG, "   ❌ ICE FAILED - could not establish connection")
                    _state.value = WebRTCState.FAILED
                }
                PeerConnection.IceConnectionState.CLOSED -> {
                    Log.d(TAG, "   🚫 ICE CLOSED")
                    _state.value = WebRTCState.DISCONNECTED
                }
                else -> {
                    Log.d(TAG, "   ICE state: $state")
                }
            }
            Log.d(TAG, "═══════════════════════════════════════════════════")
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) {
            Log.d(TAG, "ICE receiving: $receiving")
        }

        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
            Log.d(TAG, "ICE gathering state: $state")
        }

        override fun onIceCandidate(candidate: IceCandidate?) {
            candidate?.let {
                Log.d(TAG, "Local ICE candidate: ${it.sdpMid}")
                scope.launch {
                    _localIceCandidates.emit(it)
                }
            }
        }

        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {
            Log.d(TAG, "ICE candidates removed")
        }

        override fun onAddStream(stream: MediaStream?) {
            Log.d(TAG, "═══════════════════════════════════════════════════")
            Log.d(TAG, "📥 onAddStream - REMOTE STREAM ADDED!")
            Log.d(TAG, "   stream: ${stream != null}")
            Log.d(TAG, "   stream.id: ${stream?.id}")
            Log.d(TAG, "   audioTracks count: ${stream?.audioTracks?.size ?: 0}")
            Log.d(TAG, "   videoTracks count: ${stream?.videoTracks?.size ?: 0}")
            
            // Enable all audio tracks in the stream
            stream?.audioTracks?.forEachIndexed { index, track ->
                Log.d(TAG, "   Audio track[$index]: id=${track.id()}, enabled=${track.enabled()}, state=${track.state()}")
                track.setEnabled(true)
                Log.d(TAG, "   Audio track[$index] ENABLED: ${track.enabled()}")
                
                // Store as remote audio track if we don't have one
                if (remoteAudioTrack == null) {
                    remoteAudioTrack = track
                    Log.d(TAG, "   ✅ Stored as remoteAudioTrack")
                }
            }
            Log.d(TAG, "═══════════════════════════════════════════════════")
        }

        override fun onRemoveStream(stream: MediaStream?) {
            Log.d(TAG, "Remote stream removed")
        }

        override fun onDataChannel(channel: DataChannel?) {
            Log.d(TAG, "Data channel: ${channel?.label()}")
        }

        override fun onRenegotiationNeeded() {
            Log.d(TAG, "Renegotiation needed")
        }

        override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
            Log.d(TAG, "═══════════════════════════════════════════════════")
            Log.d(TAG, "🔊 onAddTrack - REMOTE TRACK ADDED!")
            Log.d(TAG, "   Thread: ${Thread.currentThread().name}")
            Log.d(TAG, "   receiver: ${receiver != null}")
            Log.d(TAG, "   receiver.track: ${receiver?.track()}")
            Log.d(TAG, "   receiver.track kind: ${receiver?.track()?.kind()}")
            Log.d(TAG, "   receiver.track id: ${receiver?.track()?.id()}")
            Log.d(TAG, "   receiver.track enabled: ${receiver?.track()?.enabled()}")
            Log.d(TAG, "   streams count: ${streams?.size ?: 0}")

            // Get the remote audio track and enable it
            receiver?.track()?.let { track ->
                Log.d(TAG, "   Processing track: kind=${track.kind()}, id=${track.id()}")
                if (track.kind() == MediaStreamTrack.AUDIO_TRACK_KIND) {
                    remoteAudioTrack = track as? AudioTrack
                    if (remoteAudioTrack != null) {
                        // CRITICAL: Enable the remote audio track for playback
                        remoteAudioTrack?.setEnabled(true)
                        Log.d(TAG, "   ✅ Remote audio track ENABLED for playback!")
                        Log.d(TAG, "   remoteAudioTrack.id: ${remoteAudioTrack?.id()}")
                        Log.d(TAG, "   remoteAudioTrack.enabled: ${remoteAudioTrack?.enabled()}")
                        Log.d(TAG, "   remoteAudioTrack.state: ${remoteAudioTrack?.state()}")

                        // Verify audio manager state
                        audioManager?.let { am ->
                            Log.d(TAG, "   Current audio state:")
                            Log.d(TAG, "   - Mode: ${am.mode}")
                            Log.d(TAG, "   - Speakerphone: ${am.isSpeakerphoneOn}")
                            Log.d(TAG, "   - Voice call volume: ${am.getStreamVolume(AudioManager.STREAM_VOICE_CALL)}")
                        }
                    } else {
                        Log.e(TAG, "   ❌ Failed to cast track to AudioTrack!")
                    }
                } else {
                    Log.d(TAG, "   Skipping non-audio track: ${track.kind()}")
                }
            }

            // Also check streams for audio tracks (backup method)
            streams?.forEach { stream ->
                Log.d(TAG, "   Stream ID: ${stream.id}")
                Log.d(TAG, "   Audio tracks in stream: ${stream.audioTracks?.size ?: 0}")
                stream.audioTracks?.forEachIndexed { index, audioTrack ->
                    Log.d(TAG, "   Audio track [$index]: id=${audioTrack.id()}, enabled=${audioTrack.enabled()}")
                    audioTrack.setEnabled(true)
                    if (remoteAudioTrack == null) {
                        remoteAudioTrack = audioTrack
                        Log.d(TAG, "   ✅ Remote audio track from stream enabled")
                    }
                }
            }
            Log.d(TAG, "═══════════════════════════════════════════════════")
        }

        override fun onTrack(transceiver: RtpTransceiver?) {
            Log.d(TAG, "═══════════════════════════════════════════════════")
            Log.d(TAG, "🔊 onTrack - TRANSCEIVER RECEIVED!")
            Log.d(TAG, "   Thread: ${Thread.currentThread().name}")
            Log.d(TAG, "   transceiver: ${transceiver != null}")
            Log.d(TAG, "   mediaType: ${transceiver?.mediaType}")
            Log.d(TAG, "   direction: ${transceiver?.direction}")
            Log.d(TAG, "   mid: ${transceiver?.mid}")
            Log.d(TAG, "   currentDirection: ${transceiver?.currentDirection}")
            Log.d(TAG, "   isStopped: ${transceiver?.isStopped}")

            // Enable the receiver's track for audio
            if (transceiver?.mediaType == MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO) {
                Log.d(TAG, "   This is an AUDIO transceiver")

                // Get receiver track
                transceiver.receiver?.track()?.let { track ->
                    Log.d(TAG, "   Receiver track: kind=${track.kind()}, id=${track.id()}, enabled=${track.enabled()}")
                    if (track is AudioTrack) {
                        track.setEnabled(true)
                        remoteAudioTrack = track
                        Log.d(TAG, "   ✅ Remote audio track from transceiver ENABLED!")
                        Log.d(TAG, "   Track state: ${track.state()}")
                        Log.d(TAG, "   Track enabled: ${track.enabled()}")
                    }
                } ?: run {
                    Log.w(TAG, "   ⚠️ Receiver track is null!")
                }

                // Log sender track (local audio)
                transceiver.sender?.track()?.let { track ->
                    Log.d(TAG, "   Sender track (local): kind=${track.kind()}, id=${track.id()}, enabled=${track.enabled()}")
                }

                // Ensure direction allows receiving
                if (transceiver.direction == RtpTransceiver.RtpTransceiverDirection.SEND_ONLY) {
                    Log.w(TAG, "   ⚠️ Transceiver is SEND_ONLY - changing to SEND_RECV")
                    transceiver.direction = RtpTransceiver.RtpTransceiverDirection.SEND_RECV
                }
            } else {
                Log.d(TAG, "   Skipping non-audio transceiver: ${transceiver?.mediaType}")
            }
            Log.d(TAG, "═══════════════════════════════════════════════════")
        }
    }

    private fun createOffer() {
        // Guard: Prevent duplicate offers
        if (hasOfferBeenCreated) {
            Log.w(TAG, "⚠️ Offer already created - skipping duplicate")
            return
        }
        hasOfferBeenCreated = true

        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp?.let {
                    Log.d(TAG, "═══════════════════════════════════════════════════")
                    Log.d(TAG, "📤 OFFER CREATED!")
                    Log.d(TAG, "   type: ${it.type}")
                    Log.d(TAG, "   description length: ${it.description.length}")
                    
                    // Check SDP for audio
                    val hasAudio = it.description.contains("m=audio")
                    val audioDirection = when {
                        it.description.contains("a=sendrecv") -> "sendrecv"
                        it.description.contains("a=sendonly") -> "sendonly"
                        it.description.contains("a=recvonly") -> "recvonly"
                        it.description.contains("a=inactive") -> "inactive"
                        else -> "unknown"
                    }
                    Log.d(TAG, "   hasAudio: $hasAudio")
                    Log.d(TAG, "   audioDirection: $audioDirection")
                    
                    // Log transceiver state at offer creation
                    Log.d(TAG, "   Transceivers at offer creation:")
                    peerConnection?.transceivers?.forEachIndexed { index, transceiver ->
                        Log.d(TAG, "     [$index] type=${transceiver.mediaType}, dir=${transceiver.direction}, mid=${transceiver.mid}")
                    }
                    Log.d(TAG, "═══════════════════════════════════════════════════")
                    
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            Log.d(TAG, "Local description set")
                            scope.launch {
                                _localSdp.emit(it)
                            }
                        }
                        override fun onCreateFailure(error: String?) {
                            Log.e(TAG, "Set local description error: $error")
                        }
                        override fun onSetFailure(error: String?) {
                            Log.e(TAG, "Set local description error: $error")
                        }
                    }, it)
                }
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "Create offer error: $error")
            }
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    // Track if remote description has been set to prevent duplicate calls
    private var hasRemoteDescriptionBeenSet = false

    fun setRemoteDescription(sdp: SessionDescription, isAnswer: Boolean = false) {
        Log.d(TAG, "═══════════════════════════════════════════════════")
        Log.d(TAG, "📥 setRemoteDescription CALLED")
        Log.d(TAG, "   Thread: ${Thread.currentThread().name}")
        Log.d(TAG, "   isAnswer: $isAnswer")
        Log.d(TAG, "   sdp.type: ${sdp.type}")
        Log.d(TAG, "   sdp.description length: ${sdp.description.length}")
        Log.d(TAG, "   hasRemoteDescriptionBeenSet: $hasRemoteDescriptionBeenSet")
        Log.d(TAG, "   peerConnection: ${peerConnection != null}")

        // Guard: If peerConnection is null, we can't set remote description
        // The caller (CallManager) should queue this and retry later
        if (peerConnection == null) {
            Log.w(TAG, "   ⚠️ PeerConnection is NULL - cannot set remote description!")
            Log.w(TAG, "   ⚠️ Caller should queue this SDP and retry after WebRTC is initialized")
            Log.d(TAG, "═══════════════════════════════════════════════════")
            return  // DON'T set hasRemoteDescriptionBeenSet here!
        }

        // Guard: Prevent duplicate setRemoteDescription calls
        if (hasRemoteDescriptionBeenSet) {
            Log.w(TAG, "   ⚠️ Remote description already set - skipping duplicate")
            Log.d(TAG, "═══════════════════════════════════════════════════")
            return
        }
        hasRemoteDescriptionBeenSet = true
        Log.d(TAG, "   ✓ Set hasRemoteDescriptionBeenSet = true")

        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d(TAG, "   ✅ Remote description set successfully!")
                Log.d(TAG, "   isAnswer: $isAnswer")
                if (!isAnswer) {
                    // We received an offer, create answer
                    Log.d(TAG, "   📤 Creating answer...")
                    createAnswer()
                }
            }
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "   ❌ Set remote description create error: $error")
            }
            override fun onSetFailure(error: String?) {
                Log.e(TAG, "   ❌ Set remote description set error: $error")
            }
        }, sdp)
        Log.d(TAG, "═══════════════════════════════════════════════════")
    }

    private fun createAnswer() {
        // Guard: Prevent duplicate answers
        if (hasAnswerBeenCreated) {
            Log.w(TAG, "⚠️ Answer already created - skipping duplicate")
            return
        }
        hasAnswerBeenCreated = true

        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }

        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp?.let {
                    Log.d(TAG, "═══════════════════════════════════════════════════")
                    Log.d(TAG, "📤 ANSWER CREATED!")
                    Log.d(TAG, "   type: ${it.type}")
                    Log.d(TAG, "   description length: ${it.description.length}")
                    
                    // Check SDP for audio
                    val hasAudio = it.description.contains("m=audio")
                    val audioDirection = when {
                        it.description.contains("a=sendrecv") -> "sendrecv"
                        it.description.contains("a=sendonly") -> "sendonly"
                        it.description.contains("a=recvonly") -> "recvonly"
                        it.description.contains("a=inactive") -> "inactive"
                        else -> "unknown"
                    }
                    Log.d(TAG, "   hasAudio: $hasAudio")
                    Log.d(TAG, "   audioDirection: $audioDirection")
                    
                    // Log transceiver state at answer creation
                    Log.d(TAG, "   Transceivers at answer creation:")
                    peerConnection?.transceivers?.forEachIndexed { index, transceiver ->
                        Log.d(TAG, "     [$index] type=${transceiver.mediaType}, dir=${transceiver.direction}, mid=${transceiver.mid}")
                    }
                    Log.d(TAG, "═══════════════════════════════════════════════════")
                    
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            Log.d(TAG, "Local description (answer) set")
                            scope.launch {
                                _localSdp.emit(it)
                            }
                        }
                        override fun onCreateFailure(error: String?) {
                            Log.e(TAG, "Set local description error: $error")
                        }
                        override fun onSetFailure(error: String?) {
                            Log.e(TAG, "Set local description error: $error")
                        }
                    }, it)
                }
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "Create answer error: $error")
            }
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    fun addIceCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
        Log.d(TAG, "Remote ICE candidate added")
    }

    fun setMuted(muted: Boolean) {
        isMuted = muted
        localAudioTrack?.setEnabled(!muted)
        Log.d(TAG, "Mute: $muted")
    }

    fun isMuted(): Boolean = isMuted

    /**
     * Enable/disable speakerphone
     */
    @Suppress("DEPRECATION")
    fun setSpeakerEnabled(enabled: Boolean) {
        audioManager?.isSpeakerphoneOn = enabled
        Log.d(TAG, "Speaker: $enabled")
    }

    @Suppress("DEPRECATION")
    fun isSpeakerEnabled(): Boolean = audioManager?.isSpeakerphoneOn ?: false

    fun close() {
        Log.d(TAG, "═══════════════════════════════════════════════════")
        Log.d(TAG, "🛑 Closing WebRTC connection")

        // Log final state before closing
        Log.d(TAG, "   Final state:")
        Log.d(TAG, "   - Local audio track: ${localAudioTrack != null}, enabled=${localAudioTrack?.enabled()}")
        Log.d(TAG, "   - Remote audio track: ${remoteAudioTrack != null}, enabled=${remoteAudioTrack?.enabled()}")
        Log.d(TAG, "   - Connection state: ${_state.value}")

        // Cancel any pending timeout
        cancelConnectionTimeout()

        // Restore audio configuration
        restoreAudioConfig()

        localAudioTrack?.setEnabled(false)
        localAudioTrack?.dispose()
        localAudioTrack = null

        remoteAudioTrack?.setEnabled(false)
        remoteAudioTrack = null

        audioSource?.dispose()
        audioSource = null

        peerConnection?.close()
        peerConnection = null

        _state.value = WebRTCState.IDLE
        Log.d(TAG, "═══════════════════════════════════════════════════")
    }

    /**
     * Log current WebRTC statistics for debugging
     */
    @Suppress("DEPRECATION")
    fun logStats() {
        Log.d(TAG, "═══════════════════════════════════════════════════")
        Log.d(TAG, "📊 WEBRTC STATS DEBUG")
        Log.d(TAG, "   State: ${_state.value}")
        Log.d(TAG, "   Local audio track: ${localAudioTrack?.id()}, enabled=${localAudioTrack?.enabled()}")
        Log.d(TAG, "   Remote audio track: ${remoteAudioTrack?.id()}, enabled=${remoteAudioTrack?.enabled()}")
        Log.d(TAG, "   Muted: $isMuted")

        // Log transceiver states
        peerConnection?.transceivers?.forEachIndexed { index, transceiver ->
            Log.d(TAG, "   Transceiver[$index]:")
            Log.d(TAG, "     - Type: ${transceiver.mediaType}")
            Log.d(TAG, "     - Direction: ${transceiver.direction}")
            Log.d(TAG, "     - Current direction: ${transceiver.currentDirection}")
            Log.d(TAG, "     - Stopped: ${transceiver.isStopped}")
            transceiver.sender?.track()?.let { track ->
                Log.d(TAG, "     - Sender track: ${track.kind()}, enabled=${track.enabled()}")
            }
            transceiver.receiver?.track()?.let { track ->
                Log.d(TAG, "     - Receiver track: ${track.kind()}, enabled=${track.enabled()}")
            }
        }

        // Log audio manager state
        audioManager?.let { am ->
            Log.d(TAG, "   AudioManager:")
            Log.d(TAG, "     - Mode: ${am.mode}")
            Log.d(TAG, "     - Speakerphone: ${am.isSpeakerphoneOn}")
            Log.d(TAG, "     - Microphone muted: ${am.isMicrophoneMute}")
            Log.d(TAG, "     - Voice call volume: ${am.getStreamVolume(AudioManager.STREAM_VOICE_CALL)}")
            Log.d(TAG, "     - Music volume: ${am.getStreamVolume(AudioManager.STREAM_MUSIC)}")
        }
        Log.d(TAG, "═══════════════════════════════════════════════════")
    }

    /**
     * Verify audio is properly configured and log any issues
     */
    fun verifyAudioSetup(): Boolean {
        Log.d(TAG, "═══════════════════════════════════════════════════")
        Log.d(TAG, "🔍 VERIFYING AUDIO SETUP")

        var isValid = true

        // Check local audio track
        if (localAudioTrack == null) {
            Log.e(TAG, "   ❌ Local audio track is NULL")
            isValid = false
        } else if (!localAudioTrack!!.enabled()) {
            Log.e(TAG, "   ❌ Local audio track is DISABLED")
            isValid = false
        } else {
            Log.d(TAG, "   ✅ Local audio track OK")
        }

        // Check remote audio track
        if (remoteAudioTrack == null) {
            Log.e(TAG, "   ❌ Remote audio track is NULL - cannot hear remote party!")
            isValid = false
        } else if (!remoteAudioTrack!!.enabled()) {
            Log.e(TAG, "   ❌ Remote audio track is DISABLED - cannot hear remote party!")
            isValid = false
        } else {
            Log.d(TAG, "   ✅ Remote audio track OK")
        }

        // Check audio manager
        audioManager?.let { am ->
            if (am.mode != AudioManager.MODE_IN_COMMUNICATION) {
                Log.w(TAG, "   ⚠️ Audio mode is not MODE_IN_COMMUNICATION: ${am.mode}")
                Log.d(TAG, "   🔧 Attempting to fix audio mode...")
                forceAudioModeForCall()
                // Check again
                if (am.mode == AudioManager.MODE_IN_COMMUNICATION) {
                    Log.d(TAG, "   ✅ Audio mode FIXED!")
                } else {
                    Log.e(TAG, "   ❌ Could not fix audio mode!")
                }
            } else {
                Log.d(TAG, "   ✅ Audio mode OK")
            }

            val volume = am.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
            if (volume == 0) {
                Log.e(TAG, "   ❌ Voice call volume is ZERO!")
                isValid = false
            } else {
                Log.d(TAG, "   ✅ Voice call volume OK: $volume")
            }
        } ?: run {
            Log.e(TAG, "   ❌ AudioManager is NULL")
            isValid = false
        }

        Log.d(TAG, "   Result: ${if (isValid) "✅ AUDIO SETUP VALID" else "❌ AUDIO ISSUES DETECTED"}")
        Log.d(TAG, "═══════════════════════════════════════════════════")
        return isValid
    }

    fun dispose() {
        Log.d(TAG, "═══════════════════════════════════════════════════")
        Log.d(TAG, "🛑 DISPOSING WebRTCManager")

        close()

        // Release voice anonymizer
        ProductionVoiceAnonymizer.release()
        Log.d(TAG, "   ✓ Voice anonymizer released")

        // Cancel all coroutines to prevent memory leaks
        scope.cancel()
        Log.d(TAG, "   ✓ Coroutine scope cancelled")

        peerConnectionFactory?.dispose()
        peerConnectionFactory = null

        // CRITICAL: Release the AudioDeviceModule AFTER the factory is disposed
        // Each call must get a fresh ADM — reusing a stale one corrupts native
        // audio routing, causing one-way audio or echo/loopback.
        audioDeviceModule?.release()
        audioDeviceModule = null
        Log.d(TAG, "   ✓ AudioDeviceModule released")

        Log.d(TAG, "   ✓ WebRTCManager disposed")
        Log.d(TAG, "═══════════════════════════════════════════════════")
    }
}

