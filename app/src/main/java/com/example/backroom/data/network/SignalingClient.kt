package com.example.backroom.data.network

import android.util.Log
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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * WebSocket connection state
 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING
}

/**
 * Incoming message from signaling server
 */
sealed class SignalingMessage {
    data class Connected(val clientId: String) : SignalingMessage()
    data class Pong(val timestamp: Long) : SignalingMessage()
    data class AvailabilityConfirmed(val available: Boolean) : SignalingMessage()
    data class ShareSubmitted(val shareId: String, val status: String) : SignalingMessage()
    data class ShareExpired(val shareId: String, val reason: String) : SignalingMessage()
    data class ShareCancelled(val shareId: String) : SignalingMessage()
    data class IncomingPreview(
        val shareId: String,
        val topic: String,
        val tone: String,
        val previewText: String,
        val durationMinutes: Int,
        val countdownSeconds: Int
    ) : SignalingMessage()
    data class MatchMade(
        val callId: String,
        val role: String,
        val topic: String?,
        val intent: String?,
        val durationMinutes: Int,
        val turnServers: List<TurnServer>
    ) : SignalingMessage()
    data class WebRtcOffer(val callId: String, val sdp: String) : SignalingMessage()
    data class WebRtcAnswer(val callId: String, val sdp: String) : SignalingMessage()
    data class IceCandidate(val callId: String, val candidate: String, val sdpMid: String?, val sdpMLineIndex: Int?) : SignalingMessage()
    data class CallEnded(val callId: String, val reason: String) : SignalingMessage()
    data class RemoteMuteState(val callId: String, val muted: Boolean) : SignalingMessage()
    data class Error(val message: String) : SignalingMessage()
}

data class TurnServer(
    val urls: List<String>,
    val username: String?,
    val credential: String?
)

/**
 * WebSocket Signaling Client
 *
 * Connects to the signaling server and handles real-time messaging
 * for call matching and WebRTC signaling.
 */
class SignalingClient {

    private val TAG = "SignalingClient"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var webSocket: WebSocket? = null
    private var clientId: String? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _messages = MutableSharedFlow<SignalingMessage>()
    val messages: SharedFlow<SignalingMessage> = _messages.asSharedFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(NetworkConfig.WS_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(NetworkConfig.WS_READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .writeTimeout(NetworkConfig.WS_READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private var reconnectAttempts = 0
    // Never give up reconnecting - essential for maintaining connection when server restarts
    private val maxReconnectAttempts = Int.MAX_VALUE
    // Max delay between reconnect attempts (30 seconds)
    private val maxReconnectDelayMs = 30_000L

    /**
     * Connect to the signaling server
     */
    fun connect() {
        Log.d(TAG, "═══════════════════════════════════════════════════")
        Log.d(TAG, "🔌 connect() CALLED")
        Log.d(TAG, "   Thread: ${Thread.currentThread().name}")
        Log.d(TAG, "   Current state: ${_connectionState.value}")
        Log.d(TAG, "   reconnectAttempts: $reconnectAttempts")

        if (_connectionState.value == ConnectionState.CONNECTING ||
            _connectionState.value == ConnectionState.CONNECTED) {
            Log.d(TAG, "   ⚠️ Already connecting or connected - returning early")
            Log.d(TAG, "═══════════════════════════════════════════════════")
            return
        }

        // Reset reconnect attempts when explicitly connecting
        reconnectAttempts = 0
        Log.d(TAG, "   ✓ Reset reconnectAttempts to 0")

        _connectionState.value = ConnectionState.CONNECTING
        val url = NetworkConfig.WS_SIGNALING_URL
        Log.d(TAG, "   🌐 URL: $url")

        val request = Request.Builder()
            .url(url)
            .build()

        Log.d(TAG, "   Creating WebSocket connection...")
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "═══════════════════════════════════════════════════")
                Log.d(TAG, "✅ WebSocket onOpen")
                Log.d(TAG, "   Response code: ${response.code}")
                Log.d(TAG, "   Response message: ${response.message}")
                Log.d(TAG, "═══════════════════════════════════════════════════")
                _connectionState.value = ConnectionState.CONNECTED
                reconnectAttempts = 0
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "📨 onMessage: ${text.take(200)}${if (text.length > 200) "..." else ""}")
                handleMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "📤 WebSocket onClosing: code=$code, reason=$reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "❌ WebSocket onClosed: code=$code, reason=$reason")
                _connectionState.value = ConnectionState.DISCONNECTED
                attemptReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "═══════════════════════════════════════════════════")
                Log.e(TAG, "❌ WebSocket onFailure")
                Log.e(TAG, "   Error: ${t.message}")
                Log.e(TAG, "   Response: ${response?.code} ${response?.message}")
                Log.e(TAG, "═══════════════════════════════════════════════════", t)
                _connectionState.value = ConnectionState.DISCONNECTED
                attemptReconnect()
            }
        })
        Log.d(TAG, "   ✓ WebSocket connection initiated")
        Log.d(TAG, "═══════════════════════════════════════════════════")
    }

    /**
     * Disconnect from the signaling server (can reconnect later)
     */
    fun disconnect() {
        Log.d(TAG, "🔌 disconnect() called")
        Log.d(TAG, "   Closing WebSocket...")
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
        reconnectAttempts = maxReconnectAttempts // Prevent auto-reconnect
        Log.d(TAG, "   ✓ Disconnected")
    }

    /**
     * Permanently destroy the client (cancels all coroutines)
     * Call this only when the app is terminating
     */
    fun destroy() {
        disconnect()
        scope.cancel()
    }

    /**
     * Register user with the server
     */
    fun register(userId: String, role: String) {
        send("register", mapOf(
            "userId" to userId,
            "role" to role
        ))
    }

    /**
     * Set listener availability
     */
    fun setAvailability(available: Boolean, topics: List<String> = emptyList(), maxDuration: Int = 15) {
        send("availability", mapOf(
            "available" to available,
            "topics" to topics,
            "maxDuration" to maxDuration
        ))
    }

    /**
     * Submit a share request (sharer looking for listener)
     */
    fun submitShareRequest(topic: String, tone: String, intent: String, duration: Int) {
        send("shareRequest", mapOf(
            "topic" to topic,
            "tone" to tone,
            "intent" to intent,
            "duration" to duration
        ))
    }

    /**
     * Cancel pending share request
     */
    fun cancelShare() {
        send("cancelShare", emptyMap())
    }

    /**
     * Accept an incoming preview (listener accepts call)
     */
    fun acceptPreview(shareId: String) {
        send("previewAccept", mapOf("shareId" to shareId))
    }

    /**
     * Decline an incoming preview
     */
    fun declinePreview(shareId: String) {
        send("previewDecline", mapOf("shareId" to shareId))
    }

    /**
     * Send WebRTC offer
     */
    fun sendOffer(callId: String, sdp: String) {
        send("webrtc/offer", mapOf(
            "callId" to callId,
            "sdp" to sdp
        ))
    }

    /**
     * Send WebRTC answer
     */
    fun sendAnswer(callId: String, sdp: String) {
        send("webrtc/answer", mapOf(
            "callId" to callId,
            "sdp" to sdp
        ))
    }

    /**
     * Send ICE candidate
     */
    fun sendIceCandidate(callId: String, candidate: String, sdpMid: String?, sdpMLineIndex: Int?) {
        send("ice", mapOf(
            "callId" to callId,
            "candidate" to candidate,
            "sdpMid" to (sdpMid ?: ""),
            "sdpMLineIndex" to (sdpMLineIndex ?: 0)
        ))
    }

    /**
     * End call
     */
    fun endCall(callId: String) {
        send("endCall", mapOf("callId" to callId))
    }

    /**
     * Submit a report about a call/user
     */
    fun submitReport(callId: String, reason: String, details: String, blockUser: Boolean) {
        Log.d(TAG, "📝 Submitting report for call $callId: reason=$reason, blockUser=$blockUser")
        send("report", mapOf(
            "callId" to callId,
            "reason" to reason,
            "details" to details,
            "blockUser" to blockUser
        ))
    }

    /**
     * Block a user
     */
    fun blockUser(userId: String) {
        Log.d(TAG, "🚫 Blocking user: $userId")
        send("blockUser", mapOf("userId" to userId))
    }

    /**
     * Send mute state to remote party
     */
    fun sendMuteState(callId: String, isMuted: Boolean) {
        send("muteState", mapOf(
            "callId" to callId,
            "muted" to isMuted
        ))
    }

    /**
     * Update FCM token for push notifications
     */
    fun updateFcmToken(token: String) {
        Log.d(TAG, "🔑 Updating FCM token: ${token.take(20)}...")
        send("fcmToken", mapOf("token" to token))
    }

    /**
     * Send ping to keep connection alive
     */
    fun ping() {
        send("ping", emptyMap())
    }

    // ============================================
    // PRIVATE METHODS
    // ============================================

    private fun send(type: String, payload: Map<String, Any>) {
        Log.d(TAG, "═══════════════════════════════════════════════════")
        Log.d(TAG, "📤 send() CALLED")
        Log.d(TAG, "   type: $type")
        Log.d(TAG, "   payload: $payload")
        Log.d(TAG, "   connectionState: ${_connectionState.value}")

        val json = JSONObject().apply {
            put("type", type)
            put("payload", JSONObject(payload))
        }
        val message = json.toString()
        Log.d(TAG, "   message: ${message.take(200)}${if (message.length > 200) "..." else ""}")

        val ws = webSocket
        if (ws == null) {
            Log.w(TAG, "   ⚠️ Cannot send '$type' - WebSocket is null")
            Log.d(TAG, "═══════════════════════════════════════════════════")
            return
        }

        val success = ws.send(message)
        if (success) {
            Log.d(TAG, "   ✅ Message sent successfully")
        } else {
            Log.w(TAG, "   ⚠️ Failed to send - WebSocket send returned false")
        }
        Log.d(TAG, "═══════════════════════════════════════════════════")
    }

    private fun handleMessage(text: String) {
        scope.launch {
            try {
                val json = JSONObject(text)
                val type = json.getString("type")
                val payload = json.optJSONObject("payload") ?: JSONObject()

                val message = when (type) {
                    "connected" -> SignalingMessage.Connected(
                        clientId = payload.getString("clientId").also { clientId = it }
                    )
                    "pong" -> SignalingMessage.Pong(
                        timestamp = payload.getLong("timestamp")
                    )
                    "availabilityConfirmed" -> SignalingMessage.AvailabilityConfirmed(
                        available = payload.getBoolean("available")
                    )
                    "shareSubmitted", "shareQueued" -> SignalingMessage.ShareSubmitted(
                        shareId = payload.getString("shareId"),
                        status = payload.getString("status")
                    )
                    "shareExpired" -> SignalingMessage.ShareExpired(
                        shareId = payload.getString("shareId"),
                        reason = payload.optString("reason", "timeout")
                    )
                    "shareCancelled" -> SignalingMessage.ShareCancelled(
                        shareId = payload.optString("shareId", "")
                    )
                    "incomingPreview" -> SignalingMessage.IncomingPreview(
                        shareId = payload.getString("shareId"),
                        topic = payload.getString("topic"),
                        tone = payload.getString("tone"),
                        previewText = payload.getString("previewText"),
                        durationMinutes = payload.getInt("durationMinutes"),
                        countdownSeconds = payload.getInt("countdownSeconds")
                    )
                    "matchMade" -> {
                        val turnServersJson = payload.optJSONArray("turnServers")
                        val turnServers = mutableListOf<TurnServer>()
                        if (turnServersJson != null) {
                            for (i in 0 until turnServersJson.length()) {
                                val ts = turnServersJson.getJSONObject(i)
                                val urlsArray = ts.getJSONArray("urls")
                                val urls = (0 until urlsArray.length()).map { urlsArray.getString(it) }
                                turnServers.add(TurnServer(
                                    urls = urls,
                                    username = ts.optString("username"),
                                    credential = ts.optString("credential")
                                ))
                            }
                        }
                        SignalingMessage.MatchMade(
                            callId = payload.getString("callId"),
                            role = payload.getString("role"),
                            topic = payload.optString("topic"),
                            intent = payload.optString("intent"),
                            durationMinutes = payload.optInt("durationMinutes", 10),
                            turnServers = turnServers
                        )
                    }
                    "webrtc/offer" -> SignalingMessage.WebRtcOffer(
                        callId = payload.getString("callId"),
                        sdp = payload.getString("sdp")
                    )
                    "webrtc/answer" -> SignalingMessage.WebRtcAnswer(
                        callId = payload.getString("callId"),
                        sdp = payload.getString("sdp")
                    )
                    "ice" -> SignalingMessage.IceCandidate(
                        callId = payload.getString("callId"),
                        candidate = payload.getString("candidate"),
                        sdpMid = payload.optString("sdpMid"),
                        sdpMLineIndex = payload.optInt("sdpMLineIndex")
                    )
                    "callEnded" -> SignalingMessage.CallEnded(
                        callId = payload.getString("callId"),
                        reason = payload.optString("reason", "ended")
                    )
                    "remoteMuteState" -> SignalingMessage.RemoteMuteState(
                        callId = payload.getString("callId"),
                        muted = payload.getBoolean("muted")
                    )
                    "error" -> SignalingMessage.Error(
                        message = payload.optString("message", "Unknown error")
                    )
                    else -> {
                        Log.w(TAG, "Unknown message type: $type")
                        null
                    }
                }

                message?.let { _messages.emit(it) }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing message: ${e.message}", e)
            }
        }
    }

    private fun attemptReconnect() {
        if (reconnectAttempts >= maxReconnectAttempts) {
            Log.w(TAG, "Max reconnect attempts reached")
            return
        }

        reconnectAttempts++
        _connectionState.value = ConnectionState.RECONNECTING

        scope.launch {
            // Exponential backoff: 1s, 2s, 4s, 8s, 16s, then cap at maxReconnectDelayMs (30s)
            val delayMs = (1000L * (1 shl (reconnectAttempts - 1).coerceAtMost(4))).coerceAtMost(maxReconnectDelayMs)
            Log.d(TAG, "Reconnecting in ${delayMs}ms (attempt $reconnectAttempts)")
            delay(delayMs)
            connect()
        }
    }
}

