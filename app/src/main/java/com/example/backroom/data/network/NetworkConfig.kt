package com.example.backroom.data.network

import com.example.backroom.BuildConfig

/**
 * Network configuration for Backroom backend services.
 *
 * Automatically switches between development and production based on build type.
 */
object NetworkConfig {

    // ============================================
    // ENVIRONMENT CONFIGURATION
    // ============================================

    /**
     * Set to true when deploying to production.
     * This can also be tied to BuildConfig.DEBUG
     */
    private val IS_PRODUCTION = !BuildConfig.DEBUG

    // ============================================
    // DEVELOPMENT SETTINGS (Local WiFi)
    // Change SERVER_IP to your computer's local IP
    // ============================================

    private const val DEV_SERVER_IP = "192.168.1.160"
    private const val DEV_REST_API_URL = "http://$DEV_SERVER_IP:8080/api/v1"
    private const val DEV_WS_SIGNALING_URL = "ws://$DEV_SERVER_IP:8443/ws"
    private const val DEV_TURN_URL = "turn:$DEV_SERVER_IP:3478"

    // ============================================
    // PRODUCTION SETTINGS (Public Internet)
    // Update PROD_DOMAIN to your actual domain
    // ============================================

    private const val PROD_DOMAIN = "backroom.llc"
    private const val PROD_REST_API_URL = "https://$PROD_DOMAIN/api/v1"
    private const val PROD_WS_SIGNALING_URL = "wss://ws.$PROD_DOMAIN/ws"
    private const val PROD_TURN_URL = "turn:turn.$PROD_DOMAIN:3478"

    // ============================================
    // ACTIVE CONFIGURATION
    // ============================================

    /** REST API base URL */
    val REST_API_URL: String
        get() = if (IS_PRODUCTION) PROD_REST_API_URL else DEV_REST_API_URL

    /** WebSocket signaling server URL */
    val WS_SIGNALING_URL: String
        get() = if (IS_PRODUCTION) PROD_WS_SIGNALING_URL else DEV_WS_SIGNALING_URL

    /** TURN server URL */
    val TURN_URL: String
        get() = if (IS_PRODUCTION) PROD_TURN_URL else DEV_TURN_URL

    /** STUN server URL (using Google's public STUN for reliability) */
    const val STUN_URL = "stun:stun.l.google.com:19302"

    // ============================================
    // TURN CREDENTIALS
    // In production, these should come from the server
    // ============================================

    const val TURN_USERNAME = "backroom"
    const val TURN_CREDENTIAL = "backroom_turn_secret"

    // ============================================
    // TIMEOUTS
    // ============================================

    const val WS_CONNECT_TIMEOUT_MS = 10_000L
    const val WS_READ_TIMEOUT_MS = 30_000L
    const val API_TIMEOUT_MS = 30_000L

    // ============================================
    // ICE SERVERS FOR WEBRTC
    // ============================================

    fun getIceServers(): List<IceServer> {
        return listOf(
            IceServer(urls = listOf(STUN_URL)),
            IceServer(
                urls = listOf(TURN_URL),
                username = TURN_USERNAME,
                credential = TURN_CREDENTIAL
            )
        )
    }

    /**
     * For debugging - print current configuration
     */
    fun getConfigSummary(): String {
        return """
            Environment: ${if (IS_PRODUCTION) "PRODUCTION" else "DEVELOPMENT"}
            REST API: $REST_API_URL
            WebSocket: $WS_SIGNALING_URL
            TURN: $TURN_URL
        """.trimIndent()
    }
}

/**
 * ICE Server configuration for WebRTC
 */
data class IceServer(
    val urls: List<String>,
    val username: String? = null,
    val credential: String? = null
)

