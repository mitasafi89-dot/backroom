package com.example.backroom.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Simple preferences manager for app state persistence.
 * Uses SharedPreferences for lightweight storage.
 */
class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    // ============================================
    // ONBOARDING
    // ============================================

    /**
     * Check if user has completed onboarding
     */
    var isOnboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, value).apply()

    // ============================================
    // LISTENER BOUNDARIES
    // ============================================

    /**
     * Listener mode enabled - default is TRUE (everyone can be a listener)
     * User can toggle this on/off
     */
    var isListenerModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_LISTENER_MODE_ENABLED, true) // Default TRUE
        set(value) {
            prefs.edit().putBoolean(KEY_LISTENER_MODE_ENABLED, value).apply()
            _listenerModeFlow.value = value
        }

    private val _listenerModeFlow = MutableStateFlow(prefs.getBoolean(KEY_LISTENER_MODE_ENABLED, true))
    val listenerModeFlow: StateFlow<Boolean> = _listenerModeFlow.asStateFlow()

    /**
     * Listener boundaries - accepted topics (stored as comma-separated string)
     */
    var acceptedTopics: Set<String>
        get() = prefs.getStringSet(KEY_ACCEPTED_TOPICS, DEFAULT_TOPICS) ?: DEFAULT_TOPICS
        set(value) = prefs.edit().putStringSet(KEY_ACCEPTED_TOPICS, value).apply()

    /**
     * Listener boundaries - max intensity level
     */
    var maxIntensity: String
        get() = prefs.getString(KEY_MAX_INTENSITY, DEFAULT_INTENSITY) ?: DEFAULT_INTENSITY
        set(value) = prefs.edit().putString(KEY_MAX_INTENSITY, value).apply()

    /**
     * Listener boundaries - max call duration in minutes
     */
    var maxDurationMinutes: Int
        get() = prefs.getInt(KEY_MAX_DURATION, DEFAULT_DURATION)
        set(value) = prefs.edit().putInt(KEY_MAX_DURATION, value).apply()

    // ============================================
    // PRIVACY & SAFETY SETTINGS
    // ============================================

    /**
     * Voice anonymization level (always on, but can have different levels)
     * Options: "basic", "enhanced"
     */
    var voiceAnonymizationLevel: String
        get() = prefs.getString(KEY_VOICE_ANONYMIZATION, "basic") ?: "basic"
        set(value) = prefs.edit().putString(KEY_VOICE_ANONYMIZATION, value).apply()


    /**
     * List of blocked user IDs
     */
    var blockedUsers: Set<String>
        get() = prefs.getStringSet(KEY_BLOCKED_USERS, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_BLOCKED_USERS, value).apply()

    fun blockUser(userId: String) {
        blockedUsers = blockedUsers + userId
    }

    fun unblockUser(userId: String) {
        blockedUsers = blockedUsers - userId
    }

    fun isUserBlocked(userId: String): Boolean = blockedUsers.contains(userId)

    // ============================================
    // NOTIFICATION SETTINGS
    // ============================================

    /**
     * Enable incoming preview notifications (for listeners)
     */
    var incomingPreviewsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIF_PREVIEWS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_NOTIF_PREVIEWS, value).apply()
            _incomingPreviewsFlow.value = value
        }

    private val _incomingPreviewsFlow = MutableStateFlow(prefs.getBoolean(KEY_NOTIF_PREVIEWS, true))
    val incomingPreviewsFlow: StateFlow<Boolean> = _incomingPreviewsFlow.asStateFlow()

    /**
     * Enable tips and reminders notifications
     */
    var tipsRemindersEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIF_TIPS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_NOTIF_TIPS, value).apply()
            _tipsRemindersFlow.value = value
        }

    private val _tipsRemindersFlow = MutableStateFlow(prefs.getBoolean(KEY_NOTIF_TIPS, true))
    val tipsRemindersFlow: StateFlow<Boolean> = _tipsRemindersFlow.asStateFlow()

    // ============================================
    // SUBSCRIPTION
    // ============================================

    /**
     * Subscription tier: "free", "plus_monthly", "plus_yearly"
     */
    var subscriptionTier: String
        get() = prefs.getString(KEY_SUBSCRIPTION_TIER, "free") ?: "free"
        set(value) = prefs.edit().putString(KEY_SUBSCRIPTION_TIER, value).apply()

    /**
     * Subscription expiry timestamp (0 if no subscription)
     */
    var subscriptionExpiryTimestamp: Long
        get() = prefs.getLong(KEY_SUBSCRIPTION_EXPIRY, 0L)
        set(value) = prefs.edit().putLong(KEY_SUBSCRIPTION_EXPIRY, value).apply()

    fun isSubscriptionActive(): Boolean {
        return subscriptionTier != "free" && subscriptionExpiryTimestamp > System.currentTimeMillis()
    }

    // ============================================
    // USER DATA
    // ============================================

    /**
     * Anonymous user ID
     */
    var anonymousUserId: String?
        get() = prefs.getString(KEY_USER_ID, null)
        set(value) = prefs.edit().putString(KEY_USER_ID, value).apply()

    /**
     * Total calls made
     */
    var totalCallsMade: Int
        get() = prefs.getInt(KEY_TOTAL_CALLS, 0)
        set(value) = prefs.edit().putInt(KEY_TOTAL_CALLS, value).apply()

    fun incrementCallCount() {
        totalCallsMade = totalCallsMade + 1
    }

    /**
     * FCM token for push notifications
     */
    var fcmToken: String?
        get() = prefs.getString(KEY_FCM_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_FCM_TOKEN, value).apply()

    // ============================================
    // UTILITIES
    // ============================================

    /**
     * Clear all preferences (useful for testing or account deletion)
     */
    fun clear() {
        prefs.edit().clear().apply()
    }

    /**
     * Export user data as a map (for data export feature)
     */
    fun exportUserData(): Map<String, Any?> {
        return mapOf(
            "anonymousUserId" to anonymousUserId,
            "totalCallsMade" to totalCallsMade,
            "acceptedTopics" to acceptedTopics.toList(),
            "maxIntensity" to maxIntensity,
            "maxDurationMinutes" to maxDurationMinutes,
            "blockedUsersCount" to blockedUsers.size,
            "subscriptionTier" to subscriptionTier,
            "incomingPreviewsEnabled" to incomingPreviewsEnabled,
            "tipsRemindersEnabled" to tipsRemindersEnabled
        )
    }

    companion object {
        private const val PREFS_NAME = "backroom_prefs"

        // Keys
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_LISTENER_MODE_ENABLED = "listener_mode_enabled"
        private const val KEY_ACCEPTED_TOPICS = "accepted_topics"
        private const val KEY_MAX_INTENSITY = "max_intensity"
        private const val KEY_MAX_DURATION = "max_duration"
        private const val KEY_VOICE_ANONYMIZATION = "voice_anonymization"
        private const val KEY_BLOCKED_USERS = "blocked_users"
        private const val KEY_NOTIF_PREVIEWS = "notif_incoming_previews"
        private const val KEY_NOTIF_TIPS = "notif_tips_reminders"
        private const val KEY_SUBSCRIPTION_TIER = "subscription_tier"
        private const val KEY_SUBSCRIPTION_EXPIRY = "subscription_expiry"
        private const val KEY_USER_ID = "anonymous_user_id"
        private const val KEY_TOTAL_CALLS = "total_calls"
        private const val KEY_FCM_TOKEN = "fcm_token"

        // Defaults
        private val DEFAULT_TOPICS = setOf("CONFESSION", "LETTING_OUT", "ADVICE", "GRIEF", "JUST_TALKING")
        private const val DEFAULT_INTENSITY = "UP_TO_HEAVY"
        private const val DEFAULT_DURATION = 15
    }
}

