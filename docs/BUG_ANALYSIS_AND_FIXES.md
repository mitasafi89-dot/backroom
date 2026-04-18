# Deep Bug Analysis and Fixes

## Bug 1: Duplicate FCM Token

### Root Cause
The FCM token was being sent multiple times due to:
1. `registerFcmToken()` called on WebSocket connection
2. `onNewToken()` callback in FirebaseMessagingService triggered at app startup
3. No deduplication flag

### Fix Applied
Added `hasFcmTokenRegistered` flag in `CallManager.kt` to prevent duplicate registration:

```kotlin
private var hasFcmTokenRegistered = false

fun registerFcmToken() {
    if (hasFcmTokenRegistered) {
        Log.d(TAG, "⚠️ FCM token already registered - skipping duplicate")
        return
    }
    hasFcmTokenRegistered = true
    // ... rest of registration
}
```

### Files Modified
- `CallManager.kt` - Added guard flag
- `BackroomMessagingService.kt` - Added clarifying comments

---

## Bug 2: No Audio Between Devices

### Root Cause Analysis
Multiple contributing factors:

1. **Speakerphone was OFF** - Audio was routing to earpiece, making it nearly inaudible without holding phone to ear

2. **Missing audio state callbacks** - No visibility into whether audio was actually flowing

3. **Insufficient remote audio track handling** - Needed more robust verification that remote audio was enabled

### Fixes Applied

#### 1. Enabled Speakerphone by Default
```kotlin
// In configureAudioForCall()
am.isSpeakerphoneOn = true  // Changed from false
```

#### 2. Added Audio Device State Callbacks
```kotlin
val audioDeviceModule = JavaAudioDeviceModule.builder(context)
    .setAudioTrackStateCallback(object : AudioTrackStateCallback {
        override fun onWebRtcAudioTrackStart() {
            Log.d(TAG, "🔊 AUDIO TRACK STARTED - Remote audio playback beginning!")
        }
        override fun onWebRtcAudioTrackStop() {
            Log.d(TAG, "🔇 AUDIO TRACK STOPPED")
        }
    })
    .setAudioRecordStateCallback(object : AudioRecordStateCallback {
        override fun onWebRtcAudioRecordStart() {
            Log.d(TAG, "🎤 AUDIO RECORD STARTED - Microphone capture beginning!")
        }
        override fun onWebRtcAudioRecordStop() {
            Log.d(TAG, "🎤 AUDIO RECORD STOPPED")
        }
    })
    .createAudioDeviceModule()
```

#### 3. Enhanced Remote Track Handling
- Added comprehensive logging in `onAddTrack()` and `onTrack()` callbacks
- Added transceiver direction verification (ensure SEND_RECV not SEND_ONLY)
- Added state validation for remote audio track

#### 4. Added Audio Verification Methods
```kotlin
fun verifyAudioSetup(): Boolean {
    // Checks local audio track
    // Checks remote audio track  
    // Checks audio manager state
    // Returns false if any issues detected
}

fun logStats() {
    // Logs all transceiver states
    // Logs all audio manager settings
    // Logs track enable states
}
```

### Files Modified
- `WebRTCManager.kt` - Major audio handling improvements

---

## Bug 3: Add Thorough Logging

### Enhancements Added

#### Audio State Callbacks
- `onWebRtcAudioTrackStart()` / `onWebRtcAudioTrackStop()` - Remote playback
- `onWebRtcAudioRecordStart()` / `onWebRtcAudioRecordStop()` - Microphone capture

#### ICE Connection State Logging
```kotlin
override fun onIceConnectionChange(state: IceConnectionState?) {
    // Now logs:
    // - Local/remote audio track states
    // - All transceiver states
    // - Audio manager configuration
    // - Volume levels
}
```

#### WebRTC Stats Logging
```kotlin
fun logStats() {
    // Logs all WebRTC statistics
    // Called automatically when connection established
}
```

#### Audio Verification on Connect
```kotlin
// In CallManager when WebRTC state becomes CONNECTED:
_webRTCManager?.verifyAudioSetup()
_webRTCManager?.logStats()
```

### Expected Log Output After Fixes
When a call connects, you should now see:
```
═══════════════════════════════════════════════════
🔊 AUDIO TRACK STARTED - Remote audio playback beginning!
═══════════════════════════════════════════════════
🎤 AUDIO RECORD STARTED - Microphone capture beginning!
═══════════════════════════════════════════════════
🔗 ICE connection state: CONNECTED
   ✅ ICE CONNECTED - WebRTC call established!
   ─────────────────────────────────────────────
   AUDIO VERIFICATION ON ICE CONNECTED:
   Local audio track: true
   Local audio enabled: true
   Remote audio track: true
   Remote audio enabled: true
   Transceiver[0]: type=AUDIO, dir=SEND_RECV
   AudioManager state:
   - Mode: 3 (IN_COMMUNICATION)
   - Speakerphone: true
   - Mic muted: false
   - Voice volume: 12
═══════════════════════════════════════════════════
```

If audio is NOT working, you'll see errors like:
```
❌ Remote audio track is NULL - cannot hear remote party!
❌ Voice call volume is ZERO!
```

---

## Summary of Changes

| File | Change |
|------|--------|
| `CallManager.kt` | Added `hasFcmTokenRegistered` flag, added audio verification on connect |
| `WebRTCManager.kt` | Enabled speakerphone, added audio callbacks, enhanced logging, added `verifyAudioSetup()` and `logStats()` |
| `BackroomMessagingService.kt` | Added clarifying comments for FCM token handling |

## Testing Recommendations

1. **After deploying fixes**, start two devices as listeners
2. Have one device submit a share request
3. Accept the preview on the other device
4. **Watch logs for**:
   - `🔊 AUDIO TRACK STARTED` - Confirms remote audio playback initialized
   - `🎤 AUDIO RECORD STARTED` - Confirms microphone capture initialized
   - `✅ AUDIO SETUP VALID` - Confirms all audio checks passed
5. **If no audio**, look for `❌` errors in the verification output

