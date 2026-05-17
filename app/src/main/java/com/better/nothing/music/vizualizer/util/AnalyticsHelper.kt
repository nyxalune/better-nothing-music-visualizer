package com.better.nothing.music.vizualizer.util

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

class AnalyticsHelper(context: Context) {
    private val firebaseAnalytics = FirebaseAnalytics.getInstance(context)

    fun logEvent(name: String, params: Bundle? = null) {
        firebaseAnalytics.logEvent(name, params)
    }

    fun logPresetSelected(presetKey: String, isCustom: Boolean) {
        val bundle = Bundle().apply {
            putString("preset_key", if (isCustom) "community_preset" else presetKey)
            putBoolean("is_custom", isCustom)
        }
        logEvent("preset_selected", bundle)
    }

    fun logPresetFavorited(presetKey: String, isFavorited: Boolean, isCustom: Boolean) {
        val bundle = Bundle().apply {
            putString("preset_key", if (isCustom) "community_preset" else presetKey)
            putBoolean("is_favorited", isFavorited)
            putBoolean("is_custom", isCustom)
            // Log raw key for internal tracking if needed, but Firebase also needs parameters
            putString(FirebaseAnalytics.Param.ITEM_ID, presetKey)
            putString(FirebaseAnalytics.Param.CONTENT_TYPE, if (isCustom) "custom_preset" else "default_preset")
        }
        logEvent("preset_favorited", bundle)
    }

    fun logCommunityPresetDownloaded(presetName: String, author: String) {
        val bundle = Bundle().apply {
            putString("preset_name", "community_preset")
        }
        logEvent("community_preset_downloaded", bundle)
    }

    fun logPresetShared(presetName: String) {
        val bundle = Bundle().apply {
            putString("preset_name", "community_preset")
        }
        logEvent("preset_shared", bundle)
    }
    
    fun logVisualizerStarted(isRunning: Boolean) {
        val bundle = Bundle().apply {
            putBoolean("is_running", isRunning)
        }
        logEvent("visualizer_toggle", bundle)
    }

    fun logCaptureSourceChanged(source: String) {
        val bundle = Bundle().apply {
            putString("source", source)
        }
        logEvent("capture_source_changed", bundle)
    }
}
