package com.better.nothing.music.vizualizer.logic

import android.content.Context
import android.os.*
import android.util.Log
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow

class BeatDetectionHapticEngine(context: Context) {

    private val TAG = "BeatDetectionHaptic"

    private val vibrator: Vibrator?
    private val vibratorManager: VibratorManager?

    private var waveform: VibrationEffect? = null
    private var hapticMultiplier = 1.0f
    private var hapticGamma = 8.0f // Default "speed"
    private var hapticSensitivity = 1.0f // Default

    // Lower cooldown = tighter beat following
    private val cooldownMs = 60L

    init {
        val appContext = context.applicationContext

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            vibratorManager =
                appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibrator = vibratorManager.defaultVibrator
        } else {
            vibratorManager = null
            vibrator = appContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        waveform = buildWaveform()
    }

    /**
     * magnitude = FFT magnitude bins
     * range = already-selected frequency range from UI slider
     */
    fun performHapticFeedback(
        magnitude: FloatArray,
        range: AudioProcessor.FrequencyRange?
    ) {

        if (
            vibrator == null ||
            !vibrator.hasVibrator() ||
            waveform == null ||
            range == null ||
            magnitude.isEmpty()
        ) {
            return
        }

        val start = max(0, minOf(range.binLo, magnitude.lastIndex))
        val end = max(start, minOf(range.binHi, magnitude.lastIndex))

        // Sum selected FFT band
        var sum = 0f

        for (i in start..end) {
            sum += magnitude[i]
        }

        // Log energy stabilizes loudness response
        val energy = ln(1f + sum)

        // Instant transient detection
        val delta = energy - prevEnergy
        prevEnergy = energy

        pushDelta(delta)

        // Use adaptive threshold with a decaying mask to prevent double-triggering
        // Sensitivity maps 0.3-6.0 where 1.0 is original base
        val threshold = max(medianDelta() * (2.2f * hapticSensitivity), thresholdMask)

        val now = SystemClock.elapsedRealtime()
        val cooldownPassed = now - lastTriggerMs >= cooldownMs

        // Main trigger condition
        if (
            delta > threshold &&
            delta > 0.025f &&
            cooldownPassed
        ) {

            triggerWaveform()
            lastTriggerMs = now
            thresholdMask = delta * 0.9f
        }

        // Decay the mask over time
        thresholdMask *= 0.85f
    }

    private fun pushDelta(delta: Float) {

        // Avoid history collapsing toward zero
        deltaHistory[deltaIndex] =
            delta.coerceAtLeast(0.0001f)

        deltaIndex =
            (deltaIndex + 1) % deltaHistory.size

        if (deltaCount < deltaHistory.size) {
            deltaCount++
        }
    }

    private fun medianDelta(): Float {

        if (deltaCount == 0) {
            return 0.01f
        }

        System.arraycopy(
            deltaHistory,
            0,
            sortedHistory,
            0,
            deltaCount
        )

        // Insertion sort is fine for tiny arrays
        for (i in 1 until deltaCount) {

            val key = sortedHistory[i]
            var j = i - 1

            while (
                j >= 0 &&
                sortedHistory[j] > key
            ) {
                sortedHistory[j + 1] =
                    sortedHistory[j]
                j--
            }

            sortedHistory[j + 1] = key
        }

        return if (deltaCount % 2 == 1) {

            sortedHistory[deltaCount / 2]

        } else {

            val mid = deltaCount / 2

            (sortedHistory[mid - 1] +
                    sortedHistory[mid]) * 0.5f
        }
    }

    private fun triggerWaveform() {

        try {
            vibrate(waveform!!)

        } catch (e: Exception) {

            Log.w(TAG, "Failed vibration", e)
        }
    }

    private fun buildWaveform(): VibrationEffect {

        val sustainMs = 40
        val decayMs = 1500
        val stepMs = 10

        val count = (sustainMs + decayMs) / stepMs

        val timings =
            LongArray(count) { stepMs.toLong() }

        val amplitudes = IntArray(count)

        if (vibrator != null && !vibrator.hasAmplitudeControl()) {
            Log.w(TAG, "Device does not support amplitude control. Waveform will be binary.")
        }

        for (i in 0 until count) {

            val t = i * stepMs

            val amp = if (t < sustainMs) {
                255f
            } else {
                // Decay starts after sustain
                val x = 1f - ((t - sustainMs).toFloat() / decayMs.toFloat())
                255f * x.coerceIn(0f, 1f).pow(hapticGamma)
            }

            amplitudes[i] = (amp * hapticMultiplier).toInt().coerceIn(0, 255)
        }

        return VibrationEffect.createWaveform(
            timings,
            amplitudes,
            -1
        )
    }

    private fun cancelVibration() {

        try {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

                vibratorManager?.cancel()

            } else {

                vibrator?.cancel()
            }

        } catch (_: Exception) {
        }
    }

    private fun vibrate(effect: VibrationEffect) {

        if (vibrator != null) {
            vibrator.vibrate(effect)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            vibratorManager?.vibrate(
                CombinedVibration.createParallel(effect)
            )
        }
    }

    fun stopHaptics() {
        cancelVibration()
    }

    fun resetDetectionState() {
        deltaIndex = 0
        deltaCount = 0
        prevEnergy = 0f
        lastTriggerMs = 0L
        thresholdMask = 0f
        deltaHistory.fill(0f)
    }

    fun setHapticMultiplier(multiplier: Float) {
        if (hapticMultiplier != multiplier) {
            hapticMultiplier = multiplier
            waveform = buildWaveform()
        }
    }

    fun setHapticGamma(gamma: Float) {
        if (hapticGamma != gamma) {
            hapticGamma = gamma.coerceIn(4f, 15f)
            waveform = buildWaveform()
        }
    }

    fun setHapticSensitivity(sensitivity: Float) {
        hapticSensitivity = sensitivity.coerceIn(0.3f, 6f)
    }
}
