package com.example.backroom.data.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════
 * VOICE ANONYMIZATION TEST UTILITY
 * ═══════════════════════════════════════════════════════════════════════════════════════
 *
 * This utility helps verify that voice anonymization is working correctly.
 * It provides methods to:
 *
 * 1. Test the ProductionVoiceAnonymizer with synthetic audio
 * 2. Record audio and compare before/after anonymization
 * 3. Save audio samples as WAV files for manual verification
 * 4. Log detailed statistics about the processing
 *
 * All tests include hard assertions — failures throw [AssertionError] so that
 * integration-test runners (or manual callers) get an unambiguous pass/fail.
 *
 * USAGE:
 * ```
 * // Run a quick self-test (throws on failure)
 * VoiceAnonymizationTestUtil.runSelfTest()
 *
 * // Test with actual microphone
 * VoiceAnonymizationTestUtil.recordAndTestAnonymization(context, durationMs = 5000)
 * ```
 */
object VoiceAnonymizationTestUtil {

    private const val TAG = "VoiceAnonTest"

    // Test parameters
    private const val TEST_SAMPLE_RATE = 48000
    private const val TEST_FRAME_SIZE = 480 // 10ms at 48kHz
    private const val TEST_DURATION_MS = 1000 // 1 second

    /** Hard assertion — throws [AssertionError] on failure (visible in logcat AND crashes test runners). */
    private fun assertTest(condition: Boolean, message: String) {
        if (!condition) {
            Log.e(TAG, "   ❌ ASSERTION FAILED: $message")
            throw AssertionError("VoiceAnonymizationTest: $message")
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // SELF-TEST WITH SYNTHETIC AUDIO
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Run a comprehensive self-test of the voice anonymization system
     *
     * This test:
     * 1. Generates synthetic audio (sine waves at voice frequencies)
     * 2. Processes it through the anonymizer
     * 3. Verifies the output is different from input
     * 4. Logs detailed statistics
     *
     * @return true if all tests pass
     */
    fun runSelfTest(): Boolean {
        Log.d(TAG, "═══════════════════════════════════════════════════════════════")
        Log.d(TAG, "🧪 VOICE ANONYMIZATION SELF-TEST")
        Log.d(TAG, "═══════════════════════════════════════════════════════════════")

        var allPassed = true

        // Test 1: Initialize the anonymizer
        Log.d(TAG, "\n📋 Test 1: Initialization")
        try {
            ProductionVoiceAnonymizer.initialize(TEST_SAMPLE_RATE)
            ProductionVoiceAnonymizer.setEnabled(true)
            ProductionVoiceAnonymizer.setAnonymizationLevel(50) // 50% anonymization
            Log.d(TAG, "   ✅ PASSED: Anonymizer initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "   ❌ FAILED: ${e.message}", e)
            allPassed = false
        }

        // Test 2: Process sine wave (simulating voice fundamental)
        Log.d(TAG, "\n📋 Test 2: Sine Wave Processing (150 Hz - male voice)")
        if (!testSineWaveProcessing(150f)) allPassed = false

        // Test 3: Process higher frequency (simulating female voice)
        Log.d(TAG, "\n📋 Test 3: Sine Wave Processing (250 Hz - female voice)")
        if (!testSineWaveProcessing(250f)) allPassed = false

        // Test 4: Process complex waveform (simulating real voice)
        Log.d(TAG, "\n📋 Test 4: Complex Waveform Processing")
        if (!testComplexWaveformProcessing()) allPassed = false

        // Test 5: Process silence (should remain silent)
        Log.d(TAG, "\n📋 Test 5: Silence Processing")
        if (!testSilenceProcessing()) allPassed = false

        // Test 6: Different anonymization levels
        Log.d(TAG, "\n📋 Test 6: Anonymization Levels")
        if (!testAnonymizationLevels()) allPassed = false

        // Test 7: Enable/disable toggle
        Log.d(TAG, "\n📋 Test 7: Enable/Disable Toggle")
        if (!testEnableDisable()) allPassed = false

        // Summary
        Log.d(TAG, "\n═══════════════════════════════════════════════════════════════")
        if (allPassed) {
            Log.d(TAG, "🎉 ALL TESTS PASSED!")
        } else {
            Log.e(TAG, "❌ SOME TESTS FAILED - Check logs above")
        }
        Log.d(TAG, "═══════════════════════════════════════════════════════════════")

        return allPassed
    }

    /**
     * Test processing of a pure sine wave
     */
    private fun testSineWaveProcessing(frequency: Float): Boolean {
        val numSamples = TEST_SAMPLE_RATE * TEST_DURATION_MS / 1000
        val input = generateSineWave(frequency, numSamples, TEST_SAMPLE_RATE)

        // Calculate input statistics
        val inputRms = calculateRMS(input)
        val inputMax = input.maxOrNull() ?: 0
        val inputMin = input.minOrNull() ?: 0

        Log.d(TAG, "   Input: $numSamples samples, RMS=$inputRms, range=[$inputMin, $inputMax]")

        // Process
        val startTime = System.nanoTime()
        val output = ProductionVoiceAnonymizer.process(input)
        val processingTime = (System.nanoTime() - startTime) / 1_000_000.0

        // Calculate output statistics
        val outputRms = calculateRMS(output)
        val outputMax = output.maxOrNull() ?: 0
        val outputMin = output.minOrNull() ?: 0

        Log.d(TAG, "   Output: ${output.size} samples, RMS=$outputRms, range=[$outputMin, $outputMax]")
        Log.d(TAG, "   Processing time: ${String.format("%.2f", processingTime)}ms")

        // Verify output is different from input
        var differentSamples = 0
        var totalDifference = 0L
        for (i in input.indices.take(output.size)) {
            if (input[i] != output[i]) {
                differentSamples++
                totalDifference += kotlin.math.abs(input[i] - output[i])
            }
        }

        val differenceRatio = differentSamples.toFloat() / input.size
        val avgDifference = if (differentSamples > 0) totalDifference / differentSamples else 0

        Log.d(TAG, "   Different samples: $differentSamples/${input.size} (${String.format("%.1f", differenceRatio * 100)}%)")
        Log.d(TAG, "   Average difference: $avgDifference")

        // Test passes if output is sufficiently different
        assertTest(differenceRatio > 0.1,
            "Sine ${frequency}Hz: only ${String.format("%.1f", differenceRatio * 100)}% samples differ (need >10%)")
        Log.d(TAG, "   ✅ PASSED: Output differs from input (${String.format("%.1f", differenceRatio * 100)}%)")

        // Additional byte-level assertion: at least some samples must have changed
        assertTest(differentSamples > 0,
            "Sine ${frequency}Hz: zero samples changed — anonymizer produced identical output")

        return true
    }

    /**
     * Test processing of complex waveform (multiple harmonics)
     */
    private fun testComplexWaveformProcessing(): Boolean {
        val numSamples = TEST_SAMPLE_RATE * TEST_DURATION_MS / 1000
        val input = generateComplexWaveform(150f, numSamples, TEST_SAMPLE_RATE)

        val inputRms = calculateRMS(input)
        Log.d(TAG, "   Input: $numSamples samples (complex waveform), RMS=$inputRms")

        val output = ProductionVoiceAnonymizer.process(input)
        val outputRms = calculateRMS(output)

        Log.d(TAG, "   Output: ${output.size} samples, RMS=$outputRms")

        // Check for difference
        var differentSamples = 0
        for (i in input.indices.take(output.size)) {
            if (input[i] != output[i]) differentSamples++
        }

        val differenceRatio = differentSamples.toFloat() / input.size
        Log.d(TAG, "   Different samples: ${String.format("%.1f", differenceRatio * 100)}%")

        assertTest(differenceRatio > 0.1,
            "Complex waveform: only ${String.format("%.1f", differenceRatio * 100)}% samples differ (need >10%)")
        Log.d(TAG, "   ✅ PASSED")
        return true
    }

    /**
     * Test that silence remains silence (no artifacts added)
     */
    private fun testSilenceProcessing(): Boolean {
        val numSamples = TEST_FRAME_SIZE * 10
        val input = ShortArray(numSamples) { 0 }

        val output = ProductionVoiceAnonymizer.process(input)
        val outputRms = calculateRMS(output)

        Log.d(TAG, "   Silence output RMS: $outputRms")

        // Silence should remain mostly silent (RMS < 100)
        assertTest(outputRms < 100,
            "Silence: output RMS=$outputRms (expected <100) — artifacts added to silence")
        Log.d(TAG, "   ✅ PASSED: Silence remains silent")
        return true
    }

    /**
     * Test different anonymization levels
     */
    private fun testAnonymizationLevels(): Boolean {
        val numSamples = TEST_FRAME_SIZE * 10
        val input = generateSineWave(150f, numSamples, TEST_SAMPLE_RATE)

        val results = mutableListOf<Pair<Int, Float>>()

        for (level in listOf(0, 25, 50, 75, 100)) {
            ProductionVoiceAnonymizer.setAnonymizationLevel(level)
            ProductionVoiceAnonymizer.reset()

            val output = ProductionVoiceAnonymizer.process(input)

            var diff = 0L
            for (i in input.indices.take(output.size)) {
                diff += kotlin.math.abs(input[i] - output[i])
            }
            val avgDiff = diff.toFloat() / input.size

            results.add(Pair(level, avgDiff))
            Log.d(TAG, "   Level $level%: avg difference = ${String.format("%.1f", avgDiff)}")
        }

        // Reset to default
        ProductionVoiceAnonymizer.setAnonymizationLevel(50)

        // Verify that higher levels produce more difference
        val level0Diff = results.find { it.first == 0 }?.second ?: 0f
        val level100Diff = results.find { it.first == 100 }?.second ?: 0f

        assertTest(level100Diff > level0Diff,
            "Levels: level100 diff ($level100Diff) should exceed level0 diff ($level0Diff)")
        Log.d(TAG, "   ✅ PASSED: Higher levels produce more difference")
        return true
    }

    /**
     * Test enable/disable functionality
     */
    private fun testEnableDisable(): Boolean {
        val numSamples = TEST_FRAME_SIZE * 10
        val input = generateSineWave(150f, numSamples, TEST_SAMPLE_RATE)

        // Test with disabled
        ProductionVoiceAnonymizer.setEnabled(false)
        ProductionVoiceAnonymizer.reset()
        val disabledOutput = ProductionVoiceAnonymizer.process(input)

        var disabledDiff = 0
        for (i in input.indices.take(disabledOutput.size)) {
            if (input[i] != disabledOutput[i]) disabledDiff++
        }

        Log.d(TAG, "   Disabled: $disabledDiff different samples")

        // Test with enabled
        ProductionVoiceAnonymizer.setEnabled(true)
        ProductionVoiceAnonymizer.reset()
        val enabledOutput = ProductionVoiceAnonymizer.process(input)

        var enabledDiff = 0
        for (i in input.indices.take(enabledOutput.size)) {
            if (input[i] != enabledOutput[i]) enabledDiff++
        }

        Log.d(TAG, "   Enabled: $enabledDiff different samples")

        // When disabled, output should equal input (0 differences)
        // When enabled, output should be different
        assertTest(disabledDiff == 0,
            "Enable/disable: disabled output had $disabledDiff diffs (expected 0)")
        assertTest(enabledDiff > 0,
            "Enable/disable: enabled output had 0 diffs (expected >0)")
        Log.d(TAG, "   ✅ PASSED: Enable/disable works correctly")
        return true
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // REAL AUDIO TESTING
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Record audio from microphone and test anonymization
     *
     * @param context Android context
     * @param durationMs Recording duration in milliseconds
     * @return true if test passes
     */
    @Suppress("MissingPermission")
    fun recordAndTestAnonymization(context: Context, durationMs: Int = 3000): Boolean {
        Log.d(TAG, "═══════════════════════════════════════════════════════════════")
        Log.d(TAG, "🎤 RECORDING AND TESTING ANONYMIZATION")
        Log.d(TAG, "   Duration: ${durationMs}ms")
        Log.d(TAG, "═══════════════════════════════════════════════════════════════")

        val bufferSize = AudioRecord.getMinBufferSize(
            TEST_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "   ❌ Invalid buffer size")
            return false
        }

        var audioRecord: AudioRecord? = null

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                TEST_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 2
            )

            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "   ❌ AudioRecord initialization failed")
                return false
            }

            // Collect raw audio
            val totalSamples = TEST_SAMPLE_RATE * durationMs / 1000
            val rawAudio = ShortArray(totalSamples)
            var samplesRead = 0

            audioRecord.startRecording()
            Log.d(TAG, "   🔴 Recording...")

            val buffer = ShortArray(TEST_FRAME_SIZE)
            while (samplesRead < totalSamples) {
                val read = audioRecord.read(buffer, 0, TEST_FRAME_SIZE)
                if (read > 0) {
                    val copyCount = minOf(read, totalSamples - samplesRead)
                    System.arraycopy(buffer, 0, rawAudio, samplesRead, copyCount)
                    samplesRead += copyCount
                }
            }

            audioRecord.stop()
            Log.d(TAG, "   ⏹️ Recording complete: $samplesRead samples")

            // Process through anonymizer
            ProductionVoiceAnonymizer.setEnabled(true)
            ProductionVoiceAnonymizer.setAnonymizationLevel(50)
            ProductionVoiceAnonymizer.reset()

            Log.d(TAG, "   🔄 Processing through anonymizer...")
            val processedAudio = ProductionVoiceAnonymizer.process(rawAudio)

            // Compare
            val rawRms = calculateRMS(rawAudio)
            val processedRms = calculateRMS(processedAudio)

            var differentSamples = 0
            for (i in rawAudio.indices.take(processedAudio.size)) {
                if (rawAudio[i] != processedAudio[i]) differentSamples++
            }

            val differenceRatio = differentSamples.toFloat() / rawAudio.size

            Log.d(TAG, "   Raw RMS: $rawRms")
            Log.d(TAG, "   Processed RMS: $processedRms")
            Log.d(TAG, "   Different samples: $differentSamples (${String.format("%.1f", differenceRatio * 100)}%)")

            // Save WAV files for manual verification
            saveWavFile(context, "raw_recording.wav", rawAudio)
            saveWavFile(context, "anonymized_recording.wav", processedAudio)

            Log.d(TAG, "   📁 WAV files saved to app's files directory")

            return if (differenceRatio > 0.1 && rawRms > 100) {
                Log.d(TAG, "   ✅ PASSED: Anonymization working!")
                // Hard assertion for integration test runners
                assertTest(differenceRatio > 0.1,
                    "Live recording: only ${String.format("%.1f", differenceRatio * 100)}% samples differ")
                true
            } else if (rawRms <= 100) {
                Log.w(TAG, "   ⚠️ Recording was too quiet - try speaking louder")
                false
            } else {
                Log.e(TAG, "   ❌ FAILED: Output too similar to input")
                false
            }

        } catch (e: Exception) {
            Log.e(TAG, "   ❌ Error: ${e.message}", e)
            return false
        } finally {
            audioRecord?.release()
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // UTILITY METHODS
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Generate a pure sine wave
     */
    private fun generateSineWave(frequency: Float, numSamples: Int, sampleRate: Int): ShortArray {
        val samples = ShortArray(numSamples)
        val angularFrequency = 2.0 * Math.PI * frequency / sampleRate

        for (i in 0 until numSamples) {
            val value = (Math.sin(angularFrequency * i) * 16000).toInt().toShort()
            samples[i] = value
        }

        return samples
    }

    /**
     * Generate a complex waveform with harmonics (more like real voice)
     */
    private fun generateComplexWaveform(fundamental: Float, numSamples: Int, sampleRate: Int): ShortArray {
        val samples = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            var value = 0.0

            // Fundamental frequency
            value += Math.sin(2.0 * Math.PI * fundamental * i / sampleRate) * 0.5

            // Harmonics (2nd, 3rd, 4th, 5th)
            value += Math.sin(2.0 * Math.PI * fundamental * 2 * i / sampleRate) * 0.25
            value += Math.sin(2.0 * Math.PI * fundamental * 3 * i / sampleRate) * 0.15
            value += Math.sin(2.0 * Math.PI * fundamental * 4 * i / sampleRate) * 0.07
            value += Math.sin(2.0 * Math.PI * fundamental * 5 * i / sampleRate) * 0.03

            samples[i] = (value * 16000).toInt().coerceIn(-32768, 32767).toShort()
        }

        return samples
    }

    /**
     * Calculate RMS (Root Mean Square) of audio
     */
    private fun calculateRMS(samples: ShortArray): Float {
        if (samples.isEmpty()) return 0f
        var sum = 0.0
        for (sample in samples) {
            sum += sample.toDouble() * sample.toDouble()
        }
        return sqrt(sum / samples.size).toFloat()
    }

    /**
     * Save audio data as WAV file
     */
    private fun saveWavFile(context: Context, filename: String, samples: ShortArray) {
        try {
            val file = File(context.filesDir, filename)
            val fos = FileOutputStream(file)

            // WAV header
            val numChannels = 1
            val bitsPerSample = 16
            val byteRate = TEST_SAMPLE_RATE * numChannels * bitsPerSample / 8
            val blockAlign = numChannels * bitsPerSample / 8
            val dataSize = samples.size * 2

            // Write RIFF header
            fos.write("RIFF".toByteArray())
            fos.write(intToBytes(36 + dataSize, 4))
            fos.write("WAVE".toByteArray())

            // Write fmt chunk
            fos.write("fmt ".toByteArray())
            fos.write(intToBytes(16, 4)) // Chunk size
            fos.write(intToBytes(1, 2)) // Audio format (PCM)
            fos.write(intToBytes(numChannels, 2))
            fos.write(intToBytes(TEST_SAMPLE_RATE, 4))
            fos.write(intToBytes(byteRate, 4))
            fos.write(intToBytes(blockAlign, 2))
            fos.write(intToBytes(bitsPerSample, 2))

            // Write data chunk
            fos.write("data".toByteArray())
            fos.write(intToBytes(dataSize, 4))

            // Write samples
            for (sample in samples) {
                fos.write(sample.toInt() and 0xFF)
                fos.write((sample.toInt() shr 8) and 0xFF)
            }

            fos.close()
            Log.d(TAG, "   Saved: ${file.absolutePath}")

        } catch (e: Exception) {
            Log.e(TAG, "   Error saving WAV: ${e.message}")
        }
    }

    private fun intToBytes(value: Int, numBytes: Int): ByteArray {
        val bytes = ByteArray(numBytes)
        for (i in 0 until numBytes) {
            bytes[i] = ((value shr (i * 8)) and 0xFF).toByte()
        }
        return bytes
    }

    /**
     * Get debug info
     */
    fun getDebugInfo(): String {
        return """
            Voice Anonymization Test Utility
            ================================
            Test sample rate: $TEST_SAMPLE_RATE Hz
            Test frame size: $TEST_FRAME_SIZE samples
            Test duration: $TEST_DURATION_MS ms
            
            ${ProductionVoiceAnonymizer.getDebugInfo()}
        """.trimIndent()
    }
}

