package com.example.backroom

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.backroom.data.audio.ProductionVoiceAnonymizer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.sin

/**
 * Instrumented tests for [ProductionVoiceAnonymizer].
 *
 * These verify that processed audio **actually differs** from the input at the
 * byte/sample level — the existing VoiceAnonymizationTestUtil logged this but
 * never asserted it.
 */
@RunWith(AndroidJUnit4::class)
class VoiceAnonymizationInstrumentedTest {

    companion object {
        private const val SAMPLE_RATE = 48000
        private const val FRAME_SIZE = 480 // 10 ms
    }

    @Before
    fun setUp() {
        ProductionVoiceAnonymizer.initialize(SAMPLE_RATE)
        ProductionVoiceAnonymizer.setEnabled(true)
        ProductionVoiceAnonymizer.setAnonymizationLevel(100) // max
    }

    @After
    fun tearDown() {
        ProductionVoiceAnonymizer.release()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun sineWave(freq: Float, samples: Int): ShortArray {
        val omega = 2.0 * Math.PI * freq / SAMPLE_RATE
        return ShortArray(samples) { i -> (sin(omega * i) * 16000).toInt().toShort() }
    }

    private fun countDifferent(a: ShortArray, b: ShortArray): Int {
        val len = minOf(a.size, b.size)
        var count = 0
        for (i in 0 until len) if (a[i] != b[i]) count++
        return count
    }

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test
    fun processedAudioDiffersFromInput_sineWave150Hz() {
        val input = sineWave(150f, FRAME_SIZE * 10)
        ProductionVoiceAnonymizer.reset()

        val output = ProductionVoiceAnonymizer.process(input)

        val diff = countDifferent(input, output)
        val ratio = diff.toFloat() / input.size

        assertTrue(
            "Expected >10% samples to differ but got ${ratio * 100}%",
            ratio > 0.10f
        )
    }

    @Test
    fun processedAudioDiffersFromInput_sineWave250Hz() {
        val input = sineWave(250f, FRAME_SIZE * 10)
        ProductionVoiceAnonymizer.reset()

        val output = ProductionVoiceAnonymizer.process(input)

        val diff = countDifferent(input, output)
        assertTrue("Zero samples changed at 250 Hz", diff > 0)
    }

    @Test
    fun processedBytesDifferFromInput() {
        val input = sineWave(150f, FRAME_SIZE * 5)
        // Convert to bytes
        val bytes = ByteArray(input.size * 2)
        for (i in input.indices) {
            bytes[i * 2] = (input[i].toInt() and 0xFF).toByte()
            bytes[i * 2 + 1] = (input[i].toInt() shr 8).toByte()
        }

        ProductionVoiceAnonymizer.reset()
        val processed = ProductionVoiceAnonymizer.processBytes(bytes)

        var diffBytes = 0
        for (i in bytes.indices) {
            if (i < processed.size && bytes[i] != processed[i]) diffBytes++
        }

        assertTrue(
            "Expected byte-level differences but got $diffBytes different bytes",
            diffBytes > 0
        )
    }

    @Test
    fun disabledAnonymizationReturnsIdenticalOutput() {
        val input = sineWave(150f, FRAME_SIZE * 5)

        ProductionVoiceAnonymizer.setEnabled(false)
        ProductionVoiceAnonymizer.reset()

        val output = ProductionVoiceAnonymizer.process(input)

        val diff = countDifferent(input, output)
        assertEquals("Disabled anonymizer should return identical audio", 0, diff)
    }

    @Test
    fun higherLevelProducesMoreDifference() {
        val input = sineWave(150f, FRAME_SIZE * 10)

        ProductionVoiceAnonymizer.setAnonymizationLevel(25)
        ProductionVoiceAnonymizer.reset()
        val outLow = ProductionVoiceAnonymizer.process(input)
        var diffLow = 0L
        for (i in input.indices.take(outLow.size)) {
            diffLow += abs(input[i] - outLow[i])
        }

        ProductionVoiceAnonymizer.setAnonymizationLevel(100)
        ProductionVoiceAnonymizer.reset()
        val outHigh = ProductionVoiceAnonymizer.process(input)
        var diffHigh = 0L
        for (i in input.indices.take(outHigh.size)) {
            diffHigh += abs(input[i] - outHigh[i])
        }

        assertTrue(
            "Level 100 diff ($diffHigh) should exceed level 25 diff ($diffLow)",
            diffHigh > diffLow
        )
    }

    @Test
    fun silenceRemainsQuiet() {
        val input = ShortArray(FRAME_SIZE * 10) { 0 }
        ProductionVoiceAnonymizer.reset()

        val output = ProductionVoiceAnonymizer.process(input)

        var sum = 0.0
        for (s in output) sum += s.toDouble() * s.toDouble()
        val rms = kotlin.math.sqrt(sum / output.size)

        assertTrue("Silence output RMS=$rms should be < 100", rms < 100)
    }
}

