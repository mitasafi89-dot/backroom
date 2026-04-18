package com.example.backroom.ui.screens.help

/**
 * Crisis severity levels
 */
enum class CrisisLevel {
    NONE,       // No crisis indicators detected
    MODERATE,   // Signs of distress but not immediate danger
    HIGH,       // Self-harm indicators
    CRITICAL    // Suicide indicators - highest priority
}

/**
 * Crisis categories
 */
enum class CrisisType {
    SUICIDE,
    SELF_HARM,
    DISTRESS,
    VIOLENCE,
    ABUSE
}

/**
 * Result of crisis analysis
 */
data class CrisisAnalysis(
    val level: CrisisLevel,
    val type: CrisisType?,
    val shouldShowResources: Boolean,
    val shouldShowModal: Boolean,
    val suggestedResourceIds: List<String>,
    val matchedKeywords: List<String> // For internal logging only, never shown to user
)

/**
 * Crisis Detector
 *
 * Analyzes user text for crisis keywords to proactively offer support.
 * This is a safety feature - it never blocks users, only offers resources.
 *
 * IMPORTANT: This is not a replacement for professional crisis detection.
 * It's a best-effort helper to surface resources when they might be needed.
 */
object CrisisDetector {

    // ============================================
    // KEYWORD LISTS
    // ============================================

    /**
     * Critical - Suicide related keywords
     * Trigger: Show modal immediately
     */
    private val suicideKeywords = listOf(
        // English
        "kill myself",
        "end my life",
        "want to die",
        "suicide",
        "suicidal",
        "no reason to live",
        "better off dead",
        "end it all",
        "don't want to be here",
        "don't want to exist",
        "wish i was dead",
        "wish i wasn't alive",
        "take my own life",
        "rather be dead",

        // Swahili
        "kujiua",
        "nataka kufa",
        "nimechoka na maisha",
        "maisha hayana maana"
    )

    /**
     * High - Self-harm related keywords
     * Trigger: Show resources banner
     */
    private val selfHarmKeywords = listOf(
        // English
        "hurt myself",
        "cutting myself",
        "self harm",
        "self-harm",
        "harming myself",
        "punish myself",
        "burn myself",
        "injure myself",

        // Swahili
        "kujidhuru",
        "kujikata"
    )

    /**
     * Moderate - Severe distress keywords
     * Trigger: Subtle resources offer post-call
     */
    private val severeDistressKeywords = listOf(
        // English
        "can't go on",
        "cannot go on",
        "no hope",
        "hopeless",
        "give up",
        "giving up",
        "worthless",
        "no point",
        "burden to everyone",
        "everyone would be better",
        "can't take it anymore",
        "can't take this anymore",
        "at my breaking point",
        "reached my limit",

        // Swahili
        "sina tumaini",
        "nimechoka",
        "siwezi kuendelea"
    )

    /**
     * Violence/Abuse indicators
     */
    private val violenceKeywords = listOf(
        // English
        "being abused",
        "hitting me",
        "beats me",
        "hurt by someone",
        "domestic violence",
        "being threatened",
        "afraid of my partner",
        "afraid of my husband",
        "afraid of my wife",
        "controlling me",
        "forced me",

        // Swahili
        "ananipiga",
        "ukatili wa nyumbani"
    )

    // ============================================
    // ANALYSIS FUNCTION
    // ============================================

    /**
     * Analyze text for crisis indicators
     *
     * @param text The user's input text (preview message)
     * @return CrisisAnalysis with detected level and recommendations
     */
    fun analyze(text: String): CrisisAnalysis {
        val lowerText = text.lowercase().trim()

        // Check for empty or very short text
        if (lowerText.length < 3) {
            return CrisisAnalysis(
                level = CrisisLevel.NONE,
                type = null,
                shouldShowResources = false,
                shouldShowModal = false,
                suggestedResourceIds = emptyList(),
                matchedKeywords = emptyList()
            )
        }

        // Check suicide keywords first (highest priority)
        val suicideMatches = findMatches(lowerText, suicideKeywords)
        if (suicideMatches.isNotEmpty()) {
            return CrisisAnalysis(
                level = CrisisLevel.CRITICAL,
                type = CrisisType.SUICIDE,
                shouldShowResources = true,
                shouldShowModal = true,
                suggestedResourceIds = listOf("befrienders_kenya", "kenya_red_cross"),
                matchedKeywords = suicideMatches
            )
        }

        // Check self-harm keywords
        val selfHarmMatches = findMatches(lowerText, selfHarmKeywords)
        if (selfHarmMatches.isNotEmpty()) {
            return CrisisAnalysis(
                level = CrisisLevel.HIGH,
                type = CrisisType.SELF_HARM,
                shouldShowResources = true,
                shouldShowModal = true,
                suggestedResourceIds = listOf("befrienders_kenya", "mhfa_kenya"),
                matchedKeywords = selfHarmMatches
            )
        }

        // Check violence/abuse keywords
        val violenceMatches = findMatches(lowerText, violenceKeywords)
        if (violenceMatches.isNotEmpty()) {
            return CrisisAnalysis(
                level = CrisisLevel.HIGH,
                type = CrisisType.VIOLENCE,
                shouldShowResources = true,
                shouldShowModal = false, // Don't show modal, just resources
                suggestedResourceIds = listOf("fida_kenya", "gvrc", "kenya_red_cross"),
                matchedKeywords = violenceMatches
            )
        }

        // Check severe distress keywords
        val distressMatches = findMatches(lowerText, severeDistressKeywords)
        if (distressMatches.isNotEmpty()) {
            return CrisisAnalysis(
                level = CrisisLevel.MODERATE,
                type = CrisisType.DISTRESS,
                shouldShowResources = false, // Show post-call if they choose "Uncomfortable"
                shouldShowModal = false,
                suggestedResourceIds = listOf("mhfa_kenya"),
                matchedKeywords = distressMatches
            )
        }

        // No crisis indicators found
        return CrisisAnalysis(
            level = CrisisLevel.NONE,
            type = null,
            shouldShowResources = false,
            shouldShowModal = false,
            suggestedResourceIds = emptyList(),
            matchedKeywords = emptyList()
        )
    }

    /**
     * Find which keywords match in the text
     */
    private fun findMatches(text: String, keywords: List<String>): List<String> {
        return keywords.filter { keyword ->
            text.contains(keyword)
        }
    }

    /**
     * Quick check if any crisis indicators are present
     * Use this for lightweight checks before full analysis
     */
    fun hasAnyCrisisIndicators(text: String): Boolean {
        val lowerText = text.lowercase()
        return suicideKeywords.any { lowerText.contains(it) } ||
               selfHarmKeywords.any { lowerText.contains(it) } ||
               violenceKeywords.any { lowerText.contains(it) }
    }

    /**
     * Check if text indicates the user might need extra support
     * (lower threshold than crisis, used for subtle support offerings)
     */
    fun mightNeedSupport(text: String): Boolean {
        val lowerText = text.lowercase()
        return severeDistressKeywords.any { lowerText.contains(it) }
    }
}

/**
 * Extension function to check a preview message
 */
fun String.checkForCrisisIndicators(): CrisisAnalysis {
    return CrisisDetector.analyze(this)
}

