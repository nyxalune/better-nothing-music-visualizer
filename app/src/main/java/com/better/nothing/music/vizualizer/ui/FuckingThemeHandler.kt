package com.better.nothing.music.vizualizer.ui

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.util.Log
import com.better.nothing.music.vizualizer.service.GlyphNotificationListener

internal class MusicThemeHandler(
    private val context: Context,
    private val viewModel: MainViewModel
) {
    private val mediaSessionManager by lazy {
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    }
    
    var activeMediaController: MediaController? = null
        private set
        
    private val mediaCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            val artwork = getArtworkBitmap(metadata)
            viewModel.setMusicArtwork(artwork)
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {}
    }

    val sessionsChangedListener = MediaSessionManager.OnActiveSessionsChangedListener { 
        updateActiveMediaController()
    }

    fun updateActiveMediaController() {
        try {
            val controllers = mediaSessionManager.getActiveSessions(
                ComponentName(context, GlyphNotificationListener::class.java)
            )
            val newController = controllers.firstOrNull()
            
            if (activeMediaController?.packageName != newController?.packageName) {
                activeMediaController?.unregisterCallback(mediaCallback)
                activeMediaController = newController
                activeMediaController?.registerCallback(mediaCallback)
                
                val artwork = getArtworkBitmap(activeMediaController?.metadata)
                viewModel.setMusicArtwork(artwork)
            }
        } catch (e: SecurityException) {
            Log.w("MusicThemeHandler", "No notification access to get media sessions")
        }
    }

    fun getArtworkBitmap(metadata: MediaMetadata?): Bitmap? {
        if (metadata == null) return null
        return try {
            metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        } catch (e: Exception) {
            null
        }
    }
    
    fun onDestroy() {
        activeMediaController?.unregisterCallback(mediaCallback)
    }
}
