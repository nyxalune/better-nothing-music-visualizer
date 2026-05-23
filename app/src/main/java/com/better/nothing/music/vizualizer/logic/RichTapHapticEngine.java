package com.better.nothing.music.vizualizer.logic;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;

import com.apprichtap.haptic.RichTapUtils;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.File;

/**
 * RichTap implementation of the haptic engine for high-fidelity vibration.
 * Based on provided SDK documentation and official samples.
 */
public final class RichTapHapticEngine {

    private static final String TAG = "RichTapHapticEngine";

    private static final long MIN_RESUBMIT_INTERVAL_MS = 25L;
    private static final int AMPLITUDE_THRESHOLD = 2;

    private static final float DEFAULT_DECAY = 0.85f;
    private static final float EPSILON = 0.0001f;
    private static final float PEAK_FALLOFF = 0.995f;
    private static final float SPECTRUM_GAIN = 4.0f;
    
    // SDK uses 0-255 for amplitude scaling
    private static final int MAX_AMPLITUDE_RICHTAP = 255; 
    private static final long SILENCE_TIMEOUT_MS = 1000L;

    private static final String HE_ASSET_NAME = "visualizer_loop.he";
    private static String sHeFilePath = null;

    private static boolean sInitialized = false;
    private static boolean sIsSupported = false;

    private float hapticMultiplier = 1.0f;
    private int hapticFrequency = 50;

    private float decayedState = 0f;
    private float peakTracker = EPSILON;

    private int lastAmplitude = -1;
    private long lastSubmitMs = 0L;
    private long lastNonZeroAmplitudeMs = 0L;
    private boolean isPlaying = false;

    // Test pattern for startup confirmation - matches user example.
    private static final String STARTUP_TEST_JSON = "{" +
            "\"Metadata\":{\"Version\":2,\"Created\":\"2022-04-26\",\"Description\":\"Exported from RichTap Creator Pro\"}," +
            "\"PatternList\":[{" +
            "  \"AbsoluteTime\":0," +
            "  \"Pattern\":[{" +
            "    \"Event\":{" +
            "      \"Type\":\"transient\"," +
            "      \"RelativeTime\":0," +
            "      \"Parameters\":{\"Intensity\":100,\"Frequency\":75}," +
            "      \"Index\":0" +
            "    }" +
            "  }]" +
            "}]" +
            "}";

    // HE 2.0 structure used for the visualizer loop. 
    private static final String CONTINUOUS_HE_JSON = "{" +
            "\"Metadata\":{\"Version\":2,\"Created\":\"2022-04-26\",\"Description\":\"Continuous Loop\"}," +
            "\"PatternList\":[{" +
            "  \"AbsoluteTime\":0," +
            "  \"Pattern\":[{" +
            "    \"Event\":{" +
            "      \"Type\":\"continuous\"," +
            "      \"RelativeTime\":0," +
            "      \"Duration\":3600000," +
            "      \"Parameters\":{" +
            "        \"Intensity\":100," +
            "        \"Frequency\":50," +
            "        \"Curve\":[" +
            "          { \"Time\":0, \"Intensity\":1.0, \"Frequency\":0 }," +
            "          { \"Time\":3600000, \"Intensity\":1.0, \"Frequency\":0 }" +
            "        ]" +
            "      }," +
            "      \"Index\":0" +
            "    }" +
            "  }]" +
            "}]" +
            "}";

    public RichTapHapticEngine(Context context) {
        if (!sInitialized) {
            try {
                // Initialize as per sample: RichTapUtils.getInstance().init(context);
                RichTapUtils.getInstance().init(context);
                
                // Poll for support status
                for (int i = 0; i < 5; i++) {
                    SystemClock.sleep(100);
                    sIsSupported = RichTapUtils.getInstance().isSupportedRichTap();
                    if (sIsSupported) break;
                }

                Log.d(TAG, "RichTap initialized. Supported: " + sIsSupported);

                if (sIsSupported) {
                    // Extract asset to file storage
                    sHeFilePath = dumpAssetToDataStorage(context, HE_ASSET_NAME);

                    // Play a short startup test to confirm motor works
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        try {
                            Log.d(TAG, "Playing startup haptic test...");
                            // Simple playback for transient test
                            RichTapUtils.getInstance().playHaptic(STARTUP_TEST_JSON, 0);
                        } catch (Exception e) {
                            Log.w(TAG, "Startup haptic test failed", e);
                        }
                    }, 1000);
                }
            } catch (Exception e) {
                Log.e(TAG, "RichTap initialization failed", e);
                sIsSupported = false;
            }
            sInitialized = true;
        }
    }

    public synchronized void setHapticMultiplier(float multiplier) {
        this.hapticMultiplier = Math.max(0f, multiplier);
    }

    public synchronized void setHapticFrequency(int frequency) {
        this.hapticFrequency = clampInt(frequency, 0, 100);
    }

    public synchronized void performHapticFeedback(float rawPeak, @Nullable AudioProcessor.VisualizerConfig config) {
        if (!sIsSupported) {
            return;
        }

        final float decay = (config != null) ? config.decay : DEFAULT_DECAY;
        float current = Math.max(0f, rawPeak) * SPECTRUM_GAIN;

        if (current > decayedState) {
            decayedState = current;
        } else {
            decayedState = (decay * decayedState) + ((1f - decay) * current);
        }

        peakTracker = Math.max(decayedState, peakTracker * PEAK_FALLOFF);
        if (peakTracker < EPSILON) peakTracker = EPSILON;

        float normalized = decayedState / peakTracker;
        float shaped = normalized * hapticMultiplier;
        
        int amplitudeValue = Math.round(Math.min(1.0f, shaped) * MAX_AMPLITUDE_RICHTAP);
        amplitudeValue = clampInt(amplitudeValue, 0, MAX_AMPLITUDE_RICHTAP);

        final long now = SystemClock.elapsedRealtime();

        if (!isPlaying) {
            if (amplitudeValue > 10) { 
                lastNonZeroAmplitudeMs = now;
                submitRichTapHaptic(amplitudeValue);
            }
            return;
        }

        if (amplitudeValue > 0) {
            lastNonZeroAmplitudeMs = now;
        } else if ((now - lastNonZeroAmplitudeMs) > SILENCE_TIMEOUT_MS) {
            stopHapticsInternal();
            return;
        }

        boolean significantChange = Math.abs(amplitudeValue - lastAmplitude) >= AMPLITUDE_THRESHOLD;
        boolean intervalElapsed = (now - lastSubmitMs) >= MIN_RESUBMIT_INTERVAL_MS;

        if (!significantChange && !intervalElapsed) {
            return;
        }

        submitRichTapHaptic(amplitudeValue);
    }

    private void submitRichTapHaptic(int amplitude) {
        try {
            if (!isPlaying) {
                if (sHeFilePath != null) {
                    File heFile = new File(sHeFilePath);
                    // playHaptic(File, loop) -> -1 for infinite loop as per documentation
                    RichTapUtils.getInstance().playHaptic(heFile, -1);
                } else {
                    // playHaptic(String, loop) -> -1 for infinite loop
                    RichTapUtils.getInstance().playHaptic(CONTINUOUS_HE_JSON, -1);
                }
                isPlaying = true;
                Log.d(TAG, "Started RichTap loop session");
            }
            
            // Adjust loop parameters: sendLoopParameter(amplitude, interval, freq)
            // freq is delta from original. Base is 50. Target is hapticFrequency (0-100).
            int deltaFreq = hapticFrequency - 50;
            RichTapUtils.getInstance().sendLoopParameter(amplitude, 0, deltaFreq);
            
            lastAmplitude = amplitude;
            lastSubmitMs = SystemClock.elapsedRealtime();
        } catch (Exception e) {
            Log.w(TAG, "Failed to submit RichTap dynamics", e);
            stopHapticsInternal();
        }
    }

    public synchronized void stopHaptics() {
        stopHapticsInternal();
        decayedState = 0f;
        peakTracker = EPSILON;
    }

    private void stopHapticsInternal() {
        if (!isPlaying) return;
        
        try {
            RichTapUtils.getInstance().stop();
            Log.d(TAG, "Stopped RichTap haptics");
        } catch (Exception e) {
            Log.w(TAG, "Failed to stop RichTap dynamics", e);
        }

        isPlaying = false;
        lastAmplitude = -1;
        lastSubmitMs = 0L;
        lastNonZeroAmplitudeMs = 0L;
    }

    public void quit() {
        RichTapUtils.getInstance().quit();
    }

    private static String dumpAssetToDataStorage(Context context, String filename) {
        File destFile = new File(context.getFilesDir(), filename);
        try {
            if (!destFile.exists()) {
                InputStream input = context.getAssets().open(filename);
                FileOutputStream output = new FileOutputStream(destFile);
                byte[] buffer = new byte[1024];
                int length;
                while ((length = input.read(buffer)) > 0) {
                    output.write(buffer, 0, length);
                }
                output.flush();
                output.close();
                input.close();
            }
            return destFile.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "Failed to dump asset: " + filename, e);
            return null;
        }
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
