package com.better.nothing.music.vizualizer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.better.nothing.music.vizualizer.R
import com.better.nothing.music.vizualizer.service.AudioCaptureService
import com.better.nothing.music.vizualizer.util.PermissionTrampolineActivity

class VisualizerWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // Refresh all widgets if service state changes
        if (intent.action == "com.better.nothing.music.vizualizer.REFRESH_WIDGET") {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, VisualizerWidget::class.java))
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.visualizer_widget)

        // Source buttons
        views.setOnClickPendingIntent(R.id.btn_source_internal, createSourcePendingIntent(context, AudioCaptureService.CaptureSource.INTERNAL))
        views.setOnClickPendingIntent(R.id.btn_source_mic, createSourcePendingIntent(context, AudioCaptureService.CaptureSource.MIC))
        views.setOnClickPendingIntent(R.id.btn_source_viz, createSourcePendingIntent(context, AudioCaptureService.CaptureSource.VIZUALIZER))

        // Viz output buttons
        views.setOnClickPendingIntent(R.id.btn_viz_haptics, createActionPendingIntent(context, AudioCaptureService.ACTION_TOGGLE_HAPTICS, 10))
        views.setOnClickPendingIntent(R.id.btn_viz_glyphs, createActionPendingIntent(context, AudioCaptureService.ACTION_TOGGLE_GLYPHS, 11))
        views.setOnClickPendingIntent(R.id.btn_viz_torch, createActionPendingIntent(context, AudioCaptureService.ACTION_TOGGLE_TORCH, 12))

        // Start/Stop button
        val isRunning = AudioCaptureService.isRunning()
        val startStopIntent = if (isRunning) {
            Intent(context, AudioCaptureService::class.java).apply { action = AudioCaptureService.ACTION_STOP }
        } else {
            Intent(context, PermissionTrampolineActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }
        
        val startStopPI = if (isRunning) {
            PendingIntent.getService(context, 20, startStopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        } else {
            PendingIntent.getActivity(context, 20, startStopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        
        views.setOnClickPendingIntent(R.id.btn_start_stop, startStopPI)
        views.setImageViewResource(R.id.btn_start_stop, if (isRunning) R.drawable.ic_stop else R.drawable.ic_play)

        // Update states (simple highlighting if possible, though standard widgets are limited)
        // For simplicity, we'll just set the texts or icons.
        
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun createSourcePendingIntent(context: Context, source: AudioCaptureService.CaptureSource): PendingIntent {
        val intent = Intent(context, AudioCaptureService::class.java).apply {
            action = AudioCaptureService.ACTION_SET_SOURCE
            putExtra(AudioCaptureService.EXTRA_SOURCE, source.name)
        }
        return PendingIntent.getService(context, source.ordinal, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun createActionPendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, AudioCaptureService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
