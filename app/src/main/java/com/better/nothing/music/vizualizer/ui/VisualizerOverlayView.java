package com.better.nothing.music.vizualizer.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

public class VisualizerOverlayView extends View {
    private float[] mMagnitudes;
    private final Paint mPaint = new Paint();
    private final int mBarColor = Color.WHITE;
    private static final int NUM_BARS = 32;

    public VisualizerOverlayView(Context context) {
        super(context);
        mPaint.setColor(mBarColor);
        mPaint.setStyle(Paint.Style.FILL);
    }

    public void updateMagnitudes(float[] magnitudes) {
        this.mMagnitudes = magnitudes;
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mMagnitudes == null || mMagnitudes.length == 0) return;

        int width = getWidth();
        int height = getHeight();
        float barWidth = (float) width / NUM_BARS;
        float spacing = 2f;

        // Group FFT bins into bars
        int binsPerBar = mMagnitudes.length / NUM_BARS;
        for (int i = 0; i < NUM_BARS; i++) {
            float sum = 0;
            for (int j = 0; j < binsPerBar; j++) {
                sum += mMagnitudes[i * binsPerBar + j];
            }
            float avg = sum / binsPerBar;
            
            // Scaled magnitude for display
            float barHeight = avg * height * 5.0f; // Multiplier for visibility
            if (barHeight > height) barHeight = height;
            if (barHeight < 2f) barHeight = 2f; // Minimum visibility

            float left = i * barWidth + spacing;
            float top = height - barHeight;
            float right = (i + 1) * barWidth - spacing;
            float bottom = height;

            canvas.drawRect(left, top, right, bottom, mPaint);
        }
    }
}
