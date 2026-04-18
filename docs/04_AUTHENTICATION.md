# Backroom Authentication Flow

## Overview

Backroom uses a tiered authentication system:
- **Free tier**: Anonymous auth (zero friction)
- **Plus tier**: Phone auth (verified identity for premium features)

This balances privacy with safety and monetization.

---

## Authentication Strategy

### Why This Approach

| Requirement | Solution |
|-------------|----------|
| Zero friction onboarding | Anonymous auth - no signup required |
| User privacy | No email, no name, no profile |
| Abuse prevention | Device binding + phone for Plus |
| Account recovery | Phone number (optional) |
| Subscription management | Phone-linked accounts |

### Auth Tiers

| Tier | Auth Method | Identity | Features |
|------|-------------|----------|----------|
| Free | Anonymous | Device-bound UID | Basic calls, 15 min max |
| Plus | Phone | Phone-verified UID | Longer calls, priority |

---

## Flow 1: Anonymous Authentication (Free Tier)

### First Launch Flow

```
┌─────────────────────────────────────────────────────────────┐
│                    FIRST LAUNCH                              │
└─────────────────────────────────────────────────────────────┘

        User                          App                     Firebase
          │                            │                          │
          │  1. Opens app              │                          │
          ├───────────────────────────►│                          │
          │                            │                          │
          │                            │  2. Check for stored UID │
          │                            ├─────────────────────────►│
          │                            │                          │
          │                            │  3. No UID found         │
          │                            │◄─────────────────────────┤
          │                            │                          │
          │                            │  4. signInAnonymously()  │
          │                            ├─────────────────────────►│
          │                            │                          │
          │                            │  5. Returns new UID      │
          │                            │◄─────────────────────────┤
          │                            │                          │
          │                            │  6. Store UID locally    │
          │                            │  7. Create user document │
          │                            │                          │
          │  8. Show onboarding        │                          │
          │◄───────────────────────────┤                          │
          │                            │                          │
```

### Implementation

```kotlin
// AuthRepository.kt
class AuthRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val preferences: SharedPreferences
) {
    suspend fun ensureAuthenticated(): AuthResult {
        // Check if already signed in
        auth.currentUser?.let { user ->
            return AuthResult.Success(user.uid)
        }
        
        // Sign in anonymously
        return try {
            val result = auth.signInAnonymously().await()
            val user = result.user ?: throw Exception("No user returned")
            
            // Create user document
            createUserDocument(user.uid)
            
            // Store locally for quick access
            preferences.edit().putString("user_id", user.uid).apply()
            
            AuthResult.Success(user.uid)
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Authentication failed")
        }
    }
    
    private suspend fun createUserDocument(userId: String) {
        val user = hashMapOf(
            "id" to odaId,
            "createdAt" to FieldValue.serverTimestamp(),
            "locale" to Locale.getDefault().language,
            "status" to "active",
            "roles" to listOf("sharer", "listener"),
            "subscriptionTier" to "free",
            "onboardingComplete" to false
        )
        
        firestore.collection("users").document(userId).set(user).await()
    }
}

sealed class AuthResult {
    data class Success(val userId: String) : AuthResult()
    data class Error(val message: String) : AuthResult()
}
```

### Returning User Flow

```
┌─────────────────────────────────────────────────────────────┐
│                    RETURNING USER                            │
└─────────────────────────────────────────────────────────────┘

        User                          App                     Firebase
          │                            │                          │
          │  1. Opens app              │                          │
          ├───────────────────────────►│                          │
          │                            │                          │
          │                            │  2. Check currentUser    │
          │                            ├─────────────────────────►│
          │                            │                          │
          │                            │  3. User exists          │
          │                            │◄─────────────────────────┤
          │                            │                          │
          │                            │  4. Verify token valid   │
          │                            ├─────────────────────────►│
          │                            │                          │
          │                            │  5. Token valid          │
          │                            │◄─────────────────────────┤
          │                            │                          │
          │  6. Go directly to Home    │                          │
          │◄───────────────────────────┤                          │
          │                            │                          │
```

### Edge Cases

| Scenario | Handling |
|----------|----------|
| Token expired | Auto-refresh (Firebase handles) |
| User deleted on server | Re-create anonymous account |
| App data cleared | New anonymous account (fresh start) |
| Reinstall on same device | New anonymous account |

---

## Flow 2: Phone Authentication (Plus Tier)

### Upgrade Flow

```
┌─────────────────────────────────────────────────────────────┐
│                    UPGRADE TO PLUS                           │
└─────────────────────────────────────────────────────────────┘

        User                          App                     Firebase
          │                            │                          │
          │  1. Tap "Upgrade to Plus"  │                          │
          ├───────────────────────────►│                          │
          │                            │                          │
          │  2. Show phone input       │                          │
          │◄───────────────────────────┤                          │
          │                            │                          │
          │  3. Enter phone number     │                          │
          │  +254 712 345 678          │                          │
          ├───────────────────────────►│                          │
          │                            │                          │
          │                            │  4. verifyPhoneNumber()  │
          │                            ├─────────────────────────►│
          │                            │                          │
          │                            │  5. SMS sent             │
          │                            │◄─────────────────────────┤
          │                            │                          │
          │  6. Show OTP input         │                          │
          │◄───────────────────────────┤                          │
          │                            │                          │
          │  7. Enter OTP: 123456      │                          │
          ├───────────────────────────►│                          │
          │                            │                          │
          │                            │  8. signInWithCredential │
          │                            ├─────────────────────────►│
          │                            │                          │
          │                            │  9. Link to anon account │
          │                            ├─────────────────────────►│
          │                            │                          │
          │                            │  10. Account linked      │
          │                            │◄─────────────────────────┤
          │                            │                          │
          │  11. Show payment flow     │                          │
          │◄───────────────────────────┤                          │
          │                            │                          │
```

### Implementation

```kotlin
// PhoneAuthManager.kt
class PhoneAuthManager(
    private val auth: FirebaseAuth,
    private val activity: Activity
) {
    private var verificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null
    
    fun startPhoneVerification(
        phoneNumber: String,
        onCodeSent: () -> Unit,
        onVerificationComplete: (PhoneAuthCredential) -> Unit,
        onError: (String) -> Unit
    ) {
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                // Auto-verification (rare, but possible)
                onVerificationComplete(credential)
            }
            
            override fun onVerificationFailed(e: FirebaseException) {
                val message = when (e) {
                    is FirebaseAuthInvalidCredentialsException -> 
                        "Invalid phone number format"
                    is FirebaseTooManyRequestsException -> 
                        "Too many attempts. Please try later."
                    else -> 
                        "Verification failed. Please try again."
                }
                onError(message)
            }
            
            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                this@PhoneAuthManager.verificationId = verificationId
                this@PhoneAuthManager.resendToken = token
                onCodeSent()
            }
        }
        
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()
        
        PhoneAuthProvider.verifyPhoneNumber(options)
    }
    
    suspend fun verifyCode(code: String): Result<Unit> {
        val verificationId = this.verificationId 
            ?: return Result.failure(Exception("No verification in progress"))
        
        val credential = PhoneAuthProvider.getCredential(verificationId, code)
        
        return try {
            // Link phone to existing anonymous account
            auth.currentUser?.linkWithCredential(credential)?.await()
                ?: throw Exception("No current user")
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun resendCode(
        phoneNumber: String,
        onCodeSent: () -> Unit,
        onError: (String) -> Unit
    ) {
        val token = resendToken ?: run {
            onError("Please start verification again")
            return
        }
        
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {}
            override fun onVerificationFailed(e: FirebaseException) {
                onError("Failed to resend code")
            }
            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                this@PhoneAuthManager.verificationId = verificationId
                this@PhoneAuthManager.resendToken = token
                onCodeSent()
            }
        }
        
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .setForceResendingToken(token)
            .build()
        
        PhoneAuthProvider.verifyPhoneNumber(options)
    }
}
```

### Phone Number Handling

```kotlin
// PhoneNumberValidator.kt
object PhoneNumberValidator {
    
    // Kenya phone format: +254 7XX XXX XXX
    private val KENYA_REGEX = Regex("^\\+254[17]\\d{8}$")
    
    fun validate(number: String): ValidationResult {
        val cleaned = number.replace(Regex("[\\s-]"), "")
        
        // Add country code if missing
        val formatted = when {
            cleaned.startsWith("0") -> "+254${cleaned.substring(1)}"
            cleaned.startsWith("254") -> "+$cleaned"
            cleaned.startsWith("+254") -> cleaned
            else -> "+254$cleaned"
        }
        
        return if (KENYA_REGEX.matches(formatted)) {
            ValidationResult.Valid(formatted)
        } else {
            ValidationResult.Invalid("Please enter a valid Kenyan phone number")
        }
    }
    
    sealed class ValidationResult {
        data class Valid(val formatted: String) : ValidationResult()
        data class Invalid(val message: String) : ValidationResult()
    }
}
```

---

## Flow 3: Account Recovery

### Scenario: User reinstalls app with Plus subscription

```
┌─────────────────────────────────────────────────────────────┐
│                    ACCOUNT RECOVERY                          │
└─────────────────────────────────────────────────────────────┘

        User                          App                     Firebase
          │                            │                          │
          │  1. Fresh install          │                          │
          ├───────────────────────────►│                          │
          │                            │                          │
          │                            │  2. Create anon account  │
          │                            ├─────────────────────────►│
          │                            │                          │
          │  3. Complete onboarding    │                          │
          │◄───────────────────────────┤                          │
          │                            │                          │
          │  4. Tap "Restore Purchase" │                          │
          ├───────────────────────────►│                          │
          │                            │                          │
          │  5. Enter phone number     │                          │
          ├───────────────────────────►│                          │
          │                            │                          │
          │                            │  6. Verify phone         │
          │                            ├─────────────────────────►│
          │                            │                          │
          │  7. Enter OTP              │                          │
          ├───────────────────────────►│                          │
          │                            │                          │
          │                            │  8. Sign in with phone   │
          │                            │  (replaces anon account) │
          │                            ├─────────────────────────►│
          │                            │                          │
          │                            │  9. Fetch subscription   │
          │                            ├─────────────────────────►│
          │                            │                          │
          │  10. Plus restored!        │                          │
          │◄───────────────────────────┤                          │
          │                            │                          │
```

### Implementation

```kotlin
// AccountRecoveryManager.kt
class AccountRecoveryManager(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val phoneAuthManager: PhoneAuthManager
) {
    suspend fun recoverAccount(phoneCredential: PhoneAuthCredential): RecoveryResult {
        // This will replace the current anonymous account
        // with the phone-linked account if it exists
        
        return try {
            val result = auth.signInWithCredential(phoneCredential).await()
            val user = result.user ?: throw Exception("No user")
            
            // Check if this phone has an existing account
            val userDoc = firestore.collection("users").document(user.uid).get().await()
            
            if (userDoc.exists()) {
                // Existing account found - check subscription
                val subscription = firestore.collection("subscriptions")
                    .document(user.uid)
                    .get()
                    .await()
                
                val tier = subscription.getString("tier") ?: "free"
                val isActive = subscription.getString("status") == "active"
                
                RecoveryResult.AccountFound(
                    userId = user.uid,
                    isPlusActive = tier == "plus" && isActive
                )
            } else {
                // New phone number - create fresh account
                RecoveryResult.NewAccount(user.uid)
            }
        } catch (e: Exception) {
            RecoveryResult.Error(e.message ?: "Recovery failed")
        }
    }
}

sealed class RecoveryResult {
    data class AccountFound(val userId: String, val isPlusActive: Boolean) : RecoveryResult()
    data class NewAccount(val userId: String) : RecoveryResult()
    data class Error(val message: String) : RecoveryResult()
}
```

---

## Flow 4: Device Binding (Anti-Abuse)

### Purpose
Track devices to prevent ban evasion without requiring personal info.

### Implementation

```kotlin
// DeviceManager.kt
class DeviceManager(
    private val firestore: FirebaseFirestore,
    private val context: Context
) {
    fun getDeviceFingerprint(): String {
        // Create a semi-unique device identifier
        // Note: This is not perfect but adds a layer of protection
        
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        
        // Hash it for privacy
        return hashSha256(androidId)
    }
    
    suspend fun registerDevice(userId: String, fcmToken: String?) {
        val deviceId = getDeviceFingerprint()
        
        val device = hashMapOf(
            "deviceId" to deviceId,
            "userId" to odaId,
            "fcmToken" to fcmToken,
            "platform" to "android",
            "appVersion" to BuildConfig.VERSION_NAME,
            "createdAt" to FieldValue.serverTimestamp(),
            "lastSeenAt" to FieldValue.serverTimestamp(),
            "riskFlags" to emptyList<String>(),
            "isBanned" to false
        )
        
        firestore.collection("devices").document(deviceId)
            .set(device, SetOptions.merge())
            .await()
    }
    
    suspend fun checkDeviceBan(): Boolean {
        val deviceId = getDeviceFingerprint()
        val device = firestore.collection("devices").document(deviceId).get().await()
        return device.getBoolean("isBanned") ?: false
    }
    
    private fun hashSha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
```

### Device Ban Flow

```kotlin
// On app launch
suspend fun checkAccess(): AccessResult {
    // First check device ban
    if (deviceManager.checkDeviceBan()) {
        return AccessResult.DeviceBanned
    }
    
    // Then check user status
    val userId = auth.currentUser?.uid ?: return AccessResult.NotAuthenticated
    val user = firestore.collection("users").document(userId).get().await()
    
    return when (user.getString("status")) {
        "active" -> AccessResult.Allowed
        "suspended" -> AccessResult.Suspended(user.getString("suspendedUntil"))
        "banned" -> AccessResult.Banned
        else -> AccessResult.Allowed
    }
}

sealed class AccessResult {
    object Allowed : AccessResult()
    object NotAuthenticated : AccessResult()
    object DeviceBanned : AccessResult()
    data class Suspended(val until: String?) : AccessResult()
    object Banned : AccessResult()
}
```

---

## Security Rules

### Firestore Rules

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    
    // Helper functions
    function isAuthenticated() {
      return request.auth != null;
    }
    
    function isOwner(userId) {
      return request.auth.uid == userId;
    }
    
    function isNotBanned() {
      return get(/databases/$(database)/documents/users/$(request.auth.uid)).data.status == 'active';
    }
    
    // Users collection
    match /users/{userId} {
      allow read: if isAuthenticated() && isOwner(userId);
      allow create: if isAuthenticated() && isOwner(userId);
      allow update: if isAuthenticated() && isOwner(userId) 
                    && !request.resource.data.diff(resource.data).affectedKeys()
                        .hasAny(['status', 'subscriptionTier']); // Can't modify these
    }
    
    // Preferences collection
    match /preferences/{userId} {
      allow read, write: if isAuthenticated() && isOwner(userId);
    }
    
    // Availability collection
    match /availability/{odayId} {
      allow read: if isAuthenticated();
      allow write: if isAuthenticated() && isOwner(userId) && isNotBanned();
    }
    
    // Call requests - create only, read own
    match /callRequests/{requestId} {
      allow create: if isAuthenticated() && isNotBanned();
      allow read: if isAuthenticated() && 
                    (resource.data.sharerId == request.auth.uid || 
                     resource.data.matchedListenerId == request.auth.uid);
    }
    
    // Call sessions - read only for participants
    match /callSessions/{sessionId} {
      allow read: if isAuthenticated() && 
                    (resource.data.sharerId == request.auth.uid || 
                     resource.data.listenerId == request.auth.uid);
    }
    
    // Feedback - create only, for own sessions
    match /feedback/{feedbackId} {
      allow create: if isAuthenticated() && 
                      request.resource.data.userId == request.auth.uid;
    }
    
    // Reports - create only
    match /reports/{reportId} {
      allow create: if isAuthenticated() && 
                      request.resource.data.reporterId == request.auth.uid;
    }
    
    // Subscriptions - read own only
    match /subscriptions/{userId} {
      allow read: if isAuthenticated() && isOwner(userId);
    }
    
    // Crisis resources - public read
    match /crisisResources/{resourceId} {
      allow read: if true;
    }
    
    // Reputation - no client access (Cloud Functions only)
    match /reputation/{userId} {
      allow read, write: if false;
    }
    
    // Devices - no client access (Cloud Functions only)
    match /devices/{deviceId} {
      allow read, write: if false;
    }
  }
}
```

### Realtime Database Rules

```json
{
  "rules": {
    "signalingRooms": {
      "$roomId": {
        ".read": "auth != null && (data.child('sharerId').val() === auth.uid || data.child('listenerId').val() === auth.uid)",
        ".write": "auth != null && (data.child('sharerId').val() === auth.uid || data.child('listenerId').val() === auth.uid || !data.exists())",
        
        "offer": {
          ".write": "auth != null && data.parent().child('sharerId').val() === auth.uid"
        },
        "answer": {
          ".write": "auth != null && data.parent().child('listenerId').val() === auth.uid"
        },
        "sharerCandidates": {
          ".write": "auth != null && data.parent().child('sharerId').val() === auth.uid"
        },
        "listenerCandidates": {
          ".write": "auth != null && data.parent().child('listenerId').val() === auth.uid"
        }
      }
    }
  }
}
```

---

## UI Components

### Phone Input Screen

```
┌─────────────────────────────┐
│ ← Back      Verify Phone    │
├─────────────────────────────┤
│                             │
│   Enter your phone number   │
│                             │
│   We'll send a verification │
│   code via SMS.             │
│                             │
│   ┌───────────────────────┐ │
│   │ +254                  │ │
│   │ ┌───────────────────┐ │ │
│   │ │ 712 345 678       │ │ │
│   │ └───────────────────┘ │ │
│   └───────────────────────┘ │
│                             │
│   Your number is only used  │
│   for verification and      │
│   account recovery.         │
│                             │
│   [  Send Code  ]           │
│                             │
└─────────────────────────────┘
```

### OTP Input Screen

```
┌─────────────────────────────┐
│ ← Back      Verify Phone    │
├─────────────────────────────┤
│                             │
│   Enter the code            │
│                             │
│   Sent to +254 712 345 678  │
│                             │
│   ┌───┐ ┌───┐ ┌───┐ ┌───┐   │
│   │ 1 │ │ 2 │ │ 3 │ │ 4 │   │
│   └───┘ └───┘ └───┘ └───┘   │
│   ┌───┐ ┌───┐               │
│   │ 5 │ │ 6 │               │
│   └───┘ └───┘               │
│                             │
│   Didn't receive it?        │
│   [ Resend in 45s ]         │
│                             │
│   [  Verify  ]              │
│                             │
└─────────────────────────────┘
```

---

## Error Handling

### Common Errors

| Error | User Message | Action |
|-------|--------------|--------|
| Invalid phone format | "Please enter a valid phone number" | Show format hint |
| Too many attempts | "Too many attempts. Try again in 1 hour." | Block with timer |
| Invalid OTP | "Incorrect code. Please try again." | Clear input |
| OTP expired | "Code expired. Please request a new one." | Show resend |
| Network error | "Check your connection and try again." | Retry button |
| Account exists (different device) | "This number is linked to another account." | Offer recovery |

### Implementation

```kotlin
// AuthErrorHandler.kt
object AuthErrorHandler {
    fun getMessage(exception: Exception): String {
        return when (exception) {
            is FirebaseAuthInvalidCredentialsException -> 
                "Invalid code. Please check and try again."
            is FirebaseAuthUserCollisionException -> 
                "This phone number is already in use."
            is FirebaseTooManyRequestsException -> 
                "Too many attempts. Please try again later."
            is FirebaseNetworkException -> 
                "Network error. Please check your connection."
            else -> 
                "Something went wrong. Please try again."
        }
    }
}
```

---

## Testing Checklist

### Anonymous Auth
- [ ] First launch creates anonymous account
- [ ] Returning user stays signed in
- [ ] Token refresh works automatically
- [ ] App data clear creates new account
- [ ] Reinstall creates new account

### Phone Auth
- [ ] Valid Kenya number accepted
- [ ] Invalid format rejected with message
- [ ] OTP sent successfully
- [ ] Correct OTP verifies
- [ ] Wrong OTP shows error
- [ ] Resend works after cooldown
- [ ] Phone links to anonymous account
- [ ] Existing phone recovers account

### Security
- [ ] Device ban blocks access
- [ ] User suspension shows message
- [ ] Banned user cannot access
- [ ] Firestore rules block unauthorized access
- [ ] Realtime DB rules enforce participants only

---

## Next Steps

1. Set up Firebase Auth in console
2. Enable Anonymous and Phone providers
3. Configure SMS templates
4. Implement auth repository
5. Create auth UI screens
6. Test on real devices
7. Monitor auth metrics

