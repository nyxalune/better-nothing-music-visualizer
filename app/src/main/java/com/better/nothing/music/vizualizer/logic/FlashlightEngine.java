package com.better.nothing.music.vizualizer.logic;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;

import com.better.nothing.music.vizualizer.model.TorchMode;

import java.util.Arrays;
import java.util.Objects;

/**
 * Advanced Flashlight controller with intensity support and temporal smoothing.
 */
public final class FlashlightEngine {

    private static final String TAG = "FlashlightEngine";

    // Interval between camera service updates to prevent overhead.
    private static final long MIN_RESUBMIT_INTERVAL_MS = 20L;

    private static final float DEFAULT_DECAY = 0.85f;
    private static final float DEFAULT_GAMMA = 2.2f;
    private static final float EPSILON = 0.001f;
    
    // Auto-normalization parameters
    private static final float PEAK_FALLOFF = 0.992f;
    private static final float SPECTRUM_GAIN = 4.0f;

    private final CameraManager cameraManager;
    private String cameraId;
    private boolean hasTorchStrength;
    private int maxTorchStrength = 1;

    // User settings
    private TorchMode torchMode = TorchMode.AMPLITUDE;
    private float flashlightMultiplier = 1.0f;
    private float flashlightGamma = DEFAULT_GAMMA;
    private float flashlightThreshold = 0.15f;
    private float flashlightSmoothing = 0.7f;
    private float flashlightBeatSensitivity = 1.0f;

    // Dynamic state
    private float decayedState = 0f;
    private float peakTracker = EPSILON;
    private float smoothedIntensity = 0f;

    // Beat detection state
    private final float[] deltaHistory = new float[61];
    private final float[] sortedHistory = new float[61];
    private int deltaIndex = 0;
    private int deltaCount = 0;
    private float prevEnergy = 0f;
    private long lastTriggerMs = 0L;
    private float thresholdMask = 0f;

    private int lastLevel = -1;
    private long lastSubmitMs = 0L;
    private boolean torchActive = false;

    public FlashlightEngine(Context context) {
        Context appContext = Objects.requireNonNull(context, "context").getApplicationContext();
        this.cameraManager = (CameraManager) appContext.getSystemService(Context.CAMERA_SERVICE);
        initCamera();
    }

    private void initCamera() {
        if (cameraManager == null) return;
        try {
            String[] ids = cameraManager.getCameraIdList();
            for (String id : ids) {
                CameraCharacteristics chars = cameraManager.getCameraIdList().length > 0 ? 
                        cameraManager.getCameraCharacteristics(id) : null;
                if (chars == null) continue;

                Boolean hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
                
                if (hasFlash != null && hasFlash && facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    this.cameraId = id;
                    
                    // PWM Intensity support check (Android 13+)
                    try {
                        // Using reflection to avoid compilation errors on environments with
                        // older SDK stubs or weird shadowing.
                        CameraCharacteristics.Key<Integer> key = getTorchMaxLevelKey();
                        if (key != null) {
                            Integer max = chars.get(key);
                            if (max != null && max > 1) {
                                this.hasTorchStrength = true;
                                this.maxTorchStrength = max;
                                Log.d(TAG, "Hardware supports torch strength up to: " + max);
                            }
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to read torch strength characteristic", e);
                    }
                    break;
                }
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to init camera for flashlight", e);
        }
    }

    @SuppressWarnings("unchecked")
    private CameraCharacteristics.Key<Integer> getTorchMaxLevelKey() {
        try {
            // This field was added in API 33.
            // Using reflection to check for the field's existence.
            Object field = CameraCharacteristics.class.getField("FLASH_INFO_STRENGTH_MAX_LEVEL").get(null);
            return (CameraCharacteristics.Key<Integer>) field;
        } catch (Exception e) {
            return null;
        }
    }

    public synchronized void setFlashlightMultiplier(float multiplier) {
        this.flashlightMultiplier = Math.max(0f, multiplier);
    }

    public synchronized void setFlashlightGamma(float gamma) {
        this.flashlightGamma = Math.max(0.1f, gamma);
    }

    public synchronized void setFlashlightThreshold(float threshold) {
        this.flashlightThreshold = Math.max(0f, threshold);
    }

    public synchronized void setFlashlightSmoothing(float smoothing) {
        this.flashlightSmoothing = Math.max(0f, Math.min(0.99f, smoothing));
    }

    public synchronized void setTorchMode(TorchMode mode) {
        this.torchMode = mode;
        if (mode == TorchMode.BEAT_DETECTION) {
            resetBeatDetection();
        }
    }

    public synchronized void setFlashlightBeatSensitivity(float sensitivity) {
        this.flashlightBeatSensitivity = Math.max(0.3f, Math.min(6.0f, sensitivity));
    }

    private void resetBeatDetection() {
        deltaIndex = 0;
        deltaCount = 0;
        prevEnergy = 0f;
        lastTriggerMs = 0L;
        thresholdMask = 0f;
        Arrays.fill(deltaHistory, 0f);
    }

    public synchronized void performFlashlightFeedback(float rawPeak, @Nullable AudioProcessor.VisualizerConfig config, float[] magnitude, int binLo, int binHi) {
        if (cameraId == null) return;

        if (torchMode == TorchMode.BEAT_DETECTION) {
            performBeatDetection(magnitude, binLo, binHi);
            return;
        }

        float current = Math.max(0f, rawPeak) * SPECTRUM_GAIN;
        final float decay = (config != null) ? config.decay : DEFAULT_DECAY;

        if (current > decayedState) {
            decayedState = current;
        } else {
            decayedState = (decay * decayedState) + ((1f - decay) * current);
        }

        if (decayedState < EPSILON) {
            decayedState = 0f;
            stopFlashlightInternal();
            return;
        }

        peakTracker = Math.max(decayedState, peakTracker * PEAK_FALLOFF);
        if (peakTracker < EPSILON) peakTracker = EPSILON;

        float normalized = decayedState / peakTracker;

        float shapedValue = (float) Math.pow(normalized, flashlightGamma) * flashlightMultiplier;
        
        if (shapedValue < flashlightThreshold) {
            shapedValue = 0f;
        } else {
            shapedValue = (shapedValue - flashlightThreshold) / (1f - flashlightThreshold);
        }

        smoothedIntensity = (flashlightSmoothing * smoothedIntensity) + ((1f - flashlightSmoothing) * shapedValue);

        int level;
        if (hasTorchStrength) {
            level = Math.round(Math.min(1.0f, smoothedIntensity) * maxTorchStrength);
        } else {
            level = smoothedIntensity > 0.4f ? 1 : 0;
        }

        if (level <= 0) {
            stopFlashlightInternal();
            return;
        }

        final long now = SystemClock.elapsedRealtime();
        boolean significantChange = Math.abs(level - lastLevel) >= Math.max(1, maxTorchStrength / 15);
        boolean intervalPassed = (now - lastSubmitMs) >= MIN_RESUBMIT_INTERVAL_MS;

        if (torchActive && !significantChange && (now - lastSubmitMs) < 80) {
            return;
        }

        if (torchActive && !intervalPassed) {
            return;
        }

        submitTorchLevel(level);
    }

    private synchronized void performBeatDetection(float[] magnitude, int binLo, int binHi) {
        if (magnitude == null || magnitude.length == 0) return;

        int start = Math.max(0, Math.min(binLo, magnitude.length - 1));
        int end = Math.max(start, Math.min(binHi, magnitude.length - 1));

        float sum = 0f;
        for (int i = start; i <= end; i++) {
            sum += magnitude[i];
        }

        float energy = (float) Math.log(1f + sum);
        float delta = energy - prevEnergy;
        prevEnergy = energy;

        pushDelta(delta);

        float threshold = Math.max(medianDelta() * (2.2f * flashlightBeatSensitivity), thresholdMask);
        long now = SystemClock.elapsedRealtime();

        if (delta > threshold && delta > 0.025f && (now - lastTriggerMs) >= 45L) {
            submitTorchLevel(maxTorchStrength);
            lastTriggerMs = now;
            thresholdMask = delta * 0.9f;
            
            // For beat detection, we want a quick flash, so we'll schedule turning it off.
            // However, this engine is called periodically.
            // We can just set a state and let the next call (or a timer) turn it off.
            // In BeatDetectionHapticEngine, it uses a waveform.
            // For torch, we might want to just let it decay or turn off after some time.
        } else {
            // If no beat, we should probably turn it off if it was a beat flash.
            if (now - lastTriggerMs > 50L) {
                stopFlashlightInternal();
            }
        }

        thresholdMask *= 0.85f;
    }

    private void pushDelta(float delta) {
        deltaHistory[deltaIndex] = Math.max(delta, 0.0001f);
        deltaIndex = (deltaIndex + 1) % deltaHistory.length;
        if (deltaCount < deltaHistory.length) {
            deltaCount++;
        }
    }

    private float medianDelta() {
        if (deltaCount == 0) return 0.01f;

        System.arraycopy(deltaHistory, 0, sortedHistory, 0, deltaCount);
        Arrays.sort(sortedHistory, 0, deltaCount);

        if (deltaCount % 2 == 1) {
            return sortedHistory[deltaCount / 2];
        } else {
            int mid = deltaCount / 2;
            return (sortedHistory[mid - 1] + sortedHistory[mid]) * 0.5f;
        }
    }

    public synchronized void stopFlashlight() {
        stopFlashlightInternal();
        decayedState = 0f;
        peakTracker = EPSILON;
        smoothedIntensity = 0f;
    }

    private void submitTorchLevel(int level) {
        if (cameraId == null) return;

        try {
            if (hasTorchStrength) {
                cameraManager.turnOnTorchWithStrengthLevel(cameraId, Math.max(1, level));
            } else {
                cameraManager.setTorchMode(cameraId, true);
            }
            torchActive = true;
            lastLevel = level;
            lastSubmitMs = SystemClock.elapsedRealtime();
        } catch (CameraAccessException | IllegalArgumentException | SecurityException e) {
            Log.w(TAG, "Failed to set torch level", e);
            torchActive = false;
        } catch (Throwable t) {
            // Fallback for extreme cases
            try {
                cameraManager.setTorchMode(cameraId, true);
                torchActive = true;
            } catch (Exception ignored) {}
        }
    }

    private void stopFlashlightInternal() {
        if (!torchActive) {
            lastLevel = 0;
            return;
        }

        try {
            if (cameraId != null) {
                cameraManager.setTorchMode(cameraId, false);
            }
        } catch (CameraAccessException | IllegalArgumentException | SecurityException e) {
            Log.w(TAG, "Failed to turn off torch", e);
        }

        torchActive = false;
        lastLevel = 0;
        lastSubmitMs = SystemClock.elapsedRealtime();
    }
}
