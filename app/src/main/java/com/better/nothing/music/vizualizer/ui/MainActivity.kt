package com.better.nothing.music.vizualizer.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.projection.MediaProjectionManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.better.nothing.music.vizualizer.R
import com.better.nothing.music.vizualizer.model.HapticMode
import com.better.nothing.music.vizualizer.model.TorchMode
import com.better.nothing.music.vizualizer.service.AudioCaptureService
import com.better.nothing.music.vizualizer.service.GlyphNotificationListener
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import kotlin.math.absoluteValue
import androidx.core.content.edit
import com.better.nothing.music.vizualizer.ui.PrimaryScreens.AudioScreen
import com.better.nothing.music.vizualizer.ui.PrimaryScreens.FlashlightScreen
import com.better.nothing.music.vizualizer.ui.PrimaryScreens.GlyphsScreen
import com.better.nothing.music.vizualizer.ui.PrimaryScreens.HapticsScreen
import com.better.nothing.music.vizualizer.ui.PrimaryScreens.SettingsScreen
import androidx.compose.runtime.collectAsState

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

            lifecycleScope.launch {
                service?.isRunningFlow()?.collect { running ->
                    viewModel.setRunning(running)
                }
            }

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

    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(Exception::class.java)
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                viewModel.linkWithCredential(credential)
            } catch (e: Exception) {
                Toast.makeText(this, "Google sign in failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun launchGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        val googleSignInClient = GoogleSignIn.getClient(this, gso)
        googleSignInLauncher.launch(googleSignInClient.signInIntent)
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
            val selectedTheme by viewModel.selectedTheme.collectAsStateWithLifecycle()
            val selectedFont by viewModel.selectedFont.collectAsStateWithLifecycle()
            val musicThemeColor by viewModel.musicThemeColor.collectAsStateWithLifecycle()
            val isRunning by viewModel.runningState.collectAsStateWithLifecycle()

            LaunchedEffect(isRunning) {
                if (isRunning) {
                    while (true) {
                        service?.getCurrentLightState()?.let {
                            viewModel.setVisualizerState(it)
                        }
                        service?.getLatestMagnitudes()?.let {
                            viewModel.setFftState(it)
                        }
                        delay(33)
                    }
                } else {
                    viewModel.setFftState(floatArrayOf())
                    viewModel.setVisualizerState(floatArrayOf())
                }
            }

            BetterVizTheme(
                themeName = selectedTheme,
                fontName = selectedFont,
                musicPrimaryColor = musicThemeColor,
                uiAmplitudeProvider = { viewModel.uiAmplitude.value }
            ) {
                BetterVizApp(
                    viewModel = viewModel,
                    onToggleVisualizer = { toggleVisualizer() },
                    onGoogleSignIn = { launchGoogleSignIn() },
                    onOverlayPermissionRequest = { 
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                        overlayPermissionLauncher.launch(intent)
                    }
                )

                // Overlays
                MainOverlays(viewModel = viewModel, selectedDevice = viewModel.selectedDevice.collectAsState().value)
                CommunityOverlays(viewModel = viewModel)
            }
        }
    }

    fun onLatencyChanged(value: Int) {
        viewModel.setLatencyMs(value)
    }

    fun onAutoDeviceToggle(enabled: Boolean) {
        if (enabled && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            // request BT permission...
        }
        viewModel.setAutoDeviceMemorize(enabled)
    }

    fun onGammaChanged(value: Float) {
        viewModel.setGammaValue(value)
        viewModel.persistGamma(value)
    }

    fun onSpectrumGainChanged(value: Float) {
        viewModel.setSpectrumGain(value)
    }

    fun onMaxBrightnessChanged(value: Int) {
        viewModel.setMaxBrightness(value)
    }

    fun onHapticMotorEnabledChanged(enabled: Boolean) {
        viewModel.setHapticMotorEnabled(enabled)
    }

    fun onHapticModeChanged(mode: HapticMode) {
        viewModel.setHapticMode(mode)
    }

    fun onHapticFreqRangeChanged(min: Float, max: Float) {
        viewModel.setHapticFreqRange(min, max)
    }

    fun onHapticMultiplierChanged(value: Float) {
        viewModel.setHapticMultiplier(value)
    }

    fun onHapticAudioGainChanged(value: Float) {
        viewModel.setHapticAudioGain(value)
    }

    fun onHapticGammaChanged(value: Float) {
        viewModel.setHapticGamma(value)
    }

    fun onHapticBeatSensitivityChanged(value: Float) {
        viewModel.setHapticBeatSensitivity(value)
    }

    fun onHapticBeatGammaChanged(value: Float) {
        viewModel.setHapticBeatGamma(value)
    }

    fun onFlashlightEnabledChanged(enabled: Boolean) {
        viewModel.setFlashlightEnabled(enabled)
    }

    fun onFlashlightModeChanged(mode: TorchMode) {
        viewModel.setFlashlightMode(mode)
    }

    fun onFlashlightFreqRangeChanged(min: Float, max: Float) {
        viewModel.setFlashlightFreqRange(min, max)
    }

    fun onFlashlightThresholdChanged(value: Float) {
        viewModel.setFlashlightThreshold(value)
    }

    fun onFlashlightSpeedMsChanged(value: Float) {
        viewModel.setFlashlightSpeedMs(value)
    }

    fun onFlashlightBeatSensitivityChanged(value: Float) {
        viewModel.setFlashlightBeatSensitivity(value)
    }

    fun onIdleBreathingEnabledChanged(enabled: Boolean) {
        viewModel.setIdleBreathingEnabled(enabled)
    }

    fun onIdlePatternChanged(pattern: String) {
        viewModel.setIdlePattern(pattern)
    }

    fun onNotificationFlashEnabledChanged(enabled: Boolean) {
        viewModel.setNotificationFlashEnabled(enabled)
    }

    fun onStrobeEnabledChanged(enabled: Boolean) {
        viewModel.setStrobeEnabled(enabled)
    }

    fun onDisableGlyphsWhenSilentChanged(enabled: Boolean) {
        viewModel.setDisableGlyphsWhenSilent(enabled)
    }

    fun onOverlayEnabledChanged(enabled: Boolean) {
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

  fun onPresetSelected(preset: String) {
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
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
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
            bindService(intent, serviceConnection, BIND_AUTO_CREATE)
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
            serviceStatic?.setAudioRoute(route)
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
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        return !prefs.getBoolean(PREF_PROJECTION_INFO_SHOWN, false)
    }

    private fun markProjectionInfoShown() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit {putBoolean(PREF_PROJECTION_INFO_SHOWN, true) }
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

fun AudioDeviceInfo.isBluetoothOutput(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_BLE_SPEAKER || type == AudioDeviceInfo.TYPE_BLE_BROADCAST
    } else {
        TODO("VERSION.SDK_INT < S")
    }
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
    onToggleVisualizer: () -> Unit,
    onGoogleSignIn: () -> Unit,
    onOverlayPermissionRequest: () -> Unit
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val isRunning by viewModel.runningState.collectAsStateWithLifecycle()
    val totalVisualizedTime by viewModel.totalVisualizedTime.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val pagerState = rememberPagerState(initialPage = selectedTab.ordinal) { Tab.entries.size }

    LaunchedEffect(selectedTab) {
        if (pagerState.currentPage != selectedTab.ordinal) {
            pagerState.animateScrollToPage(selectedTab.ordinal)
        }
    }

    val haptics = LocalHapticFeedback.current
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != selectedTab.ordinal) {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
        viewModel.selectTab(Tab.entries[pagerState.currentPage])
    }

    Scaffold(
        bottomBar = {
            NativeBottomBar(
                selectedTab = selectedTab,
                visibleTabs = Tab.entries.toList(),
                onTabSelected = { viewModel.selectTab(it) }
            )
        },
        floatingActionButton = {
            StartStopButton(running = isRunning, onClick = onToggleVisualizer)
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier
                .padding(padding)
                .padding(horizontal = LocalAppSpacing.current.edge)
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                    userScrollEnabled = true
                ) { page ->
                    val tab = Tab.entries[page]
                    val pageOffset =
                        ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val fraction = 1f - pageOffset.coerceIn(0f, 1f)
                                val scale = 0.8f + (1f - 0.8f) * fraction
                                scaleX = scale
                                scaleY = scale
                                alpha = fraction * fraction
                            }
                    ) {
                        when (tab) {
                            Tab.Audio -> {
                                val latencyMs by viewModel.latencyMs.collectAsStateWithLifecycle()
                                val latencyPresets by viewModel.latencyPresets.collectAsStateWithLifecycle()
                                val autoDeviceEnabled by viewModel.autoDeviceMemorize.collectAsStateWithLifecycle()
                                val fftData by viewModel.fftState.collectAsStateWithLifecycle()
                                val captureSource by viewModel.captureSource.collectAsStateWithLifecycle()
                                val shizukuUnlocked by viewModel.shizukuSourceUnlocked.collectAsStateWithLifecycle()
                                val dynamicGainEnabled by viewModel.dynamicGainEnabled.collectAsStateWithLifecycle()

                                AudioScreen(
                                    isRunning = isRunning,
                                    sessionDuration = totalVisualizedTime,
                                    latencyMs = latencyMs,
                                    onLatencyChanged = { viewModel.setLatencyMs(it) },
                                    latencyPresets = latencyPresets,
                                    onLatencyPresetsChanged = { viewModel.updateLatencyPresets(it) },
                                    autoDeviceEnabled = autoDeviceEnabled,
                                    onAutoDeviceToggle = { viewModel.setAutoDeviceMemorize(it) },
                                    connectedDeviceName = MainActivity.serviceStatic?.getActiveAudioRouteKey()
                                        ?: "Unknown",
                                    fftData = fftData,
                                    captureSource = captureSource,
                                    onCaptureSourceChanged = { viewModel.setCaptureSource(it) },
                                    shizukuUnlocked = shizukuUnlocked
                                )
                            }
                            Tab.Glyphs -> {
                                val gammaValue by viewModel.gammaValue.collectAsStateWithLifecycle()
                                val maxBrightness by viewModel.maxBrightness.collectAsStateWithLifecycle()
                                val presets by viewModel.presetInfos.collectAsStateWithLifecycle()
                                val selectedPreset by viewModel.selectedPreset.collectAsStateWithLifecycle()
                                val selectedDevice by viewModel.selectedDevice.collectAsStateWithLifecycle()

                                GlyphsScreen(
                                    gammaValue = gammaValue,
                                    onGammaChanged = {
                                        viewModel.setGammaValue(it); viewModel.persistGamma(
                                        it
                                    )
                                    },
                                    maxBrightness = maxBrightness,
                                    onMaxBrightnessChanged = { viewModel.setMaxBrightness(it) },
                                    presets = presets,
                                    selectedPreset = selectedPreset,
                                    onPresetSelected = { viewModel.setSelectedPreset(it) },
                                    isRunning = isRunning,
                                    selectedDevice = selectedDevice,
                                    viewModel = viewModel
                                )
                            }
                            Tab.Haptics -> {
                                val hapticMotorEnabled by viewModel.hapticMotorEnabled.collectAsStateWithLifecycle()
                                val hapticMode by viewModel.hapticMode.collectAsStateWithLifecycle()
                                val hapticFreqMin by viewModel.hapticFreqMin.collectAsStateWithLifecycle()
                                val hapticFreqMax by viewModel.hapticFreqMax.collectAsStateWithLifecycle()
                                val hapticMultiplier by viewModel.hapticMultiplier.collectAsStateWithLifecycle()
                                val hapticAudioGain by viewModel.hapticAudioGain.collectAsStateWithLifecycle()
                                val hapticGamma by viewModel.hapticGamma.collectAsStateWithLifecycle()
                                val hapticBeatSensitivity by viewModel.hapticBeatSensitivity.collectAsStateWithLifecycle()
                                val hapticBeatGamma by viewModel.hapticBeatGamma.collectAsStateWithLifecycle()
                                val isBeatDetected by viewModel.isBeatDetected.collectAsStateWithLifecycle()

                                HapticsScreen(
                                    hapticMotorEnabled = hapticMotorEnabled,
                                    onHapticMotorEnabledChanged = {
                                        viewModel.setHapticMotorEnabled(
                                            it
                                        )
                                    },
                                    hapticMode = hapticMode,
                                    onHapticModeChanged = { viewModel.setHapticMode(it) },
                                    hapticFreqMin = hapticFreqMin,
                                    hapticFreqMax = hapticFreqMax,
                                    onHapticFreqRangeChanged = { min, max ->
                                        viewModel.setHapticFreqRange(
                                            min,
                                            max
                                        )
                                    },
                                    hapticMultiplier = hapticMultiplier,
                                    onHapticMultiplierChanged = { viewModel.setHapticMultiplier(it) },
                                    hapticAudioGain = hapticAudioGain,
                                    onHapticAudioGainChanged = { viewModel.setHapticAudioGain(it) },
                                    hapticGamma = hapticGamma,
                                    onHapticGammaChanged = { viewModel.setHapticGamma(it) },
                                    hapticBeatSensitivity = hapticBeatSensitivity,
                                    onHapticBeatSensitivityChanged = {
                                        viewModel.setHapticBeatSensitivity(
                                            it
                                        )
                                    },
                                    hapticBeatGamma = hapticBeatGamma,
                                    onHapticBeatGammaChanged = { viewModel.setHapticBeatGamma(it) },
                                    hapticAmplitudeProvider = { viewModel.hapticAmplitude.value },
                                    isBeatDetectedProvider = { isBeatDetected }
                                )
                            }
                            Tab.Flashlight -> {
                                val flashlightEnabled by viewModel.flashlightEnabled.collectAsStateWithLifecycle()
                                val flashlightMode by viewModel.flashlightMode.collectAsStateWithLifecycle()
                                val flashlightFreqMin by viewModel.flashlightFreqMin.collectAsStateWithLifecycle()
                                val flashlightFreqMax by viewModel.flashlightFreqMax.collectAsStateWithLifecycle()
                                val flashlightThreshold by viewModel.flashlightThreshold.collectAsStateWithLifecycle()
                                val flashlightSpeedMs by viewModel.flashlightSpeedMs.collectAsStateWithLifecycle()
                                val flashlightBeatSensitivity by viewModel.flashlightBeatSensitivity.collectAsStateWithLifecycle()
                                val flashlightIntensityLevels by viewModel.flashlightIntensityLevels.collectAsStateWithLifecycle()
                                val isFlashlightBeatDetected by viewModel.isFlashlightBeatDetected.collectAsStateWithLifecycle()

                                FlashlightScreen(
                                    flashlightEnabled = flashlightEnabled,
                                    onFlashlightEnabledChanged = { viewModel.setFlashlightEnabled(it) },
                                    flashlightMode = flashlightMode,
                                    onFlashlightModeChanged = { viewModel.setFlashlightMode(it) },
                                    flashlightFreqMin = flashlightFreqMin,
                                    flashlightFreqMax = flashlightFreqMax,
                                    onFlashlightFreqRangeChanged = { min, max ->
                                        viewModel.setFlashlightFreqRange(
                                            min,
                                            max
                                        )
                                    },
                                    flashlightThreshold = flashlightThreshold,
                                    onFlashlightThresholdChanged = {
                                        viewModel.setFlashlightThreshold(
                                            it
                                        )
                                    },
                                    flashlightSpeedMs = flashlightSpeedMs,
                                    onFlashlightSpeedMsChanged = { viewModel.setFlashlightSpeedMs(it) },
                                    flashlightBeatSensitivity = flashlightBeatSensitivity,
                                    onFlashlightBeatSensitivityChanged = {
                                        viewModel.setFlashlightBeatSensitivity(
                                            it
                                        )
                                    },
                                    flashlightIntensityLevels = flashlightIntensityLevels,
                                    flashlightAmplitudeProvider = { viewModel.flashlightAmplitude.value },
                                    isBeatDetectedProvider = { isFlashlightBeatDetected }
                                )
                            }
                            Tab.Settings -> {
                                val idleBreathingEnabled by viewModel.idleBreathingEnabled.collectAsStateWithLifecycle()
                                val idlePattern by viewModel.idlePattern.collectAsStateWithLifecycle()
                                val notificationFlashEnabled by viewModel.notificationFlashEnabled.collectAsStateWithLifecycle()
                                val strobeEnabled by viewModel.strobeEnabled.collectAsStateWithLifecycle()
                                val disableGlyphsWhenSilent by viewModel.disableGlyphsWhenSilent.collectAsStateWithLifecycle()
                                val overlayEnabled by viewModel.overlayEnabled.collectAsStateWithLifecycle()

                                SettingsScreen(
                                    viewModel = viewModel,
                                    idleBreathingEnabled = idleBreathingEnabled,
                                    onIdleBreathingEnabledChanged = {
                                        viewModel.setIdleBreathingEnabled(
                                            it
                                        )
                                    },
                                    idlePattern = idlePattern,
                                    onIdlePatternChanged = { viewModel.setIdlePattern(it) },
                                    notificationFlashEnabled = notificationFlashEnabled,
                                    onNotificationFlashEnabledChanged = {
                                        viewModel.setNotificationFlashEnabled(
                                            it
                                        )
                                    },
                                    strobeEnabled = strobeEnabled,
                                    onStrobeEnabledChanged = { viewModel.setStrobeEnabled(it) },
                                    disableGlyphsWhenSilent = disableGlyphsWhenSilent,
                                    onDisableGlyphsWhenSilentChanged = {
                                        viewModel.setDisableGlyphsWhenSilent(
                                            it
                                        )
                                    },
                                    overlayEnabled = overlayEnabled,
                                    onOverlayEnabledChanged = { enabled ->
                                        if (enabled && !Settings.canDrawOverlays(context)) {
                                            onOverlayPermissionRequest()
                                        } else {
                                            viewModel.setOverlayEnabled(enabled)
                                        }
                                    },
                                    onGoogleSignIn = onGoogleSignIn
                                )
                            }
                        }
                    }
                }
            }

            // Overlays
            MainOverlays(viewModel = viewModel, selectedDevice = viewModel.selectedDevice.collectAsState().value)
            CommunityOverlays(viewModel = viewModel)
        }
    }
}
