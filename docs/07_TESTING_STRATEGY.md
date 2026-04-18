# Backroom Testing Strategy

## Overview

This document defines the testing approach for Backroom, including:
- Unit tests
- Integration tests
- UI tests
- End-to-end tests
- User acceptance testing
- Beta rollout plan

---

## Testing Pyramid

```
                    ╱╲
                   ╱  ╲
                  ╱ E2E╲         ~10% - Critical user journeys
                 ╱──────╲
                ╱        ╲
               ╱Integration╲     ~20% - Component interactions
              ╱────────────╲
             ╱              ╲
            ╱   Unit Tests   ╲   ~70% - Business logic, utilities
           ╱──────────────────╲
```

---

## 1. Unit Tests

### What to Test
- ViewModels
- Repositories (with mocked dependencies)
- Utility functions
- Data transformations
- Validation logic

### Test Structure

```kotlin
// Example: PreviewValidatorTest.kt
class PreviewValidatorTest {
    
    private lateinit var validator: PreviewValidator
    
    @Before
    fun setup() {
        validator = PreviewValidator()
    }
    
    @Test
    fun `valid intent line passes validation`() {
        val result = validator.validateIntentLine(
            "Lost someone close, need to talk through the pain"
        )
        assertTrue(result.isValid)
    }
    
    @Test
    fun `intent line too short fails validation`() {
        val result = validator.validateIntentLine("Hi")
        assertFalse(result.isValid)
        assertEquals("Message must be at least 10 characters", result.errorMessage)
    }
    
    @Test
    fun `intent line too long fails validation`() {
        val longText = "a".repeat(150)
        val result = validator.validateIntentLine(longText)
        assertFalse(result.isValid)
        assertEquals("Message must be 120 characters or less", result.errorMessage)
    }
    
    @Test
    fun `intent line with phone number fails validation`() {
        val result = validator.validateIntentLine(
            "Call me at 0712345678 to talk"
        )
        assertFalse(result.isValid)
        assertEquals("Please remove personal information", result.errorMessage)
    }
    
    @Test
    fun `intent line with email fails validation`() {
        val result = validator.validateIntentLine(
            "Email me at test@example.com"
        )
        assertFalse(result.isValid)
    }
}
```

### ViewModel Tests

```kotlin
// Example: HomeViewModelTest.kt
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()
    
    private lateinit var viewModel: HomeViewModel
    private lateinit var fakeUserRepository: FakeUserRepository
    private lateinit var fakeAvailabilityRepository: FakeAvailabilityRepository
    private lateinit var fakeNetworkMonitor: FakeNetworkMonitor
    
    @Before
    fun setup() {
        fakeUserRepository = FakeUserRepository()
        fakeAvailabilityRepository = FakeAvailabilityRepository()
        fakeNetworkMonitor = FakeNetworkMonitor()
        
        viewModel = HomeViewModel(
            userRepository = fakeUserRepository,
            availabilityRepository = fakeAvailabilityRepository,
            networkMonitor = fakeNetworkMonitor
        )
    }
    
    @Test
    fun `initial state is correct`() = runTest {
        val state = viewModel.uiState.first()
        
        assertEquals(Role.SHARER, state.currentRole)
        assertFalse(state.isAvailable)
        assertTrue(state.canStartCall)
    }
    
    @Test
    fun `switching to listener role updates state`() = runTest {
        viewModel.switchRole(Role.LISTENER)
        
        val state = viewModel.uiState.first()
        assertEquals(Role.LISTENER, state.currentRole)
    }
    
    @Test
    fun `toggling availability updates repository`() = runTest {
        viewModel.switchRole(Role.LISTENER)
        viewModel.toggleAvailability(true)
        
        val availability = fakeAvailabilityRepository.currentAvailability.first()
        assertEquals(AvailabilityStatus.AVAILABLE, availability.status)
    }
    
    @Test
    fun `offline state disables call button`() = runTest {
        fakeNetworkMonitor.setNetworkState(NetworkState.OFFLINE)
        
        val state = viewModel.uiState.first()
        assertFalse(state.canStartCall)
        assertTrue(state.showOfflineBanner)
    }
}

// MainDispatcherRule for coroutine testing
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description?) {
        Dispatchers.setMain(dispatcher)
    }
    override fun finished(description: Description?) {
        Dispatchers.resetMain()
    }
}
```

### Repository Tests

```kotlin
// Example: FeedbackRepositoryTest.kt
@OptIn(ExperimentalCoroutinesApi::class)
class FeedbackRepositoryTest {
    
    private lateinit var repository: FeedbackRepository
    private lateinit var fakeFirestore: FakeFirestore
    private lateinit var fakeLocalDb: FakeLocalDatabase
    private lateinit var fakeNetworkMonitor: FakeNetworkMonitor
    
    @Before
    fun setup() {
        fakeFirestore = FakeFirestore()
        fakeLocalDb = FakeLocalDatabase()
        fakeNetworkMonitor = FakeNetworkMonitor(NetworkState.ONLINE)
        
        repository = FeedbackRepository(
            firestore = fakeFirestore,
            localDatabase = fakeLocalDb,
            networkMonitor = fakeNetworkMonitor
        )
    }
    
    @Test
    fun `feedback submitted online goes to Firestore`() = runTest {
        val feedback = Feedback(
            sessionId = "session-123",
            userId = "user-456",
            sentiment = "helped",
            thanked = true,
            recognized = false
        )
        
        val result = repository.submitFeedback(feedback)
        
        assertTrue(result.isSuccess)
        assertEquals(1, fakeFirestore.feedbackCollection.size)
        assertEquals(0, fakeLocalDb.pendingFeedback.size)
    }
    
    @Test
    fun `feedback submitted offline is queued locally`() = runTest {
        fakeNetworkMonitor.setNetworkState(NetworkState.OFFLINE)
        
        val feedback = Feedback(
            sessionId = "session-123",
            userId = "user-456",
            sentiment = "helped",
            thanked = true,
            recognized = false
        )
        
        val result = repository.submitFeedback(feedback)
        
        assertTrue(result.isSuccess)
        assertEquals(0, fakeFirestore.feedbackCollection.size)
        assertEquals(1, fakeLocalDb.pendingFeedback.size)
    }
    
    @Test
    fun `pending feedback syncs when online`() = runTest {
        // Add pending feedback
        fakeLocalDb.addPendingFeedback(PendingFeedback(
            sessionId = "session-123",
            sentiment = "helped",
            thanked = true,
            recognized = false,
            createdAt = System.currentTimeMillis()
        ))
        
        // Trigger sync
        repository.syncPendingFeedback()
        
        // Verify synced
        assertEquals(1, fakeFirestore.feedbackCollection.size)
        assertEquals(0, fakeLocalDb.pendingFeedback.size)
    }
}
```

---

## 2. Integration Tests

### Firestore Integration

```kotlin
// Example: FirestoreIntegrationTest.kt
@RunWith(AndroidJUnit4::class)
class FirestoreIntegrationTest {
    
    private lateinit var firestore: FirebaseFirestore
    
    @Before
    fun setup() {
        // Use Firebase Emulator
        firestore = FirebaseFirestore.getInstance()
        firestore.useEmulator("10.0.2.2", 8080)
    }
    
    @After
    fun teardown() {
        // Clear emulator data
        runBlocking {
            // Clear test collections
        }
    }
    
    @Test
    fun createAndReadUser() = runBlocking {
        val userId = "test-user-${UUID.randomUUID()}"
        
        // Create user
        val user = hashMapOf(
            "id" to userId,
            "createdAt" to FieldValue.serverTimestamp(),
            "status" to "active"
        )
        firestore.collection("users").document(userId).set(user).await()
        
        // Read user
        val doc = firestore.collection("users").document(userId).get().await()
        
        assertTrue(doc.exists())
        assertEquals("active", doc.getString("status"))
    }
    
    @Test
    fun matchingQueryReturnsCorrectListeners() = runBlocking {
        // Setup: Create listeners with different settings
        createListener("listener-1", topics = listOf("grief", "venting"), toneMax = "heavy")
        createListener("listener-2", topics = listOf("advice"), toneMax = "light")
        createListener("listener-3", topics = listOf("grief"), toneMax = "very_heavy")
        
        // Query for grief + heavy
        val results = firestore.collection("availability")
            .whereEqualTo("status", "available")
            .whereArrayContains("topicsAllowed", "grief")
            .get()
            .await()
        
        // Should match listener-1 and listener-3
        assertEquals(2, results.size())
    }
    
    private suspend fun createListener(
        id: String,
        topics: List<String>,
        toneMax: String
    ) {
        val availability = hashMapOf(
            "userId" to id,
            "status" to "available",
            "topicsAllowed" to topics,
            "toneMax" to toneMax
        )
        firestore.collection("availability").document(id).set(availability).await()
    }
}
```

### WebRTC Integration

```kotlin
// Example: WebRTCIntegrationTest.kt
@RunWith(AndroidJUnit4::class)
class WebRTCIntegrationTest {
    
    private lateinit var context: Context
    private lateinit var webRTCManager: WebRTCManager
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        webRTCManager = WebRTCManager(context)
    }
    
    @After
    fun teardown() {
        webRTCManager.release()
    }
    
    @Test
    fun peerConnectionCreatesSuccessfully() {
        webRTCManager.initializePeerConnection()
        
        val state = webRTCManager.connectionState.value
        assertNotEquals(CallConnectionState.FAILED, state)
    }
    
    @Test
    fun localAudioTrackIsCreated() {
        webRTCManager.initializePeerConnection()
        webRTCManager.startLocalAudio()
        
        assertTrue(webRTCManager.hasLocalAudioTrack())
    }
    
    @Test
    fun voiceMorphingProcessesAudio() {
        val processor = VoiceMorphingProcessor(MorphingLevel.MEDIUM)
        val testAudio = ShortArray(960) { it.toShort() }
        
        val morphed = processor.processSamples(testAudio)
        
        assertNotNull(morphed)
        // Morphed audio should be different from input
        assertFalse(morphed.contentEquals(testAudio))
    }
}
```

---

## 3. UI Tests (Compose)

### Screen Tests

```kotlin
// Example: HomeScreenTest.kt
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Test
    fun homeScreen_showsSharerRole_byDefault() {
        composeTestRule.setContent {
            BackroomTheme {
                HomeScreen(
                    uiState = HomeUiState(currentRole = Role.SHARER),
                    onRoleSwitch = {},
                    onStartCall = {},
                    onToggleAvailability = {}
                )
            }
        }
        
        composeTestRule.onNodeWithText("Start a Call").assertIsDisplayed()
        composeTestRule.onNodeWithText("SHARER").assertIsDisplayed()
    }
    
    @Test
    fun homeScreen_showsListenerToggle_whenListenerRole() {
        composeTestRule.setContent {
            BackroomTheme {
                HomeScreen(
                    uiState = HomeUiState(currentRole = Role.LISTENER),
                    onRoleSwitch = {},
                    onStartCall = {},
                    onToggleAvailability = {}
                )
            }
        }
        
        composeTestRule.onNodeWithText("Available to Listen").assertIsDisplayed()
        composeTestRule.onNode(hasToggleableState()).assertIsDisplayed()
    }
    
    @Test
    fun homeScreen_showsOfflineBanner_whenOffline() {
        composeTestRule.setContent {
            BackroomTheme {
                HomeScreen(
                    uiState = HomeUiState(
                        currentRole = Role.SHARER,
                        showOfflineBanner = true,
                        canStartCall = false
                    ),
                    onRoleSwitch = {},
                    onStartCall = {},
                    onToggleAvailability = {}
                )
            }
        }
        
        composeTestRule.onNodeWithText("You're offline").assertIsDisplayed()
        composeTestRule.onNodeWithText("Start a Call").assertIsNotEnabled()
    }
    
    @Test
    fun homeScreen_roleSwitchWorks() {
        var selectedRole = Role.SHARER
        
        composeTestRule.setContent {
            BackroomTheme {
                HomeScreen(
                    uiState = HomeUiState(currentRole = selectedRole),
                    onRoleSwitch = { selectedRole = it },
                    onStartCall = {},
                    onToggleAvailability = {}
                )
            }
        }
        
        composeTestRule.onNodeWithText("Listener").performClick()
        
        assertEquals(Role.LISTENER, selectedRole)
    }
}
```

### Preview Flow Tests

```kotlin
// Example: SharerPreviewFlowTest.kt
@RunWith(AndroidJUnit4::class)
class SharerPreviewFlowTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Test
    fun previewFlow_completesSuccessfully() {
        var submitted = false
        
        composeTestRule.setContent {
            BackroomTheme {
                SharerPreviewFlow(
                    onSubmit = { submitted = true },
                    onCancel = {}
                )
            }
        }
        
        // Step 1: Select topic
        composeTestRule.onNodeWithText("Grief").performClick()
        composeTestRule.onNodeWithText("Continue").performClick()
        
        // Step 2: Select tone
        composeTestRule.onNodeWithText("Heavy").performClick()
        composeTestRule.onNodeWithText("Continue").performClick()
        
        // Step 3: Write intent
        composeTestRule.onNode(hasSetTextAction())
            .performTextInput("Lost someone close, need to talk through the pain")
        composeTestRule.onNodeWithText("Continue").performClick()
        
        // Step 4: Select duration
        composeTestRule.onNodeWithText("10 minutes").performClick()
        composeTestRule.onNodeWithText("Review & Submit").performClick()
        
        // Step 5: Review and submit
        composeTestRule.onNodeWithText("Find a Listener").performClick()
        
        assertTrue(submitted)
    }
    
    @Test
    fun previewFlow_validatesIntentLine() {
        composeTestRule.setContent {
            BackroomTheme {
                SharerPreviewFlow(onSubmit = {}, onCancel = {})
            }
        }
        
        // Navigate to intent step
        composeTestRule.onNodeWithText("Grief").performClick()
        composeTestRule.onNodeWithText("Continue").performClick()
        composeTestRule.onNodeWithText("Heavy").performClick()
        composeTestRule.onNodeWithText("Continue").performClick()
        
        // Enter too-short intent
        composeTestRule.onNode(hasSetTextAction()).performTextInput("Hi")
        composeTestRule.onNodeWithText("Continue").performClick()
        
        // Should show error
        composeTestRule.onNodeWithText("Message must be at least 10 characters")
            .assertIsDisplayed()
    }
}
```

---

## 4. End-to-End Tests

### Critical User Journeys

```kotlin
// Example: FullCallFlowE2ETest.kt
@RunWith(AndroidJUnit4::class)
@LargeTest
class FullCallFlowE2ETest {
    
    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)
    
    @Before
    fun setup() {
        // Use Firebase Emulator
        // Clear test data
        // Setup test users
    }
    
    @Test
    fun completeCallFlow_sharerToListener() {
        // This test simulates the full flow
        // In practice, you'd need two emulators or devices
        
        // 1. Sharer creates preview
        onView(withText("Start a Call")).perform(click())
        onView(withText("Grief")).perform(click())
        onView(withText("Continue")).perform(click())
        onView(withText("Heavy")).perform(click())
        onView(withText("Continue")).perform(click())
        onView(withHint("What do you want to say?"))
            .perform(typeText("Lost someone close"))
        onView(withText("Continue")).perform(click())
        onView(withText("10 minutes")).perform(click())
        onView(withText("Find a Listener")).perform(click())
        
        // 2. Wait for matching (would need mock or second device)
        Thread.sleep(2000)
        
        // 3. Verify waiting screen
        onView(withText("Finding...")).check(matches(isDisplayed()))
    }
}
```

### Automated Test Devices

For proper E2E testing, we need:
1. **Firebase Test Lab** - Cloud-based device testing
2. **Two emulators** - One as sharer, one as listener
3. **Mock backend** - For predictable behavior

---

## 5. User Acceptance Testing (UAT)

### Test Scenarios

| ID | Scenario | Steps | Expected Result |
|----|----------|-------|-----------------|
| UAT-001 | New user onboarding | Install app, go through onboarding | User reaches home screen |
| UAT-002 | Complete a call as sharer | Create preview, wait, complete call | Feedback submitted |
| UAT-003 | Receive and accept call as listener | Enable availability, get preview, accept | Call connects |
| UAT-004 | Skip incoming preview | Get preview, tap skip | Returns to waiting |
| UAT-005 | Emergency exit during call | During call, tap emergency exit | Call ends, report option shown |
| UAT-006 | Submit abuse report | After call, submit report | Confirmation shown |
| UAT-007 | Change listener boundaries | Go to settings, modify boundaries | Changes saved |
| UAT-008 | Upgrade to Plus | Navigate to Plus, complete purchase | Features unlocked |

### UAT Checklist Template

```markdown
## UAT Session: [Date]
### Tester: [Name]
### Device: [Model, Android Version]

#### Pre-Test Setup
- [ ] App installed fresh
- [ ] Network connectivity confirmed
- [ ] Screen recording enabled

#### Test Execution

**UAT-001: New User Onboarding**
- [ ] App launches successfully
- [ ] Welcome screen displayed
- [ ] Safety overview shown
- [ ] Mic permission requested
- [ ] Notification permission requested
- [ ] Location permission offered (optional)
- [ ] Language selection works
- [ ] Home screen reached

Result: PASS / FAIL
Notes: _______________

**UAT-002: Complete Call as Sharer**
- [ ] "Start a Call" button works
- [ ] Topic selection works
- [ ] Tone selection works
- [ ] Intent input works
- [ ] Character limit enforced
- [ ] Duration selection works
- [ ] Review screen shows correct data
- [ ] Matching starts
- [ ] (If matched) Call connects
- [ ] Timer displays correctly
- [ ] Audio works both ways
- [ ] Call ends properly
- [ ] Feedback screen shown

Result: PASS / FAIL
Notes: _______________

...
```

---

## 6. Beta Rollout Plan

### Phase 1: Internal Testing (Week 1)

**Participants:** Team members only (5-10 people)

**Goals:**
- Find critical bugs
- Validate core flows work
- Test on various devices

**Devices:**
- Low-end: Samsung A10, Tecno Spark
- Mid-range: Samsung A52, Pixel 5
- High-end: Samsung S23, Pixel 8

### Phase 2: Friends & Family (Week 2-3)

**Participants:** 20-30 trusted people

**Goals:**
- Get honest feedback
- Test real conversations
- Identify UX issues

**Recruitment:**
- Personal invitations
- NDA or trust agreement
- Feedback form required

### Phase 3: Closed Beta - University (Week 4-6)

**Participants:** 50-100 students from one university

**Goals:**
- Test with real user behavior
- Validate matching algorithm
- Test safety systems

**University Selection Criteria:**
- Tech-forward student body
- Mental health awareness programs
- Supportive administration

**Recruitment:**
- Partner with student wellness office
- Posters and social media
- Incentive: Free Plus for 3 months

### Phase 4: Open Beta (Week 7-10)

**Participants:** 500-1000 users

**Goals:**
- Scale testing
- Performance under load
- Monetization validation

**Distribution:**
- Google Play Beta track
- Limited geographic region (Nairobi first)
- Waitlist if demand exceeds capacity

---

## 7. Performance Testing

### Metrics to Measure

| Metric | Target | Tool |
|--------|--------|------|
| App cold start | < 2s | Firebase Performance |
| Screen transition | < 300ms | Firebase Performance |
| Call connect time | < 5s | Custom logging |
| Audio latency | < 200ms | Manual testing |
| Memory usage | < 150MB | Android Profiler |
| Battery drain | < 2% per 10min call | Battery Historian |

### Load Testing

```kotlin
// Simulated load test for matching engine
@Test
fun matchingEngine_handlesHighLoad() = runTest {
    val matchingEngine = MatchingEngine(fakeFirestore)
    
    // Simulate 100 concurrent requests
    val jobs = (1..100).map { i ->
        async {
            val request = CallRequest(
                id = "request-$i",
                topic = listOf("grief", "venting", "advice").random(),
                tone = listOf("light", "heavy").random(),
                duration = 10
            )
            matchingEngine.findListener(request)
        }
    }
    
    val results = jobs.awaitAll()
    
    // All should complete
    assertEquals(100, results.size)
    
    // Check timing
    // Average should be < 500ms
}
```

---

## 8. Security Testing

### Checklist

- [ ] Authentication tokens not logged
- [ ] Sensitive data encrypted at rest
- [ ] Network traffic uses TLS
- [ ] No PII in analytics
- [ ] Firestore rules prevent unauthorized access
- [ ] Rate limiting prevents abuse
- [ ] Input validation on all user inputs
- [ ] No hardcoded secrets in code

### Penetration Testing (Pre-Launch)

Consider hiring security firm for:
- API security audit
- Mobile app security review
- Firestore rules review

---

## 9. Accessibility Testing

### Automated

```kotlin
// Using Accessibility Scanner
@Test
fun homeScreen_meetsAccessibilityGuidelines() {
    composeTestRule.setContent {
        BackroomTheme {
            HomeScreen(...)
        }
    }
    
    // Check for content descriptions
    composeTestRule.onAllNodes(hasClickAction())
        .assertAll(hasContentDescription())
    
    // Check touch target sizes
    composeTestRule.onAllNodes(hasClickAction())
        .assertAll(hasMinTouchTargetSize(48.dp, 48.dp))
}
```

### Manual

- [ ] TalkBack navigation works
- [ ] All buttons have labels
- [ ] Color contrast meets WCAG AA
- [ ] Focus order is logical
- [ ] No audio-only information

---

## 10. Test Coverage Goals

| Layer | Target Coverage |
|-------|-----------------|
| ViewModels | 80% |
| Repositories | 80% |
| Utilities | 90% |
| UI (Compose) | 60% |
| Integration | Key flows |
| E2E | Critical paths |

### Coverage Reporting

```groovy
// build.gradle.kts
android {
    buildTypes {
        debug {
            enableUnitTestCoverage = true
            enableAndroidTestCoverage = true
        }
    }
}

// Run: ./gradlew createDebugCoverageReport
```

---

## Next Steps

1. Set up test infrastructure (Firebase Emulator)
2. Write unit tests for existing ViewModels
3. Create fake implementations for dependencies
4. Set up UI testing with Compose
5. Define E2E test scenarios
6. Plan beta recruitment
7. Set up crash reporting (Crashlytics)
8. Configure performance monitoring

