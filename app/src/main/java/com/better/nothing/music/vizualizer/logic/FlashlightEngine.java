package com.better.nothing.music.vizualizer.logic;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;

import com.better.nothing.music.vizualizer.model.TorchMode;

import java.util.Arrays;
import java.util.Objects;

/**
 * Flashlight controller with bass-amplitude and beat-detection modes.
 */
public final class FlashlightEngine {

    private static final String TAG = "FlashlightEngine";
    private static final float SPECTRUM_GAIN = 4.0f;
    private static final float EPSILON = 0.001f;
    private static final long MIN_RESUBMIT_INTERVAL_MS = 20L;
    private static final long BEAT_MIN_COOLDOWN_MS = 60L;
    private static final int BEAT_PATTERN_STEPS = 24;

    private final CameraManager cameraManager;
    private String cameraId;
    private boolean hasTorchStrength;
    private int maxTorchStrength = 1;

    private TorchMode torchMode = TorchMode.AMPLITUDE;

    // In amplitude mode this is a threshold for binary torches and a multiplier for
    // multi-intensity torches. Keeping one slider in the UI makes the behavior easy
    // to explain without branching the screen layout.
    private float amplitudeThresholdOrMultiplier = 0.15f;

    private float flashlightBeatSensitivity = 1.0f;
    private float flashlightBeatSpeedMs = 90f;

    private final float[] deltaHistory = new float[61];
    private final float[] sortedHistory = new float[61];
    private final float[] beatPattern = buildBeatPattern();
    private int deltaIndex = 0;
    private int deltaCount = 0;
    private float prevEnergy = 0f;
    private long lastTriggerMs = 0L;
    private float thresholdMask = 0f;

    private long beatFlashStartMs = 0L;
    private long beatFlashDurationMs = 90L;

    private int lastLevel = -1;
    private long lastSubmitMs = 0L;
    private boolean torchActive = false;

    public FlashlightEngine(Context context) {
        Context appContext = Objects.requireNonNull(context, "context").getApplicationContext();
        this.cameraManager = (CameraManager) appContext.getSystemService(Context.CAMERA_SERVICE);
        initCamera();
    }

    public static int detectTorchIntensityLevels(Context context) {
        Context appContext = Objects.requireNonNull(context, "context").getApplicationContext();
        CameraManager manager = (CameraManager) appContext.getSystemService(Context.CAMERA_SERVICE);
        if (manager == null) {
            return 1;
        }

        try {
            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics chars = manager.getCameraCharacteristics(id);
                if (!supportsBackTorch(chars)) {
                    continue;
                }

                int max = readTorchStrengthLevel(chars);
                return Math.max(1, max);
            }
        } catch (CameraAccessException e) {
            Log.w(TAG, "Failed to detect torch intensity levels", e);
        }

        return 1;
    }

    public synchronized int getTorchIntensityLevels() {
        return hasTorchStrength ? Math.max(1, maxTorchStrength) : 1;
    }

    public synchronized boolean hasVariableTorchStrength() {
        return hasTorchStrength && maxTorchStrength > 1;
    }

    private void initCamera() {
        if (cameraManager == null) {
            return;
        }

        try {
            for (String id : cameraManager.getCameraIdList()) {
                CameraCharacteristics chars = cameraManager.getCameraCharacteristics(id);
                if (!supportsBackTorch(chars)) {
                    continue;
                }

                cameraId = id;
                int max = readTorchStrengthLevel(chars);
                if (max > 1) {
                    hasTorchStrength = true;
                    maxTorchStrength = max;
                    Log.d(TAG, "Hardware supports torch strength up to: " + max);
                }
                return;
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to init camera for flashlight", e);
        }
    }

    private static boolean supportsBackTorch(@Nullable CameraCharacteristics chars) {
        if (chars == null) {
            return false;
        }
        Boolean hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
        Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
        return Boolean.TRUE.equals(hasFlash) && facing != null && facing == CameraCharacteristics.LENS_FACING_BACK;
    }

    @SuppressWarnings("unchecked")
    private static int readTorchStrengthLevel(@Nullable CameraCharacteristics chars) {
        if (chars == null) {
            return 1;
        }

        try {
            Object field = CameraCharacteristics.class.getField("FLASH_INFO_STRENGTH_MAX_LEVEL").get(null);
            if (field instanceof CameraCharacteristics.Key) {
                CameraCharacteristics.Key<Integer> key = (CameraCharacteristics.Key<Integer>) field;
                Integer max = chars.get(key);
                if (max != null && max > 0) {
                    return max;
                }
            }
        } catch (Throwable ignored) {
        }

        return 1;
    }

    public synchronized void setFlashlightThreshold(float threshold) {
        this.amplitudeThresholdOrMultiplier = Math.max(0f, threshold);
    }

    public synchronized void setTorchMode(TorchMode mode) {
        this.torchMode = mode;
        if (mode == TorchMode.BEAT_DETECTION) {
            resetBeatDetection();
        } else {
            beatFlashStartMs = 0L;
        }
    }

    public synchronized void setFlashlightBeatSensitivity(float sensitivity) {
        this.flashlightBeatSensitivity = Math.max(0.3f, Math.min(6.0f, sensitivity));
    }

    public synchronized void setFlashlightSpeedMs(float speedMs) {
        this.flashlightBeatSpeedMs = clamp(speedMs, 40f, 150f);
        this.beatFlashDurationMs = (long) flashlightBeatSpeedMs;
    }

    public synchronized void performFlashlightFeedback(
            float rawPeak,
            @Nullable AudioProcessor.VisualizerConfig config,
            float[] magnitude,
            int binLo,
            int binHi
    ) {
        if (cameraId == null) {
            return;
        }

        if (torchMode == TorchMode.BEAT_DETECTION) {
            performBeatDetection(magnitude, binLo, binHi);
            return;
        }

        performAmplitudeFeedback(rawPeak);
    }

    private void performAmplitudeFeedback(float rawPeak) {
        float current = clamp(Math.max(0f, rawPeak) * SPECTRUM_GAIN, 0f, 1f);

        if (hasVariableTorchStrength()) {
            float normalized = clamp(current * amplitudeThresholdOrMultiplier, 0f, 1f);
            int level = Math.round(normalized * maxTorchStrength);
            if (level <= 0) {
                stopFlashlightInternal();
                return;
            }
            submitTorchLevel(Math.max(1, Math.min(maxTorchStrength, level)));
            return;
        }

        if (current < amplitudeThresholdOrMultiplier) {
            stopFlashlightInternal();
            return;
        }

        submitTorchLevel(1);
    }

    private synchronized void performBeatDetection(float[] magnitude, int binLo, int binHi) {
        if (magnitude == null || magnitude.length == 0) {
            if (beatFlashStartMs != 0L) {
                updateBeatFlashState();
            }
            return;
        }

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

        if (delta > threshold && delta > 0.025f && (now - lastTriggerMs) >= BEAT_MIN_COOLDOWN_MS) {
            lastTriggerMs = now;
            thresholdMask = delta * 0.8f;
            beatFlashStartMs = now;
            beatFlashDurationMs = (long) flashlightBeatSpeedMs;
            updateBeatFlashState();
        } else if (beatFlashStartMs != 0L) {
            updateBeatFlashState();
        } else {
            stopFlashlightInternal();
        }

        thresholdMask *= 0.85f;
    }

    private void updateBeatFlashState() {
        long now = SystemClock.elapsedRealtime();
        long elapsed = now - beatFlashStartMs;

        if (elapsed >= beatFlashDurationMs) {
            stopFlashlightInternal();
            return;
        }

        float progress = clamp(elapsed / (float) Math.max(1L, beatFlashDurationMs), 0f, 1f);
        float intensity = sampleBeatPattern(progress);

        if (hasVariableTorchStrength()) {
            int level = Math.max(1, Math.round(intensity * maxTorchStrength));
            submitTorchLevel(Math.min(maxTorchStrength, level));
        } else {
            submitTorchLevel(1);
        }
    }

    private float sampleBeatPattern(float progress) {
        int index = Math.min(
                BEAT_PATTERN_STEPS - 1,
                Math.max(0, Math.round(progress * (BEAT_PATTERN_STEPS - 1)))
        );
        return beatPattern[index];
    }

    private static float[] buildBeatPattern() {
        float[] pattern = new float[BEAT_PATTERN_STEPS];
        for (int i = 0; i < BEAT_PATTERN_STEPS; i++) {
            float progress = i / (float) (BEAT_PATTERN_STEPS - 1);
            float inverted = 1f - progress;
            pattern[i] = (float) Math.pow(inverted, 2.4f);
        }
        return pattern;
    }

    private void resetBeatDetection() {
        deltaIndex = 0;
        deltaCount = 0;
        prevEnergy = 0f;
        lastTriggerMs = 0L;
        thresholdMask = 0f;
        Arrays.fill(deltaHistory, 0f);
    }

    private void pushDelta(float delta) {
        deltaHistory[deltaIndex] = Math.max(delta, 0.0001f);
        deltaIndex = (deltaIndex + 1) % deltaHistory.length;
        if (deltaCount < deltaHistory.length) {
            deltaCount++;
        }
    }

    private float medianDelta() {
        if (deltaCount == 0) {
            return 0.01f;
        }

        System.arraycopy(deltaHistory, 0, sortedHistory, 0, deltaCount);
        Arrays.sort(sortedHistory, 0, deltaCount);

        if (deltaCount % 2 == 1) {
            return sortedHistory[deltaCount / 2];
        }

        int mid = deltaCount / 2;
        return (sortedHistory[mid - 1] + sortedHistory[mid]) * 0.5f;
    }

    public synchronized void stopFlashlight() {
        stopFlashlightInternal();
        beatFlashStartMs = 0L;
    }

    private void submitTorchLevel(int level) {
        if (cameraId == null) {
            return;
        }

        final long now = SystemClock.elapsedRealtime();
        boolean intervalPassed = (now - lastSubmitMs) >= MIN_RESUBMIT_INTERVAL_MS;
        boolean significantChange = Math.abs(level - lastLevel) >= Math.max(1, maxTorchStrength / 15);

        if (torchActive && !intervalPassed && !significantChange) {
            return;
        }

        try {
            if (hasVariableTorchStrength()) {
                if (Build.VERSION.SDK_INT >= 33) {
                    cameraManager.turnOnTorchWithStrengthLevel(cameraId, Math.max(1, level));
                } else {
                    cameraManager.setTorchMode(cameraId, true);
                }
            } else {
                cameraManager.setTorchMode(cameraId, true);
            }

            torchActive = true;
            lastLevel = level;
            lastSubmitMs = now;
        } catch (CameraAccessException | IllegalArgumentException | SecurityException e) {
            Log.w(TAG, "Failed to set torch level", e);
            torchActive = false;
        } catch (Throwable t) {
            try {
                cameraManager.setTorchMode(cameraId, true);
                torchActive = true;
            } catch (Exception ignored) {
            }
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

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
