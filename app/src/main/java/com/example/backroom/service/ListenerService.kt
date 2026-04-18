package com.example.backroom.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.backroom.MainActivity
import com.example.backroom.R
import com.example.backroom.data.network.CallManager
import com.example.backroom.data.network.ConnectionState
import com.example.backroom.data.network.SignalingMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the listener connection alive
 * and shows notifications when someone wants to talk.
 */
class ListenerService : Service() {

    companion object {
        private const val TAG = "ListenerService"

        // Notification channels
        const val CHANNEL_ID_FOREGROUND = "backroom_listener_channel"
        const val CHANNEL_ID_INCOMING = "backroom_incoming_channel"

        // Notification IDs
        const val NOTIFICATION_ID_FOREGROUND = 1
        const val NOTIFICATION_ID_INCOMING = 2

        // Intent actions
        const val ACTION_START_LISTENING = "com.example.backroom.START_LISTENING"
        const val ACTION_STOP_LISTENING = "com.example.backroom.STOP_LISTENING"
        const val ACTION_ACCEPT_PREVIEW = "com.example.backroom.ACCEPT_PREVIEW"
        const val ACTION_DECLINE_PREVIEW = "com.example.backroom.DECLINE_PREVIEW"

        /**
         * Start the listener service
         */
        fun startListening(context: Context) {
            val intent = Intent(context, ListenerService::class.java).apply {
                action = ACTION_START_LISTENING
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Stop the listener service
         */
        fun stopListening(context: Context) {
            val intent = Intent(context, ListenerService::class.java).apply {
                action = ACTION_STOP_LISTENING
            }
            context.startService(intent)
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isListening = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "ListenerService created")
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "═══════════════════════════════════════════════════")
        Log.d(TAG, "📥 onStartCommand CALLED")
        Log.d(TAG, "   action: ${intent?.action}")
        Log.d(TAG, "   flags: $flags")
        Log.d(TAG, "   startId: $startId")
        Log.d(TAG, "   isListening: $isListening")
        Log.d(TAG, "═══════════════════════════════════════════════════")

        when (intent?.action) {
            ACTION_START_LISTENING -> {
                Log.d(TAG, "▶️ ACTION_START_LISTENING received")
                startListening()
            }
            ACTION_STOP_LISTENING -> {
                Log.d(TAG, "⏹️ ACTION_STOP_LISTENING received")
                stopListening()
            }
            ACTION_ACCEPT_PREVIEW -> {
                Log.d(TAG, "✅ ACTION_ACCEPT_PREVIEW received")
                CallManager.acceptPreview()
                dismissIncomingNotification()
            }
            ACTION_DECLINE_PREVIEW -> {
                Log.d(TAG, "❌ ACTION_DECLINE_PREVIEW received")
                CallManager.declinePreview()
                dismissIncomingNotification()
            }
            else -> {
                Log.d(TAG, "❓ Unknown or null action: ${intent?.action}")
            }
        }

        Log.d(TAG, "   Returning START_STICKY")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        Log.d(TAG, "ListenerService destroyed")
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // Foreground service channel (low importance, silent)
            val foregroundChannel = NotificationChannel(
                CHANNEL_ID_FOREGROUND,
                "Listener Mode",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when you're available to listen"
                setShowBadge(false)
            }

            // Incoming call channel (high importance, with sound)
            val incomingChannel = NotificationChannel(
                CHANNEL_ID_INCOMING,
                "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications when someone wants to talk"
                enableVibration(true)
                enableLights(true)
            }

            notificationManager.createNotificationChannel(foregroundChannel)
            notificationManager.createNotificationChannel(incomingChannel)
        }
    }

    private fun startListening() {
        Log.d(TAG, "═══════════════════════════════════════════════════")
        Log.d(TAG, "🎧 startListening() CALLED")
        Log.d(TAG, "   Current isListening: $isListening")

        if (isListening) {
            Log.d(TAG, "   ⚠️ Already listening - returning early")
            Log.d(TAG, "═══════════════════════════════════════════════════")
            return
        }
        isListening = true
        Log.d(TAG, "   ✓ Set isListening = true")

        // IMPORTANT: Start foreground FIRST before any other operations
        // Note: We use specialUse type only. Microphone access works when app is in foreground.
        Log.d(TAG, "   📢 Starting foreground notification...")
        startForeground(NOTIFICATION_ID_FOREGROUND, createForegroundNotification())
        Log.d(TAG, "   ✓ Foreground notification started")

        // Initialize CallManager if needed (after startForeground)
        Log.d(TAG, "   🔧 Initializing CallManager...")
        CallManager.initialize(applicationContext)
        Log.d(TAG, "   ✓ CallManager initialized")

        // Set as listener and available
        Log.d(TAG, "   👂 Setting role to listener with availability=true...")
        CallManager.setRoleListener(true)
        Log.d(TAG, "   ✓ Role set to listener")

        // Monitor connection state
        Log.d(TAG, "   🔌 Starting connection state monitor coroutine...")
        scope.launch {
            Log.d(TAG, "   [ConnectionMonitor] Coroutine started")
            CallManager.signalingClient.connectionState.collectLatest { state ->
                Log.d(TAG, "   [ConnectionMonitor] 🔌 State changed: $state")
                updateForegroundNotification(state)
            }
        }

        // Monitor incoming previews
        Log.d(TAG, "   📬 Starting incoming preview monitor coroutine...")
        scope.launch {
            Log.d(TAG, "   [PreviewMonitor] Coroutine started")
            CallManager.incomingPreview.collectLatest { preview ->
                Log.d(TAG, "   [PreviewMonitor] 📬 Preview changed: ${preview?.shareId ?: "null"}")
                if (preview != null) {
                    Log.d(TAG, "   [PreviewMonitor] Showing notification for: ${preview.topic}")
                    showIncomingNotification(preview)
                } else {
                    Log.d(TAG, "   [PreviewMonitor] Dismissing notification (preview is null)")
                    dismissIncomingNotification()
                }
            }
        }

        // Monitor match - open app when matched (only for listener role)
        Log.d(TAG, "   🎯 Starting match monitor coroutine...")
        scope.launch {
            Log.d(TAG, "   [MatchMonitor] Coroutine started")
            CallManager.matchInfo.collectLatest { match ->
                Log.d(TAG, "   [MatchMonitor] ═══════════════════════════════════════")
                Log.d(TAG, "   [MatchMonitor] 🎯 Match info changed")
                Log.d(TAG, "   [MatchMonitor]    match: ${match?.callId ?: "null"}")

                if (match != null) {
                    val isListener = match.role == "listener"
                    val isConsumed = CallManager.matchConsumed.value
                    val currentRole = CallManager.currentRole.value

                    Log.d(TAG, "   [MatchMonitor]    callId: ${match.callId}")
                    Log.d(TAG, "   [MatchMonitor]    match.role: ${match.role}")
                    Log.d(TAG, "   [MatchMonitor]    isListener: $isListener")
                    Log.d(TAG, "   [MatchMonitor]    isConsumed: $isConsumed")
                    Log.d(TAG, "   [MatchMonitor]    currentRole: $currentRole")

                    if (isListener && !isConsumed) {
                        Log.d(TAG, "   [MatchMonitor] ✅ Conditions met! Opening app...")
                        dismissIncomingNotification()
                        openAppForCall(match.callId)
                        Log.d(TAG, "   [MatchMonitor] ✓ App opened")
                    } else {
                        Log.d(TAG, "   [MatchMonitor] ❌ Skipping - isListener=$isListener, isConsumed=$isConsumed")
                    }
                } else {
                    Log.d(TAG, "   [MatchMonitor]    Match is null - nothing to do")
                }
                Log.d(TAG, "   [MatchMonitor] ═══════════════════════════════════════")
            }
        }

        Log.d(TAG, "   ✅ All monitor coroutines launched")
        Log.d(TAG, "═══════════════════════════════════════════════════")
        Log.d(TAG, "✅ Started listening successfully")
    }

    private fun stopListening() {
        Log.d(TAG, "═══════════════════════════════════════════════════")
        Log.d(TAG, "🛑 stopListening() CALLED")
        Log.d(TAG, "   Current isListening: $isListening")

        isListening = false
        Log.d(TAG, "   ✓ Set isListening = false")

        Log.d(TAG, "   📴 Setting listener availability to false...")
        CallManager.setRoleListener(false)
        Log.d(TAG, "   ✓ Listener availability set to false")

        Log.d(TAG, "   🔕 Stopping foreground...")
        stopForeground(STOP_FOREGROUND_REMOVE)
        Log.d(TAG, "   ✓ Foreground stopped")

        Log.d(TAG, "   ⏹️ Calling stopSelf()...")
        stopSelf()
        Log.d(TAG, "═══════════════════════════════════════════════════")
        Log.d(TAG, "✅ Stopped listening")
    }

    private fun createForegroundNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            // Use SINGLE_TOP to bring existing activity to front without recreating it
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, ListenerService::class.java).apply {
            action = ACTION_STOP_LISTENING
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID_FOREGROUND)
            .setContentTitle("Backroom - Listening")
            .setContentText("You're available to receive calls")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(0, "Stop Listening", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateForegroundNotification(state: ConnectionState) {
        val statusText = when (state) {
            ConnectionState.CONNECTED -> "You're available to receive calls"
            ConnectionState.CONNECTING -> "Connecting..."
            ConnectionState.RECONNECTING -> "Reconnecting..."
            ConnectionState.DISCONNECTED -> "Disconnected - Tap to reconnect"
        }

        // Intent to open app when notification is tapped
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Stop listening action
        val stopIntent = Intent(this, ListenerService::class.java).apply {
            action = ACTION_STOP_LISTENING
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_FOREGROUND)
            .setContentTitle("Backroom - Listening")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(0, "Stop Listening", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID_FOREGROUND, notification)
    }

    private fun showIncomingNotification(preview: SignalingMessage.IncomingPreview) {
        Log.d(TAG, "Showing incoming notification: ${preview.topic}")

        // Intent to open app
        val openIntent = Intent(this, MainActivity::class.java).apply {
            // Use SINGLE_TOP to bring existing activity to front without recreating it
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("incomingPreview", true)
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 10, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Accept action
        val acceptIntent = Intent(this, ListenerService::class.java).apply {
            action = ACTION_ACCEPT_PREVIEW
        }
        val acceptPendingIntent = PendingIntent.getService(
            this, 11, acceptIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Decline action
        val declineIntent = Intent(this, ListenerService::class.java).apply {
            action = ACTION_DECLINE_PREVIEW
        }
        val declinePendingIntent = PendingIntent.getService(
            this, 12, declineIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_INCOMING)
            .setContentTitle("Someone wants to talk")
            .setContentText("${preview.topic} • ${preview.tone} • ${preview.durationMinutes} min")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("\"${preview.previewText}\"\n\n${preview.topic} • ${preview.tone} • ${preview.durationMinutes} min"))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .addAction(0, "Accept", acceptPendingIntent)
            .addAction(0, "Decline", declinePendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID_INCOMING, notification)
    }

    private fun dismissIncomingNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.cancel(NOTIFICATION_ID_INCOMING)
    }

    private fun openAppForCall(callId: String) {
        Log.d(TAG, "Opening app for call: $callId")
        val intent = Intent(this, MainActivity::class.java).apply {
            // Use SINGLE_TOP to bring existing activity to front without recreating it
            // This preserves the navigation state and allows proper navigation to InCallScreen
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("callId", callId)
            putExtra("navigateToCall", true)
        }
        startActivity(intent)
    }
}

