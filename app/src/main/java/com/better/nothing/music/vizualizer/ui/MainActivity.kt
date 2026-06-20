package com.better.nothing.music.vizualizer.ui

import com.better.nothing.music.vizualizer.R
import com.better.nothing.music.vizualizer.model.*
import com.better.nothing.music.vizualizer.service.AudioCaptureService
import com.better.nothing.music.vizualizer.service.HapticsTileService
import com.better.nothing.music.vizualizer.service.VisualizerTileService
import com.better.nothing.music.vizualizer.service.GlyphNotificationListener

import rikka.shizuku.Shizuku
import kotlinx.coroutines.delay

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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import com.google.firebase.database.FirebaseDatabase
import androidx.compose.runtime.remember
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
    private var showProjectionInfoDialog by mutableStateOf(false)

    private val musicThemeHandler by lazy { MusicThemeHandler(this, viewModel) }

    companion object {
        const val EXTRA_REQUEST_START = "request_start"
        const val PREF_PROJECTION_INFO_SHOWN = "projection_info_shown"
        const val PREFS_NAME = "viz_prefs"
        var serviceStatic: AudioCaptureService? = null
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

    private val projectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            deliverProjectionToken(result.resultCode, result.data!!)
        }
    }

    private val notificationLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            // Permission granted
        }
    }

    private val audioPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
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
        }
    }

    private val sessionsChangedListener = MediaSessionManager.OnActiveSessionsChangedListener { 
        updateActiveMediaController()
    }

    private fun updateActiveMediaController() {
        try {
            val controllers = mediaSessionManager.getActiveSessions(
                ComponentName(this, GlyphNotificationListener::class.java)
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

    private val overlayPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Settings.canDrawOverlays(this)) {
            viewModel.setOverlayEnabled(true)
        } else {
            Toast.makeText(this, getString(R.string.overlay_permission_denied), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        FirebaseFuckery.init()

        val intent = Intent(this, AudioCaptureService::class.java)
        startForegroundService(intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

        audioManager.registerAudioDeviceCallback(audioDeviceCallback, mainHandler)

        if (isNotificationServiceEnabled()) {
            mediaSessionManager.addOnActiveSessionsChangedListener(
                musicThemeHandler.sessionsChangedListener,
                ComponentName(this, GlyphNotificationListener::class.java)
            )
            musicThemeHandler.updateActiveMediaController()
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
            val totalVisualizedTime by viewModel.totalVisualizedTime.collectAsStateWithLifecycle()
            val shizukuUnlocked by viewModel.shizukuSourceUnlocked.collectAsStateWithLifecycle()
            val dynamicGainEnabled by viewModel.dynamicGainEnabled.collectAsStateWithLifecycle()
            val fftData by viewModel.fftState.collectAsStateWithLifecycle()

            LaunchedEffect(viewModel.userId) {
                viewModel.syncStats()
            }

            LaunchedEffect(isRunning) {
                if (isRunning) {
                    while (true) {
                        service?.getCurrentLightState()?.let {
                            viewModel.setVisualizerState(it)
                        }
                        delay(33)
                    }
                }
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
                musicThemeColor = musicThemeColor,
                totalVisualizedTime = totalVisualizedTime,
                shizukuUnlocked = shizukuUnlocked,
                dynamicGainEnabled = dynamicGainEnabled,
                connectedDeviceName = musicThemeHandler.activeMediaController?.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown",
                latencyMs = latencyMs,
                autoDeviceEnabled = autoDeviceMemorize,
                captureSource = captureSource,
                fftData = fftData,
                gammaValue = gammaValue,
                onGammaChanged = { onGammaChanged(it) }
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
    }

    private fun onLatencyChanged(value: Int) {
        viewModel.setLatencyMs(value)
    }

    private fun onAutoDeviceToggle(enabled: Boolean) {
        if (enabled && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            // request BT permission...
        }
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
    }

    private fun onIdlePatternChanged(pattern: String) {
        viewModel.setIdlePattern(pattern)
    }

    private fun onNotificationFlashEnabledChanged(enabled: Boolean) {
        viewModel.setNotificationFlashEnabled(enabled)
    }

    private fun onStrobeEnabledChanged(enabled: Boolean) {
        viewModel.setStrobeEnabled(enabled)
    }

    private fun onDisableGlyphsWhenSilentChanged(enabled: Boolean) {
        viewModel.setDisableGlyphsWhenSilent(enabled)
    }

    private fun onOverlayEnabledChanged(enabled: Boolean) {
        if (enabled && !Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            overlayPermissionLauncher.launch(intent)
        } else {
            viewModel.setOverlayEnabled(enabled)
        }
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
                AudioCaptureService.CaptureSource.MIC -> {
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
        if (viewModel.captureSource.value == AudioCaptureService.CaptureSource.INTERNAL) {
            requestProjection()
        } else {
            s.startVisualizer()
        }
    }

    private fun requestProjection() {
        if (shouldShowProjectionInfo()) {
            showProjectionInfoDialog = true
        } else {
            launchProjection()
        }
    }

    private fun launchProjection() {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun deliverProjectionToken(resultCode: Int, data: Intent) {
        val s = service
        if (s != null) {
            s.startCapture(resultCode, data)
        } else {
            pendingResultCode = resultCode
            pendingData = data
            hasPendingToken = true
            pendingVisualizerStart = true
            FirebaseFuckery.init()

        val intent = Intent(this, AudioCaptureService::class.java)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun applyServiceSettings() {
        service?.let {
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
            MainActivity.serviceStatic?.setAudioRoute(route)
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
            toggleVisualizer()
        }
    }

    private fun shouldShowProjectionInfo(): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return !prefs.getBoolean(PREF_PROJECTION_INFO_SHOWN, false)
    }

    private fun markProjectionInfoShown() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(PREF_PROJECTION_INFO_SHOWN, true).apply()
    }

    private fun resolvePreferredAudioRoute(): AudioRoute? {
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        var preferred: AudioDeviceInfo? = null
        for (device in outputs) {
            if (device.isBluetoothOutput()) {
                preferred = device
                break
            }
        }
        if (preferred == null) {
            for (device in outputs) {
                if (device.isWiredOutput()) {
                    preferred = device
                    break
                }
            }
        }
        if (preferred == null) {
            for (device in outputs) {
                if (device.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                    preferred = device
                    break
                }
            }
        }
        return preferred?.toAudioRoute()
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(serviceConnection)
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        musicThemeHandler.onDestroy()
        if (isNotificationServiceEnabled()) {
            mediaSessionManager.removeOnActiveSessionsChangedListener(musicThemeHandler.sessionsChangedListener)
        }
    }
}

fun isUsefulOutputRoute(device: AudioDeviceInfo): Boolean {
    return device.isBluetoothOutput() || device.isWiredOutput() || device.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
}

fun AudioDeviceInfo.isBluetoothOutput(): Boolean {
    return type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_BLE_SPEAKER || type == AudioDeviceInfo.TYPE_BLE_BROADCAST
}

fun AudioDeviceInfo.isWiredOutput(): Boolean {
    return type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || type == AudioDeviceInfo.TYPE_WIRED_HEADSET || type == AudioDeviceInfo.TYPE_USB_HEADSET
}

fun AudioDeviceInfo.toAudioRoute(): AudioRoute {
    val name = if (type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) "Internal Speaker" else productName.toString()
    return AudioRoute(type.toString() + "_" + name, name)
}

val HeavyEasingSpec = tween<Float>(durationMillis = 600)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BetterVizApp(
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
    musicThemeColor: Color,
    totalVisualizedTime: Long,
    shizukuUnlocked: Boolean,
    dynamicGainEnabled: Boolean,
    connectedDeviceName: String,
    latencyMs: Int,
    autoDeviceEnabled: Boolean,
    captureSource: AudioCaptureService.CaptureSource,
    fftData: FloatArray,
    gammaValue: Float,
    onGammaChanged: (Float) -> Unit
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
                    when (tab) {
                        Tab.Audio -> {
                            AudioScreen(
                                isRunning = isRunning,
                                sessionDuration = totalVisualizedTime,
                                latencyMs = latencyMs,
                                onLatencyChanged = { onLatencyChanged(it) },
                                latencyPresets = latencyPresets,
                                onLatencyPresetsChanged = { viewModel.updateLatencyPresets(it) },
                                autoDeviceEnabled = autoDeviceEnabled,
                                onAutoDeviceToggle = { onAutoDeviceToggle(it) },
                                connectedDeviceName = connectedDeviceName,
                                fftData = fftData,
                                captureSource = captureSource,
                                onCaptureSourceChanged = { viewModel.setCaptureSource(it) },
                                shizukuUnlocked = shizukuUnlocked,
                                dynamicGainEnabled = dynamicGainEnabled,
                                onDynamicGainToggle = { viewModel.setDynamicGainEnabled(it) }
                            )
                        }
                        Tab.Glyphs -> {
                            GlyphsScreen(
                                gammaValue = gammaValue,
                                onGammaChanged = onGammaChanged,
                                maxBrightness = maxBrightness,
                                onMaxBrightnessChanged = { onMaxBrightnessChanged(it) },
                                presets = presets,
                                selectedPreset = selectedPreset,
                                onPresetSelected = { onPresetSelected(it) },
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

                // Overlays
                MainOverlays(viewModel = viewModel, selectedDevice = selectedDevice)
                CommunityOverlays(viewModel = viewModel)
            }
        }
    }
}

