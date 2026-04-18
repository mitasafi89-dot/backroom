# Firebase Cloud Messaging (FCM) Notifications

This module handles push notifications for Backroom using Firebase Cloud Messaging.

## Configuration

### Service Account

The service account file is located at:
```
backend/rest-api/firebase-service-account.json
```

**⚠️ IMPORTANT: Never commit this file to public repositories!**

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `GOOGLE_APPLICATION_CREDENTIALS` | `./firebase-service-account.json` | Path to service account file |
| `FCM_PROJECT_ID` | `backroom-b51b1` | Firebase project ID |

## Notification Types

### 1. Share Accepted (to Sharer)
Sent when a listener accepts a sharer's request.

```json
{
  "title": "Someone is here for you",
  "body": "A listener has accepted your share. Connecting now...",
  "data": {
    "type": "share_accepted",
    "call_id": "uuid"
  }
}
```

### 2. Incoming Preview (to Listener)
Sent when a new share matches the listener's boundaries.

```json
{
  "title": "Someone needs to talk",
  "body": "Grief: \"Lost someone close, need to talk...\"",
  "data": {
    "type": "incoming_preview",
    "share_id": "uuid",
    "topic": "Grief"
  }
}
```

### 3. Subscription Active
Sent when M-Pesa payment is confirmed.

```json
{
  "title": "Welcome to Backroom Plus! 🎉",
  "body": "Your monthly subscription is now active.",
  "data": {
    "type": "subscription_active",
    "plan": "monthly"
  }
}
```

### 4. Subscription Expiring
Sent X days before subscription expires.

```json
{
  "title": "Subscription expiring soon",
  "body": "Your Backroom Plus subscription expires in 3 days.",
  "data": {
    "type": "subscription_expiring",
    "days_left": "3"
  }
}
```

## Usage in Code

```typescript
import { NotificationsService } from './notifications/notifications.service';

@Injectable()
export class SomeService {
  constructor(private readonly notifications: NotificationsService) {}

  async someMethod() {
    // Send to specific device
    await this.notifications.sendToToken({
      token: 'device_fcm_token',
      notification: {
        title: 'Hello',
        body: 'This is a test notification',
        data: { key: 'value' },
      },
    });

    // Use convenience methods
    await this.notifications.notifyShareAccepted(userToken, callId);
    await this.notifications.notifyIncomingPreview(listenerToken, shareId, topic, preview);
  }
}
```

## Android Client Setup

### 1. Add google-services.json

Download from Firebase Console and place in:
```
app/google-services.json
```

### 2. Add dependencies (build.gradle.kts)

```kotlin
// Project level
plugins {
    id("com.google.gms.google-services") version "4.4.0" apply false
}

// App level
plugins {
    id("com.google.gms.google-services")
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-messaging-ktx")
}
```

### 3. Create FirebaseMessagingService

```kotlin
class BackroomMessagingService : FirebaseMessagingService() {
    
    override fun onNewToken(token: String) {
        // Send token to your server
        sendTokenToServer(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        when (data["type"]) {
            "share_accepted" -> handleShareAccepted(data)
            "incoming_preview" -> handleIncomingPreview(data)
            else -> showNotification(message.notification)
        }
    }
}
```

### 4. Register in AndroidManifest.xml

```xml
<service
    android:name=".BackroomMessagingService"
    android:exported="false">
    <intent-filter>
        <action android:name="com.google.firebase.MESSAGING_EVENT" />
    </intent-filter>
</service>
```

## Testing

### Send Test Notification

```bash
# Get a test token from your Android app logs
# Then use Firebase Console or this curl command:

curl -X POST \
  'https://fcm.googleapis.com/v1/projects/backroom-b51b1/messages:send' \
  -H "Authorization: Bearer $(gcloud auth print-access-token)" \
  -H 'Content-Type: application/json' \
  -d '{
    "message": {
      "token": "DEVICE_TOKEN_HERE",
      "notification": {
        "title": "Test",
        "body": "Hello from Backroom!"
      }
    }
  }'
```

### Using Firebase Console

1. Go to Firebase Console → Cloud Messaging
2. Click "Send your first message"
3. Enter title and body
4. Select your app
5. Click "Send test message"
6. Enter device token
7. Send!

## Troubleshooting

### "Firebase not initialized"
- Check that `firebase-service-account.json` exists
- Verify the file path in `GOOGLE_APPLICATION_CREDENTIALS`

### "Invalid registration token"
- Token may be expired or from a different app
- Device may have uninstalled the app
- Token was generated for a different Firebase project

### Notifications not received
- Check if app has notification permissions
- Verify device is not in Do Not Disturb mode
- Check if app is force-stopped
- Look at Android logcat for FCM messages

## Security Notes

- Service account file contains private key - keep it secret
- Rotate service account keys periodically
- Use topic subscriptions carefully (anyone can subscribe)
- Validate data in notifications server-side

