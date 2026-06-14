package com.better.nothing.music.vizualizer.logic;

import org.jtransforms.fft.DoubleFFT_1D;

/**
 * Handles audio capture, FFT processing, and frequency analysis.
 */
public class AudioProcessor {

    private int sampleRate = 44100;
    private static final float SPECTRUM_LEAKAGE_FLOOR_RATIO = 0.12f;
    private static final float EPSILON = 0.000001f;

    private int fftSize;
    private int analysisWindow;
    private float hzPerBin;

    private float[] ring;
    private int ringPosition = 0;
    private int filled = 0;

    private double[] fftData;
    private float[] magnitude;
    private float[] hann;
    private DoubleFFT_1D fft;

    // Improved Autogain state
    private float mRunningMax = 0.05f;
    private float mTargetPeak = 0.18f;
    private float mAutoGain = 1.0f;
    private boolean mAutoGainEnabled = false;

    private static final float DECAY_SLOW = 0.9998f;
    private static final float GAIN_SMOOTHING = 0.15f; // Alpha for gain smoothing

    public AudioProcessor() {
        updateFFTSize(); // Default
    }

    public void updateFFTSize() {
        updateFFTSize(44100);
    }

    public void updateFFTSize(int sampleRate) {
        // Reduced from 4096 to 2048 to improve temporal responsiveness and reduce window latency.
        int newFftSize = 2048; 

        if (this.fftSize == newFftSize && this.fft != null && this.sampleRate == sampleRate) {
            return;
        }

        this.sampleRate = sampleRate;
        this.fftSize = newFftSize;
        this.analysisWindow = fftSize;
        this.hzPerBin = (float) sampleRate / (float) fftSize;

        this.fft = new DoubleFFT_1D(fftSize);
        this.fftData = new double[fftSize * 2];
        this.magnitude = new float[fftSize / 2 + 1];
        this.hann = buildHannWindow(fftSize);

        this.ring = new float[analysisWindow];
        this.ringPosition = 0;
        this.filled = 0;
    }

    public float getHzPerBin() {
        return hzPerBin;
    }

    public int getFFTSize() {
        return fftSize;
    }

    public void setAutoGainEnabled(boolean enabled) {
        this.mAutoGainEnabled = enabled;
        if (!enabled) {
            mAutoGain = 1.0f;
            mRunningMax = 0.05f;
        }
    }

    public AudioFrameResult processAudioFrame(short[] hopBuffer, VisualizerConfig config, FrequencyRange hapticRange, boolean isInternalSource) {
        // Fill ring buffer
        for (short value : hopBuffer) {
            ring[ringPosition] = value / 32768f;
            ringPosition = (ringPosition + 1) % analysisWindow;
        }
        filled = Math.min(filled + hopBuffer.length, analysisWindow);

        if (filled < analysisWindow) {
            return null; // Not enough data yet
        }

        // Process FFT
        // We use realForwardFull which expects real input in the first half of the array
        // and produces complex output in the full array (interleaved).
        for (int i = 0; i < fftSize; i++) {
            fftData[i] = ring[(ringPosition + i) % analysisWindow] * hann[i];
        }

        fft.realForwardFull(fftData);
        for (int i = 0; i <= fftSize / 2; i++) {
            double re = fftData[2 * i];
            double im = fftData[2 * i + 1];
            // Normalize magnitude by fftSize
            float mag = (float) (Math.hypot(re, im) / fftSize);

            // Amplify high frequencies: linear boost from 1.0x at 0Hz to ~4.0x at 20kHz
            float freq = i * hzPerBin;
            float boost = 1f + (freq / 15000f) * 5f;
            float rawMag = mag * boost;

            // Internal sources (MediaProjection) are already normalized by the system.
            // Applying auto-gain to them often causes clipping/overshoot.
            if (mAutoGainEnabled && !isInternalSource) {
                // Update running max with adaptive decay: faster if we're way above target, slower if we're below
                float decay = rawMag > mRunningMax ? 0.95f : DECAY_SLOW;
                mRunningMax = Math.max(mRunningMax * decay, rawMag);
                
                if (mRunningMax > 0.0001f) {
                    float desiredGain = mTargetPeak / Math.max(mRunningMax, 0.005f);
                    // Clamp gain to reasonable range [0.5, 20.0] for high quality normalization
                    desiredGain = Math.max(0.5f, Math.min(20.0f, desiredGain));
                    
                    // Smooth the gain changes to prevent flickering
                    mAutoGain = (mAutoGain * (1f - GAIN_SMOOTHING)) + (desiredGain * GAIN_SMOOTHING);
                }
                magnitude[i] = rawMag * mAutoGain;
            } else {
                magnitude[i] = rawMag;
            }
        }

        // Compute magnitudes
        float[] uniqueMagnitudes = computeUniqueMagnitudes(config, magnitude);
        float hapticPeak = hapticRange != null ? computeRangeMagnitude(hapticRange, magnitude) : 0f;

        return new AudioFrameResult(uniqueMagnitudes, hapticPeak, magnitude);
    }

    private float[] computeUniqueMagnitudes(VisualizerConfig config, float[] magnitude) {
        if (config == null) return new float[0];
        float[] uniqueMagnitudes = new float[config.uniqueRanges.length];
        float dominantMagnitude = 0f;
        for (int i = 0; i < config.uniqueRanges.length; i++) {
            float magnitudeSum = computeRangeMagnitude(config.uniqueRanges[i], magnitude);
            uniqueMagnitudes[i] = magnitudeSum;
            if (magnitudeSum > dominantMagnitude) {
                dominantMagnitude = magnitudeSum;
            }
        }

        if (dominantMagnitude <= EPSILON) {
            return uniqueMagnitudes;
        }

        float leakageFloor = dominantMagnitude * SPECTRUM_LEAKAGE_FLOOR_RATIO;
        boolean hasFilteredEnergy = false;
        for (int i = 0; i < uniqueMagnitudes.length; i++) {
            uniqueMagnitudes[i] = Math.max(0f, uniqueMagnitudes[i] - leakageFloor);
            if (uniqueMagnitudes[i] > EPSILON) {
                hasFilteredEnergy = true;
            }
        }

        // If the leakage floor wipes every band, fall back to the raw per-range values
        // so the visualizer still receives energy and can normalize it downstream.
        if (!hasFilteredEnergy) {
            for (int i = 0; i < config.uniqueRanges.length; i++) {
                uniqueMagnitudes[i] = computeRangeMagnitude(config.uniqueRanges[i], magnitude);
            }
        }
        return uniqueMagnitudes;
    }

    public float computeRangeMagnitude(FrequencyRange range, float[] magnitude) {
        if (range == null || magnitude == null || magnitude.length == 0) {
            return 0f;
        }

        int start = Math.max(0, Math.min(range.binLo, magnitude.length - 1));
        int end = Math.max(start, Math.min(range.binHi, magnitude.length - 1));
        float sumSquares = 0f;
        for (int bin = start; bin <= end; bin++) {
            sumSquares += magnitude[bin] * magnitude[bin];
        }
        int count = end - start + 1;
        return count > 0 ? (float) Math.sqrt(sumSquares / count) : 0f;
    }

    private static float[] buildHannWindow(int size) {
        float[] hann = new float[size];
        double denom = Math.max(1d, size - 1d);
        for (int i = 0; i < size; i++) {
            double phase = (2d * Math.PI * i) / denom;
            hann[i] = (float) (0.5d * (1d - Math.cos(phase)));
        }
        return hann;
    }

    // Inner classes for config
    public static final class VisualizerConfig {
        public final String presetKey;
        public final String description;
        public final float decay;
        public final ZoneSpec[] zones;
        public final FrequencyRange[] uniqueRanges;
        public final int[][] zoneToRangeIndices;

        public VisualizerConfig(
                String presetKey,
                String description,
                float decay,
                ZoneSpec[] zones,
                FrequencyRange[] uniqueRanges,
                int[][] zoneToRangeIndices
        ) {
            this.presetKey = presetKey;
            this.description = description;
            this.decay = decay;
            this.zones = zones;
            this.uniqueRanges = uniqueRanges;
            this.zoneToRangeIndices = zoneToRangeIndices;
        }
    }

    public static final class ZoneSpec {
        public final float lowHz;
        public final float highHz;
        public final float lowPercent;
        public final float highPercent;

        public ZoneSpec(float lowHz, float highHz, float lowPercent, float highPercent) {
            this.lowHz = lowHz;
            this.highHz = highHz;
            this.lowPercent = lowPercent;
            this.highPercent = highPercent;
        }

        boolean hasPercentSlice() {
            return !Float.isNaN(lowPercent) && !Float.isNaN(highPercent);
        }
    }

    public static final class FrequencyRange {
        public final float lowHz;
        public final float highHz;
        public final int binLo;
        public final int binHi;

        public FrequencyRange(float lowHz, float highHz, float hzPerBin, int fftSize) {
            this.lowHz = lowHz;
            this.highHz = highHz;
            this.binLo = Math.max(0, (int) Math.ceil(lowHz / hzPerBin));
            this.binHi = Math.max(binLo, Math.min(fftSize / 2, (int) Math.floor(highHz / hzPerBin)));
        }
    }

    public static final class AudioFrameResult {
        public final float[] uniqueMagnitudes;
        public final float hapticPeak;
        public final float[] magnitude;

        public AudioFrameResult(float[] uniqueMagnitudes, float hapticPeak, float[] magnitude) {
            this.uniqueMagnitudes = uniqueMagnitudes;
            this.hapticPeak = hapticPeak;
            this.magnitude = magnitude;
        }
    }
}
