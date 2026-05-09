package com.better.nothing.music.vizualizer

enum class HapticMode {
    BASS_TO_AMPLITUDE,
    BEAT_DETECTION
}

/**
 * Placeholder for the Haptic Engine logic.
 * These will be fully implemented in the future.
 */
interface HapticEngine {
    fun process(fftData: FloatArray)
}

class BassToAmplitudeEngine : HapticEngine {
    override fun process(fftData: FloatArray) {
        // TODO: Move current haptics logic here
    }
}

class BeatDetectionEngine : HapticEngine {
    override fun process(fftData: FloatArray) {
        // TODO: Implement beat detection logic
    }
}
