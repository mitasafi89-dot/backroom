package com.example.backroom.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.backroom.MainActivity
import com.example.backroom.R
import com.example.backroom.data.network.CallManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Firebase Cloud Messaging Service
 *
 * Handles push notifications when the app is not running or in background.
 * This is the fallback mechanism when WebSocket connection is not available.
 *
 * Flow:
 * 1. Server detects listener is not connected via WebSocket
 * 2. Server sends FCM push notification with preview data
 * 3. This service receives the push
 * 4. Shows notification and/or starts ListenerService
 */
class BackroomMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "BackroomFCM"

        // Notification channel for FCM messages
        const val CHANNEL_ID_FCM = "backroom_fcm_channel"
        const val NOTIFICATION_ID_FCM_INCOMING = 100
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "BackroomMessagingService created")
        createNotificationChannel()
    }

    /**
     * Called when a new FCM token is generated.
     * This happens on first app install or when token is refreshed.
     * Note: CallManager.registerFcmToken() may also send token on connection,
     * but server-side handles duplicates gracefully by overwriting.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "═══════════════════════════════════════════════════")
        Log.d(TAG, "🔑 New FCM token received (onNewToken callback)")
        Log.d(TAG, "   Token: ${token.take(20)}...${token.takeLast(10)}")
        Log.d(TAG, "   Note: This is a Firebase SDK callback for token refresh")
        Log.d(TAG, "═══════════════════════════════════════════════════")

        // Send the new token to the server
        // This is separate from registerFcmToken() - this handles token REFRESH
        // while registerFcmToken() handles initial registration on connection
        try {
            CallManager.updateFcmToken(token)
            Log.d(TAG, "✅ FCM token sent to server via onNewToken")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to send FCM token to server", e)
        }
    }

    /**
     * Called when a message is received from FCM.
     *
     * Message types:
     * - incomingPreview: Someone wants to talk
     * - matchMade: Call has been matched
     * - callEnded: Call has ended
     */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        Log.d(TAG, "═══════════════════════════════════════════════════")
        Log.d(TAG, "📨 FCM message received")
        Log.d(TAG, "   From: ${message.from}")
        Log.d(TAG, "   Data: ${message.data}")
        Log.d(TAG, "   Notification: ${message.notification?.title}")
        Log.d(TAG, "═══════════════════════════════════════════════════")

        val data = message.data
        val messageType = data["type"] ?: return

        when (messageType) {
            "incomingPreview" -> handleIncomingPreview(data)
            "matchMade" -> handleMatchMade(data)
            "callEnded" -> handleCallEnded(data)
            "wakeUp" -> handleWakeUp(data)
            else -> {
                Log.w(TAG, "Unknown FCM message type: $messageType")
            }
        }
    }

    /**
     * Handle incoming preview push notification.
     * Shows a notification and optionally starts the ListenerService.
     */
    private fun handleIncomingPreview(data: Map<String, String>) {
        Log.d(TAG, "📞 Handling incoming preview")

        val shareId = data["shareId"] ?: return
        val topic = data["topic"] ?: "Someone"
        val tone = data["tone"] ?: ""
        val previewText = data["previewText"] ?: ""
        val durationMinutes = data["durationMinutes"]?.toIntOrNull() ?: 10
        val countdownSeconds = data["countdownSeconds"]?.toIntOrNull() ?: 30

        Log.d(TAG, "   ShareId: $shareId")
        Log.d(TAG, "   Topic: $topic")
        Log.d(TAG, "   Tone: $tone")
        Log.d(TAG, "   Preview: ${previewText.take(50)}...")

        // Try to start ListenerService to handle this via WebSocket
        // This will establish connection and get the full preview
        try {
            ListenerService.startListening(this)
            Log.d(TAG, "   ✓ Started ListenerService")
        } catch (e: Exception) {
            Log.e(TAG, "   ❌ Failed to start ListenerService", e)
        }

        // Also show a notification immediately (in case service takes time to connect)
        showIncomingPreviewNotification(shareId, topic, tone, previewText, durationMinutes)
    }

    /**
     * Handle match made push notification.
     * Opens the app directly to the call screen.
     */
    private fun handleMatchMade(data: Map<String, String>) {
        Log.d(TAG, "🎯 Handling match made")

        val callId = data["callId"] ?: return
        val role = data["role"] ?: "listener"

        Log.d(TAG, "   CallId: $callId")
        Log.d(TAG, "   Role: $role")

        // Dismiss any incoming preview notification
        dismissNotification(NOTIFICATION_ID_FCM_INCOMING)

        // Open app for the call
        openAppForCall(callId)
    }

    /**
     * Handle call ended push notification.
     * Dismisses any active notifications.
     */
    private fun handleCallEnded(data: Map<String, String>) {
        Log.d(TAG, "📴 Handling call ended")

        val callId = data["callId"] ?: return
        val reason = data["reason"] ?: ""

        Log.d(TAG, "   CallId: $callId")
        Log.d(TAG, "   Reason: $reason")

        // Dismiss notifications
        dismissNotification(NOTIFICATION_ID_FCM_INCOMING)
        dismissNotification(ListenerService.NOTIFICATION_ID_INCOMING)
    }

    /**
     * Handle wake up push notification.
     * Used to wake the app and establish WebSocket connection.
     */
    private fun handleWakeUp(data: Map<String, String>) {
        Log.d(TAG, "⏰ Handling wake up")

        // Start ListenerService to re-establish connection
        try {
            ListenerService.startListening(this)
            Log.d(TAG, "   ✓ Started ListenerService for wake up")
        } catch (e: Exception) {
            Log.e(TAG, "   ❌ Failed to start ListenerService", e)
        }
    }

    private fun showIncomingPreviewNotification(
        shareId: String,
        topic: String,
        tone: String,
        previewText: String,
        durationMinutes: Int
    ) {
        Log.d(TAG, "🔔 Showing incoming preview notification")

        // Intent to open app
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("incomingPreview", true)
            putExtra("shareId", shareId)
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 100, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Accept action - opens app and accepts
        val acceptIntent = Intent(this, ListenerService::class.java).apply {
            action = ListenerService.ACTION_ACCEPT_PREVIEW
        }
        val acceptPendingIntent = PendingIntent.getService(
            this, 101, acceptIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Decline action
        val declineIntent = Intent(this, ListenerService::class.java).apply {
            action = ListenerService.ACTION_DECLINE_PREVIEW
        }
        val declinePendingIntent = PendingIntent.getService(
            this, 102, declineIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val contentText = if (tone.isNotEmpty()) {
            "$topic • $tone • $durationMinutes min"
        } else {
            "$topic • $durationMinutes min"
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_FCM)
            .setContentTitle("Someone wants to talk")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("\"$previewText\"\n\n$contentText"))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .addAction(0, "Accept", acceptPendingIntent)
            .addAction(0, "Decline", declinePendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID_FCM_INCOMING, notification)

        Log.d(TAG, "   ✓ Notification shown")
    }

    private fun openAppForCall(callId: String) {
        Log.d(TAG, "📱 Opening app for call: $callId")

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("callId", callId)
            putExtra("navigateToCall", true)
        }
        startActivity(intent)
    }

    private fun dismissNotification(notificationId: Int) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.cancel(notificationId)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_FCM,
                "Incoming Calls (Background)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for incoming calls when app is not running"
                enableVibration(true)
                enableLights(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}

