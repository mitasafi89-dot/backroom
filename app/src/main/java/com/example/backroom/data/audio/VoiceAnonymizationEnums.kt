package com.example.backroom.data.audio

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 * VOICE ANONYMIZATION ENUMS
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *
 * These enums define the configuration options for voice anonymization.
 * The actual processing is done by ProductionVoiceAnonymizer.
 */

/**
 * Voice Anonymization Levels
 *
 * Single source of truth for anonymization presets.
 *
 * @property pitchFactor The pitch multiplier (< 1.0 = deeper voice)
 * @property intLevel    The 0-100 integer level consumed by [ProductionVoiceAnonymizer]
 * @property description Human-readable description
 */
enum class AnonymizationLevel(
    val pitchFactor: Float,
    val intLevel: Int,
    val description: String
) {
    NONE(1.0f, 0, "No anonymization"),
    LIGHT(0.90f, 25, "Light - Subtle voice change"),
    MEDIUM(0.75f, 50, "Medium - Noticeable but natural"),
    STRONG(0.60f, 75, "Strong - Significant voice change"),
    MAXIMUM(0.45f, 100, "Maximum - Unrecognizable voice")
}

/**
 * Direction of voice modification
 *
 * Controls whether the voice is shifted deeper (more masculine)
 * or higher (more feminine).
 */
enum class AnonymizationDirection {
    DEEPER,  // Lower pitch (more masculine)
    HIGHER   // Higher pitch (more feminine)
}

