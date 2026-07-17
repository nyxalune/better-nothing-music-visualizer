package com.better.nothing.music.vizualizer.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

public class VisualizerOverlayView extends View {
    private float[] mMagnitudes;
    private final Paint mPaint = new Paint();
    private static final int NUM_BARS = 16;
    private final float[] mSmoothedMagnitudes = new float[NUM_BARS];
    private int mColor = Color.WHITE;
    private float mSensitivity = 1.0f;

    public VisualizerOverlayView(Context context) {
        super(context);
        mPaint.setColor(mColor);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setAntiAlias(true);
    }

    public void setColor(int color) {
        this.mColor = color;
        mPaint.setColor(color);
        invalidate();
    }

    public void setSensitivity(float sensitivity) {
        this.mSensitivity = sensitivity;
    }

    public void updateMagnitudes(float[] magnitudes) {
        if (magnitudes == null || magnitudes.length == 0) return;
        this.mMagnitudes = magnitudes;
        
        // Logarithmic grouping for better visual representation
        float minFreq = 20f;
        float maxFreq = 12000f; // Human hearing energy mostly below here for visuals
        float sampleRate = 44100f;
        float hzPerBin = sampleRate / (2f * (magnitudes.length - 1));

        for (int i = 0; i < NUM_BARS; i++) {
            float lowFreq = (float) (minFreq * Math.pow(maxFreq / minFreq, (double) i / NUM_BARS));
            float highFreq = (float) (minFreq * Math.pow(maxFreq / minFreq, (double) (i + 1) / NUM_BARS));
            
            int binLo = Math.max(0, (int) (lowFreq / hzPerBin));
            int binHi = Math.min(magnitudes.length - 1, (int) (highFreq / hzPerBin));
            
            float sum = 0;
            int count = 0;
            for (int j = binLo; j <= binHi; j++) {
                sum += magnitudes[j];
                count++;
            }
            float avg = count > 0 ? sum / count : 0f;
            
            // Smoothing for visual stability
            float current = avg * 60.0f * mSensitivity; // Gain
            mSmoothedMagnitudes[i] = mSmoothedMagnitudes[i] * 0.7f + current * 0.3f;
        }
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mMagnitudes == null) return;

        int width = getWidth();
        int height = getHeight();
        float barWidth = (float) width / NUM_BARS;
        float spacing = 1.5f;
        float cornerRadius = 2f;

        for (int i = 0; i < NUM_BARS; i++) {
            float val = mSmoothedMagnitudes[i];
            float barHeight = val * height;
            if (barHeight > height) barHeight = height;
            if (barHeight < 1.0f) barHeight = 1.0f; // Baseline

            float left = i * barWidth + spacing;
            float top = height - barHeight;
            float right = (i + 1) * barWidth - spacing;
            float bottom = height;

            // Draw rounded bars
            canvas.drawRoundRect(left, top, right, bottom, cornerRadius, cornerRadius, mPaint);
        }
    }
}
