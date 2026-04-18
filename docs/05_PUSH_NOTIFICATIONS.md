# Backroom Push Notification Strategy

## Overview

Push notifications are critical for Backroom because:
1. Listeners need to be notified of incoming previews in real-time
2. Sharers need to know when their call connects
3. The app may be in background when events occur

Tech: Firebase Cloud Messaging (FCM)

---

## Notification Types

| Type | Priority | Sound | Vibrate | Wake Screen | Channel |
|------|----------|-------|---------|-------------|---------|
| Incoming Preview | HIGH | Yes | Yes | Yes | incoming_calls |
| Call Connected | HIGH | Yes | Yes | Yes | call_status |
| Call Ending Soon | HIGH | No | Yes | No | call_status |
| Feedback Thanks | DEFAULT | No | No | No | social |
| Listener Reminder | DEFAULT | No | No | No | reminders |
| Weekly Summary | LOW | No | No | No | reminders |

---

## Notification Channels (Android)

```kotlin
// NotificationChannelManager.kt
object NotificationChannelManager {
    
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        
        val channels = listOf(
            // High priority - incoming calls
            NotificationChannel(
                "incoming_calls",
                "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Someone wants to talk to you"
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 250, 250)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true) // Important for call-like notifications
            },
            
            // High priority - call status
            NotificationChannel(
                "call_status",
                "Call Status",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Updates about your active calls"
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
            
            // Default - social interactions
            NotificationChannel(
                "social",
                "Social",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Thanks and acknowledgments"
            },
            
            // Low priority - reminders
            NotificationChannel(
                "reminders",
                "Reminders",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Tips and reminders to listen"
            }
        )
        
        channels.forEach { notificationManager.createNotificationChannel(it) }
    }
}
```

---

## 1. Incoming Preview Notification

### When Triggered
- Listener is AVAILABLE
- Matching engine selects them for a call request
- Preview needs to be shown

### Server-Side (Cloud Function)

```javascript
// sendPreviewNotification.js
async function sendPreviewNotification(listenerId, request) {
    // Get listener's FCM token
    const device = await db.collection('devices')
        .where('userId', '==', listenerId)
        .orderBy('lastSeenAt', 'desc')
        .limit(1)
        .get();
    
    if (device.empty) {
        console.log('No device found for listener');
        return;
    }
    
    const fcmToken = device.docs[0].data().fcmToken;
    if (!fcmToken) return;
    
    // Format tone for display
    const toneDisplay = {
        'light': 'Light',
        'heavy': 'Heavy',
        'very_heavy': 'Very Heavy'
    };
    
    const message = {
        token: fcmToken,
        
        // Notification payload (shown if app in background)
        notification: {
            title: 'Someone wants to talk',
            body: `${capitalizeFirst(request.topic)} · ${toneDisplay[request.tone]} · ${request.desiredDurationMin} min`
        },
        
        // Data payload (always delivered to app)
        data: {
            type: 'incoming_preview',
            requestId: request.id,
            topic: request.topic,
            tone: request.tone,
            intentLine: request.intentLine,
            duration: String(request.desiredDurationMin),
            timestamp: String(Date.now())
        },
        
        // Android-specific options
        android: {
            priority: 'high',
            ttl: 30000, // 30 seconds (match preview timeout)
            notification: {
                channelId: 'incoming_calls',
                icon: 'ic_notification',
                color: '#6B4EE6',
                sound: 'default',
                clickAction: 'OPEN_PREVIEW'
            }
        }
    };
    
    try {
        await admin.messaging().send(message);
        console.log('Preview notification sent to:', listenerId);
    } catch (error) {
        console.error('FCM error:', error);
        
        // Handle invalid token
        if (error.code === 'messaging/invalid-registration-token' ||
            error.code === 'messaging/registration-token-not-registered') {
            await db.collection('devices').doc(device.docs[0].id).update({
                fcmToken: null
            });
        }
    }
}

function capitalizeFirst(str) {
    return str.charAt(0).toUpperCase() + str.slice(1);
}
```

### Client-Side (Android)

```kotlin
// BackroomFirebaseMessagingService.kt
class BackroomFirebaseMessagingService : FirebaseMessagingService() {
    
    override fun onNewToken(token: String) {
        // Update token in Firestore
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
                val deviceId = DeviceManager(applicationContext).getDeviceFingerprint()
                
                FirebaseFirestore.getInstance()
                    .collection("devices")
                    .document(deviceId)
                    .update("fcmToken", token)
            } catch (e: Exception) {
                Log.e("FCM", "Failed to update token", e)
            }
        }
    }
    
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val data = remoteMessage.data
        
        when (data["type"]) {
            "incoming_preview" -> handleIncomingPreview(data)
            "call_connected" -> handleCallConnected(data)
            "call_ending_soon" -> handleCallEndingSoon(data)
            "feedback_thanks" -> handleFeedbackThanks(data)
            else -> Log.w("FCM", "Unknown notification type: ${data["type"]}")
        }
    }
    
    private fun handleIncomingPreview(data: Map<String, String>) {
        val requestId = data["requestId"] ?: return
        val topic = data["topic"] ?: return
        val tone = data["tone"] ?: return
        val intentLine = data["intentLine"] ?: ""
        val duration = data["duration"]?.toIntOrNull() ?: 10
        
        // Check if notification is still valid (not expired)
        val timestamp = data["timestamp"]?.toLongOrNull() ?: return
        if (System.currentTimeMillis() - timestamp > 30_000) {
            // Preview expired, don't show
            return
        }
        
        // Build notification with actions
        val acceptIntent = Intent(this, PreviewActionReceiver::class.java).apply {
            action = "ACTION_ACCEPT"
            putExtra("requestId", requestId)
        }
        val acceptPendingIntent = PendingIntent.getBroadcast(
            this, 0, acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val skipIntent = Intent(this, PreviewActionReceiver::class.java).apply {
            action = "ACTION_SKIP"
            putExtra("requestId", requestId)
        }
        val skipPendingIntent = PendingIntent.getBroadcast(
            this, 1, skipIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Full screen intent for high priority
        val fullScreenIntent = Intent(this, IncomingPreviewActivity::class.java).apply {
            putExtra("requestId", requestId)
            putExtra("topic", topic)
            putExtra("tone", tone)
            putExtra("intentLine", intentLine)
            putExtra("duration", duration)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 2, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, "incoming_calls")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Someone wants to talk")
            .setContentText("$topic · $tone · $duration min")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("\"$intentLine\""))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setTimeoutAfter(30_000) // Auto-dismiss after 30s
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(R.drawable.ic_check, "Accept", acceptPendingIntent)
            .addAction(R.drawable.ic_close, "Skip", skipPendingIntent)
            .build()
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(INCOMING_PREVIEW_ID, notification)
        
        // Also update app state if in foreground
        IncomingPreviewManager.onPreviewReceived(
            PreviewData(requestId, topic, tone, intentLine, duration)
        )
    }
    
    private fun handleCallConnected(data: Map<String, String>) {
        val roomId = data["roomId"] ?: return
        val sessionId = data["sessionId"] ?: return
        
        // Launch call activity
        val intent = Intent(this, CallActivity::class.java).apply {
            putExtra("roomId", roomId)
            putExtra("sessionId", sessionId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val notification = NotificationCompat.Builder(this, "call_status")
            .setSmallIcon(R.drawable.ic_phone)
            .setContentTitle("Call starting")
            .setContentText("Your call is connecting...")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))
            .build()
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(CALL_CONNECTED_ID, notification)
        
        // Start activity if we have permission
        startActivity(intent)
    }
    
    private fun handleCallEndingSoon(data: Map<String, String>) {
        val secondsRemaining = data["secondsRemaining"]?.toIntOrNull() ?: 30
        
        val notification = NotificationCompat.Builder(this, "call_status")
            .setSmallIcon(R.drawable.ic_clock)
            .setContentTitle("Call ending soon")
            .setContentText("$secondsRemaining seconds remaining")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(CALL_ENDING_ID, notification)
    }
    
    private fun handleFeedbackThanks(data: Map<String, String>) {
        val notification = NotificationCompat.Builder(this, "social")
            .setSmallIcon(R.drawable.ic_heart)
            .setContentTitle("Someone thanked you!")
            .setContentText("Your listening made a difference.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(THANKS_ID, notification)
    }
    
    companion object {
        const val INCOMING_PREVIEW_ID = 1001
        const val CALL_CONNECTED_ID = 1002
        const val CALL_ENDING_ID = 1003
        const val THANKS_ID = 1004
    }
}
```

### Preview Action Receiver

```kotlin
// PreviewActionReceiver.kt
class PreviewActionReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        val requestId = intent.getStringExtra("requestId") ?: return
        
        // Dismiss notification
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.cancel(BackroomFirebaseMessagingService.INCOMING_PREVIEW_ID)
        
        when (intent.action) {
            "ACTION_ACCEPT" -> {
                // Call Cloud Function to accept
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val functions = FirebaseFunctions.getInstance()
                        functions.getHttpsCallable("onPreviewAccepted")
                            .call(hashMapOf("requestId" to requestId))
                            .await()
                    } catch (e: Exception) {
                        Log.e("Preview", "Failed to accept", e)
                        // Show error toast on main thread
                    }
                }
            }
            
            "ACTION_SKIP" -> {
                // Call Cloud Function to skip
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val functions = FirebaseFunctions.getInstance()
                        functions.getHttpsCallable("onPreviewSkipped")
                            .call(hashMapOf("requestId" to requestId))
                            .await()
                    } catch (e: Exception) {
                        Log.e("Preview", "Failed to skip", e)
                    }
                }
            }
        }
    }
}
```

---

## 2. Call Connected Notification

### When Triggered
- Listener accepts preview
- Call session is created
- Both parties need to connect

### Server-Side

```javascript
// notifyCallConnected.js
async function notifyCallConnected(sharerId, roomId, sessionId) {
    const device = await getLatestDevice(sharerId);
    if (!device?.fcmToken) return;
    
    const message = {
        token: device.fcmToken,
        
        notification: {
            title: 'Call starting',
            body: 'Someone is ready to listen'
        },
        
        data: {
            type: 'call_connected',
            roomId: roomId,
            sessionId: sessionId,
            timestamp: String(Date.now())
        },
        
        android: {
            priority: 'high',
            ttl: 60000,
            notification: {
                channelId: 'call_status',
                icon: 'ic_phone',
                color: '#22C55E'
            }
        }
    };
    
    await admin.messaging().send(message);
}
```

---

## 3. Listener Reminder Notification

### When Triggered
- User has been idle for 3+ days
- User has listened before (not new)
- Sent at optimal time (afternoon local time)

### Server-Side (Scheduled Function)

```javascript
// scheduledReminders.js
exports.sendListenerReminders = functions.pubsub
    .schedule('every day 14:00')
    .timeZone('Africa/Nairobi')
    .onRun(async (context) => {
        const threeDaysAgo = new Date(Date.now() - 3 * 24 * 60 * 60 * 1000);
        
        // Find inactive listeners
        const users = await db.collection('users')
            .where('roles', 'array-contains', 'listener')
            .where('stats.lastActiveAt', '<', threeDaysAgo)
            .where('stats.totalCallsAsListener', '>', 0)
            .limit(100) // Batch size
            .get();
        
        const messages = [];
        
        for (const userDoc of users.docs) {
            const device = await getLatestDevice(userDoc.id);
            if (!device?.fcmToken) continue;
            
            messages.push({
                token: device.fcmToken,
                notification: {
                    title: 'Ready to listen?',
                    body: 'Someone might need you today.'
                },
                data: {
                    type: 'listener_reminder'
                },
                android: {
                    priority: 'normal',
                    notification: {
                        channelId: 'reminders',
                        icon: 'ic_notification'
                    }
                }
            });
        }
        
        // Send in batches of 500
        const batches = chunk(messages, 500);
        for (const batch of batches) {
            await admin.messaging().sendAll(batch);
        }
    });
```

---

## 4. Feedback Thanks Notification

### When Triggered
- Other party submits feedback with "thanked: true"

### Server-Side

```javascript
// onFeedbackSubmitted trigger
if (feedback.thanked) {
    const session = await db.collection('callSessions').doc(feedback.sessionId).get();
    const recipientId = feedback.role === 'sharer' 
        ? session.data().listenerId 
        : session.data().sharerId;
    
    await sendThanksNotification(recipientId);
}

async function sendThanksNotification(userId) {
    const device = await getLatestDevice(userId);
    if (!device?.fcmToken) return;
    
    const message = {
        token: device.fcmToken,
        notification: {
            title: 'Someone thanked you!',
            body: 'Your listening made a difference.'
        },
        data: {
            type: 'feedback_thanks'
        },
        android: {
            priority: 'normal',
            notification: {
                channelId: 'social',
                icon: 'ic_heart',
                color: '#EF4444'
            }
        }
    };
    
    await admin.messaging().send(message);
}
```

---

## Notification UI Mockups

### Incoming Preview (Lock Screen)

```
┌─────────────────────────────┐
│ 🔔 Backroom          now    │
├─────────────────────────────┤
│                             │
│   Someone wants to talk     │
│                             │
│   Grief · Heavy · 10 min    │
│                             │
│   "Lost someone close,      │
│    need to talk through     │
│    the pain"                │
│                             │
│   [Skip]         [Accept]   │
│                             │
└─────────────────────────────┘
```

### Incoming Preview (Heads-up)

```
┌─────────────────────────────────────┐
│ 🔔 Backroom                         │
│ Someone wants to talk               │
│ Grief · Heavy · 10 min              │
│                                     │
│        [Skip]    [Accept]           │
└─────────────────────────────────────┘
```

### Call Connected

```
┌─────────────────────────────┐
│ 📞 Backroom          now    │
├─────────────────────────────┤
│                             │
│   Call starting             │
│   Someone is ready to       │
│   listen                    │
│                             │
│   Tap to join               │
│                             │
└─────────────────────────────┘
```

---

## Permission Handling

### Request Flow

```kotlin
// NotificationPermissionManager.kt
class NotificationPermissionManager(private val activity: Activity) {
    
    private val requestLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onPermissionGranted()
        } else {
            onPermissionDenied()
        }
    }
    
    fun checkAndRequestPermission() {
        when {
            // Android 13+ requires explicit permission
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                when {
                    ContextCompat.checkSelfPermission(
                        activity,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED -> {
                        onPermissionGranted()
                    }
                    
                    activity.shouldShowRequestPermissionRationale(
                        Manifest.permission.POST_NOTIFICATIONS
                    ) -> {
                        showRationale()
                    }
                    
                    else -> {
                        requestLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
            
            // Older Android versions - check if notifications enabled
            else -> {
                if (NotificationManagerCompat.from(activity).areNotificationsEnabled()) {
                    onPermissionGranted()
                } else {
                    showSettingsPrompt()
                }
            }
        }
    }
    
    private fun showRationale() {
        AlertDialog.Builder(activity)
            .setTitle("Notifications")
            .setMessage("We need notifications to tell you when someone wants to talk. Without this, you might miss calls.")
            .setPositiveButton("Allow") { _, _ ->
                requestLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            .setNegativeButton("Not now", null)
            .show()
    }
    
    private fun showSettingsPrompt() {
        AlertDialog.Builder(activity)
            .setTitle("Enable Notifications")
            .setMessage("Please enable notifications in Settings to receive incoming calls.")
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                }
                activity.startActivity(intent)
            }
            .setNegativeButton("Not now", null)
            .show()
    }
    
    private fun onPermissionGranted() {
        // Register FCM token
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            // Save to Firestore
        }
    }
    
    private fun onPermissionDenied() {
        // Update UI to show notifications won't work
        // User can still use app, but listener mode is degraded
    }
}
```

---

## Foreground Service for Active Calls

When in a call, we need a foreground service to:
1. Keep the call alive when app is backgrounded
2. Show ongoing notification
3. Prevent system from killing the process

```kotlin
// CallForegroundService.kt
class CallForegroundService : Service() {
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sessionId = intent?.getStringExtra("sessionId") ?: return START_NOT_STICKY
        val duration = intent.getIntExtra("durationMin", 10)
        
        val notification = createOngoingNotification(sessionId, duration)
        startForeground(ONGOING_CALL_ID, notification)
        
        return START_STICKY
    }
    
    private fun createOngoingNotification(sessionId: String, durationMin: Int): Notification {
        val endIntent = Intent(this, CallActionReceiver::class.java).apply {
            action = "ACTION_END_CALL"
            putExtra("sessionId", sessionId)
        }
        val endPendingIntent = PendingIntent.getBroadcast(
            this, 0, endIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val returnIntent = Intent(this, CallActivity::class.java).apply {
            putExtra("sessionId", sessionId)
        }
        val returnPendingIntent = PendingIntent.getActivity(
            this, 0, returnIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, "call_status")
            .setSmallIcon(R.drawable.ic_phone)
            .setContentTitle("Backroom call in progress")
            .setContentText("$durationMin minute call")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(returnPendingIntent)
            .addAction(R.drawable.ic_end_call, "End", endPendingIntent)
            .build()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    companion object {
        const val ONGOING_CALL_ID = 2001
    }
}
```

---

## Testing Notifications

### Manual Testing Checklist

- [ ] Incoming preview shows when app in foreground
- [ ] Incoming preview shows when app in background
- [ ] Incoming preview shows on lock screen
- [ ] Accept action from notification works
- [ ] Skip action from notification works
- [ ] Notification auto-dismisses after 30s
- [ ] Call connected launches call activity
- [ ] Ongoing call notification shows during call
- [ ] End call from notification works
- [ ] Thanks notification shows correctly
- [ ] Reminder notification shows at scheduled time
- [ ] Notifications respect Do Not Disturb (except incoming)

### FCM Testing Tools

```bash
# Send test notification via Firebase CLI
firebase functions:shell

# In shell:
sendPreviewNotification({userId: "test-user-id", request: {...}})
```

### Debug Logging

```kotlin
// Enable verbose FCM logging
FirebaseMessaging.getInstance().isAutoInitEnabled = true

// Log all received messages
override fun onMessageReceived(remoteMessage: RemoteMessage) {
    Log.d("FCM", "From: ${remoteMessage.from}")
    Log.d("FCM", "Data: ${remoteMessage.data}")
    remoteMessage.notification?.let {
        Log.d("FCM", "Notification: ${it.title} - ${it.body}")
    }
    // ... rest of handling
}
```

---

## Notification Analytics

### Events to Track

| Event | Properties |
|-------|------------|
| notification_received | type, source |
| notification_displayed | type |
| notification_clicked | type, action |
| notification_dismissed | type |
| notification_action_accept | requestId |
| notification_action_skip | requestId |
| notification_permission_granted | |
| notification_permission_denied | |

### Implementation

```kotlin
// Track notification interactions
fun trackNotificationAction(type: String, action: String) {
    FirebaseAnalytics.getInstance(context).logEvent("notification_action") {
        param("notification_type", type)
        param("action", action)
        param("timestamp", System.currentTimeMillis())
    }
}
```

---

## Best Practices

### Do
- Use high priority only for time-sensitive notifications
- Include relevant info in notification body
- Provide quick actions (Accept/Skip)
- Auto-dismiss expired notifications
- Respect user's notification preferences
- Track delivery and engagement

### Don't
- Send too many reminders (max 1/day)
- Use notifications for marketing (only transactional)
- Wake user at night (respect quiet hours)
- Send duplicate notifications
- Keep stale notifications around

---

## Next Steps

1. Set up FCM in Firebase Console
2. Create notification channels on app launch
3. Implement FirebaseMessagingService
4. Test with real FCM messages
5. Add analytics tracking
6. Monitor delivery rates in Firebase Console

