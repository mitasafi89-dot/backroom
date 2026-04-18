# Backroom Offline Handling

## Overview

Offline handling is critical for Backroom because:
1. Kenya has variable network connectivity
2. Users may move between WiFi/mobile data
3. Calls can drop mid-session
4. Feedback should work even with poor connection

This document defines offline behavior for every scenario.

---

## Network States

| State | Definition | Behavior |
|-------|------------|----------|
| ONLINE | Full connectivity | Normal operation |
| DEGRADED | Slow/unstable connection | Reduced quality, warnings |
| OFFLINE | No connectivity | Cached data, queued actions |
| RECONNECTING | Recovering from offline | Sync pending, show status |

---

## Connectivity Detection

```kotlin
// NetworkMonitor.kt
class NetworkMonitor(private val context: Context) {
    
    private val connectivityManager = 
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    private val _networkState = MutableStateFlow(NetworkState.UNKNOWN)
    val networkState: StateFlow<NetworkState> = _networkState.asStateFlow()
    
    init {
        registerNetworkCallback()
    }
    
    private fun registerNetworkCallback() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            
            override fun onAvailable(network: Network) {
                checkConnectionQuality(network)
            }
            
            override fun onLost(network: Network) {
                _networkState.value = NetworkState.OFFLINE
            }
            
            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities
            ) {
                checkConnectionQuality(network, capabilities)
            }
        })
    }
    
    private fun checkConnectionQuality(
        network: Network,
        capabilities: NetworkCapabilities? = null
    ) {
        val caps = capabilities ?: connectivityManager.getNetworkCapabilities(network)
        
        _networkState.value = when {
            caps == null -> NetworkState.OFFLINE
            
            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> 
                NetworkState.OFFLINE
            
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                val linkSpeed = caps.linkDownstreamBandwidthKbps
                if (linkSpeed > 1000) NetworkState.ONLINE
                else NetworkState.DEGRADED
            }
            
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                val linkSpeed = caps.linkDownstreamBandwidthKbps
                when {
                    linkSpeed > 2000 -> NetworkState.ONLINE    // 4G+
                    linkSpeed > 500 -> NetworkState.DEGRADED   // 3G
                    else -> NetworkState.DEGRADED              // 2G
                }
            }
            
            else -> NetworkState.DEGRADED
        }
    }
    
    fun isOnline(): Boolean = _networkState.value == NetworkState.ONLINE
    fun isOffline(): Boolean = _networkState.value == NetworkState.OFFLINE
}

enum class NetworkState {
    UNKNOWN,
    ONLINE,
    DEGRADED,
    OFFLINE,
    RECONNECTING
}
```

---

## Scenario 1: App Launch While Offline

### Behavior

```
┌────────────────────────────────────────────────────────────┐
│                    OFFLINE APP LAUNCH                       │
└────────────────────────────────────────────────────────────┘

1. App opens
2. Detect offline state
3. Load cached user data (Firestore offline persistence)
4. Show Home screen with offline banner
5. Disable: Start Call, Toggle Availability
6. Enable: Settings, Help (cached)
7. When back online: Auto-sync, remove banner

User sees:
┌─────────────────────────────┐
│ ⚠️ You're offline           │
├─────────────────────────────┤
│                             │
│   [Start a Call] (disabled) │
│                             │
│   Check your connection     │
│   to make or receive calls. │
│                             │
└─────────────────────────────┘
```

### Implementation

```kotlin
// HomeViewModel.kt
class HomeViewModel(
    private val networkMonitor: NetworkMonitor
) : ViewModel() {
    
    val uiState = combine(
        networkMonitor.networkState,
        userRepository.userFlow,
        availabilityRepository.availabilityFlow
    ) { network, user, availability ->
        HomeUiState(
            isOnline = network == NetworkState.ONLINE,
            isOffline = network == NetworkState.OFFLINE,
            isDegraded = network == NetworkState.DEGRADED,
            canStartCall = network == NetworkState.ONLINE && user != null,
            canToggleAvailability = network == NetworkState.ONLINE,
            showOfflineBanner = network == NetworkState.OFFLINE,
            user = user,
            availability = availability
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), HomeUiState())
}

// HomeScreen.kt
@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val state by viewModel.uiState.collectAsState()
    
    Column {
        // Offline banner
        AnimatedVisibility(visible = state.showOfflineBanner) {
            OfflineBanner()
        }
        
        // Degraded connection warning
        AnimatedVisibility(visible = state.isDegraded) {
            DegradedBanner()
        }
        
        // Start Call button
        Button(
            onClick = { /* navigate to call flow */ },
            enabled = state.canStartCall
        ) {
            Text(if (state.isOffline) "Offline" else "Start a Call")
        }
    }
}

@Composable
fun OfflineBanner() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.WifiOff, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("You're offline. Check your connection.")
        }
    }
}
```

---

## Scenario 2: Network Drops During Matching

### Behavior

```
┌────────────────────────────────────────────────────────────┐
│                NETWORK DROPS DURING MATCHING                │
└────────────────────────────────────────────────────────────┘

Timeline:
0:00 - User submits call request
0:10 - Matching in progress
0:15 - Network drops
0:20 - Client detects offline
0:25 - Show "Connection lost" message
1:00 - Show "Try again" option
5:00 - Server-side: Request expires if no reconnection

Client Flow:
1. Detect offline during WAITING state
2. Keep request active (server handles timeout)
3. Show message: "Connection lost. Waiting to reconnect..."
4. Start reconnection attempts
5. If back online within 1 min: Resume matching
6. If still offline after 1 min: Show "Try again" button
7. Server auto-expires request after 5 min with no client ping

Server Flow:
1. Request marked as MATCHING
2. No client heartbeat for 30s
3. Mark request as STALE
4. After 5 min stale: Auto-expire request
5. If client reconnects while STALE: Resume matching
```

### Implementation

```kotlin
// MatchingViewModel.kt
class MatchingViewModel(
    private val networkMonitor: NetworkMonitor,
    private val callRepository: CallRepository
) : ViewModel() {
    
    private var reconnectionJob: Job? = null
    
    val uiState = MutableStateFlow(MatchingUiState())
    
    init {
        observeNetwork()
    }
    
    private fun observeNetwork() {
        viewModelScope.launch {
            networkMonitor.networkState.collect { state ->
                when (state) {
                    NetworkState.OFFLINE -> handleOffline()
                    NetworkState.ONLINE -> handleReconnected()
                    NetworkState.DEGRADED -> showDegradedWarning()
                    else -> {}
                }
            }
        }
    }
    
    private fun handleOffline() {
        uiState.update { it.copy(
            status = MatchingStatus.CONNECTION_LOST,
            message = "Connection lost. Waiting to reconnect..."
        )}
        
        // Start reconnection timer
        reconnectionJob = viewModelScope.launch {
            delay(60_000) // 1 minute
            if (networkMonitor.isOffline()) {
                uiState.update { it.copy(
                    status = MatchingStatus.RECONNECTION_TIMEOUT,
                    message = "Still offline. Would you like to try again when connected?",
                    showRetry = true
                )}
            }
        }
    }
    
    private fun handleReconnected() {
        reconnectionJob?.cancel()
        
        viewModelScope.launch {
            // Check if our request is still valid
            val requestStatus = callRepository.checkRequestStatus(currentRequestId)
            
            when (requestStatus) {
                RequestStatus.ACTIVE -> {
                    uiState.update { it.copy(
                        status = MatchingStatus.MATCHING,
                        message = "Back online! Finding a listener..."
                    )}
                }
                RequestStatus.EXPIRED -> {
                    uiState.update { it.copy(
                        status = MatchingStatus.EXPIRED,
                        message = "Your request expired. Would you like to try again?",
                        showRetry = true
                    )}
                }
                RequestStatus.MATCHED -> {
                    // Listener was found while we were offline!
                    navigateToCall()
                }
            }
        }
    }
    
    fun retryMatching() {
        // Resubmit the call request
        viewModelScope.launch {
            uiState.update { it.copy(status = MatchingStatus.MATCHING) }
            callRepository.submitCallRequest(currentPreview)
        }
    }
}
```

---

## Scenario 3: Network Drops During Call

### Behavior

This is the most critical scenario.

```
┌────────────────────────────────────────────────────────────┐
│                  NETWORK DROPS DURING CALL                  │
└────────────────────────────────────────────────────────────┘

Timeline:
0:00 - Call active, both parties connected
3:45 - Party A loses connection
3:46 - WebRTC detects ICE failure
3:47 - Party A sees "Reconnecting..."
3:47 - Party B audio cuts out
3:50 - Party B sees "Other party reconnecting..."
4:00 - 15 second reconnection window starts
4:15 - If not reconnected: Call ends for both
4:15 - Both see "Call dropped. Connection lost."

Grace Period Rules:
- 15 seconds to reconnect
- Both parties notified of drop
- If reconnected: Resume seamlessly
- If not: End call gracefully
- Post-call feedback still offered
```

### WebRTC Connection Monitoring

```kotlin
// WebRTCManager.kt (connection monitoring section)
class WebRTCManager {
    
    private var reconnectionTimer: Job? = null
    private val RECONNECTION_TIMEOUT_MS = 15_000L
    
    private val peerConnectionObserver = object : PeerConnection.Observer {
        
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
            when (state) {
                PeerConnection.IceConnectionState.CONNECTED -> {
                    onConnectionEstablished()
                }
                
                PeerConnection.IceConnectionState.DISCONNECTED -> {
                    onConnectionInterrupted()
                }
                
                PeerConnection.IceConnectionState.FAILED -> {
                    onConnectionFailed()
                }
                
                PeerConnection.IceConnectionState.CLOSED -> {
                    onConnectionClosed()
                }
                
                else -> {}
            }
        }
        
        override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
            Log.d("WebRTC", "Connection state: $state")
        }
        
        // ... other observer methods
    }
    
    private fun onConnectionInterrupted() {
        _connectionState.value = CallConnectionState.RECONNECTING
        
        // Start grace period timer
        reconnectionTimer = CoroutineScope(Dispatchers.Main).launch {
            delay(RECONNECTION_TIMEOUT_MS)
            
            // Still disconnected after grace period
            if (_connectionState.value == CallConnectionState.RECONNECTING) {
                onConnectionFailed()
            }
        }
        
        // Attempt ICE restart
        attemptIceRestart()
    }
    
    private fun onConnectionEstablished() {
        reconnectionTimer?.cancel()
        _connectionState.value = CallConnectionState.CONNECTED
    }
    
    private fun onConnectionFailed() {
        reconnectionTimer?.cancel()
        _connectionState.value = CallConnectionState.FAILED
        
        // Notify call manager to end call
        callEndCallback?.invoke(EndReason.CONNECTION_LOST)
    }
    
    private fun attemptIceRestart() {
        peerConnection?.let { pc ->
            val constraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
            }
            pc.createOffer(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription) {
                    pc.setLocalDescription(this, sdp)
                    // Send new offer through signaling
                    signalingChannel.sendOffer(sdp)
                }
                // ... other methods
            }, constraints)
        }
    }
}

enum class CallConnectionState {
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    FAILED,
    CLOSED
}
```

### In-Call UI During Reconnection

```kotlin
// CallScreen.kt
@Composable
fun CallScreen(viewModel: CallViewModel) {
    val connectionState by viewModel.connectionState.collectAsState()
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Main call UI
        CallContent(viewModel)
        
        // Reconnection overlay
        AnimatedVisibility(
            visible = connectionState == CallConnectionState.RECONNECTING,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            ReconnectionOverlay()
        }
    }
}

@Composable
fun ReconnectionOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(color = Color.White)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Reconnecting...",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Please wait",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
```

---

## Scenario 4: Network Drops During Incoming Preview

### Behavior

```
┌────────────────────────────────────────────────────────────┐
│              NETWORK DROPS DURING PREVIEW                   │
└────────────────────────────────────────────────────────────┘

1. Listener receives preview notification
2. Network drops before they can respond
3. 30-second timer continues on server
4. If not back online in 30s: Auto-skip
5. Call rematched to another listener
6. When listener comes back online:
   - If < 30s: Show "Preview expired"
   - If > 30s: Nothing (already rematched)
```

### Implementation

```kotlin
// IncomingPreviewViewModel.kt
class IncomingPreviewViewModel(
    private val networkMonitor: NetworkMonitor,
    private val callRepository: CallRepository,
    requestId: String
) : ViewModel() {
    
    private val previewTimeout = 30_000L
    private var timeoutJob: Job? = null
    
    val uiState = MutableStateFlow(PreviewUiState())
    
    init {
        startTimer()
        observeNetwork()
    }
    
    private fun startTimer() {
        val startTime = System.currentTimeMillis()
        
        timeoutJob = viewModelScope.launch {
            while (isActive) {
                val elapsed = System.currentTimeMillis() - startTime
                val remaining = ((previewTimeout - elapsed) / 1000).toInt()
                
                uiState.update { it.copy(secondsRemaining = remaining) }
                
                if (remaining <= 0) {
                    handleTimeout()
                    break
                }
                
                delay(1000)
            }
        }
    }
    
    private fun observeNetwork() {
        viewModelScope.launch {
            networkMonitor.networkState.collect { state ->
                if (state == NetworkState.OFFLINE) {
                    uiState.update { it.copy(
                        isOffline = true,
                        message = "Connection lost. Preview may expire."
                    )}
                } else if (state == NetworkState.ONLINE && uiState.value.isOffline) {
                    // Back online - check if preview still valid
                    checkPreviewValidity()
                }
            }
        }
    }
    
    private suspend fun checkPreviewValidity() {
        val status = callRepository.checkRequestStatus(requestId)
        
        when (status) {
            RequestStatus.ACTIVE -> {
                // Preview still valid
                uiState.update { it.copy(isOffline = false, message = null) }
            }
            else -> {
                // Preview expired or matched to someone else
                uiState.update { it.copy(
                    expired = true,
                    message = "This preview has expired."
                )}
            }
        }
    }
    
    fun accept() {
        if (networkMonitor.isOffline()) {
            uiState.update { it.copy(
                error = "Can't accept while offline. Check your connection."
            )}
            return
        }
        
        viewModelScope.launch {
            try {
                callRepository.acceptPreview(requestId)
            } catch (e: Exception) {
                uiState.update { it.copy(error = "Failed to accept. Please try again.") }
            }
        }
    }
}
```

---

## Scenario 5: Submitting Feedback Offline

### Behavior

```
┌────────────────────────────────────────────────────────────┐
│              FEEDBACK SUBMISSION OFFLINE                    │
└────────────────────────────────────────────────────────────┘

1. Call ends
2. User goes offline
3. User submits feedback
4. Feedback queued locally
5. When back online: Auto-submit
6. User sees: "Feedback saved. Will submit when online."
```

### Implementation

```kotlin
// FeedbackRepository.kt
class FeedbackRepository(
    private val firestore: FirebaseFirestore,
    private val localDatabase: AppDatabase,
    private val networkMonitor: NetworkMonitor
) {
    suspend fun submitFeedback(feedback: Feedback): Result<Unit> {
        return if (networkMonitor.isOnline()) {
            // Submit directly
            try {
                firestore.collection("feedback")
                    .add(feedback.toMap())
                    .await()
                Result.success(Unit)
            } catch (e: Exception) {
                // Failed - queue locally
                queueLocally(feedback)
                Result.success(Unit) // Still success from user perspective
            }
        } else {
            // Queue locally
            queueLocally(feedback)
            Result.success(Unit)
        }
    }
    
    private suspend fun queueLocally(feedback: Feedback) {
        localDatabase.pendingFeedbackDao().insert(
            PendingFeedback(
                sessionId = feedback.sessionId,
                sentiment = feedback.sentiment,
                thanked = feedback.thanked,
                recognized = feedback.recognized,
                createdAt = System.currentTimeMillis()
            )
        )
    }
    
    // Called when app comes online
    suspend fun syncPendingFeedback() {
        val pending = localDatabase.pendingFeedbackDao().getAll()
        
        for (item in pending) {
            try {
                firestore.collection("feedback")
                    .add(item.toFeedbackMap())
                    .await()
                
                localDatabase.pendingFeedbackDao().delete(item)
            } catch (e: Exception) {
                Log.e("Feedback", "Failed to sync: ${item.sessionId}", e)
                // Will retry next time
            }
        }
    }
}

// Room entity for local queue
@Entity(tableName = "pending_feedback")
data class PendingFeedback(
    @PrimaryKey val sessionId: String,
    val sentiment: String,
    val thanked: Boolean,
    val recognized: Boolean?,
    val createdAt: Long
)

@Dao
interface PendingFeedbackDao {
    @Query("SELECT * FROM pending_feedback")
    suspend fun getAll(): List<PendingFeedback>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(feedback: PendingFeedback)
    
    @Delete
    suspend fun delete(feedback: PendingFeedback)
}
```

### Sync Trigger

```kotlin
// SyncManager.kt
class SyncManager(
    private val networkMonitor: NetworkMonitor,
    private val feedbackRepository: FeedbackRepository,
    private val reportRepository: ReportRepository
) {
    init {
        CoroutineScope(Dispatchers.IO).launch {
            networkMonitor.networkState.collect { state ->
                if (state == NetworkState.ONLINE) {
                    syncPendingData()
                }
            }
        }
    }
    
    private suspend fun syncPendingData() {
        feedbackRepository.syncPendingFeedback()
        reportRepository.syncPendingReports()
    }
}
```

---

## Firestore Offline Persistence

### Setup

```kotlin
// FirebaseInitializer.kt
object FirebaseInitializer {
    
    fun initialize(context: Context) {
        // Enable offline persistence
        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
            .build()
        
        FirebaseFirestore.getInstance().firestoreSettings = settings
    }
}
```

### What Gets Cached

| Collection | Cached | Reason |
|------------|--------|--------|
| users | Yes | User profile needed offline |
| preferences | Yes | Listener boundaries |
| availability | No | Must be real-time |
| callRequests | No | Must be real-time |
| callSessions | Partial | Recent sessions for feedback |
| feedback | Write-queued | Can submit offline |
| reports | Write-queued | Can submit offline |
| crisisResources | Yes | Emergency numbers always available |

### Cache Management

```kotlin
// CacheManager.kt
class CacheManager(
    private val firestore: FirebaseFirestore
) {
    // Pre-cache crisis resources on first launch
    suspend fun preCacheCriticalData() {
        // Cache crisis resources
        firestore.collection("crisisResources")
            .whereEqualTo("country", "KE")
            .get(Source.SERVER) // Force server fetch
            .await()
        
        // Now they're cached for offline access
    }
    
    // Clear old cached data periodically
    suspend fun cleanupCache() {
        // Firestore handles this automatically based on cache size
        // But we can clear specific data if needed
    }
}
```

---

## UI Indicators

### Network Status Bar

```kotlin
// NetworkStatusBar.kt
@Composable
fun NetworkStatusBar(networkState: NetworkState) {
    AnimatedVisibility(
        visible = networkState != NetworkState.ONLINE,
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut()
    ) {
        val backgroundColor = when (networkState) {
            NetworkState.OFFLINE -> MaterialTheme.colorScheme.error
            NetworkState.DEGRADED -> MaterialTheme.colorScheme.tertiary
            NetworkState.RECONNECTING -> MaterialTheme.colorScheme.secondary
            else -> MaterialTheme.colorScheme.surface
        }
        
        val message = when (networkState) {
            NetworkState.OFFLINE -> "No internet connection"
            NetworkState.DEGRADED -> "Slow connection - call quality may be affected"
            NetworkState.RECONNECTING -> "Reconnecting..."
            else -> ""
        }
        
        val icon = when (networkState) {
            NetworkState.OFFLINE -> Icons.Default.WifiOff
            NetworkState.DEGRADED -> Icons.Default.SignalCellularConnectedNoInternet4Bar
            NetworkState.RECONNECTING -> Icons.Default.Sync
            else -> Icons.Default.Wifi
        }
        
        Surface(
            color = backgroundColor,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onError
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.onError,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
```

---

## Testing Offline Scenarios

### Manual Testing

1. **Enable Airplane Mode** during various states
2. **Disable WiFi/Data** individually
3. **Use Android Emulator's network settings** to simulate
4. **Use Firebase Local Emulator** for consistent testing

### Automated Testing

```kotlin
// OfflineTest.kt
@Test
fun feedbackIsQueuedWhenOffline() = runTest {
    // Arrange
    val networkMonitor = FakeNetworkMonitor(NetworkState.OFFLINE)
    val localDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    val repository = FeedbackRepository(firestore, localDb, networkMonitor)
    
    val feedback = Feedback(
        sessionId = "test-session",
        sentiment = "helped",
        thanked = true,
        recognized = false
    )
    
    // Act
    val result = repository.submitFeedback(feedback)
    
    // Assert
    assertTrue(result.isSuccess)
    
    val pending = localDb.pendingFeedbackDao().getAll()
    assertEquals(1, pending.size)
    assertEquals("test-session", pending[0].sessionId)
}

@Test
fun pendingFeedbackSyncsWhenOnline() = runTest {
    // Arrange
    val networkMonitor = FakeNetworkMonitor(NetworkState.OFFLINE)
    // ... setup with pending feedback
    
    // Act - come online
    networkMonitor.setState(NetworkState.ONLINE)
    
    // Wait for sync
    advanceTimeBy(1000)
    
    // Assert
    val pending = localDb.pendingFeedbackDao().getAll()
    assertEquals(0, pending.size) // Should be synced and cleared
}
```

---

## Edge Cases Summary

| Scenario | Behavior | User Feedback |
|----------|----------|---------------|
| Launch offline | Cached data shown | "You're offline" banner |
| Matching drops | Wait 1 min, then retry option | "Connection lost. Waiting..." |
| Call drops | 15s grace period | "Reconnecting..." overlay |
| Preview drops | Auto-skip after 30s | "Connection lost. Preview may expire." |
| Feedback offline | Queue locally | "Saved. Will submit when online." |
| Report offline | Queue locally | "Report saved." |
| Settings change offline | Apply locally, sync later | "Saved offline." |

---

## Next Steps

1. Implement NetworkMonitor
2. Add offline banners to all screens
3. Set up Firestore offline persistence
4. Create local Room database for pending actions
5. Implement SyncManager
6. Test all offline scenarios
7. Add analytics for offline events

