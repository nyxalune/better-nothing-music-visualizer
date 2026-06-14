package com.better.nothing.music.vizualizer.ui

import com.better.nothing.music.vizualizer.R
import com.better.nothing.music.vizualizer.model.HapticMode
import com.better.nothing.music.vizualizer.model.TorchMode
import com.better.nothing.music.vizualizer.model.DeviceProfile
import com.better.nothing.music.vizualizer.service.AudioCaptureService
import com.better.nothing.music.vizualizer.service.HapticsTileService
import com.better.nothing.music.vizualizer.service.VisualizerTileService

import rikka.shizuku.Shizuku
import kotlinx.coroutines.flow.collect

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.provider.Settings
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.palette.graphics.Palette
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.animation.animateColorAsState
import android.media.session.PlaybackState
import android.media.MediaMetadata
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.RequiresPermission
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.absoluteValue

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val audioManager by lazy {
        getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private val projectionManager by lazy {
        getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private var service: AudioCaptureService? = null
    private var bound = false
    private var pendingResultCode = 0
    private var pendingData: Intent? = null
    private var hasPendingToken = false
    private var pendingVisualizerStart = false
    var showProjectionInfoDialog by mutableStateOf(false)

    companion object {
        const val EXTRA_REQUEST_START = "extra_request_start"
        private const val PREF_PROJECTION_INFO_SHOWN = "projection_info_shown"
        var serviceStatic: AudioCaptureService? = null
            private set
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            refreshConnectedAudioRoute()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            refreshConnectedAudioRoute()
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val localBinder = binder as AudioCaptureService.LocalBinder
            service = localBinder.getService()
            serviceStatic = service
            bound = true
            
            applyServiceSettings()

            if (hasPendingToken) {
                deliverProjectionToken(pendingResultCode, pendingData!!)
                hasPendingToken = false
            }

            if (pendingVisualizerStart) {
                service?.startVisualizer()
                pendingVisualizerStart = false
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            serviceStatic = null
            bound = false
        }
    }

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            if (bound) {
                deliverProjectionToken(result.resultCode, result.data!!)
            } else {
                pendingResultCode = result.resultCode
                pendingData = result.data
                hasPendingToken = true
                val intent = Intent(this, AudioCaptureService::class.java)
                bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            }
        } else {
            Toast.makeText(this, getString(R.string.screen_capture_denied), Toast.LENGTH_SHORT).show()
        }
    }

    private val notificationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            requestProjection()
        } else {
            Toast.makeText(this, getString(R.string.notifications_required), Toast.LENGTH_LONG).show()
        }
    }

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            toggleVisualizer()
        } else {
            Toast.makeText(this, getString(R.string.audio_permission_required), Toast.LENGTH_SHORT).show()
        }
    }

    private val mediaSessionManager by lazy {
        getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    }
    private var activeMediaController: MediaController? = null
    private val mediaCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            val artwork = getArtworkBitmap(metadata)
            viewModel.setMusicArtwork(artwork)
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            // Not strictly needed for artwork but useful if we wanted to track play/pause
        }
    }

    private val sessionsChangedListener = MediaSessionManager.OnActiveSessionsChangedListener { 
        updateActiveMediaController()
    }

    private fun updateActiveMediaController() {
        try {
            val controllers = mediaSessionManager.getActiveSessions(
                ComponentName(this, com.better.nothing.music.vizualizer.service.MusicNotificationListener::class.java)
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
            Log.w("MainActivity", "No notification access to get media sessions")
        }
    }

    private fun getArtworkBitmap(metadata: MediaMetadata?): Bitmap? {
        if (metadata == null) return null
        return try {
            metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        } catch (e: Exception) {
            null
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            viewModel.setOverlayEnabled(true)
        } else {
            Toast.makeText(this, getString(R.string.overlay_permission_denied), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val intent = Intent(this, AudioCaptureService::class.java)
        startForegroundService(intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

        audioManager.registerAudioDeviceCallback(audioDeviceCallback, mainHandler)

        if (isNotificationServiceEnabled()) {
            mediaSessionManager.addOnActiveSessionsChangedListener(
                sessionsChangedListener,
                ComponentName(this, com.better.nothing.music.vizualizer.service.MusicNotificationListener::class.java)
            )
            updateActiveMediaController()
        }

        setContent {
            val isRunning by viewModel.runningState.collectAsStateWithLifecycle()
            val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
            val selectedDevice by viewModel.selectedDevice.collectAsStateWithLifecycle()
            val latencyMs by viewModel.latencyMs.collectAsStateWithLifecycle()
            val latencyPresets by viewModel.latencyPresets.collectAsStateWithLifecycle()
            val autoDeviceMemorize by viewModel.autoDeviceMemorize.collectAsStateWithLifecycle()
            val gammaValue by viewModel.gammaValue.collectAsStateWithLifecycle()
            val spectrumGain by viewModel.spectrumGain.collectAsStateWithLifecycle()
            val maxBrightness by viewModel.maxBrightness.collectAsStateWithLifecycle()
            val captureSource by viewModel.captureSource.collectAsStateWithLifecycle()
            val presetInfos by viewModel.presetInfos.collectAsStateWithLifecycle()
            val selectedPreset by viewModel.selectedPreset.collectAsStateWithLifecycle()

            val hapticMotorEnabled by viewModel.hapticMotorEnabled.collectAsStateWithLifecycle()
            val hapticMode by viewModel.hapticMode.collectAsStateWithLifecycle()
            val hapticFreqMin by viewModel.hapticFreqMin.collectAsStateWithLifecycle()
            val hapticFreqMax by viewModel.hapticFreqMax.collectAsStateWithLifecycle()
            val hapticMultiplier by viewModel.hapticMultiplier.collectAsStateWithLifecycle()
            val hapticAudioGain by viewModel.hapticAudioGain.collectAsStateWithLifecycle()
            val hapticGamma by viewModel.hapticGamma.collectAsStateWithLifecycle()
            val hapticBeatSensitivity by viewModel.hapticBeatSensitivity.collectAsStateWithLifecycle()
            val hapticBeatGamma by viewModel.hapticBeatGamma.collectAsStateWithLifecycle()
            val hapticAmplitude by viewModel.hapticAmplitude.collectAsStateWithLifecycle()

            val flashlightEnabled by viewModel.flashlightEnabled.collectAsStateWithLifecycle()
            val flashlightMode by viewModel.flashlightMode.collectAsStateWithLifecycle()
            val flashlightFreqMin by viewModel.flashlightFreqMin.collectAsStateWithLifecycle()
            val flashlightFreqMax by viewModel.flashlightFreqMax.collectAsStateWithLifecycle()
            val flashlightThreshold by viewModel.flashlightThreshold.collectAsStateWithLifecycle()
            val flashlightSpeedMs by viewModel.flashlightSpeedMs.collectAsStateWithLifecycle()
            val flashlightBeatSensitivity by viewModel.flashlightBeatSensitivity.collectAsStateWithLifecycle()
            val flashlightIntensityLevels by viewModel.flashlightIntensityLevels.collectAsStateWithLifecycle()
            val flashlightAmplitude by viewModel.flashlightAmplitude.collectAsStateWithLifecycle()

            val idleBreathingEnabled by viewModel.idleBreathingEnabled.collectAsStateWithLifecycle()
            val idlePattern by viewModel.idlePattern.collectAsStateWithLifecycle()
            val notificationFlashEnabled by viewModel.notificationFlashEnabled.collectAsStateWithLifecycle()
            val strobeEnabled by viewModel.strobeEnabled.collectAsStateWithLifecycle()
            val disableGlyphsWhenSilent by viewModel.disableGlyphsWhenSilent.collectAsStateWithLifecycle()
            val overlayEnabled by viewModel.overlayEnabled.collectAsStateWithLifecycle()

            val isBeatDetected by viewModel.isBeatDetected.collectAsStateWithLifecycle()
            val isFlashlightBeatDetected by viewModel.isFlashlightBeatDetected.collectAsStateWithLifecycle()

            val uiAmplitude by viewModel.uiAmplitude.collectAsStateWithLifecycle()
            val musicThemeColor by viewModel.musicThemeColor.collectAsStateWithLifecycle()

            LaunchedEffect(viewModel.userId) {
                viewModel.syncStats()
            }

            BetterVizApp(
                viewModel = viewModel,
                selectedTab = selectedTab,
                onTabSelected = { viewModel.selectTab(it) },
                isRunning = isRunning,
                selectedDevice = selectedDevice,
                onDeviceChanged = { viewModel.setSpoofedDevice(it) },
                latencyPresets = latencyPresets,
                onLatencyChanged = { onLatencyChanged(it) },
                spectrumGain = spectrumGain,
                onSpectrumGainChanged = { onSpectrumGainChanged(it) },
                maxBrightness = maxBrightness,
                onMaxBrightnessChanged = { onMaxBrightnessChanged(it) },
                presets = presetInfos,
                selectedPreset = selectedPreset,
                onPresetSelected = { onPresetSelected(it) },
                onToggleVisualizer = { toggleVisualizer() },
                onAutoDeviceToggle = { onAutoDeviceToggle(it) },
                hapticMotorEnabled = hapticMotorEnabled,
                onHapticMotorEnabledChanged = { onHapticMotorEnabledChanged(it) },
                hapticMode = hapticMode,
                onHapticModeChanged = { onHapticModeChanged(it) },
                hapticFreqMin = hapticFreqMin,
                hapticFreqMax = hapticFreqMax,
                onHapticFreqRangeChanged = { min, max -> onHapticFreqRangeChanged(min, max) },
                hapticMultiplier = hapticMultiplier,
                onHapticMultiplierChanged = { onHapticMultiplierChanged(it) },
                hapticAudioGain = hapticAudioGain,
                onHapticAudioGainChanged = { onHapticAudioGainChanged(it) },
                hapticGamma = hapticGamma,
                onHapticGammaChanged = { onHapticGammaChanged(it) },
                hapticBeatSensitivity = hapticBeatSensitivity,
                onHapticBeatSensitivityChanged = { onHapticBeatSensitivityChanged(it) },
                hapticBeatGamma = hapticBeatGamma,
                onHapticBeatGammaChanged = { onHapticBeatGammaChanged(it) },
                hapticAmplitudeProvider = { hapticAmplitude },
                isBeatDetectedProvider = { isBeatDetected },
                flashlightEnabled = flashlightEnabled,
                onFlashlightEnabledChanged = { onFlashlightEnabledChanged(it) },
                flashlightMode = flashlightMode,
                onFlashlightModeChanged = { onFlashlightModeChanged(it) },
                flashlightFreqMin = flashlightFreqMin,
                flashlightFreqMax = flashlightFreqMax,
                onFlashlightFreqRangeChanged = { min, max -> onFlashlightFreqRangeChanged(min, max) },
                flashlightThreshold = flashlightThreshold,
                onFlashlightThresholdChanged = { onFlashlightThresholdChanged(it) },
                flashlightSpeedMs = flashlightSpeedMs,
                onFlashlightSpeedMsChanged = { onFlashlightSpeedMsChanged(it) },
                flashlightBeatSensitivity = flashlightBeatSensitivity,
                onFlashlightBeatSensitivityChanged = { onFlashlightBeatSensitivityChanged(it) },
                flashlightIntensityLevels = flashlightIntensityLevels,
                flashlightAmplitudeProvider = { flashlightAmplitude },
                isFlashlightBeatDetectedProvider = { isFlashlightBeatDetected },
                idleBreathingEnabled = idleBreathingEnabled,
                onIdleBreathingEnabledChanged = { onIdleBreathingEnabledChanged(it) },
                idlePattern = idlePattern,
                onIdlePatternChanged = { onIdlePatternChanged(it) },
                notificationFlashEnabled = notificationFlashEnabled,
                onNotificationFlashEnabledChanged = { onNotificationFlashEnabledChanged(it) },
                strobeEnabled = strobeEnabled,
                onStrobeEnabledChanged = { onStrobeEnabledChanged(it) },
                disableGlyphsWhenSilent = disableGlyphsWhenSilent,
                onDisableGlyphsWhenSilentChanged = { onDisableGlyphsWhenSilentChanged(it) },
                overlayEnabled = overlayEnabled,
                onOverlayEnabledChanged = { onOverlayEnabledChanged(it) },
                uiAmplitudeProvider = { uiAmplitude },
                musicThemeColor = musicThemeColor
            )

            if (showProjectionInfoDialog) {
                MediaProjectionInfoDialog(
                    onConfirm = {
                        showProjectionInfoDialog = false
                        markProjectionInfoShown()
                        launchProjection()
                    },
                    onDismiss = { showProjectionInfoDialog = false }
                )
            }
        }

        handleLaunchIntent(intent)
    }

    private fun initDatabase() {
        try {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true)
        } catch (e: Exception) {
            Log.w("MainActivity", "Firebase persistence already enabled or error: ${e.message}")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLaunchIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        refreshConnectedAudioRoute()
        
        lifecycleScope.launch {
            service?.isRunningFlow?.collect { running ->
                viewModel.setRunning(running)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // We keep the service bound while in BG to keep visualizer running
    }

    override fun onResume() {
        super.onResume()
        if (isNotificationServiceEnabled()) {
            updateActiveMediaController()
        }
        
        // Ensure UI state matches service state
        service?.let {
            viewModel.setRunning(it.isVisualizerRunning())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        if (bound) {
            unbindService(serviceConnection)
            bound = false
        }
        mediaSessionManager.removeOnActiveSessionsChangedListener(sessionsChangedListener)
    }

    // ── Callbacks ─────────────────────────────────────────────────────────────

    private fun onLatencyChanged(value: Int) {
        viewModel.setLatencyMs(value)
    }

    private fun onAutoDeviceToggle(enabled: Boolean) {
        viewModel.setAutoDeviceMemorize(enabled)
    }

    private fun onGammaChanged(value: Float) {
        viewModel.setGammaValue(value)
        viewModel.persistGamma(value)
    }

    private fun onSpectrumGainChanged(value: Float) {
        viewModel.setSpectrumGain(value)
    }

    private fun onMaxBrightnessChanged(value: Int) {
        viewModel.setMaxBrightness(value)
    }

    private fun onHapticMotorEnabledChanged(enabled: Boolean) {
        viewModel.setHapticMotorEnabled(enabled)
    }

    private fun onHapticModeChanged(mode: HapticMode) {
        viewModel.setHapticMode(mode)
        service?.setHapticMode(mode)
    }

    private fun onHapticFreqRangeChanged(min: Float, max: Float) {
        viewModel.setHapticFreqRange(min, max)
    }

    private fun onHapticMultiplierChanged(value: Float) {
        viewModel.setHapticMultiplier(value)
    }

    private fun onHapticAudioGainChanged(value: Float) {
        viewModel.setHapticAudioGain(value)
    }

    private fun onHapticGammaChanged(value: Float) {
        viewModel.setHapticGamma(value)
    }

    private fun onHapticBeatSensitivityChanged(value: Float) {
        viewModel.setHapticBeatSensitivity(value)
    }

    private fun onHapticBeatGammaChanged(value: Float) {
        viewModel.setHapticBeatGamma(value)
    }

    private fun onFlashlightEnabledChanged(enabled: Boolean) {
        viewModel.setFlashlightEnabled(enabled)
    }

    private fun onFlashlightModeChanged(mode: TorchMode) {
        viewModel.setFlashlightMode(mode)
    }

    private fun onFlashlightFreqRangeChanged(min: Float, max: Float) {
        viewModel.setFlashlightFreqRange(min, max)
    }

    private fun onFlashlightThresholdChanged(value: Float) {
        viewModel.setFlashlightThreshold(value)
    }

    private fun onFlashlightSpeedMsChanged(value: Float) {
        viewModel.setFlashlightSpeedMs(value)
    }

    private fun onFlashlightBeatSensitivityChanged(value: Float) {
        viewModel.setFlashlightBeatSensitivity(value)
    }

    private fun onIdleBreathingEnabledChanged(enabled: Boolean) {
        viewModel.setIdleBreathingEnabled(enabled)
        service?.setIdleBreathingEnabled(enabled)
    }

    private fun onIdlePatternChanged(pattern: String) {
        viewModel.setIdlePattern(pattern)
        service?.setIdlePattern(pattern)
    }

    private fun onNotificationFlashEnabledChanged(enabled: Boolean) {
        if (enabled && !isNotificationServiceEnabled()) {
            val message = getString(R.string.notification_access_required)
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
            return
        }
        viewModel.setNotificationFlashEnabled(enabled)
        service?.setNotificationFlashEnabled(enabled)
    }

    private fun onStrobeEnabledChanged(enabled: Boolean) {
        viewModel.setStrobeEnabled(enabled)
        service?.setStrobeEnabled(enabled)
    }

    private fun onDisableGlyphsWhenSilentChanged(enabled: Boolean) {
        viewModel.setDisableGlyphsWhenSilent(enabled)
        service?.setDisableGlyphsWhenSilent(enabled)
    }

    private fun onOverlayEnabledChanged(enabled: Boolean) {
        if (enabled && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
            return
        }
        viewModel.setOverlayEnabled(enabled)
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val pkgName = packageName
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        if (flat != null) {
            val names = flat.split(":")
            for (name in names) {
                val cn = ComponentName.unflattenFromString(name)
                if (cn != null && cn.packageName == pkgName) return true
            }
        }
        return false
    }

    private fun onPresetSelected(preset: String) {
        viewModel.setSelectedPreset(preset)
    }

    // ── Logic ─────────────────────────────────────────────────────────────────

    private fun toggleVisualizer() {
        val s = service ?: return
        if (s.isVisualizerRunning()) {
            s.stopVisualizer()
        } else {
            val source = viewModel.captureSource.value
            when (source) {
                AudioCaptureService.CaptureSource.INTERNAL -> {
                    if (shouldShowProjectionInfo()) {
                        showProjectionInfoDialog = true
                    } else {
                        launchProjection()
                    }
                }
                AudioCaptureService.CaptureSource.MICROPHONE -> {
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        s.startVisualizer()
                    } else {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
                AudioCaptureService.CaptureSource.VIZUALIZER -> s.startVisualizer()
                AudioCaptureService.CaptureSource.SHIZUKU -> startShizukuVisualizer()
            }
        }
    }

    private fun startShizukuVisualizer() {
        val s = service ?: return
        try {
            if (Shizuku.isPreV11()) return
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                s.startVisualizer()
            } else if (Shizuku.shouldShowRequestPermissionRationale()) {
                Toast.makeText(this, getString(R.string.shizuku_permission_required), Toast.LENGTH_LONG).show()
            } else {
                Shizuku.requestPermission(1001)
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.shizuku_not_running), Toast.LENGTH_LONG).show()
        }
    }

    private fun startStandardVisualizer() {
        val s = service ?: return
        if (s.isVisualizerRunning()) return
        
        val source = viewModel.captureSource.value
        if (source == AudioCaptureService.CaptureSource.INTERNAL) {
            if (shouldShowProjectionInfo()) {
                showProjectionInfoDialog = true
            } else {
                launchProjection()
            }
        } else if (source == AudioCaptureService.CaptureSource.MICROPHONE) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                s.startVisualizer()
            } else {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        } else {
            s.startVisualizer()
        }
    }

    private fun requestProjection() {
        val mgr = getSystemService(Context.MEDIA_PROJECTION_MANAGER_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(mgr.createScreenCaptureIntent())
    }

    private fun launchProjection() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        requestProjection()
    }

    private fun deliverProjectionToken(resultCode: Int, data: Intent) {
        val s = service
        if (s != null) {
            s.setMediaProjectionToken(resultCode, data)
            s.startVisualizer()
        } else {
            pendingResultCode = resultCode
            pendingData = data
            hasPendingToken = true
            pendingVisualizerStart = true
            val intent = Intent(this, AudioCaptureService::class.java)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun applyServiceSettings() {
        service?.let { s ->
            it.setDevice(viewModel.selectedDevice.value)
            it.setCaptureSource(viewModel.captureSource.value)
            it.setLatencyMs(viewModel.latencyMs.value)
            it.setGamma(viewModel.gammaValue.value)
            it.setSpectrumGain(viewModel.spectrumGain.value)
            it.setMaxBrightness(viewModel.maxBrightness.value)
            it.setSelectedPreset(viewModel.selectedPreset.value)
            it.setHapticMotorEnabled(viewModel.hapticMotorEnabled.value)
            it.setHapticMode(viewModel.hapticMode.value)
            it.setFlashlightEnabled(viewModel.flashlightEnabled.value)
            it.setIdleBreathingEnabled(viewModel.idleBreathingEnabled.value)
            it.setIdlePattern(viewModel.idlePattern.value)
            it.setNotificationFlashEnabled(viewModel.notificationFlashEnabled.value)
            it.setStrobeEnabled(viewModel.strobeEnabled.value)
            it.setDisableGlyphsWhenSilent(viewModel.disableGlyphsWhenSilent.value)
        }
    }

    private fun refreshConnectedAudioRoute() {
        val route = resolvePreferredAudioRoute()
        if (route != null) {
            MainActivity.serviceStatic?.setAudioRoute(route.storageKey)
            if (viewModel.autoDeviceMemorize.value) {
                viewModel.reloadLatencyForCurrentRoute()
            }
        }
    }

    private fun stopEverything() {
        service?.stopVisualizer()
    }

    private fun handleLaunchIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_REQUEST_START, false) == true) {
            startStandardVisualizer()
        }
    }

    private fun shouldShowProjectionInfo(): Boolean {
        val prefs = getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
        return !prefs.getBoolean(PREF_PROJECTION_INFO_SHOWN, false)
    }

    private fun markProjectionInfoShown() {
        val prefs = getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean(PREF_PROJECTION_INFO_SHOWN, true).apply()
    }

    private fun resolvePreferredAudioRoute(): AudioRoute? {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val best = devices.filter { isUsefulOutputRoute(it) }
            .sortedBy { 
                if (it.isBluetoothOutput()) 0 
                else if (it.isWiredOutput()) 1 
                else 2 
            }
            .firstOrNull()

        return best?.toAudioRoute()
    }
}

private fun isUsefulOutputRoute(info: AudioDeviceInfo): Boolean {
    return info.isBluetoothOutput() || info.isWiredOutput() || info.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
}

private fun AudioDeviceInfo.isBluetoothOutput(): Boolean {
    return type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
}

private fun AudioDeviceInfo.isWiredOutput(): Boolean {
    return type == AudioDeviceInfo.TYPE_WIRED_HEADSET || type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || type == AudioDeviceInfo.TYPE_USB_DEVICE
}

private fun AudioDeviceInfo.toAudioRoute(): AudioRoute {
    val name = if (type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) "Internal Speaker" else productName.toString()
    val key = if (type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) "internal" else "device_${productName.toString().hashCode()}"
    return AudioRoute(key, name)
}

val HeavyEasingSpec = tween<Float>(
    durationMillis = 600,
    easing = androidx.compose.animation.core.FastOutSlowInEasing
)

@Composable
fun MediaProjectionInfoDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mediaprojection_permission)) },
        text = {
            Text(stringResource(R.string.mediaprojection_description))
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.continue_label))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        textContentColor = MaterialTheme.colorScheme.onSurface
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BetterVizApp(
    viewModel: MainViewModel,
    selectedTab: Tab,
    onTabSelected: (Tab) -> Unit,
    isRunning: Boolean,
    selectedDevice: Int,
    onDeviceChanged: (Int) -> Unit,
    latencyPresets: List<Int>,
    onLatencyChanged: (Int) -> Unit,
    spectrumGain: Float,
    onSpectrumGainChanged: (Float) -> Unit,
    maxBrightness: Int,
    onMaxBrightnessChanged: (Int) -> Unit,
    presets: List<AudioCaptureService.PresetInfo>,
    selectedPreset: String,
    onPresetSelected: (String) -> Unit,
    onToggleVisualizer: () -> Unit,
    onAutoDeviceToggle: (Boolean) -> Unit,
    hapticMotorEnabled: Boolean,
    onHapticMotorEnabledChanged: (Boolean) -> Unit,
    hapticMode: HapticMode,
    onHapticModeChanged: (HapticMode) -> Unit,
    hapticFreqMin: Float,
    hapticFreqMax: Float,
    onHapticFreqRangeChanged: (Float, Float) -> Unit,
    hapticMultiplier: Float,
    onHapticMultiplierChanged: (Float) -> Unit,
    hapticAudioGain: Float,
    onHapticAudioGainChanged: (Float) -> Unit,
    hapticGamma: Float,
    onHapticGammaChanged: (Float) -> Unit,
    hapticBeatSensitivity: Float,
    onHapticBeatSensitivityChanged: (Float) -> Unit,
    hapticBeatGamma: Float,
    onHapticBeatGammaChanged: (Float) -> Unit,
    hapticAmplitudeProvider: () -> Float,
    isBeatDetectedProvider: () -> Boolean,
    flashlightEnabled: Boolean,
    onFlashlightEnabledChanged: (Boolean) -> Unit,
    flashlightMode: TorchMode,
    onFlashlightModeChanged: (TorchMode) -> Unit,
    flashlightFreqMin: Float,
    flashlightFreqMax: Float,
    onFlashlightFreqRangeChanged: (Float, Float) -> Unit,
    flashlightThreshold: Float,
    onFlashlightThresholdChanged: (Float) -> Unit,
    flashlightSpeedMs: Float,
    onFlashlightSpeedMsChanged: (Float) -> Unit,
    flashlightBeatSensitivity: Float,
    onFlashlightBeatSensitivityChanged: (Float) -> Unit,
    flashlightIntensityLevels: Int,
    flashlightAmplitudeProvider: () -> Float,
    isFlashlightBeatDetectedProvider: () -> Boolean,
    idleBreathingEnabled: Boolean,
    onIdleBreathingEnabledChanged: (Boolean) -> Unit,
    idlePattern: String,
    onIdlePatternChanged: (String) -> Unit,
    notificationFlashEnabled: Boolean,
    onNotificationFlashEnabledChanged: (Boolean) -> Unit,
    strobeEnabled: Boolean,
    onStrobeEnabledChanged: (Boolean) -> Unit,
    disableGlyphsWhenSilent: Boolean,
    onDisableGlyphsWhenSilentChanged: (Boolean) -> Unit,
    overlayEnabled: Boolean,
    onOverlayEnabledChanged: (Boolean) -> Unit,
    uiAmplitudeProvider: () -> Float,
    musicThemeColor: Color
) {
    val pagerState = rememberPagerState(initialPage = selectedTab.ordinal) { Tab.entries.size }
    val scope = rememberCoroutineScope()

    LaunchedEffect(selectedTab) {
        if (pagerState.currentPage != selectedTab.ordinal) {
            pagerState.animateScrollToPage(selectedTab.ordinal)
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        onTabSelected(Tab.entries[pagerState.currentPage])
    }

    CompositionLocalProvider(LocalUIAmplitude provides uiAmplitudeProvider) {
        Scaffold(
            bottomBar = {
                NativeBottomBar(
                    selectedTab = selectedTab,
                    visibleTabs = Tab.entries.toList(),
                    onTabSelected = onTabSelected
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize()
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                    userScrollEnabled = true
                ) { page ->
                    val tab = Tab.entries[page]
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                                val absOffset = pageOffset.absoluteValue
                                alpha = 1f - absOffset.coerceIn(0f, 1f)
                                translationX = pageOffset * size.width * 0.1f
                            }
                    ) {
                        when (tab) {
                            Tab.Audio -> {
                                val captureSource by viewModel.captureSource.collectAsStateWithLifecycle()
                                val autoDeviceMemorize by viewModel.autoDeviceMemorize.collectAsStateWithLifecycle()
                                val latencyMs by viewModel.latencyMs.collectAsStateWithLifecycle()
                                val latencyPresets by viewModel.latencyPresets.collectAsStateWithLifecycle()
                                val spectrumGain by viewModel.spectrumGain.collectAsStateWithLifecycle()
                                val fftData by viewModel.fftState.collectAsStateWithLifecycle()

                                AudioSetupScreen(
                                    isRunning = isRunning,
                                    onToggleVisualizer = onToggleVisualizer,
                                    captureSource = captureSource,
                                    onCaptureSourceChanged = { viewModel.setCaptureSource(it) },
                                    autoDeviceMemorize = autoDeviceMemorize,
                                    onAutoDeviceToggle = onAutoDeviceToggle,
                                    currentLatency = latencyMs,
                                    onLatencyChanged = onLatencyChanged,
                                    latencyPresets = latencyPresets,
                                    onLatencyPresetsChanged = { viewModel.updateLatencyPresets(it) },
                                    spectrumGain = spectrumGain,
                                    onSpectrumGainChanged = onSpectrumGainChanged,
                                    fftData = fftData
                                )
                            }
                            Tab.Glyphs -> {
                                val gammaValue by viewModel.gammaValue.collectAsStateWithLifecycle()
                                val maxBrightness by viewModel.maxBrightness.collectAsStateWithLifecycle()
                                val presets by viewModel.presetInfos.collectAsStateWithLifecycle()
                                val selectedPreset by viewModel.selectedPreset.collectAsStateWithLifecycle()

                                GlyphsScreen(
                                    gammaValue = gammaValue,
                                    onGammaChanged = onGammaChanged,
                                    maxBrightness = maxBrightness,
                                    onMaxBrightnessChanged = onMaxBrightnessChanged,
                                    presets = presets,
                                    selectedPreset = selectedPreset,
                                    onPresetSelected = onPresetSelected,
                                    isRunning = isRunning,
                                    selectedDevice = selectedDevice,
                                    viewModel = viewModel
                                )
                            }
                            Tab.Haptics -> {
                                HapticsScreen(
                                    hapticMotorEnabled = hapticMotorEnabled,
                                    onHapticMotorEnabledChanged = onHapticMotorEnabledChanged,
                                    hapticMode = hapticMode,
                                    onHapticModeChanged = onHapticModeChanged,
                                    hapticFreqMin = hapticFreqMin,
                                    hapticFreqMax = hapticFreqMax,
                                    onHapticFreqRangeChanged = onHapticFreqRangeChanged,
                                    hapticMultiplier = hapticMultiplier,
                                    onHapticMultiplierChanged = onHapticMultiplierChanged,
                                    hapticAudioGain = hapticAudioGain,
                                    onHapticAudioGainChanged = onHapticAudioGainChanged,
                                    hapticGamma = hapticGamma,
                                    onHapticGammaChanged = onHapticGammaChanged,
                                    hapticBeatSensitivity = hapticBeatSensitivity,
                                    onHapticBeatSensitivityChanged = onHapticBeatSensitivityChanged,
                                    hapticBeatGamma = hapticBeatGamma,
                                    onHapticBeatGammaChanged = onHapticBeatGammaChanged,
                                    hapticAmplitudeProvider = hapticAmplitudeProvider,
                                    isBeatDetectedProvider = isBeatDetectedProvider
                                )
                            }
                            Tab.Flashlight -> {
                                FlashlightScreen(
                                    flashlightEnabled = flashlightEnabled,
                                    onFlashlightEnabledChanged = onFlashlightEnabledChanged,
                                    flashlightMode = flashlightMode,
                                    onFlashlightModeChanged = onFlashlightModeChanged,
                                    flashlightFreqMin = flashlightFreqMin,
                                    flashlightFreqMax = flashlightFreqMax,
                                    onFlashlightFreqRangeChanged = onFlashlightFreqRangeChanged,
                                    flashlightThreshold = flashlightThreshold,
                                    onFlashlightThresholdChanged = onFlashlightThresholdChanged,
                                    flashlightSpeedMs = flashlightSpeedMs,
                                    onFlashlightSpeedMsChanged = onFlashlightSpeedMsChanged,
                                    flashlightBeatSensitivity = flashlightBeatSensitivity,
                                    onFlashlightBeatSensitivityChanged = onFlashlightBeatSensitivityChanged,
                                    flashlightIntensityLevels = flashlightIntensityLevels,
                                    flashlightAmplitudeProvider = flashlightAmplitudeProvider,
                                    isBeatDetectedProvider = isFlashlightBeatDetectedProvider
                                )
                            }
                            Tab.Settings -> {
                                SettingsScreen(
                                    viewModel = viewModel,
                                    idleBreathingEnabled = idleBreathingEnabled,
                                    onIdleBreathingEnabledChanged = onIdleBreathingEnabledChanged,
                                    idlePattern = idlePattern,
                                    onIdlePatternChanged = onIdlePatternChanged,
                                    notificationFlashEnabled = notificationFlashEnabled,
                                    onNotificationFlashEnabledChanged = onNotificationFlashEnabledChanged,
                                    strobeEnabled = strobeEnabled,
                                    onStrobeEnabledChanged = onStrobeEnabledChanged,
                                    disableGlyphsWhenSilent = disableGlyphsWhenSilent,
                                    onDisableGlyphsWhenSilentChanged = onDisableGlyphsWhenSilentChanged,
                                    overlayEnabled = overlayEnabled,
                                    onOverlayEnabledChanged = onOverlayEnabledChanged
                                )
                            }
                        }
                    }
                }

                // Overlay Modals
                val isShowingEditor by viewModel.isShowingEditor.collectAsStateWithLifecycle()
                val isShowingAbout by viewModel.isShowingAbout.collectAsStateWithLifecycle()
                val isShowingLicense by viewModel.isShowingLicense.collectAsStateWithLifecycle()
                val isShowingCommunity by viewModel.isShowingCommunity.collectAsStateWithLifecycle()
                val isShowingAnnouncementHistory by viewModel.showAnnouncementHistory.collectAsStateWithLifecycle()
                val isShowingLeaderboard by viewModel.isShowingLeaderboard.collectAsStateWithLifecycle()

                if (isShowingEditor) {
                    CustomPresetEditorScreen(
                        device = selectedDevice,
                        onDismiss = { viewModel.hideEditor() },
                        onSave = { name, json -> viewModel.saveCustomPreset(name, json) }
                    )
                }

                if (isShowingAbout) {
                    AboutScreen(onDismiss = { viewModel.hideAbout() })
                }

                if (isShowingLicense) {
                    LicenseScreen(onDismiss = { viewModel.hideLicense() })
                }

                if (isShowingCommunity) {
                    CommunityPresetsScreen(
                        viewModel = viewModel,
                        onDismiss = { viewModel.hideCommunity() }
                    )
                }

                if (isShowingAnnouncementHistory) {
                    AnnouncementHistoryScreen(
                        viewModel = viewModel,
                        onDismiss = { viewModel.hideAnnouncementHistory() }
                    )
                }

                if (isShowingLeaderboard) {
                    LeaderboardScreen(
                        viewModel = viewModel,
                        onDismiss = { viewModel.hideLeaderboard() }
                    )
                }

                val latestAnnouncement by viewModel.latestAnnouncement.collectAsStateWithLifecycle()
                val showAnnouncementModal by viewModel.showAnnouncementModal.collectAsStateWithLifecycle()
                
                if (showAnnouncementModal && latestAnnouncement != null) {
                    AnnouncementModal(
                        announcement = latestAnnouncement!!,
                        onDismiss = { viewModel.dismissAnnouncement() }
                    )
                }

                val thanksMessage by viewModel.thanksMessage.collectAsStateWithLifecycle()
                if (thanksMessage != null) {
                    ThanksModal(
                        message = thanksMessage!!,
                        onDismiss = { viewModel.dismissThanksMessage() }
                    )
                }
                
                val showAnnouncementEditor by viewModel.showAnnouncementEditor.collectAsStateWithLifecycle()
                if (showAnnouncementEditor) {
                    AnnouncementEditor(
                        onPost = { t, m, s, l, lt -> viewModel.postAnnouncement(t, m, s, l, lt) },
                        onDismiss = { viewModel.hideAnnouncementEditor() }
                    )
                }
            }
        }
    }
}

@Composable
fun ThanksModal(message: String, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "BETTER NOTHING MUSIC VISUALIZER",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "WE APPRECIATE YOUR SUPPORT.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = message.uppercase(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "YOU'RE WELCOME!",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("PROCEED")
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    )
}
