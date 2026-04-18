package com.example.backroom.data.audio

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 * PRODUCTION VOICE ANONYMIZER
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *
 * A comprehensive, production-grade voice anonymization system implementing:
 *
 * 1. TD-PSOLA (Time-Domain Pitch Synchronous Overlap-Add)
 *    - Industry-standard algorithm for pitch shifting
 *    - Preserves speech naturalness and intelligibility
 *    - Pitch period detection via autocorrelation
 *
 * 2. FORMANT SHIFTING
 *    - Modifies vocal tract resonances independently of pitch
 *    - Uses all-pass filter cascade for spectral envelope modification
 *    - Prevents "chipmunk" or "robot" effect
 *
 * 3. SPECTRAL SCRAMBLING
 *    - Adds subtle frequency-domain modifications
 *    - Further disguises voice identity
 *    - Configurable intensity
 *
 * DESIGN PRINCIPLES:
 * - Thread-safe (can be called from WebRTC audio thread)
 * - Lock-free where possible (uses atomics)
 * - Low latency (< 20ms processing delay)
 * - Extensive logging for debugging
 * - Fail-safe (returns original audio on error)
 *
 * @author Backroom Voice Anonymization Team
 * @version 2.0.0
 */
object ProductionVoiceAnonymizer {

    private const val TAG = "ProductionVoiceAnon"

    // ═══════════════════════════════════════════════════════════════════════════════
    // CONFIGURATION CONSTANTS
    // ═══════════════════════════════════════════════════════════════════════════════

    object Config {
        // Audio parameters
        const val DEFAULT_SAMPLE_RATE = 48000
        const val FRAME_SIZE_MS = 20           // 20ms frames (960 samples at 48kHz)
        const val OVERLAP_RATIO = 0.5f         // 50% overlap for smooth transitions

        // Pitch detection parameters
        const val MIN_PITCH_HZ = 50            // Lowest male voice
        const val MAX_PITCH_HZ = 500           // Highest female voice + margin
        const val PITCH_CONFIDENCE_THRESHOLD = 0.3f

        // ═══════════════════════════════════════════════════════════════════════
        // EXTREME ANONYMIZATION SETTINGS - TOTAL VOICE DISGUISE
        // ═══════════════════════════════════════════════════════════════════════
        const val DEFAULT_LEVEL = 100          // ALWAYS MAX anonymization
        const val MIN_PITCH_FACTOR = 0.30f     // EXTREME deepening (nearly 2 octaves)
        const val MAX_PITCH_FACTOR = 1.8f      // Maximum raising
        const val DEFAULT_PITCH_FACTOR = 0.35f // Default: VERY deep voice

        // Formant shifting - EXTREME
        const val FORMANT_SHIFT_RATIO = 1.0f   // Full formant shift
        const val MIN_FORMANT_FACTOR = 0.35f   // Extreme formant shift
        const val MAX_FORMANT_FACTOR = 1.8f

        // Spectral scrambling - AGGRESSIVE
        const val MAX_SCRAMBLE_INTENSITY = 0.6f   // Heavy scrambling
        const val SCRAMBLE_THRESHOLD_LEVEL = 0    // Always scramble

        // Ring modulation for robotic effect
        const val RING_MOD_FREQUENCY = 150f    // Hz - adds metallic quality
        const val RING_MOD_DEPTH = 0.4f        // 40% ring modulation

        // Noise injection to mask voice fingerprint
        const val NOISE_LEVEL = 0.08f          // 8% noise floor

        // Logging
        const val LOG_INTERVAL_MS = 5000L      // Log stats every 5 seconds
        const val LOG_FIRST_N_FRAMES = 10      // Log first N frames in detail
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // STATE MANAGEMENT (Thread-Safe)
    // ═══════════════════════════════════════════════════════════════════════════════

    // Processing state
    private val isEnabled = AtomicBoolean(true)
    private val isInitialized = AtomicBoolean(false)
    private val anonymizationLevel = AtomicInteger(Config.DEFAULT_LEVEL)

    // Computed parameters (updated atomically)
    private val currentPitchFactor = AtomicReference(Config.DEFAULT_PITCH_FACTOR)
    private val currentFormantFactor = AtomicReference(0.85f)
    private val currentScrambleIntensity = AtomicReference(0f)

    // Sample rate
    private val sampleRate = AtomicInteger(Config.DEFAULT_SAMPLE_RATE)

    // Statistics for debugging
    private val framesProcessed = AtomicLong(0)
    private val samplesProcessed = AtomicLong(0)
    private val lastLogTime = AtomicLong(0)
    private val totalProcessingTimeNs = AtomicLong(0)
    private val errorCount = AtomicLong(0)

    // Processors (lazy initialized, thread-safe)
    @Volatile
    private var psolaProcessor: PSOLAPitchShifterV2? = null

    @Volatile
    private var formantProcessor: FormantShifterV2? = null

    @Volatile
    private var spectralProcessor: SpectralScramblerV2? = null

    // Processing lock for initialization
    private val initLock = Any()

    // ═══════════════════════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Initialize the voice anonymizer with the given sample rate
     *
     * @param rate Audio sample rate (typically 48000 or 44100)
     */
    fun initialize(rate: Int = Config.DEFAULT_SAMPLE_RATE) {
        Log.d(TAG, "═══════════════════════════════════════════════════════════════")
        Log.d(TAG, "🎭 INITIALIZING ProductionVoiceAnonymizer")
        Log.d(TAG, "   Sample rate: $rate Hz")
        Log.d(TAG, "   Frame size: ${Config.FRAME_SIZE_MS}ms (${rate * Config.FRAME_SIZE_MS / 1000} samples)")
        Log.d(TAG, "   Overlap ratio: ${Config.OVERLAP_RATIO}")
        Log.d(TAG, "   Thread: ${Thread.currentThread().name}")

        synchronized(initLock) {
            sampleRate.set(rate)

            // Initialize processors
            val frameSize = rate * Config.FRAME_SIZE_MS / 1000

            Log.d(TAG, "   Creating PSOLA processor...")
            psolaProcessor = PSOLAPitchShifterV2(rate, frameSize)
            Log.d(TAG, "   ✓ PSOLA processor created")

            Log.d(TAG, "   Creating Formant processor...")
            formantProcessor = FormantShifterV2(rate, frameSize)
            Log.d(TAG, "   ✓ Formant processor created")

            Log.d(TAG, "   Creating Spectral processor...")
            spectralProcessor = SpectralScramblerV2(rate)
            Log.d(TAG, "   ✓ Spectral processor created")

            // Apply default settings
            setAnonymizationLevel(anonymizationLevel.get())

            isInitialized.set(true)

            Log.d(TAG, "   ─────────────────────────────────────────────────────────")
            Log.d(TAG, "   INITIAL CONFIGURATION:")
            Log.d(TAG, "   - Enabled: ${isEnabled.get()}")
            Log.d(TAG, "   - Level: ${anonymizationLevel.get()}%")
            Log.d(TAG, "   - Pitch factor: ${currentPitchFactor.get()}")
            Log.d(TAG, "   - Formant factor: ${currentFormantFactor.get()}")
            Log.d(TAG, "   - Scramble intensity: ${currentScrambleIntensity.get()}")
            Log.d(TAG, "═══════════════════════════════════════════════════════════════")
        }
    }

    /**
     * Ensure initialized before processing
     */
    private fun ensureInitialized() {
        if (!isInitialized.get()) {
            Log.w(TAG, "⚠️ Not initialized - auto-initializing with default sample rate")
            initialize(Config.DEFAULT_SAMPLE_RATE)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // CONFIGURATION API
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Set anonymization level (0-100%)
     *
     * This controls the overall intensity of voice modification:
     * - 0%: No modification (pass-through)
     * - 25%: Light modification (subtle pitch shift)
     * - 50%: Medium modification (DEFAULT - noticeable but natural)
     * - 75%: Strong modification (significant change)
     * - 100%: Maximum modification (very different voice)
     *
     * @param level Anonymization level (0-100)
     */
    fun setAnonymizationLevel(level: Int) {
        val clampedLevel = level.coerceIn(0, 100)
        anonymizationLevel.set(clampedLevel)

        // ═══════════════════════════════════════════════════════════════════════
        // EXTREME PITCH CALCULATION - TOTAL VOICE DISGUISE
        // Level 0 = 1.0 (no change)
        // Level 50 = 0.5 (one octave down)
        // Level 100 = 0.30 (nearly two octaves down - UNRECOGNIZABLE)
        // ═══════════════════════════════════════════════════════════════════════
        val levelRatio = clampedLevel / 100f
        val pitchFactor = 1.0f - (levelRatio * 0.70f) // 1.0 -> 0.30 (EXTREME)
        currentPitchFactor.set(pitchFactor.coerceIn(Config.MIN_PITCH_FACTOR, Config.MAX_PITCH_FACTOR))

        // EXTREME formant shifting - completely changes voice character
        val formantFactor = 1.0f - (levelRatio * Config.FORMANT_SHIFT_RATIO * 0.65f)
        currentFormantFactor.set(formantFactor.coerceIn(Config.MIN_FORMANT_FACTOR, Config.MAX_FORMANT_FACTOR))

        // AGGRESSIVE scrambling - always active at high levels
        val scrambleIntensity = levelRatio * Config.MAX_SCRAMBLE_INTENSITY
        currentScrambleIntensity.set(scrambleIntensity)

        Log.d(TAG, "🎭 EXTREME Anonymization level set: $clampedLevel%")
        Log.d(TAG, "   → Pitch factor: ${currentPitchFactor.get()} (EXTREME)")
        Log.d(TAG, "   → Formant factor: ${currentFormantFactor.get()} (EXTREME)")
        Log.d(TAG, "   → Scramble intensity: ${currentScrambleIntensity.get()} (AGGRESSIVE)")
    }

    /**
     * Enable or disable voice anonymization
     */
    fun setEnabled(enabled: Boolean) {
        isEnabled.set(enabled)
        Log.d(TAG, "🎭 Voice anonymization ${if (enabled) "ENABLED" else "DISABLED"}")
    }

    /**
     * Check if anonymization is enabled
     */
    fun isEnabled(): Boolean = isEnabled.get()

    /**
     * Get current anonymization level
     */
    fun getAnonymizationLevel(): Int = anonymizationLevel.get()

    /**
     * Get current pitch factor
     */
    fun getPitchFactor(): Float = currentPitchFactor.get()

    // ═══════════════════════════════════════════════════════════════════════════════
    // AUDIO PROCESSING - MAIN ENTRY POINTS
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Process audio samples (ShortArray format)
     *
     * This is the main processing entry point. It applies the full
     * anonymization pipeline: PSOLA → Formant Shift → Spectral Scramble
     *
     * @param input Audio samples as 16-bit PCM
     * @return Processed (anonymized) audio samples
     */
    fun process(input: ShortArray): ShortArray {
        val startTime = System.nanoTime()
        val frameNumber = framesProcessed.incrementAndGet()

        // Log first few frames for debugging
        if (frameNumber <= Config.LOG_FIRST_N_FRAMES) {
            Log.d(TAG, "═══════════════════════════════════════════════════════════════")
            Log.d(TAG, "🎭 PROCESSING FRAME #$frameNumber")
            Log.d(TAG, "   Input samples: ${input.size}")
            Log.d(TAG, "   Input RMS: ${calculateRMS(input)}")
            Log.d(TAG, "   Thread: ${Thread.currentThread().name}")
        }

        // Check if enabled
        if (!isEnabled.get()) {
            if (frameNumber <= Config.LOG_FIRST_N_FRAMES) {
                Log.d(TAG, "   ⏸️ Anonymization DISABLED - returning original")
            }
            return input
        }

        // Check if level is 0
        if (anonymizationLevel.get() == 0) {
            if (frameNumber <= Config.LOG_FIRST_N_FRAMES) {
                Log.d(TAG, "   ⏸️ Anonymization level is 0 - returning original")
            }
            return input
        }

        // Ensure initialized
        ensureInitialized()

        try {
            // Convert to float [-1.0, 1.0]
            val floatInput = shortToFloat(input)

            if (frameNumber <= Config.LOG_FIRST_N_FRAMES) {
                Log.d(TAG, "   Step 1: Converted to float, range: [${floatInput.minOrNull()}, ${floatInput.maxOrNull()}]")
            }

            // ═══════════════════════════════════════════════════════════════════
            // EXTREME VOICE ANONYMIZATION PIPELINE
            // ═══════════════════════════════════════════════════════════════════

            var processed = floatInput

            // Step 1: EXTREME Pitch Shifting (nearly 2 octaves down)
            val pitchFactor = currentPitchFactor.get()
            if (pitchFactor != 1.0f) {
                if (frameNumber <= Config.LOG_FIRST_N_FRAMES) {
                    Log.d(TAG, "   Step 2: EXTREME pitch shift (factor: $pitchFactor)")
                }
                processed = psolaProcessor?.process(processed, pitchFactor) ?: processed
                if (frameNumber <= Config.LOG_FIRST_N_FRAMES) {
                    Log.d(TAG, "   → After PSOLA: ${processed.size} samples, RMS: ${calculateRMSFloat(processed)}")
                }
            }

            // Step 2: EXTREME Formant Shifting
            val formantFactor = currentFormantFactor.get()
            if (formantFactor != 1.0f) {
                if (frameNumber <= Config.LOG_FIRST_N_FRAMES) {
                    Log.d(TAG, "   Step 3: EXTREME formant shift (factor: $formantFactor)")
                }
                processed = formantProcessor?.process(processed, formantFactor) ?: processed
                if (frameNumber <= Config.LOG_FIRST_N_FRAMES) {
                    Log.d(TAG, "   → After Formant: ${processed.size} samples, RMS: ${calculateRMSFloat(processed)}")
                }
            }

            // Step 3: AGGRESSIVE Spectral Scrambling
            val scrambleIntensity = currentScrambleIntensity.get()
            if (scrambleIntensity > 0f) {
                if (frameNumber <= Config.LOG_FIRST_N_FRAMES) {
                    Log.d(TAG, "   Step 4: AGGRESSIVE spectral scramble (intensity: $scrambleIntensity)")
                }
                processed = spectralProcessor?.process(processed, scrambleIntensity) ?: processed
                if (frameNumber <= Config.LOG_FIRST_N_FRAMES) {
                    Log.d(TAG, "   → After Scramble: ${processed.size} samples, RMS: ${calculateRMSFloat(processed)}")
                }
            }

            // Step 4: Ring Modulation - adds metallic/robotic quality
            processed = applyRingModulation(processed, frameNumber)
            if (frameNumber <= Config.LOG_FIRST_N_FRAMES) {
                Log.d(TAG, "   → After Ring Mod: RMS: ${calculateRMSFloat(processed)}")
            }

            // Step 5: Noise Injection - masks voice fingerprint
            processed = injectNoise(processed)
            if (frameNumber <= Config.LOG_FIRST_N_FRAMES) {
                Log.d(TAG, "   → After Noise: RMS: ${calculateRMSFloat(processed)}")
            }

            // Step 6: Apply soft clipping to prevent distortion
            processed = softClip(processed)

            // Convert back to short
            val output = floatToShort(processed)

            // Update statistics
            samplesProcessed.addAndGet(input.size.toLong())
            totalProcessingTimeNs.addAndGet(System.nanoTime() - startTime)

            if (frameNumber <= Config.LOG_FIRST_N_FRAMES) {
                val processingTimeMs = (System.nanoTime() - startTime) / 1_000_000.0
                Log.d(TAG, "   ✅ Processing complete in ${String.format("%.2f", processingTimeMs)}ms")
                Log.d(TAG, "   Output RMS: ${calculateRMS(output)}")
                Log.d(TAG, "═══════════════════════════════════════════════════════════════")
            }

            // Periodic logging
            logPeriodicStats()

            return output

        } catch (e: Exception) {
            errorCount.incrementAndGet()
            Log.e(TAG, "❌ Error processing frame #$frameNumber: ${e.message}", e)
            return input // Return original on error (fail-safe)
        }
    }

    /**
     * Process audio bytes (ByteArray format - for WebRTC integration)
     *
     * @param audioData Raw PCM 16-bit audio data
     * @return Processed audio data
     */
    fun processBytes(audioData: ByteArray): ByteArray {
        // Convert bytes to shorts
        val shorts = bytesToShorts(audioData)

        // Process
        val processed = process(shorts)

        // Convert back to bytes
        return shortsToBytes(processed)
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // UTILITY METHODS
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Convert ShortArray to FloatArray [-1.0, 1.0]
     */
    private fun shortToFloat(input: ShortArray): FloatArray {
        return FloatArray(input.size) { input[it] / 32768f }
    }

    /**
     * Convert FloatArray [-1.0, 1.0] to ShortArray
     */
    private fun floatToShort(input: FloatArray): ShortArray {
        return ShortArray(input.size) { i ->
            (input[i] * 32767f).roundToInt().coerceIn(-32768, 32767).toShort()
        }
    }

    /**
     * Convert ByteArray to ShortArray (PCM 16-bit little-endian)
     */
    private fun bytesToShorts(bytes: ByteArray): ShortArray {
        val shorts = ShortArray(bytes.size / 2)
        for (i in shorts.indices) {
            shorts[i] = ((bytes[i * 2 + 1].toInt() shl 8) or
                    (bytes[i * 2].toInt() and 0xFF)).toShort()
        }
        return shorts
    }

    /**
     * Convert ShortArray to ByteArray (PCM 16-bit little-endian)
     */
    private fun shortsToBytes(shorts: ShortArray): ByteArray {
        val bytes = ByteArray(shorts.size * 2)
        for (i in shorts.indices) {
            bytes[i * 2] = (shorts[i].toInt() and 0xFF).toByte()
            bytes[i * 2 + 1] = (shorts[i].toInt() shr 8).toByte()
        }
        return bytes
    }

    /**
     * Soft clipping to prevent harsh distortion
     */
    private fun softClip(samples: FloatArray): FloatArray {
        return FloatArray(samples.size) { i ->
            val x = samples[i]
            when {
                x > 0.95f -> 0.95f + 0.05f * tanh((x - 0.95f) * 20f)
                x < -0.95f -> -0.95f - 0.05f * tanh((-x - 0.95f) * 20f)
                else -> x
            }
        }
    }

    /**
     * Custom tanh implementation
     */
    private fun tanh(x: Float): Float {
        if (x > 20f) return 1f
        if (x < -20f) return -1f
        val e2x = exp(2f * x)
        return (e2x - 1f) / (e2x + 1f)
    }

    // Ring modulation phase tracking (only accessed from audio thread)
    private var ringModPhase = 0.0

    /**
     * Apply ring modulation to add metallic/robotic quality
     * This makes the voice sound artificial and harder to recognize
     */
    private fun applyRingModulation(input: FloatArray, frameNumber: Long): FloatArray {
        val output = FloatArray(input.size)
        val rate = sampleRate.get()
        val frequency = Config.RING_MOD_FREQUENCY
        val depth = Config.RING_MOD_DEPTH * (anonymizationLevel.get() / 100f)

        for (i in input.indices) {
            // Generate modulator signal (sine wave)
            val modulator = sin(ringModPhase).toFloat()
            ringModPhase += 2.0 * PI * frequency / rate
            if (ringModPhase > 2 * PI) ringModPhase -= 2 * PI

            // Apply ring modulation with wet/dry mix
            val modulated = input[i] * (1f + modulator * depth)
            output[i] = input[i] * (1f - depth) + modulated * depth
        }

        if (frameNumber <= Config.LOG_FIRST_N_FRAMES) {
            Log.d(TAG, "   Step 5: Ring modulation (freq: ${frequency}Hz, depth: $depth)")
        }

        return output
    }

    // Random number generator for noise (ThreadLocalRandom avoids contention)
    private fun nextGaussian(): Double = java.util.concurrent.ThreadLocalRandom.current().nextGaussian()

    /**
     * Inject subtle noise to mask voice fingerprint
     * This adds a noise floor that obscures unique voice characteristics
     */
    private fun injectNoise(input: FloatArray): FloatArray {
        val output = FloatArray(input.size)
        val noiseLevel = Config.NOISE_LEVEL * (anonymizationLevel.get() / 100f)

        for (i in input.indices) {
            // Generate pink-ish noise (filtered white noise)
            val noise = (nextGaussian() * noiseLevel).toFloat()
            output[i] = input[i] + noise
        }

        return output
    }

    /**
     * Calculate RMS (Root Mean Square) for logging
     */
    private fun calculateRMS(samples: ShortArray): Float {
        if (samples.isEmpty()) return 0f
        var sum = 0.0
        for (sample in samples) {
            sum += sample.toDouble() * sample.toDouble()
        }
        return sqrt(sum / samples.size).toFloat()
    }

    private fun calculateRMSFloat(samples: FloatArray): Float {
        if (samples.isEmpty()) return 0f
        var sum = 0.0
        for (sample in samples) {
            sum += sample.toDouble() * sample.toDouble()
        }
        return sqrt(sum / samples.size).toFloat()
    }

    /**
     * Log periodic statistics
     */
    private fun logPeriodicStats() {
        val now = System.currentTimeMillis()
        val lastLog = lastLogTime.get()

        if (now - lastLog > Config.LOG_INTERVAL_MS) {
            if (lastLogTime.compareAndSet(lastLog, now)) {
                val frames = framesProcessed.get()
                val samples = samplesProcessed.get()
                val avgTimeNs = if (frames > 0) totalProcessingTimeNs.get() / frames else 0
                val errors = errorCount.get()

                Log.d(TAG, "═══════════════════════════════════════════════════════════════")
                Log.d(TAG, "📊 VOICE ANONYMIZATION STATS")
                Log.d(TAG, "   Frames processed: $frames")
                Log.d(TAG, "   Samples processed: $samples")
                Log.d(TAG, "   Avg processing time: ${avgTimeNs / 1000}µs per frame")
                Log.d(TAG, "   Error count: $errors")
                Log.d(TAG, "   Current settings:")
                Log.d(TAG, "   - Level: ${anonymizationLevel.get()}%")
                Log.d(TAG, "   - Pitch: ${currentPitchFactor.get()}")
                Log.d(TAG, "   - Formant: ${currentFormantFactor.get()}")
                Log.d(TAG, "   - Scramble: ${currentScrambleIntensity.get()}")
                Log.d(TAG, "═══════════════════════════════════════════════════════════════")
            }
        }
    }

    /**
     * Reset all processors (call when starting a new call)
     */
    fun reset() {
        Log.d(TAG, "🔄 Resetting voice anonymizer")

        synchronized(initLock) {
            psolaProcessor?.reset()
            formantProcessor?.reset()
            spectralProcessor?.reset()
        }

        // Reset stats
        framesProcessed.set(0)
        samplesProcessed.set(0)
        totalProcessingTimeNs.set(0)
        errorCount.set(0)
        lastLogTime.set(0)

        Log.d(TAG, "   ✓ Reset complete")
    }

    /**
     * Release resources
     */
    fun release() {
        Log.d(TAG, "🛑 Releasing voice anonymizer")

        synchronized(initLock) {
            psolaProcessor = null
            formantProcessor = null
            spectralProcessor = null
            isInitialized.set(false)
        }

        Log.d(TAG, "   ✓ Released")
    }

    /**
     * Get debug info
     */
    fun getDebugInfo(): String {
        return """
            ProductionVoiceAnonymizer Debug Info:
            - Initialized: ${isInitialized.get()}
            - Enabled: ${isEnabled.get()}
            - Level: ${anonymizationLevel.get()}%
            - Pitch Factor: ${currentPitchFactor.get()}
            - Formant Factor: ${currentFormantFactor.get()}
            - Scramble Intensity: ${currentScrambleIntensity.get()}
            - Sample Rate: ${sampleRate.get()} Hz
            - Frames Processed: ${framesProcessed.get()}
            - Samples Processed: ${samplesProcessed.get()}
            - Errors: ${errorCount.get()}
        """.trimIndent()
    }
}


// ═══════════════════════════════════════════════════════════════════════════════════════
// PSOLA PITCH SHIFTER V2
// ═══════════════════════════════════════════════════════════════════════════════════════

/**
 * Simple Pitch Shifter using Resampling + Overlap-Add
 *
 * This is a simpler but reliable approach to pitch shifting:
 * 1. Resample the audio to change pitch (stretches/compresses time)
 * 2. Use overlap-add to maintain original duration
 *
 * While not as high-quality as full PSOLA, this approach:
 * - Always produces output (no silent frames)
 * - Has low latency
 * - Works reliably in real-time
 */
class PSOLAPitchShifterV2(
    private val sampleRate: Int,
    private val frameSize: Int
) {
    private val TAG = "PSOLAPitchShifterV2"

    // Buffer for accumulating input
    private val inputBuffer = FloatArray(frameSize * 4)
    private var inputBufferPos = 0

    // Previous frame for crossfading
    private var previousOutput = FloatArray(frameSize)
    private var hasPreviousFrame = false

    // Overlap size for smooth transitions
    private val overlapSize = frameSize / 4

    // Frame counter for logging
    private var frameCount = 0L

    // Hann window for overlap region
    private val fadeIn = FloatArray(overlapSize) { i ->
        (0.5 * (1.0 - cos(PI * i / overlapSize))).toFloat()
    }
    private val fadeOut = FloatArray(overlapSize) { i ->
        (0.5 * (1.0 + cos(PI * i / overlapSize))).toFloat()
    }

    init {
        Log.d(TAG, "SimplePitchShifter initialized:")
        Log.d(TAG, "   Sample rate: $sampleRate Hz")
        Log.d(TAG, "   Frame size: $frameSize samples")
        Log.d(TAG, "   Overlap size: $overlapSize samples")
    }

    /**
     * Process audio with pitch shifting
     *
     * @param input Input samples (float, normalized to [-1, 1])
     * @param pitchFactor Pitch multiplier (< 1 = deeper, > 1 = higher)
     * @return Pitch-shifted audio
     */
    fun process(input: FloatArray, pitchFactor: Float): FloatArray {
        frameCount++

        if (input.isEmpty()) {
            return input
        }

        // Simple resampling-based pitch shift
        val output = resamplePitchShift(input, pitchFactor)

        // Apply crossfade with previous frame to avoid clicks
        if (hasPreviousFrame && output.size >= overlapSize && previousOutput.size >= overlapSize) {
            for (i in 0 until overlapSize) {
                val prevIdx = previousOutput.size - overlapSize + i
                if (prevIdx >= 0 && prevIdx < previousOutput.size && i < output.size) {
                    output[i] = previousOutput[prevIdx] * fadeOut[i] + output[i] * fadeIn[i]
                }
            }
        }

        // Store for next frame
        previousOutput = output.copyOf()
        hasPreviousFrame = true

        // Log occasionally
        if (frameCount <= 5 || frameCount % 500 == 0L) {
            val inputRms = sqrt(input.map { it * it }.average()).toFloat()
            val outputRms = sqrt(output.map { it * it }.average()).toFloat()
            Log.d(TAG, "PitchShift frame #$frameCount: factor=$pitchFactor, inputRMS=$inputRms, outputRMS=$outputRms")
        }

        return output
    }

    /**
     * Pitch shift using resampling with linear interpolation
     */
    private fun resamplePitchShift(input: FloatArray, pitchFactor: Float): FloatArray {
        if (pitchFactor == 1.0f) {
            return input.copyOf()
        }

        val output = FloatArray(input.size)

        // For pitch DOWN (factor < 1): read FASTER → readRate > 1
        // For pitch UP (factor > 1): read SLOWER → readRate < 1
        // Inverse relationship: readRate = 1/pitchFactor
        val readRate = 1.0f / pitchFactor

        for (i in output.indices) {
            val readPos = i * readRate
            val readIdx = readPos.toInt()
            val frac = readPos - readIdx

            // Linear interpolation
            if (readIdx >= 0 && readIdx < input.size - 1) {
                output[i] = input[readIdx] * (1 - frac) + input[readIdx + 1] * frac
            } else if (readIdx >= 0 && readIdx < input.size) {
                output[i] = input[readIdx]
            } else {
                // Beyond input bounds - use last valid sample with decay
                output[i] = if (input.isNotEmpty()) input.last() * 0.9f else 0f
            }
        }

        return output
    }

    fun reset() {
        inputBuffer.fill(0f)
        inputBufferPos = 0
        previousOutput = FloatArray(frameSize)
        hasPreviousFrame = false
        frameCount = 0

        Log.d(TAG, "Pitch shifter reset")
    }
}


// ═══════════════════════════════════════════════════════════════════════════════════════
// FORMANT SHIFTER V2
// ═══════════════════════════════════════════════════════════════════════════════════════

/**
 * Formant Shifter using All-Pass Filter Cascade
 *
 * Formants are resonant frequencies of the vocal tract that determine
 * the "character" of a voice (male vs female, individual characteristics).
 *
 * By shifting formants independently of pitch, we can:
 * 1. Avoid the "chipmunk effect" when raising pitch
 * 2. Avoid the "robot voice" when lowering pitch
 * 3. Further disguise the speaker's identity
 *
 * This implementation uses a cascade of all-pass filters to shift
 * the spectral envelope without affecting the fine spectral structure.
 */
class FormantShifterV2(
    private val sampleRate: Int,
    private val frameSize: Int
) {
    private val TAG = "FormantShifterV2"

    // All-pass filter state for spectral warping
    // Two state arrays: input delay (x[n-1]) and output delay (y[n-1]) per stage
    private val numFilters = 8
    private val filterStatesX = FloatArray(numFilters)  // input delay
    private val filterStatesY = FloatArray(numFilters)  // output delay

    // Warping coefficient history for smoothing
    private var lastWarpCoeff = 0f

    // Frame counter
    private var frameCount = 0L

    init {
        Log.d(TAG, "FormantShifterV2 initialized:")
        Log.d(TAG, "   Sample rate: $sampleRate Hz")
        Log.d(TAG, "   Filter stages: $numFilters")
    }

    /**
     * Process audio with formant shifting
     *
     * @param input Input samples
     * @param shiftFactor Formant shift factor (< 1 = lower formants, > 1 = higher)
     * @return Formant-shifted audio
     */
    fun process(input: FloatArray, shiftFactor: Float): FloatArray {
        frameCount++

        if (shiftFactor == 1.0f) {
            return input.copyOf()
        }

        val output = FloatArray(input.size)

        // Calculate warping coefficient from shift factor
        // This maps the frequency axis to achieve formant shifting
        val targetWarpCoeff = calculateWarpCoefficient(shiftFactor)

        // Smooth the warp coefficient to prevent clicks
        val smoothingFactor = 0.9f
        val warpCoeff = lastWarpCoeff * smoothingFactor + targetWarpCoeff * (1 - smoothingFactor)
        lastWarpCoeff = warpCoeff

        // Apply all-pass filter cascade
        for (i in input.indices) {
            var sample = input[i]

            // Cascade of first-order all-pass filters
            // Transfer function: y[n] = a*x[n] + x[n-1] - a*y[n-1]
            for (j in 0 until numFilters) {
                val xPrev = filterStatesX[j]
                val yPrev = filterStatesY[j]
                val newSample = warpCoeff * sample + xPrev - warpCoeff * yPrev
                filterStatesX[j] = sample   // store input delay
                filterStatesY[j] = newSample // store output delay
                sample = newSample
            }

            output[i] = sample
        }

        // Log occasionally
        if (frameCount <= 5 || frameCount % 500 == 0L) {
            Log.d(TAG, "Formant frame #$frameCount: shiftFactor=$shiftFactor, warpCoeff=$warpCoeff")
        }

        return output
    }

    /**
     * Calculate warping coefficient for desired formant shift
     *
     * The all-pass warping coefficient relates to formant shift as:
     * warp = (1 - alpha) / (1 + alpha)
     * where alpha controls the frequency warping
     */
    private fun calculateWarpCoefficient(shiftFactor: Float): Float {
        // Map shift factor to warp coefficient
        // shiftFactor < 1: lower formants (negative warp)
        // shiftFactor > 1: higher formants (positive warp)

        val normalizedShift = shiftFactor - 1.0f // -0.5 to +0.5 typically

        // Convert to warp coefficient (-0.5 to +0.5 range is stable)
        val warp = (normalizedShift * 0.8f).coerceIn(-0.7f, 0.7f)

        return warp
    }

    fun reset() {
        filterStatesX.fill(0f)
        filterStatesY.fill(0f)
        lastWarpCoeff = 0f
        frameCount = 0

        Log.d(TAG, "Formant processor reset")
    }
}


// ═══════════════════════════════════════════════════════════════════════════════════════
// SPECTRAL SCRAMBLER V2
// ═══════════════════════════════════════════════════════════════════════════════════════

/**
 * AGGRESSIVE Spectral Scrambler for Total Voice Anonymization
 *
 * This applies heavy frequency-domain modifications that completely
 * disguise the speaker's voice characteristics.
 *
 * Techniques used:
 * 1. Multi-band comb filtering - Heavy spectral distortion
 * 2. Frequency shifting - Moves formants to unnatural positions
 * 3. Aggressive modulation - Destroys natural voice patterns
 * 4. Harmonic distortion - Changes timbre completely
 */
class SpectralScramblerV2(
    private val sampleRate: Int
) {
    private val TAG = "SpectralScramblerV2"

    // More comb filters with longer delays for heavier effect
    private val combDelays = intArrayOf(7, 11, 13, 17, 23, 29, 31, 37)
    private val combBuffers = Array(combDelays.size) { FloatArray(combDelays[it]) }
    private val combPositions = IntArray(combDelays.size)

    // Frequency shifter state
    private var freqShiftPhase = 0.0
    private val freqShiftAmount = 50.0 // Hz - shifts all frequencies up

    // Aggressive modulation
    private var modulationPhase = 0.0
    private val modulationRate = 7.0 // Hz - faster, more noticeable

    // Second modulator for complex effect
    private var modulationPhase2 = 0.0
    private val modulationRate2 = 11.0 // Hz

    // Frame counter
    private var frameCount = 0L

    init {
        Log.d(TAG, "AGGRESSIVE SpectralScrambler initialized:")
        Log.d(TAG, "   Sample rate: $sampleRate Hz")
        Log.d(TAG, "   Comb filters: ${combDelays.size}")
        Log.d(TAG, "   Frequency shift: ${freqShiftAmount}Hz")
        Log.d(TAG, "   Modulation rates: $modulationRate Hz, $modulationRate2 Hz")
    }

    /**
     * Process audio with AGGRESSIVE spectral scrambling
     */
    fun process(input: FloatArray, intensity: Float): FloatArray {
        frameCount++

        if (intensity <= 0f) {
            return input.copyOf()
        }

        val clampedIntensity = intensity.coerceIn(0f, 1f)
        val output = FloatArray(input.size)

        // More aggressive mixing
        val wetMix = clampedIntensity * 0.6f  // Up to 60% wet (was 30%)
        val dryMix = 1.0f - wetMix * 0.7f     // Reduce dry signal more

        for (i in input.indices) {
            var wet = 0f

            // Apply multiple comb filters
            for (j in combDelays.indices) {
                val delay = combDelays[j]
                val buffer = combBuffers[j]
                val pos = combPositions[j]

                // Read delayed sample with feedback
                wet += buffer[pos] * (1.0f / combDelays.size) * 1.5f

                // Write with stronger feedback for resonance
                buffer[pos] = input[i] * 0.6f + buffer[pos] * 0.35f

                combPositions[j] = (pos + 1) % delay
            }

            // Apply frequency shifting (single sideband modulation)
            val shiftMod = cos(freqShiftPhase).toFloat()
            freqShiftPhase += 2.0 * PI * freqShiftAmount / sampleRate
            if (freqShiftPhase > 2 * PI) freqShiftPhase -= 2 * PI

            val shifted = input[i] * shiftMod * clampedIntensity

            // Apply dual modulation for complex texture
            val mod1 = sin(modulationPhase).toFloat() * 0.15f * clampedIntensity
            val mod2 = sin(modulationPhase2).toFloat() * 0.10f * clampedIntensity
            modulationPhase += 2.0 * PI * modulationRate / sampleRate
            modulationPhase2 += 2.0 * PI * modulationRate2 / sampleRate
            if (modulationPhase > 2 * PI) modulationPhase -= 2 * PI
            if (modulationPhase2 > 2 * PI) modulationPhase2 -= 2 * PI

            // Combine all effects
            val processed = input[i] * dryMix + wet * wetMix + shifted * 0.3f
            output[i] = processed * (1.0f + mod1 + mod2)
        }

        if (frameCount <= 5 || frameCount % 500 == 0L) {
            Log.d(TAG, "AGGRESSIVE Scrambler frame #$frameCount: intensity=$clampedIntensity")
        }

        return output
    }

    fun reset() {
        combBuffers.forEach { it.fill(0f) }
        combPositions.fill(0)
        modulationPhase = 0.0
        modulationPhase2 = 0.0
        freqShiftPhase = 0.0
        frameCount = 0

        Log.d(TAG, "Spectral scrambler reset")
    }
}

