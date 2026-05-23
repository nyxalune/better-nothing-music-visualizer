package com.better.nothing.music.vizualizer.logic;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;

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
    private float flashlightMultiplier = 1.0f;
    private float flashlightGamma = DEFAULT_GAMMA;
    private float flashlightThreshold = 0.15f;
    private float flashlightSmoothing = 0.7f;

    // Dynamic state
    private float decayedState = 0f;
    private float peakTracker = EPSILON;
    private float smoothedIntensity = 0f;

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
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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
            return (CameraCharacteristics.Key<Integer>) CameraCharacteristics.class
                    .getField("FLASH_INFO_STRENGTH_MAX_LEVEL").get(null);
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

    public synchronized void performFlashlightFeedback(float rawPeak, @Nullable AudioProcessor.VisualizerConfig config) {
        if (cameraId == null) return;

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

        float shaped = (float) Math.pow(normalized, flashlightGamma) * flashlightMultiplier;
        
        if (shaped < flashlightThreshold) {
            shaped = 0f;
        } else {
            shaped = (shaped - flashlightThreshold) / (1f - flashlightThreshold);
        }

        smoothedIntensity = (flashlightSmoothing * smoothedIntensity) + ((1f - flashlightSmoothing) * shaped);

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

    public synchronized void stopFlashlight() {
        stopFlashlightInternal();
        decayedState = 0f;
        peakTracker = EPSILON;
        smoothedIntensity = 0f;
    }

    private void submitTorchLevel(int level) {
        if (cameraId == null) return;

        try {
            if (hasTorchStrength && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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
