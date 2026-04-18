# Backroom Backend Architecture

## Overview

This document defines the backend architecture for Backroom MVP.
Tech stack: Firebase (Auth, Firestore, Cloud Functions, FCM).
Future migration path to PostgreSQL + custom backend at scale.

---

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                        BACKROOM BACKEND                              │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌──────────────┐                                                   │
│  │ Android App  │                                                   │
│  │  (Compose)   │                                                   │
│  └──────┬───────┘                                                   │
│         │                                                           │
│         │ HTTPS / WebSocket                                         │
│         ▼                                                           │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                    FIREBASE SERVICES                          │  │
│  ├──────────────────────────────────────────────────────────────┤  │
│  │                                                               │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐   │  │
│  │  │ Firebase    │  │ Firestore   │  │ Cloud Functions     │   │  │
│  │  │ Auth        │  │ Database    │  │ (Node.js)           │   │  │
│  │  │             │  │             │  │                     │   │  │
│  │  │ - Anonymous │  │ - Users     │  │ - Matching engine   │   │  │
│  │  │ - Phone     │  │ - Calls     │  │ - Preview screening │   │  │
│  │  │             │  │ - Reports   │  │ - Safety triggers   │   │  │
│  │  │             │  │ - etc.      │  │ - Notifications     │   │  │
│  │  └─────────────┘  └─────────────┘  └─────────────────────┘   │  │
│  │                                                               │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐   │  │
│  │  │ FCM         │  │ Realtime DB │  │ Cloud Storage       │   │  │
│  │  │ Push Notif  │  │ Signaling   │  │ (future: resources) │   │  │
│  │  └─────────────┘  └─────────────┘  └─────────────────────┘   │  │
│  │                                                               │  │
│  └──────────────────────────────────────────────────────────────┘  │
│         │                                                           │
│         │                                                           │
│         ▼                                                           │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                    EXTERNAL SERVICES                          │  │
│  ├──────────────────────────────────────────────────────────────┤  │
│  │                                                               │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐   │  │
│  │  │ TURN Server │  │ Perspective │  │ Analytics           │   │  │
│  │  │ (Twilio)    │  │ API (Google)│  │ (Firebase)          │   │  │
│  │  │             │  │             │  │                     │   │  │
│  │  │ NAT         │  │ Content     │  │ Events, metrics     │   │  │
│  │  │ traversal   │  │ moderation  │  │ dashboards          │   │  │
│  │  └─────────────┘  └─────────────┘  └─────────────────────┘   │  │
│  │                                                               │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 1. Authentication

### Strategy

| Tier | Auth Method | Details |
|------|-------------|---------|
| Free | Anonymous | Firebase Anonymous Auth, device-bound |
| Plus | Phone | Firebase Phone Auth, verified number |

### Anonymous Auth Flow

```
1. App launches first time
2. Firebase creates anonymous UID
3. UID stored locally + linked to device ID
4. User can use app without any input
5. If app reinstalled, new anonymous UID created
```

### Phone Auth Flow (Backroom Plus)

```
1. User taps "Upgrade to Plus"
2. Enter phone number
3. Receive OTP via SMS
4. Verify OTP
5. Link phone to existing anonymous account
6. Phone stored encrypted, used only for:
   - Account recovery
   - Ban enforcement
```

### Security Rules Concept

```javascript
// Firestore rules (simplified)
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    
    // Users can only read/write their own data
    match /users/{userId} {
      allow read, write: if request.auth.uid == userId;
    }
    
    // Call requests - authenticated users only
    match /callRequests/{requestId} {
      allow create: if request.auth != null;
      allow read: if request.auth.uid == resource.data.sharerId 
                  || request.auth.uid == resource.data.listenerId;
    }
    
    // Availability - authenticated users only
    match /availability/{userId} {
      allow read: if request.auth != null;
      allow write: if request.auth.uid == userId;
    }
  }
}
```

---

## 2. Firestore Database Schema

### Collection: users

```javascript
users/{userId}
{
  id: string,                    // Firebase UID
  createdAt: timestamp,
  locale: string,                // "en" | "sw"
  status: string,                // "active" | "suspended" | "banned"
  roles: string[],               // ["sharer", "listener"]
  subscriptionTier: string,      // "free" | "plus"
  subscriptionExpiry: timestamp | null,
  onboardingComplete: boolean,
  
  // Aggregated stats (updated by Cloud Functions)
  stats: {
    totalCallsAsSharer: number,
    totalCallsAsListener: number,
    avgRatingReceived: number,
    lastActiveAt: timestamp
  }
}
```

### Collection: preferences

```javascript
preferences/{userId}
{
  userId: string,
  
  // Listener boundaries
  topicsAllowed: string[],       // ["confession", "venting", ...]
  toneMax: string,               // "light" | "heavy" | "very_heavy"
  maxDurationMin: number,        // 5, 10, 15, 30
  languages: string[],           // ["en", "sw"]
  lightOnly: boolean,
  
  // Privacy
  geoFilterEnabled: boolean,
  geoFilterRadiusKm: number,     // 10 default
  anonymizationLevel: string,    // "basic" | "heavy"
  
  // Location (coarse, optional)
  lastKnownLocation: geopoint | null,
  locationUpdatedAt: timestamp | null,
  
  updatedAt: timestamp
}
```

### Collection: availability

```javascript
availability/{odayId}
{
  odayId: string,                // odayId = odayHoday
  status: string,                // "available" | "not_available" | "in_call" | "cooldown"
  updatedAt: timestamp,
  cooldownUntil: timestamp | null,
  
  // Denormalized for query efficiency
  topicsAllowed: string[],
  toneMax: string,
  maxDurationMin: number,
  languages: string[],
  geoHash: string | null,        // For geo queries
  
  // Matching metadata
  recentCallCount: number,       // Calls in last hour
  lastHeavyCallAt: timestamp | null
}
```

### Collection: callRequests

```javascript
callRequests/{requestId}
{
  id: string,
  sharerId: string,
  
  // Preview content
  topic: string,
  tone: string,
  intentLine: string,            // Max 120 chars
  language: string,
  desiredDurationMin: number,
  
  // State
  status: string,                // "queued" | "matching" | "matched" | "connected" | "completed" | "cancelled" | "expired"
  
  // Matching
  matchedListenerId: string | null,
  skipCount: number,             // How many listeners skipped
  skippedListenerIds: string[],  // Don't rematch these
  
  // Timestamps
  createdAt: timestamp,
  matchedAt: timestamp | null,
  connectedAt: timestamp | null,
  completedAt: timestamp | null,
  
  // Moderation
  moderationPassed: boolean,
  moderationFlags: string[]      // Any flagged issues
}
```

### Collection: callSessions

```javascript
callSessions/{sessionId}
{
  id: string,
  requestId: string,
  sharerId: string,
  listenerId: string,
  
  // Pseudonyms for this call only
  sharerPseudonym: string,       // "Sharer #3018"
  listenerPseudonym: string,     // "Listener #8492"
  
  // Call data
  startedAt: timestamp,
  endedAt: timestamp | null,
  durationSec: number,
  requestedDurationMin: number,
  
  // End reason
  endReason: string,             // "normal" | "timeout" | "drop" | "emergency" | "abuse"
  endedBy: string | null,        // Which userId ended it
  
  // Topic reference (for post-call context)
  topic: string,
  tone: string
}
```

### Collection: feedback

```javascript
feedback/{feedbackId}
{
  id: string,
  sessionId: string,
  userId: string,                // Who submitted
  role: string,                  // "sharer" | "listener"
  
  sentiment: string,             // "helped" | "neutral" | "uncomfortable"
  thanked: boolean,              // Sent anonymous thanks
  recognized: boolean | null,    // Recognized other person
  
  createdAt: timestamp
}
```

### Collection: reports

```javascript
reports/{reportId}
{
  id: string,
  sessionId: string,
  reporterId: string,
  accusedId: string,
  
  category: string,              // "abuse" | "mismatch" | "inappropriate" | "unsafe" | "other"
  notes: string | null,          // Optional details
  blocked: boolean,              // Did reporter block accused
  
  // Resolution
  status: string,                // "open" | "reviewing" | "resolved" | "dismissed"
  resolvedAt: timestamp | null,
  resolution: string | null,     // "warning" | "suspension" | "ban" | "no_action"
  
  createdAt: timestamp
}
```

### Collection: reputation

```javascript
reputation/{userId}
{
  userId: string,
  score: number,                 // 0-100, starts at 50
  
  // Breakdown
  positiveCount: number,         // "helped" ratings
  neutralCount: number,
  negativeCount: number,         // "uncomfortable" ratings
  reportCount: number,           // Reports against user
  mismatchCount: number,         // Preview vs call mismatch reports
  
  // Status
  warnings: number,
  suspensions: number,
  isShadowBanned: boolean,
  
  lastUpdated: timestamp
}
```

### Collection: devices

```javascript
devices/{deviceId}
{
  deviceId: string,              // Hashed device fingerprint
  userId: string,
  
  fcmToken: string | null,       // For push notifications
  platform: string,              // "android"
  appVersion: string,
  
  // Risk tracking
  riskFlags: string[],           // ["multiple_accounts", "ban_evasion", ...]
  isBanned: boolean,
  
  createdAt: timestamp,
  lastSeenAt: timestamp
}
```

### Collection: subscriptions

```javascript
subscriptions/{userId}
{
  userId: string,
  tier: string,                  // "free" | "plus"
  
  // Billing
  provider: string,              // "google_play" | "mpesa" (future)
  providerSubscriptionId: string | null,
  
  startedAt: timestamp | null,
  expiresAt: timestamp | null,
  status: string,                // "active" | "expired" | "cancelled"
  
  updatedAt: timestamp
}
```

### Collection: crisisResources

```javascript
crisisResources/{resourceId}
{
  id: string,
  country: string,               // "KE"
  name: string,                  // "Befrienders Kenya"
  phone: string,                 // "+254722178177"
  description: string,
  hours: string,                 // "24/7"
  languages: string[],           // ["en", "sw"]
  
  isActive: boolean,
  verifiedAt: timestamp,
  
  order: number                  // Display order
}
```

---

## 3. WebRTC Signaling Architecture

### Signaling Flow

```
┌─────────────┐                    ┌─────────────┐
│   Sharer    │                    │  Listener   │
│   Device    │                    │   Device    │
└──────┬──────┘                    └──────┬──────┘
       │                                  │
       │  1. Create call request          │
       ├─────────────────────────────────►│
       │     (Firestore)                  │
       │                                  │
       │  2. Matching finds listener      │
       │◄─────────────────────────────────┤
       │     (Cloud Function)             │
       │                                  │
       │  3. Preview sent to listener     │
       │                                  │
       │  4. Listener accepts             │
       │◄─────────────────────────────────┤
       │                                  │
       │  5. Create signaling room        │
       │     (Realtime DB)                │
       │                                  │
       │  6. Exchange SDP Offer           │
       ├─────────────────────────────────►│
       │     (Realtime DB)                │
       │                                  │
       │  7. Exchange SDP Answer          │
       │◄─────────────────────────────────┤
       │     (Realtime DB)                │
       │                                  │
       │  8. Exchange ICE Candidates      │
       │◄────────────────────────────────►│
       │     (Realtime DB)                │
       │                                  │
       │  9. P2P Connection Established   │
       │◄═══════════════════════════════►│
       │     (Direct WebRTC)              │
       │                                  │
       │ 10. Voice + Morphing             │
       │◄═══════════════════════════════►│
       │                                  │
```

### Realtime Database Structure (Signaling)

```javascript
signalingRooms/{roomId}
{
  createdAt: timestamp,
  sharerId: string,
  listenerId: string,
  status: string,                // "waiting" | "connected" | "ended"
  
  offer: {
    type: string,                // "offer"
    sdp: string
  },
  
  answer: {
    type: string,                // "answer"
    sdp: string
  },
  
  sharerCandidates: {
    {candidateId}: {
      candidate: string,
      sdpMid: string,
      sdpMLineIndex: number
    }
  },
  
  listenerCandidates: {
    {candidateId}: {
      candidate: string,
      sdpMid: string,
      sdpMLineIndex: number
    }
  }
}
```

### STUN/TURN Configuration

```javascript
const iceServers = [
  // Google's free STUN servers
  { urls: "stun:stun.l.google.com:19302" },
  { urls: "stun:stun1.l.google.com:19302" },
  
  // TURN server (Twilio or self-hosted)
  {
    urls: "turn:your-turn-server.com:3478",
    username: "generated-username",
    credential: "generated-credential"
  }
];
```

### TURN Server Options

| Option | Cost | Pros | Cons |
|--------|------|------|------|
| Twilio | ~$0.004/GB | Reliable, global | Per-usage cost |
| Coturn (self-hosted) | Server cost | No per-usage | Maintenance |
| Xirsys | ~$0.005/GB | Easy setup | Less reliable |

**MVP Recommendation:** Start with Twilio. At 1000 calls/day, cost is ~$30-50/month.

---

## 4. Cloud Functions

### Function: onCallRequestCreated

Triggers when sharer submits preview.

```javascript
// Pseudocode
exports.onCallRequestCreated = functions.firestore
  .document('callRequests/{requestId}')
  .onCreate(async (snap, context) => {
    const request = snap.data();
    
    // 1. Screen content with Perspective API
    const moderationResult = await moderateContent(request.intentLine);
    if (moderationResult.blocked) {
      await snap.ref.update({ 
        status: 'blocked',
        moderationFlags: moderationResult.flags 
      });
      return;
    }
    
    // 2. Check for crisis keywords
    if (moderationResult.crisisDetected) {
      await triggerCrisisFlow(request.sharerId);
    }
    
    // 3. Find matching listeners
    await findAndNotifyListener(request);
  });
```

### Function: findAndNotifyListener

Matching algorithm implementation.

```javascript
// Pseudocode
async function findAndNotifyListener(request) {
  // Query available listeners
  const listeners = await db.collection('availability')
    .where('status', '==', 'available')
    .where('topicsAllowed', 'array-contains', request.topic)
    .where('languages', 'array-contains', request.language)
    .where('maxDurationMin', '>=', request.desiredDurationMin)
    .get();
  
  // Filter by tone
  const filtered = listeners.docs.filter(doc => {
    const data = doc.data();
    return toneLevel(data.toneMax) >= toneLevel(request.tone);
  });
  
  // Exclude already-skipped listeners
  const eligible = filtered.filter(doc => 
    !request.skippedListenerIds.includes(doc.id)
  );
  
  if (eligible.length === 0) {
    // No listeners available
    await handleNoListeners(request);
    return;
  }
  
  // Score and select best match
  const scored = eligible.map(doc => ({
    doc,
    score: calculateMatchScore(doc.data(), request)
  }));
  scored.sort((a, b) => b.score - a.score);
  
  const selectedListener = scored[0].doc;
  
  // Update request
  await db.collection('callRequests').doc(request.id).update({
    status: 'matching',
    matchedListenerId: selectedListener.id
  });
  
  // Send push notification
  await sendPreviewNotification(selectedListener.id, request);
}
```

### Function: onPreviewAccepted

When listener accepts preview.

```javascript
// Pseudocode
exports.onPreviewAccepted = functions.https.onCall(async (data, context) => {
  const { requestId } = data;
  const listenerId = context.auth.uid;
  
  // Verify listener is the matched one
  const request = await db.collection('callRequests').doc(requestId).get();
  if (request.data().matchedListenerId !== listenerId) {
    throw new Error('Not authorized');
  }
  
  // Create signaling room
  const roomId = generateRoomId();
  await rtdb.ref(`signalingRooms/${roomId}`).set({
    createdAt: Date.now(),
    sharerId: request.data().sharerId,
    listenerId: listenerId,
    status: 'waiting'
  });
  
  // Create call session
  const session = await db.collection('callSessions').add({
    requestId: requestId,
    sharerId: request.data().sharerId,
    listenerId: listenerId,
    sharerPseudonym: generatePseudonym('Sharer'),
    listenerPseudonym: generatePseudonym('Listener'),
    startedAt: admin.firestore.FieldValue.serverTimestamp(),
    topic: request.data().topic,
    tone: request.data().tone
  });
  
  // Update request status
  await db.collection('callRequests').doc(requestId).update({
    status: 'connected',
    connectedAt: admin.firestore.FieldValue.serverTimestamp()
  });
  
  // Notify sharer
  await sendCallConnectedNotification(request.data().sharerId, roomId);
  
  return { roomId, sessionId: session.id };
});
```

### Function: onPreviewSkipped

When listener skips preview.

```javascript
// Pseudocode
exports.onPreviewSkipped = functions.https.onCall(async (data, context) => {
  const { requestId } = data;
  const listenerId = context.auth.uid;
  
  const requestRef = db.collection('callRequests').doc(requestId);
  
  await db.runTransaction(async (transaction) => {
    const request = await transaction.get(requestRef);
    const data = request.data();
    
    // Add to skipped list
    const skippedListenerIds = [...data.skippedListenerIds, listenerId];
    
    transaction.update(requestRef, {
      skippedListenerIds,
      skipCount: data.skipCount + 1,
      matchedListenerId: null,
      status: 'queued'
    });
  });
  
  // Try to find another listener
  const request = await requestRef.get();
  await findAndNotifyListener(request.data());
});
```

### Function: onCallEnded

Cleanup and stats update.

```javascript
// Pseudocode
exports.onCallEnded = functions.https.onCall(async (data, context) => {
  const { sessionId, endReason } = data;
  const userId = context.auth.uid;
  
  // Update session
  await db.collection('callSessions').doc(sessionId).update({
    endedAt: admin.firestore.FieldValue.serverTimestamp(),
    endReason: endReason,
    endedBy: userId
  });
  
  // Calculate duration
  const session = await db.collection('callSessions').doc(sessionId).get();
  const duration = (session.data().endedAt - session.data().startedAt) / 1000;
  
  await db.collection('callSessions').doc(sessionId).update({
    durationSec: duration
  });
  
  // Update user stats
  await updateUserStats(session.data().sharerId, 'sharer');
  await updateUserStats(session.data().listenerId, 'listener');
  
  // Clean up signaling room
  await rtdb.ref(`signalingRooms/${session.data().roomId}`).remove();
});
```

### Function: onFeedbackSubmitted

Update reputation scores.

```javascript
// Pseudocode
exports.onFeedbackSubmitted = functions.firestore
  .document('feedback/{feedbackId}')
  .onCreate(async (snap, context) => {
    const feedback = snap.data();
    const session = await db.collection('callSessions').doc(feedback.sessionId).get();
    
    // Determine who receives the rating
    const ratedUserId = feedback.role === 'sharer' 
      ? session.data().listenerId 
      : session.data().sharerId;
    
    // Update reputation
    const repRef = db.collection('reputation').doc(ratedUserId);
    
    await db.runTransaction(async (transaction) => {
      const rep = await transaction.get(repRef);
      const data = rep.data() || { score: 50, positiveCount: 0, neutralCount: 0, negativeCount: 0 };
      
      let scoreChange = 0;
      if (feedback.sentiment === 'helped') {
        data.positiveCount++;
        scoreChange = 2;
      } else if (feedback.sentiment === 'neutral') {
        data.neutralCount++;
        scoreChange = 0;
      } else if (feedback.sentiment === 'uncomfortable') {
        data.negativeCount++;
        scoreChange = -5;
      }
      
      data.score = Math.max(0, Math.min(100, data.score + scoreChange));
      data.lastUpdated = admin.firestore.FieldValue.serverTimestamp();
      
      transaction.set(repRef, data, { merge: true });
    });
    
    // Handle recognition
    if (feedback.recognized === true) {
      await addToExclusionList(feedback.userId, ratedUserId);
    }
  });
```

### Function: onReportCreated

Handle abuse reports.

```javascript
// Pseudocode
exports.onReportCreated = functions.firestore
  .document('reports/{reportId}')
  .onCreate(async (snap, context) => {
    const report = snap.data();
    
    // Update accused user's reputation
    const repRef = db.collection('reputation').doc(report.accusedId);
    await repRef.update({
      reportCount: admin.firestore.FieldValue.increment(1)
    });
    
    // Check for automatic actions
    const rep = await repRef.get();
    const data = rep.data();
    
    if (data.reportCount >= 3 && !data.isShadowBanned) {
      // Auto shadow-ban after 3 reports
      await repRef.update({ isShadowBanned: true });
      
      // Remove from availability
      await db.collection('availability').doc(report.accusedId).update({
        status: 'suspended'
      });
    }
    
    // If blocked, add to exclusion list
    if (report.blocked) {
      await addToBlockList(report.reporterId, report.accusedId);
    }
  });
```

---

## 5. Push Notifications (FCM)

### Notification Types

| Type | Trigger | Priority | Content |
|------|---------|----------|---------|
| incoming_preview | Matched to listener | High | "Someone wants to talk" |
| call_connected | Listener accepts | High | "Call starting now" |
| call_reminder | User hasn't opened in 3 days | Normal | "Ready to listen?" |
| feedback_thanks | Received anonymous thanks | Normal | "Someone thanked you" |

### FCM Data Structure

```javascript
// Incoming preview notification
{
  notification: {
    title: "Someone wants to talk",
    body: "Topic: Grief • Heavy • 10 min"
  },
  data: {
    type: "incoming_preview",
    requestId: "abc123",
    topic: "grief",
    tone: "heavy",
    duration: "10"
  },
  android: {
    priority: "high",
    notification: {
      channelId: "incoming_calls",
      sound: "default"
    }
  }
}
```

### Android Notification Channels

```kotlin
// Create channels on app startup
val channels = listOf(
    NotificationChannel(
        "incoming_calls",
        "Incoming Calls",
        NotificationManager.IMPORTANCE_HIGH
    ).apply {
        description = "Notifications for incoming call previews"
        setSound(defaultSoundUri, audioAttributes)
        enableVibration(true)
    },
    
    NotificationChannel(
        "call_status",
        "Call Status",
        NotificationManager.IMPORTANCE_HIGH
    ).apply {
        description = "Notifications about call connections"
    },
    
    NotificationChannel(
        "reminders",
        "Reminders",
        NotificationManager.IMPORTANCE_DEFAULT
    ).apply {
        description = "Tips and reminders"
    }
)
```

---

## 6. Offline Handling

### Scenarios and Behavior

| Scenario | Behavior |
|----------|----------|
| Network drops during matching | Request stays queued for 5 min, then expires |
| Network drops during preview | Listener has 30 sec; if not back, auto-skip |
| Network drops during call | 15 sec grace period to reconnect |
| Network drops during feedback | Queue locally, submit when back online |

### Firestore Offline Persistence

```kotlin
// Enable offline persistence
val settings = FirebaseFirestoreSettings.Builder()
    .setPersistenceEnabled(true)
    .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
    .build()
firestore.firestoreSettings = settings
```

### Optimistic UI Updates

```kotlin
// Example: Toggle availability
fun toggleAvailability(available: Boolean) {
    // Update UI immediately
    _uiState.value = _uiState.value.copy(isAvailable = available)
    
    // Then sync to server
    viewModelScope.launch {
        try {
            repository.setAvailability(available)
        } catch (e: Exception) {
            // Rollback UI if server fails
            _uiState.value = _uiState.value.copy(isAvailable = !available)
            showError("Failed to update. Check your connection.")
        }
    }
}
```

---

## 7. Security Considerations

### Data Encryption

| Data | At Rest | In Transit |
|------|---------|------------|
| User data | Firestore encryption (default) | HTTPS/TLS |
| Voice | Not stored | DTLS-SRTP (WebRTC) |
| Signaling | Realtime DB encryption | WSS |
| FCM tokens | Encrypted | HTTPS |

### Rate Limiting

```javascript
// Cloud Function rate limiting
const rateLimit = require('express-rate-limit');

const createCallLimiter = rateLimit({
  windowMs: 60 * 60 * 1000, // 1 hour
  max: 10, // 10 calls per hour for free tier
  message: 'Too many calls. Please wait.'
});
```

### Input Validation

```javascript
// Example validation
function validateIntentLine(text) {
  if (!text || typeof text !== 'string') return false;
  if (text.length > 120) return false;
  if (text.length < 10) return false;
  
  // Check for PII patterns
  const piiPatterns = [
    /\b\d{10}\b/,           // Phone numbers
    /\b\d{3}-\d{3}-\d{4}\b/, // Phone with dashes
    /@\w+\.\w+/,            // Emails
    /\b(?:nairobi|mombasa|kisumu)\b/i // City names
  ];
  
  for (const pattern of piiPatterns) {
    if (pattern.test(text)) return false;
  }
  
  return true;
}
```

---

## 8. Scaling Considerations

### Current Limits (Firebase Free Tier)

| Resource | Limit | Backroom Impact |
|----------|-------|--------------|
| Firestore reads | 50K/day | ~5,000 calls/day max |
| Firestore writes | 20K/day | ~2,000 calls/day max |
| Cloud Functions | 125K/month | ~4,000 calls/day max |
| Realtime DB | 100 connections | ~50 concurrent calls |

### When to Upgrade

| Milestone | Action |
|-----------|--------|
| 100 DAU | Stay on free tier |
| 500 DAU | Upgrade to Blaze (pay-as-you-go) |
| 2,000 DAU | Consider dedicated TURN |
| 10,000 DAU | Evaluate PostgreSQL migration |

### Migration Path to PostgreSQL

```
Phase 1 (MVP): Firebase everything
Phase 2 (Scale): 
  - Keep Firebase Auth
  - Migrate Firestore to PostgreSQL
  - Keep Realtime DB for signaling
  - Add Redis for caching
Phase 3 (Enterprise):
  - Custom auth service
  - Kubernetes deployment
  - Multiple region support
```

---

## 9. Monitoring and Alerts

### Key Metrics to Monitor

| Metric | Threshold | Alert |
|--------|-----------|-------|
| Call success rate | < 90% | Critical |
| Matching time | > 60s avg | Warning |
| Error rate | > 5% | Critical |
| Active connections | > 80% capacity | Warning |

### Firebase Monitoring Setup

```javascript
// Log custom metrics
functions.logger.info('Call connected', {
  sessionId: sessionId,
  matchTime: matchTimeMs,
  topic: topic
});

// Set up alerts in Firebase Console
// Performance Monitoring > Alerts
```

---

## 10. Cost Estimation

### Monthly Costs at Scale

| Users (DAU) | Firebase | TURN | Total |
|-------------|----------|------|-------|
| 100 | Free | $0 | $0 |
| 500 | $25 | $15 | $40 |
| 1,000 | $50 | $30 | $80 |
| 5,000 | $200 | $150 | $350 |
| 10,000 | $400 | $300 | $700 |

### Cost Optimization Tips

1. Use Firestore queries efficiently (indexes)
2. Cache availability data locally
3. Batch writes where possible
4. Use P2P connections when possible (reduce TURN)
5. Compress signaling data

---

## Next Steps

1. Set up Firebase project
2. Deploy initial Firestore rules
3. Implement auth flow
4. Create first Cloud Functions
5. Test signaling with 2 devices
6. Add TURN server configuration

