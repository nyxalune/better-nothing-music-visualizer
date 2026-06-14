package com.better.nothing.music.vizualizer.ui

import com.better.nothing.music.vizualizer.R
import com.better.nothing.music.vizualizer.BuildConfig
import com.better.nothing.music.vizualizer.model.HapticMode
import com.better.nothing.music.vizualizer.model.TorchMode
import com.better.nothing.music.vizualizer.model.DeviceProfile
import com.better.nothing.music.vizualizer.logic.AudioProcessor
import com.better.nothing.music.vizualizer.service.AudioCaptureService
import com.better.nothing.music.vizualizer.service.HapticsTileService
import com.better.nothing.music.vizualizer.service.VisualizerTileService
import com.better.nothing.music.vizualizer.util.AnalyticsHelper
import com.better.nothing.music.vizualizer.logic.FlashlightEngine
import com.better.nothing.music.vizualizer.logic.BeatDetector
import com.better.nothing.music.vizualizer.logic.CommunityRepository
import com.better.nothing.music.vizualizer.logic.AnnouncementRepository
import com.better.nothing.music.vizualizer.logic.LeaderboardRepository
import com.better.nothing.music.vizualizer.logic.UserRepository
import com.better.nothing.music.vizualizer.model.CommunityPreset
import com.better.nothing.music.vizualizer.model.Announcement
import com.better.nothing.music.vizualizer.model.ZoneData
import com.better.nothing.music.vizualizer.model.LeaderboardEntry
import com.better.nothing.music.vizualizer.model.UserProfile
import com.better.nothing.music.vizualizer.ui.CommunityPresetsScreen
import com.better.nothing.music.vizualizer.ui.AnnouncementModal
import com.better.nothing.music.vizualizer.ui.AnnouncementHistoryScreen
import com.better.nothing.music.vizualizer.ui.LeaderboardScreen

import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.collect

import android.Manifest
import android.app.Application
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
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.animation.core.EaseOutQuart
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxWidth
import android.util.Base64
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.auth.FirebaseAuth
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
import androidx.core.content.edit
import androidx.core.content.FileProvider
import java.io.FileOutputStream
import java.io.InputStream
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.absoluteValue
import kotlin.math.sqrt
import kotlin.math.pow

enum class Tab(val label: String, val labelRes: Int) {
    Audio("Audio", R.string.tab_audio), 
    Glyphs("Glyphs", R.string.tab_glyphs), 
    Haptics("Haptics", R.string.tab_haptics), 
    Flashlight("Flashlight", R.string.tab_flashlight), 
    Settings("Settings", R.string.tab_settings);
}

private data class AudioRoute(
    val storageKey: String,
    val displayName: String,
)

internal class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val ctx = application
    private val communityRepository = CommunityRepository()
    private val announcementRepository = AnnouncementRepository()
    private val leaderboardRepository = LeaderboardRepository()
    private val userRepository = UserRepository()
    private val analytics = AnalyticsHelper(application)

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile = _userProfile.asStateFlow()

    private val _totalVisualizedTime = MutableStateFlow(0L)
    val totalVisualizedTime = _totalVisualizedTime.asStateFlow()

    private val _totalGlyphTime = MutableStateFlow(0L)
    val totalGlyphTime = _totalGlyphTime.asStateFlow()

    private val _totalHapticTime = MutableStateFlow(0L)
    val totalHapticTime = _totalHapticTime.asStateFlow()

    private val _totalFlashlightTime = MutableStateFlow(0L)
    val totalFlashlightTime = _totalFlashlightTime.asStateFlow()

    private val _userNickname = MutableStateFlow("Anonymous")
    val userNickname = _userNickname.asStateFlow()

    private val _userId = MutableStateFlow<String?>(null)
    val userId = _userId.asStateFlow()

    private var lastStatsSyncMs = 0L

    private val _isShowingLeaderboard = MutableStateFlow(false)
    val isShowingLeaderboard = _isShowingLeaderboard.asStateFlow()

    private val _devPassword = MutableStateFlow<String?>(null)

    fun verifyDeveloperPassword(input: String): Boolean {
        if (input.isBlank()) return false
        val encrypted = _devPassword.value ?: return false
        val decrypted = try {
            String(Base64.decode(encrypted, Base64.DEFAULT))
        } catch (e: Exception) {
            ""
        }
        return input == decrypted
    }

    private val _thanksMessage = MutableStateFlow<String?>(null)
    val thanksMessage = _thanksMessage.asStateFlow()
    private val thanksQueue = mutableListOf<String>()

    fun dismissThanksMessage() {
        if (thanksQueue.isNotEmpty()) {
            _thanksMessage.value = thanksQueue.removeAt(0)
        } else {
            _thanksMessage.value = null
        }
    }

    private fun showThanks(message: String) {
        if (_thanksMessage.value == null) {
            _thanksMessage.value = message
        } else {
            thanksQueue.add(message)
        }
    }

    private val _favoritePresets = MutableStateFlow<Set<String>>(emptySet())
    val favoritePresets = _favoritePresets.asStateFlow()

    private val _captureSource = MutableStateFlow(AudioCaptureService.CaptureSource.INTERNAL)
    val captureSource = _captureSource.asStateFlow()

    private val _latestAnnouncement = MutableStateFlow<Announcement?>(null)
    val latestAnnouncement = _latestAnnouncement.asStateFlow()

    private val _showAnnouncementModal = MutableStateFlow(false)
    val showAnnouncementModal = _showAnnouncementModal.asStateFlow()

    private val _showAnnouncementEditor = MutableStateFlow(false)
    val showAnnouncementEditor = _showAnnouncementEditor.asStateFlow()

    private val _showAnnouncementHistory = MutableStateFlow(false)
    val showAnnouncementHistory = _showAnnouncementHistory.asStateFlow()

    private val _showSpoofingSettings = MutableStateFlow(false)
    val showSpoofingSettings = _showSpoofingSettings.asStateFlow()

    private val _shizukuSourceUnlocked = MutableStateFlow(false)
    val shizukuSourceUnlocked = _shizukuSourceUnlocked.asStateFlow()

    fun setShizukuSourceUnlocked(unlocked: Boolean) {
        _shizukuSourceUnlocked.value = unlocked
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putBoolean("shizuku_source_unlocked", unlocked) }
        }
    }

    fun setShowSpoofingSettings(show: Boolean) {
        _showSpoofingSettings.value = show
        analytics.logSettingChanged("show_spoofing_settings", show)
    }

    fun showAnnouncementEditor() { 
        _showAnnouncementEditor.value = true
        analytics.logScreenView("announcement_editor")
    }
    fun hideAnnouncementEditor() { _showAnnouncementEditor.value = false }

    fun showAnnouncementHistory() { 
        _showAnnouncementHistory.value = true
        analytics.logScreenView("announcement_history")
    }
    fun hideAnnouncementHistory() { _showAnnouncementHistory.value = false }

    fun dismissAnnouncement() {
        val announcement = _latestAnnouncement.value ?: return
        _showAnnouncementModal.value = false
        analytics.logAnnouncementClicked(announcement.id.toString(), "dismiss")
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putString("last_seen_announcement_id", announcement.id.toString()) }
        }
    }

    fun postAnnouncement(title: String, message: String, style: String, link: String? = null, linkText: String? = null) {
        viewModelScope.launch {
            try {
                val announcement = Announcement(
                    id = System.currentTimeMillis().toString(),
                    title = title,
                    message = message,
                    style = style,
                    link = link.takeIf { it?.isNotBlank() == true },
                    linkText = linkText.takeIf { it?.isNotBlank() == true },
                    timestamp = System.currentTimeMillis()
                )
                announcementRepository.postAnnouncement(announcement)
                _showAnnouncementEditor.value = false
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, ctx.getString(R.string.announcement_posted), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, ctx.getString(R.string.failed_to_post, e.message), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val announcementHistory = announcementRepository.getAnnouncementHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        val prefs = ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
        _favoritePresets.value = prefs.getStringSet("favorite_presets", emptySet()) ?: emptySet()
        
        val savedSource = prefs.getString("capture_source", AudioCaptureService.CaptureSource.INTERNAL.name)
        _captureSource.value = AudioCaptureService.CaptureSource.valueOf(savedSource ?: AudioCaptureService.CaptureSource.INTERNAL.name)

        _shizukuSourceUnlocked.value = prefs.getBoolean("shizuku_source_unlocked", false)

        _totalVisualizedTime.value = prefs.getLong("total_visualized_time", 0L)
        _totalGlyphTime.value = prefs.getLong("total_glyph_time", 0L)
        _totalHapticTime.value = prefs.getLong("total_haptic_time", 0L)
        _totalFlashlightTime.value = prefs.getLong("total_flashlight_time", 0L)
        _userNickname.value = prefs.getString("user_nickname", "Anonymous") ?: "Anonymous"
        _spoofLocale.value = prefs.getString("spoof_locale", null)

        var uId = prefs.getString("user_id", null)
        if (uId == null) {
            uId = java.util.UUID.randomUUID().toString()
            prefs.edit().putString("user_id", uId).apply()
        }
        _userId.value = uId

        // Track app openings and show thanks messages
        val openCount = prefs.getInt("app_open_count", 0) + 1
        prefs.edit().putInt("app_open_count", openCount).apply()
        analytics.logAppOpen(openCount)

        viewModelScope.launch {
            if (BuildConfig.DEBUG) {
                if (openCount == 1) {
                    showThanks("thanks for downloading")
                    showThanks("thanks for installing")
                } else if (openCount >= 2) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(ctx, ctx.getString(R.string.thanks_using), Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                if (openCount == 1) {
                    showThanks(ctx.getString(R.string.thanks_downloading))
                    showThanks(ctx.getString(R.string.thanks_installing))
                } else if (openCount > 1) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(ctx, ctx.getString(R.string.thanks_using), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    fun toggleFavorite(presetKey: String) {
        val current = _favoritePresets.value.toMutableSet()
        val isFavorited = if (current.contains(presetKey)) {
            current.remove(presetKey)
            false
        } else {
            current.add(presetKey)
            true
        }
        _favoritePresets.value = current
        
        val isCustom = _presetInfos.value.find { it.key == presetKey }?.description?.startsWith("Custom:") == true
        analytics.logPresetFavorited(presetKey, isFavorited, isCustom)
        
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit().putStringSet("favorite_presets", current).apply()
        }
    }

    private val _m3eEnabled = MutableStateFlow(true)
    val m3eEnabled = _m3eEnabled.asStateFlow()

    fun setM3EEnabled(enabled: Boolean) {
        _m3eEnabled.value = enabled
        analytics.logSettingChanged("m3e_enabled", enabled)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putBoolean("m3e_enabled", enabled) }
        }
    }

    // ── Tab ───────────────────────────────────────────────────────────────────
    private val _selectedTab = MutableStateFlow(Tab.Audio)
    val selectedTab = _selectedTab.asStateFlow()
    fun selectTab(tab: Tab) { 
        _selectedTab.value = tab
        analytics.logTabSelected(tab.name)
    }

    fun setCaptureSource(source: AudioCaptureService.CaptureSource) {
        if (source == AudioCaptureService.CaptureSource.SHIZUKU) {
            if (!checkShizukuPermission()) {
                return
            }
        }
        _captureSource.value = source
        MainActivity.serviceStatic?.setCaptureSource(source)
        analytics.logCaptureSourceChanged(source.name)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putString("capture_source", source.name) }
        }
    }

    private fun checkShizukuPermission(): Boolean {
        try {
            if (Shizuku.isPreV11()) return false
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                analytics.logShizukuPermissionResult(true)
                return true
            } else if (Shizuku.shouldShowRequestPermissionRationale()) {
                Toast.makeText(ctx, ctx.getString(R.string.shizuku_permission_required), Toast.LENGTH_LONG).show()
                analytics.logShizukuPermissionResult(false)
                return false
            } else {
                Shizuku.requestPermission(1001)
                return false
            }
        } catch (e: Exception) {
            analytics.logError("shizuku_error", e.message ?: "Unknown Shizuku error")
            Toast.makeText(ctx, ctx.getString(R.string.shizuku_not_running), Toast.LENGTH_LONG).show()
            return false
        }
    }

    // ── Device ────────────────────────────────────────────────────────────────
    // Exposed as MutableStateFlow (not just a val) so the Activity can always
    // read the latest device synchronously when binding the service.
    val selectedDevice = MutableStateFlow(DeviceProfile.DEVICE_NP2)

    private val _developerModeEnabled = MutableStateFlow(false)
    val developerModeEnabled = _developerModeEnabled.asStateFlow()

    private val _spoofedDevice = MutableStateFlow(DeviceProfile.DEVICE_NP1)
    val spoofedDevice = _spoofedDevice.asStateFlow()

    private val _spoofLocale = MutableStateFlow<String?>(null)
    val spoofLocale = _spoofLocale.asStateFlow()

    fun setDeveloperModeEnabled(enabled: Boolean) {
        _developerModeEnabled.value = enabled
        analytics.logSettingChanged("developer_mode", enabled)
        updateSelectedDevice()
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putBoolean("developer_mode_v2", enabled) }
        }
    }

    fun setSpoofedDevice(device: Int) {
        _spoofedDevice.value = device
        analytics.logDeviceSpoofed(phoneModelForDevice(device))
        if (_developerModeEnabled.value) {
            updateSelectedDevice()
        }
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putInt("spoofed_device", device) }
        }
    }

    fun setSpoofLocale(localeTag: String?) {
        _spoofLocale.value = localeTag
        val appLocales = if (localeTag == null) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(localeTag)
        }
        AppCompatDelegate.setApplicationLocales(appLocales)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putString("spoof_locale", localeTag) }
        }
    }

    private fun updateSelectedDevice() {
        val actualDevice = DeviceProfile.detectDevice()
        val targetDevice = if (_developerModeEnabled.value) _spoofedDevice.value else actualDevice

        if (selectedDevice.value != targetDevice) {
            selectedDevice.value = targetDevice
            if (targetDevice == DeviceProfile.DEVICE_UNKNOWN && _selectedTab.value == Tab.Glyphs) {
                _selectedTab.value = Tab.Audio
            }
            refreshPresets()
            reloadLatencyForCurrentRoute()
            // Forward to service if bound
            MainActivity.serviceStatic?.setDevice(targetDevice)
        }
    }

    // ── Latency ───────────────────────────────────────────────────────────────
    private val _latencyMs = MutableStateFlow(0)
    val latencyMs = _latencyMs.asStateFlow()

    private val _latencyPresets = MutableStateFlow(listOf(0, 150, 300, 500))
    val latencyPresets = _latencyPresets.asStateFlow()

    /**
     * Updates the current system latency and persists it to disk.
     */
    fun setLatencyMs(value: Int) {
        _latencyMs.value = value
        analytics.logLatencyChanged(value, activeLatencyRouteKey())
        viewModelScope.launch(Dispatchers.IO) {
            AudioCaptureService.saveLatencyCompensationMs(
                ctx,
                selectedDevice.value,
                activeLatencyRouteKey(),
                value
            )
        }
    }


    // ── Gamma ─────────────────────────────────────────────────────────────────
    private val _gammaValue = MutableStateFlow(AudioCaptureService.DEFAULT_GAMMA)
    val gammaValue = _gammaValue.asStateFlow()
    fun setGammaValue(value: Float) { _gammaValue.value = value }

    private val _spectrumGain = MutableStateFlow(4.0f)
    val spectrumGain = _spectrumGain.asStateFlow()
    fun setSpectrumGain(value: Float) {
        _spectrumGain.value = value
        analytics.logSettingChanged("spectrum_gain", value)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putFloat("spectrum_gain", value) }
        }
    }

    private val _maxBrightness = MutableStateFlow(4095)
    val maxBrightness = _maxBrightness.asStateFlow()
    fun setMaxBrightness(value: Int) {
        val clamped = value.coerceIn(0, 4500)
        _maxBrightness.value = clamped
        analytics.logSettingChanged("max_brightness", clamped)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putInt("max_brightness", clamped) }
        }
    }

    // ── Running state ─────────────────────────────────────────────────────────
    private val _runningState = MutableStateFlow(false)
    val runningState = _runningState.asStateFlow()
    fun setRunning(running: Boolean) {
        _runningState.value = running
        analytics.logVisualizerStarted(running)
    }

    // ── Presets ──────────────────────────────────────────────────────────────
    private val _selectedPreset = MutableStateFlow("")
    val selectedPreset = _selectedPreset.asStateFlow()
    fun currentPreset(): String = _selectedPreset.value
    fun setSelectedPreset(key: String) {
        if (key.isNotBlank()) {
            _selectedPreset.value = key
            val isCustom = _presetInfos.value.find { it.key == key }?.description?.startsWith("Custom:") == true
            analytics.logPresetSelected(key, isCustom)
            viewModelScope.launch(Dispatchers.IO) {
                ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                    .edit { putString("selected_preset", key) }
            }
        }
    }

    private val _presetInfos = MutableStateFlow<List<AudioCaptureService.PresetInfo>>(emptyList())
    val presetInfos = _presetInfos.asStateFlow()

    // ── Device Memorization ──────────────────────────────────────────────────
    private val _autoDeviceEnabled = MutableStateFlow(true)
    val autoDeviceEnabled = _autoDeviceEnabled.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName = _connectedDeviceName.asStateFlow()

    private val _connectedDeviceKey = MutableStateFlow<String?>(null)

    private val _glyphTabEnabled = MutableStateFlow(true)

    private val _hapticsTabEnabled = MutableStateFlow(true)

    private val _idleBreathingEnabled = MutableStateFlow(false)
    val idleBreathingEnabled = _idleBreathingEnabled.asStateFlow()

    private val _idlePattern = MutableStateFlow("pulse")
    val idlePattern = _idlePattern.asStateFlow()

    private val _notificationFlashEnabled = MutableStateFlow(false)
    val notificationFlashEnabled = _notificationFlashEnabled.asStateFlow()

    private val _strobeEnabled = MutableStateFlow(false)
    val strobeEnabled = _strobeEnabled.asStateFlow()

    private val _configVersion = MutableStateFlow("Unknown")
    val configVersion = _configVersion.asStateFlow()

    private val _remoteConfigVersion = MutableStateFlow<String?>(null)
    val remoteConfigVersion = _remoteConfigVersion.asStateFlow()

    private val _appRemoteVersion = MutableStateFlow<String?>(null)
    val appRemoteVersion = _appRemoteVersion.asStateFlow()

    private val _disableGlyphsWhenSilent = MutableStateFlow(false)
    val disableGlyphsWhenSilent = _disableGlyphsWhenSilent.asStateFlow()

    private val _dynamicGainEnabled = MutableStateFlow(false)
    val dynamicGainEnabled = _dynamicGainEnabled.asStateFlow()

    private val _overlayEnabled = MutableStateFlow(false)
    val overlayEnabled = _overlayEnabled.asStateFlow()

    private val _overlayWidth = MutableStateFlow(120)
    val overlayWidth = _overlayWidth.asStateFlow()

    private val _overlayHeight = MutableStateFlow(12)
    val overlayHeight = _overlayHeight.asStateFlow()

    private val _overlayYOffset = MutableStateFlow(2)
    val overlayYOffset = _overlayYOffset.asStateFlow()

    // ── Zones Update ──────────────────────────────────────────────────────────
    private val _configUpdateStatus = MutableStateFlow<ConfigUpdateStatus>(ConfigUpdateStatus.Idle)
    val configUpdateStatus = _configUpdateStatus.asStateFlow()

    private val _appUpdateStatus = MutableStateFlow<AppUpdateStatus>(AppUpdateStatus.Idle)
    val appUpdateStatus = _appUpdateStatus.asStateFlow()

    private val _showUpdateDialog = MutableStateFlow(false)
    val showUpdateDialog = _showUpdateDialog.asStateFlow()

    fun dismissUpdateDialog() {
        _showUpdateDialog.value = false
    }

    sealed class ConfigUpdateStatus {
        object Idle : ConfigUpdateStatus()
        object Updating : ConfigUpdateStatus()
        data class Success(val message: String) : ConfigUpdateStatus()
        data class Error(val message: String) : ConfigUpdateStatus()
    }

    sealed class AppUpdateStatus {
        object Idle : AppUpdateStatus()
        object Checking : AppUpdateStatus()
        data class Available(
            val version: String,
            val url: String,
            val changelog: String? = null,
            val apkUrl: String? = null
        ) : AppUpdateStatus()
        data class Downloading(val progress: Float) : AppUpdateStatus()
        object UpToDate : AppUpdateStatus()
        data class Error(val message: String) : AppUpdateStatus()
    }

    // FIXED: Proper thread handling for network and UI updates
    fun updateZonesConfig() {
        // 1. Set loading state immediately on Main Thread
        _configUpdateStatus.value = ConfigUpdateStatus.Updating

        viewModelScope.launch {
            announcementRepository.getLatestAnnouncement().collect { announcement ->
                _latestAnnouncement.value = announcement
                if (announcement != null) {
                    val sharedPrefs = ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                    val lastSeenId = sharedPrefs.getString("last_seen_announcement_id", "")
                    if (announcement.id.toString() != lastSeenId) {
                        _showAnnouncementModal.value = true
                    }
                }
            }
        }

        viewModelScope.launch {
            try {
                // 2. Perform network/download on IO Thread
                val success = withContext(Dispatchers.IO) {
                    performUpdateAction()
                }

                // 3. Back on Main Thread automatically after withContext
                if (success) {
                    _configUpdateStatus.value = ConfigUpdateStatus.Success(ctx.getString(R.string.config_update_success))
                }
                // Errors are handled inside performUpdateAction setting the status directly now,
                // or we could return Result object. To keep it simple with existing code:
            } catch (e: Exception) {
                // Catch unexpected errors
                _configUpdateStatus.value = ConfigUpdateStatus.Error(ctx.getString(R.string.config_error_updating, e.message))
            }
        }
    }

    private suspend fun performUpdateAction(): Boolean {
        // This runs on Dispatchers.IO (called from withContext(IO) above)
        return try {
            val url = URL("https://raw.githubusercontent.com/Aleks-Levet/better-nothing-music-visualizer/main/zones.config?t=${System.currentTimeMillis()}")
            val connection = withContext(Dispatchers.IO) {
                url.openConnection()
            } as HttpURLConnection
            connection.useCaches = false
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val content = connection.inputStream.bufferedReader().use { it.readText() }
                // Basic validation
                JSONObject(content)

                val file = File(ctx.filesDir, "zones.config")
                file.writeText(content)

                // Refresh presets (file IO)
                refreshPresetsInternal()

                val newVersion = AudioCaptureService.loadZonesConfigVersion(ctx)
                _configVersion.value = newVersion
                _remoteConfigVersion.value = newVersion

                // Force running service to reload its config from disk
                MainActivity.serviceStatic?.reloadConfig()
                true
            } else {
                withContext(Dispatchers.Main) {
                    _configUpdateStatus.value = ConfigUpdateStatus.Error(ctx.getString(R.string.config_download_error, connection.responseCode))
                }
                false
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                _configUpdateStatus.value = ConfigUpdateStatus.Error(ctx.getString(R.string.config_error_updating, e.message))
            }
            false
        }
    }

    fun importZonesConfig(uri: Uri) {
        _configUpdateStatus.value = ConfigUpdateStatus.Updating
        viewModelScope.launch {
            announcementRepository.getLatestAnnouncement().collect { announcement ->
                _latestAnnouncement.value = announcement
                if (announcement != null) {
                    val sharedPrefs = ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                    val lastSeenId = sharedPrefs.getString("last_seen_announcement_id", "")
                    if (announcement.id.toString() != lastSeenId) {
                        _showAnnouncementModal.value = true
                    }
                }
            }
        }

        viewModelScope.launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    val content = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    if (content == null) return@withContext false

                    // Basic validation
                    JSONObject(content)

                    val file = File(ctx.filesDir, "zones.config")
                    file.writeText(content)

                    // Refresh presets (file IO)
                    refreshPresetsInternal()

                    val newVersion = AudioCaptureService.loadZonesConfigVersion(ctx)
                    _configVersion.value = newVersion
                    _remoteConfigVersion.value = null // Clear remote version since we are on local

                    // Force running service to reload its config from disk
                    MainActivity.serviceStatic?.reloadConfig()
                    true
                }

                if (success) {
                    _configUpdateStatus.value = ConfigUpdateStatus.Success(ctx.getString(R.string.config_import_success))
                } else {
                    _configUpdateStatus.value = ConfigUpdateStatus.Error(ctx.getString(R.string.config_import_error))
                }
            } catch (e: Exception) {
                _configUpdateStatus.value = ConfigUpdateStatus.Error(ctx.getString(R.string.config_error_importing, e.message))
            }
        }
    }

    fun resetConfigUpdateStatus() {
        _configUpdateStatus.value = ConfigUpdateStatus.Idle
    }

    fun checkRemoteConfigVersion() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = URL("https://raw.githubusercontent.com/Aleks-Levet/better-nothing-music-visualizer/main/zones.config?t=${System.currentTimeMillis()}")
                val connection = url.openConnection() as HttpURLConnection
                connection.useCaches = false
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val content = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(content)
                    val remoteVersion = json.optString("version", "Unknown")
                    _remoteConfigVersion.value = remoteVersion
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to check remote version", e)
            }
        }
    }

    fun checkAppUpdate() {
        _appUpdateStatus.value = AppUpdateStatus.Checking
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = URL("https://api.github.com/repos/Aleks-Levet/better-nothing-music-visualizer/releases/latest")
                val connection = url.openConnection() as HttpURLConnection
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                connection.setRequestProperty("User-Agent", "BetterNothingMusicVisualizer") // Required by GitHub API
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val content = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(content)
                    val tagName = json.getString("tag_name")
                    val htmlUrl = json.getString("html_url")
                    val body = json.optString("body")
                    
                    val assets = json.optJSONArray("assets")
                    var apkUrl: String? = null
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.getString("name")
                            if (name.endsWith(".apk")) {
                                apkUrl = asset.getString("browser_download_url")
                                break
                            }
                        }
                    }
                    
                    val latestVersion = tagName.removePrefix("v")
                    val currentVersion = BuildConfig.VERSION_NAME

                    _appRemoteVersion.value = latestVersion
                    val available = isNewerVersion(currentVersion, latestVersion)
                    analytics.logUpdateChecked(currentVersion, latestVersion, available)

                    if (available) {
                        _appUpdateStatus.value = AppUpdateStatus.Available(latestVersion, htmlUrl, body, apkUrl)
                        _showUpdateDialog.value = true
                    } else {
                        _appUpdateStatus.value = AppUpdateStatus.UpToDate
                    }
                } else {
                    val errorMsg = "Check failed: ${connection.responseCode}"
                    analytics.logError("update_check_failed", errorMsg)
                    _appUpdateStatus.value = AppUpdateStatus.Error(errorMsg)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(ctx, errorMsg, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                val errorMsg = "Update check error: ${e.message}"
                analytics.logError("update_check_exception", errorMsg)
                _appUpdateStatus.value = AppUpdateStatus.Error(errorMsg)
                Log.e("MainViewModel", "App update check failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, errorMsg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun downloadAndInstallUpdate(apkUrl: String, versionName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = URL(apkUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 30000
                
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val fileLength = connection.contentLength
                    val destinationFile = File(ctx.externalCacheDir, "update_$versionName.apk")
                    
                    connection.inputStream.use { input ->
                        FileOutputStream(destinationFile).use { output ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            var totalBytesRead = 0L
                            
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                totalBytesRead += bytesRead
                                if (fileLength > 0) {
                                    val progress = totalBytesRead.toFloat() / fileLength.toFloat()
                                    _appUpdateStatus.value = AppUpdateStatus.Downloading(progress)
                                }
                            }
                        }
                    }
                    
                    withContext(Dispatchers.Main) {
                        installApk(destinationFile)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(ctx, "Download failed: ${connection.responseCode}", Toast.LENGTH_SHORT).show()
                        _appUpdateStatus.value = AppUpdateStatus.Error("Download failed")
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Download failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, "Download error: ${e.message}", Toast.LENGTH_SHORT).show()
                    _appUpdateStatus.value = AppUpdateStatus.Error(e.message ?: "Unknown error")
                }
            }
        }
    }

    private fun installApk(file: File) {
        try {
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
        } catch (e: Exception) {
            Log.e("MainViewModel", "Installation failed", e)
            Toast.makeText(ctx, "Installation failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isNewerVersion(current: String, latest: String): Boolean {
        // Remove common suffixes like .debug or -beta
        val cleanCurrent = current.split("-", " ").first()
        val cleanLatest = latest.split("-", " ").first()

        val currentParts = cleanCurrent.split(".").mapNotNull { it.takeWhile { c -> c.isDigit() }.toIntOrNull() }
        val latestParts = cleanLatest.split(".").mapNotNull { it.takeWhile { c -> c.isDigit() }.toIntOrNull() }
        
        val length = maxOf(currentParts.size, latestParts.size)
        for (i in 0 until length) {
            val curr = if (i < currentParts.size) currentParts[i] else 0
            val lat = if (i < latestParts.size) latestParts[i] else 0
            if (lat > curr) return true
            if (lat < curr) return false
        }
        return false
    }

    // ── Theme & Font ─────────────────────────────────────────────────────────
    private val _selectedTheme = MutableStateFlow("Default")
    val selectedTheme = _selectedTheme.asStateFlow()

    private val _musicPrimaryColor = MutableStateFlow<Color?>(null)
    val musicPrimaryColor = _musicPrimaryColor.asStateFlow()

    fun updateMusicColor(bitmap: Bitmap?) {
        if (bitmap == null) {
            _musicPrimaryColor.value = null
            return
        }
        viewModelScope.launch(Dispatchers.Default) {
            try {
                // Resize for faster processing
                val scaled = if (bitmap.width > 200 || bitmap.height > 200) {
                    Bitmap.createScaledBitmap(bitmap, 128, 128, true)
                } else bitmap

                val palette = Palette.from(scaled).generate()
                
                // Prioritize vibrant swatches for better UI colors
                val swatch = palette.vibrantSwatch 
                    ?: palette.lightVibrantSwatch
                    ?: palette.darkVibrantSwatch
                    ?: palette.mutedSwatch
                    ?: palette.dominantSwatch

                withContext(Dispatchers.Main) {
                    val color = swatch?.rgb?.let { Color(it) }
                    _musicPrimaryColor.value = color
                    color?.let {
                        MainActivity.serviceStatic?.setOverlayColor(it.toArgb())
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Palette generation failed", e)
            }
        }
    }

    fun isNotificationAccessGranted(): Boolean {
        val flat = android.provider.Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")
        return flat?.contains(ctx.packageName) == true
    }

    private val _selectedFont = MutableStateFlow("NDot")
    val selectedFont = _selectedFont.asStateFlow()

    fun setSelectedTheme(theme: String) {
        _selectedTheme.value = theme
        analytics.logThemeChanged(theme)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putString("selected_theme", theme) }
        }
    }

    fun setSelectedFont(font: String) {
        _selectedFont.value = font
        analytics.logFontChanged(font)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putString("selected_font", font) }
        }
    }

    // ── Visualizer State (Live Preview) ─────────────────────────────────────
    private val _visualizerState = MutableStateFlow(floatArrayOf())
    val visualizerState = _visualizerState.asStateFlow()

    private val _fftState = MutableStateFlow(floatArrayOf())
    val fftState = _fftState.asStateFlow()

    private val _sessionDuration = MutableStateFlow(0L)
    val sessionDuration = _sessionDuration.asStateFlow()

    fun updateVisualizerState(state: FloatArray) {
        _visualizerState.value = state
    }

    fun updateFftState(state: FloatArray) {
        _fftState.value = state
    }

    private val _isEditingPreset = MutableStateFlow(false)
    val isEditingPreset = _isEditingPreset.asStateFlow()

    fun showEditor() { 
        _isEditingPreset.value = true 
        analytics.logScreenView("preset_editor")
    }
    fun hideEditor() { _isEditingPreset.value = false }

    private val _isShowingAbout = MutableStateFlow(false)
    val isShowingAbout = _isShowingAbout.asStateFlow()

    fun showAbout() { 
        _isShowingAbout.value = true 
        analytics.logScreenView("about")
    }
    fun hideAbout() { _isShowingAbout.value = false }

    private val _isShowingLicense = MutableStateFlow(false)
    val isShowingLicense = _isShowingLicense.asStateFlow()

    fun showLicense() { 
        _isShowingLicense.value = true 
        analytics.logScreenView("license")
    }
    fun hideLicense() { _isShowingLicense.value = false }

    private val _isShowingCommunity = MutableStateFlow(false)
    val isShowingCommunity = _isShowingCommunity.asStateFlow()

    fun showCommunity() { 
        _isShowingCommunity.value = true 
        analytics.logScreenView("community_presets")
    }
    fun hideCommunity() { _isShowingCommunity.value = false }

    fun showLeaderboard() { 
        _isShowingLeaderboard.value = true 
        analytics.logScreenView("leaderboard")
    }
    fun hideLeaderboard() { _isShowingLeaderboard.value = false }

    fun setUserNickname(name: String) {
        _userNickname.value = name
        analytics.logProfileUpdate("nickname")
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putString("user_nickname", name) }
            
            val currentProfile = _userProfile.value ?: UserProfile(
                userId = ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE).getString("user_id", "") ?: "",
                displayName = name
            )
            val updatedProfile = currentProfile.copy(displayName = name)
            userRepository.saveUserProfile(updatedProfile)
            _userProfile.value = updatedProfile
            syncLeaderboard()
        }
    }

    fun updateProfilePicture(uri: Uri) {
        analytics.logProfileUpdate("profile_picture_uri")
        viewModelScope.launch {
            val userId = ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE).getString("user_id", null) ?: return@launch
            try {
                val url = userRepository.uploadProfilePicture(userId, uri, ctx)
                val currentProfile = _userProfile.value ?: UserProfile(
                    userId = userId,
                    displayName = _userNickname.value
                )
                val updatedProfile = currentProfile.copy(profilePictureUrl = url)
                userRepository.saveUserProfile(updatedProfile)
                _userProfile.value = updatedProfile
                syncLeaderboard()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, ctx.getString(R.string.failed_to_upload_image, e.message), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun selectDefaultAvatar(resourceId: Int) {
        analytics.logProfileUpdate("default_avatar")
        viewModelScope.launch {
            val userId = ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE).getString("user_id", null) ?: return@launch
            try {
                Log.d("MainViewModel", "Setting default avatar from resource $resourceId")
                val base64Data = userRepository.uploadAvatarFromResource(userId, resourceId, ctx)
                Log.d("MainViewModel", "Avatar encoded successfully, saving to profile")
                
                val currentProfile = _userProfile.value ?: UserProfile(
                    userId = userId,
                    displayName = _userNickname.value
                )
                val updatedProfile = currentProfile.copy(profilePictureUrl = base64Data)
                userRepository.saveUserProfile(updatedProfile)
                _userProfile.value = updatedProfile
                syncLeaderboard()
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, "Profile picture updated!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Default avatar selection failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, "Update failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun updateSessionDuration(durationMs: Long) {
        val delta = durationMs - _sessionDuration.value
        if (delta > 0) {
            _totalVisualizedTime.value += delta
            if (runningState.value) {
                // Approximate active times based on settings
                if (selectedDevice.value != DeviceProfile.DEVICE_UNKNOWN) _totalGlyphTime.value += delta
                if (hapticMotorEnabled.value) _totalHapticTime.value += delta
                if (flashlightEnabled.value) _totalFlashlightTime.value += delta
            }
        }
        _sessionDuration.value = durationMs

        // Auto-save and sync every 30 seconds
        val now = System.currentTimeMillis()
        if (now - lastStatsSyncMs > 30000) {
            persistStats()
            syncLeaderboard()
            lastStatsSyncMs = now
        }
    }

    private fun persistStats() {
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE).edit {
                putLong("total_visualized_time", _totalVisualizedTime.value)
                putLong("total_glyph_time", _totalGlyphTime.value)
                putLong("total_haptic_time", _totalHapticTime.value)
                putLong("total_flashlight_time", _totalFlashlightTime.value)
            }
        }
    }

    private fun syncLeaderboard() {
        val userId = ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
            .getString("user_id", null) ?: return
        
        analytics.logStatsSynced(
            _totalVisualizedTime.value,
            _totalGlyphTime.value,
            _totalHapticTime.value,
            _totalFlashlightTime.value
        )
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entry = LeaderboardEntry(
                    userId = userId,
                    name = _userNickname.value,
                    profilePictureUrl = _userProfile.value?.profilePictureUrl,
                    totalTimeMs = _totalVisualizedTime.value,
                    lastUpdated = System.currentTimeMillis()
                )
                leaderboardRepository.updateScore(entry)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to sync leaderboard", e)
            }
        }
    }

    val leaderboardEntries = leaderboardRepository.getTopUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _communityError = MutableStateFlow<String?>(null)
    val communityError = _communityError.asStateFlow()

    val communityPresets = communityRepository.getPresets()
        .onEach { _communityError.value = null }
        .catch { e -> _communityError.value = e.message }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun sharePresetToCommunity(name: String, author: String, zones: List<AudioProcessor.ZoneSpec>) {
        viewModelScope.launch {
            announcementRepository.getLatestAnnouncement().collect { announcement ->
                _latestAnnouncement.value = announcement
                if (announcement != null) {
                    val sharedPrefs = ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                    val lastSeenId = sharedPrefs.getString("last_seen_announcement_id", "")
                    if (announcement.id.toString() != lastSeenId) {
                        _showAnnouncementModal.value = true
                    }
                }
            }
        }

        viewModelScope.launch {
            try {
                val preset = CommunityPreset(
                    name = name,
                    author = author,
                    authorId = _userId.value ?: "",
                    phoneModel = phoneModelForDevice(selectedDevice.value),
                    zones = zones.map { ZoneData.fromZoneSpec(it) }
                )
                communityRepository.uploadPreset(preset)
                analytics.logPresetShared(name)
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, ctx.getString(R.string.thanks_participating), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to share preset", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, "Failed to share: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun deleteCommunityPreset(preset: CommunityPreset) {
        viewModelScope.launch {
            try {
                communityRepository.deletePreset(preset.id)
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, "Preset deleted", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to delete preset", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun deleteCustomPreset(presetKey: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(ctx.filesDir, "zones.config")
                if (!file.exists()) return@launch

                val json = JSONObject(file.readText())
                if (json.has(presetKey)) {
                    json.remove(presetKey)
                    file.writeText(json.toString(2))
                    
                    // Remove from favorites if present
                    val currentFavs = _favoritePresets.value.toMutableSet()
                    if (currentFavs.remove(presetKey)) {
                        _favoritePresets.value = currentFavs
                        ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                            .edit().putStringSet("favorite_presets", currentFavs).apply()
                    }

                    refreshPresetsInternal()
                    MainActivity.serviceStatic?.reloadConfig()

                    withContext(Dispatchers.Main) {
                        Toast.makeText(ctx, "Preset deleted", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to delete custom preset", e)
            }
        }
    }

    fun downloadPreset(preset: CommunityPreset) {
        saveCustomPreset(preset.name, preset.zones.map { it.toZoneSpec() }, preset.author)
        analytics.logCommunityPresetDownloaded(preset.name, preset.author)
        viewModelScope.launch {
            announcementRepository.getLatestAnnouncement().collect { announcement ->
                _latestAnnouncement.value = announcement
                if (announcement != null) {
                    val sharedPrefs = ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                    val lastSeenId = sharedPrefs.getString("last_seen_announcement_id", "")
                    if (announcement.id.toString() != lastSeenId) {
                        _showAnnouncementModal.value = true
                    }
                }
            }
        }

        viewModelScope.launch {
            communityRepository.incrementDownloadCount(preset.id)
            _isShowingCommunity.value = false
        }
    }

    fun saveCustomPreset(name: String, zones: List<AudioProcessor.ZoneSpec>, author: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(ctx.filesDir, "zones.config")
                val content = if (file.exists()) {
                    file.readText()
                } else {
                    ctx.assets.open("zones.config").bufferedReader().use { it.readText() }
                }
                val json = JSONObject(content)

                val presetJson = JSONObject()
                val description = if (author != null) "Custom: $name by $author" else "Custom: $name"
                presetJson.put("description", description)
                presetJson.put("phone_model", phoneModelForDevice(selectedDevice.value))

                val zonesArray = JSONArray()
                for (zone in zones) {
                    val zoneArray = JSONArray()
                    zoneArray.put(zone.lowHz.toDouble())
                    zoneArray.put(zone.highHz.toDouble())
                    zoneArray.put(0) // legacy amp field
                    if (java.lang.Float.isNaN(zone.lowPercent)) {
                        zoneArray.put(JSONObject.NULL)
                    } else {
                        zoneArray.put(zone.lowPercent.toDouble())
                    }
                    if (java.lang.Float.isNaN(zone.highPercent)) {
                        zoneArray.put(JSONObject.NULL)
                    } else {
                        zoneArray.put(zone.highPercent.toDouble())
                    }
                    zonesArray.put(zoneArray)
                }
                presetJson.put("zones", zonesArray)

                // Use a sanitized key
                val key = name.lowercase().replace(Regex("[^a-z0-9]"), "_")
                json.put(key, presetJson)

                file.writeText(json.toString(2))
                refreshPresetsInternal()
                MainActivity.serviceStatic?.reloadConfig()

                withContext(Dispatchers.Main) {
                    _selectedPreset.value = key
                    _isEditingPreset.value = false
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to save custom preset", e)
            }
        }
    }

    private fun phoneModelForDevice(device: Int): String {
        return when (device) {
            DeviceProfile.DEVICE_NP1 -> "PHONE1"
            DeviceProfile.DEVICE_NP2 -> "PHONE2"
            DeviceProfile.DEVICE_NP2A -> "PHONE2A"
            DeviceProfile.DEVICE_NP3A -> "PHONE3A"
            DeviceProfile.DEVICE_NP4A -> "PHONE4A"
            DeviceProfile.DEVICE_NP4APRO -> "PHONE4A_PRO"
            DeviceProfile.DEVICE_NP3 -> "PHONE3"
            else -> "UNKNOWN"
        }
    }

    // ── Haptic Settings ──────────────────────────────────────────────────────
    private val _hapticMotorEnabled = MutableStateFlow(false)
    val hapticMotorEnabled = _hapticMotorEnabled.asStateFlow()

    private val _hapticMode = MutableStateFlow(HapticMode.BASS_TO_AMPLITUDE)
    val hapticMode = _hapticMode.asStateFlow()

    private val _hapticFreqMin = MutableStateFlow(60f)
    val hapticFreqMin = _hapticFreqMin.asStateFlow()

    private val _hapticFreqMax = MutableStateFlow(250f)
    val hapticFreqMax = _hapticFreqMax.asStateFlow()

    private val _hapticMultiplier = MutableStateFlow(1.0f)
    val hapticMultiplier = _hapticMultiplier.asStateFlow()

    private val _hapticAudioGain = MutableStateFlow(1.0f)
    val hapticAudioGain = _hapticAudioGain.asStateFlow()

    private val _hapticGamma = MutableStateFlow(2.0f)
    val hapticGamma = _hapticGamma.asStateFlow()

    private val _hapticBeatSensitivity = MutableStateFlow(1.0f)
    val hapticBeatSensitivity = _hapticBeatSensitivity.asStateFlow()

    private val _hapticBeatGamma = MutableStateFlow(8.0f)
    val hapticBeatGamma = _hapticBeatGamma.asStateFlow()

    fun setHapticMotorEnabled(enabled: Boolean) {
        _hapticMotorEnabled.value = enabled
        analytics.logSettingChanged("haptic_motor_enabled", enabled)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putBoolean("haptic_motor_enabled", enabled) }
            HapticsTileService.requestRefresh(ctx)
        }
    }

    fun setHapticMode(mode: HapticMode) {
        _hapticMode.value = mode
        analytics.logSettingChanged("haptic_mode", mode.name)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putString("haptic_mode", mode.name) }
        }
    }

    fun setHapticFreqRange(min: Float, max: Float) {
        _hapticFreqMin.value = min
        _hapticFreqMax.value = max
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit {
                    putInt("haptic_freq_min", min.toInt())
                    putInt("haptic_freq_max", max.toInt())
                }
        }
    }

    fun setHapticMultiplier(multiplier: Float) {
        _hapticMultiplier.value = multiplier.coerceIn(0.3f, 1.5f)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putFloat("haptic_multiplier", multiplier.coerceIn(0.3f, 1.5f)) }
        }
    }

    fun setHapticAudioGain(gain: Float) {
        _hapticAudioGain.value = gain
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putFloat("haptic_audio_gain", gain) }
        }
    }

    fun setHapticGamma(gamma: Float) {
        _hapticGamma.value = gamma
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putFloat("haptic_gamma", gamma) }
        }
    }

    fun setHapticBeatSensitivity(sensitivity: Float) {
        _hapticBeatSensitivity.value = sensitivity
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putFloat("haptic_beat_sensitivity", sensitivity) }
        }
    }

    fun setHapticBeatGamma(gamma: Float) {
        _hapticBeatGamma.value = gamma
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putFloat("haptic_beat_gamma", gamma) }
        }
    }

    // ── Flashlight Settings ──────────────────────────────────────────────────
    private val _flashlightEnabled = MutableStateFlow(false)
    val flashlightEnabled = _flashlightEnabled.asStateFlow()

    private val _flashlightMode = MutableStateFlow(TorchMode.AMPLITUDE)
    val flashlightMode = _flashlightMode.asStateFlow()

    private val _flashlightFreqMin = MutableStateFlow(60f)
    val flashlightFreqMin = _flashlightFreqMin.asStateFlow()

    private val _flashlightFreqMax = MutableStateFlow(250f)
    val flashlightFreqMax = _flashlightFreqMax.asStateFlow()

    private val _flashlightThreshold = MutableStateFlow(0.15f)
    val flashlightThreshold = _flashlightThreshold.asStateFlow()

    private val _flashlightSpeedMs = MutableStateFlow(90f)
    val flashlightSpeedMs = _flashlightSpeedMs.asStateFlow()

    private val _flashlightBeatSensitivity = MutableStateFlow(1.0f)
    val flashlightBeatSensitivity = _flashlightBeatSensitivity.asStateFlow()

    private val _flashlightIntensityLevels = MutableStateFlow(1)
    val flashlightIntensityLevels = _flashlightIntensityLevels.asStateFlow()

    fun setFlashlightEnabled(enabled: Boolean) {
        _flashlightEnabled.value = enabled
        analytics.logSettingChanged("flashlight_enabled", enabled)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putBoolean("flashlight_enabled", enabled) }
        }
    }

    fun setFlashlightMode(mode: TorchMode) {
        _flashlightMode.value = mode
        analytics.logSettingChanged("flashlight_mode", mode.name)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putString("flashlight_mode", mode.name) }
        }
    }

    fun setFlashlightFreqRange(min: Float, max: Float) {
        _flashlightFreqMin.value = min
        _flashlightFreqMax.value = max
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit {
                    putInt("flashlight_freq_min", min.toInt())
                    putInt("flashlight_freq_max", max.toInt())
                }
        }
    }

    fun setFlashlightThreshold(threshold: Float) {
        _flashlightThreshold.value = threshold
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putFloat("flashlight_threshold", threshold) }
        }
    }

    fun setFlashlightSpeedMs(speedMs: Float) {
        val min = if (_flashlightIntensityLevels.value > 1) 150f else 20f
        val max = if (_flashlightIntensityLevels.value > 1) 700f else 150f
        val clamped = speedMs.coerceIn(min, max)
        _flashlightSpeedMs.value = clamped
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putFloat("flashlight_speed_ms", clamped) }
        }
    }

    fun setFlashlightBeatSensitivity(sensitivity: Float) {
        _flashlightBeatSensitivity.value = sensitivity
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putFloat("flashlight_beat_sensitivity", sensitivity) }
        }
    }

    private fun loadFlashlightSpeedMs(prefs: android.content.SharedPreferences): Float {
        val min = if (_flashlightIntensityLevels.value > 1) 150f else 20f
        val max = if (_flashlightIntensityLevels.value > 1) 700f else 150f

        if (prefs.contains("flashlight_speed_ms")) {
            return prefs.getFloat("flashlight_speed_ms", 90f).coerceIn(min, max)
        }

        val legacyGamma = prefs.getFloat("flashlight_gamma", 2.2f)
        return legacyGammaToSpeedMs(legacyGamma)
    }

    private fun legacyGammaToSpeedMs(gamma: Float): Float {
        if (gamma <= 0f) return 90f
        if (gamma < 10f) {
            val clampedGamma = gamma.coerceIn(1f, 4f)
            val normalized = (clampedGamma - 1f) / 3f
            return 150f - (normalized * 130f)
        }
        return gamma.coerceIn(20f, 150f)
    }

    fun setIdleBreathingEnabled(enabled: Boolean) {
        _idleBreathingEnabled.value = enabled
        analytics.logSettingChanged("idle_breathing_enabled", enabled)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putBoolean("idle_breathing_enabled", enabled) }
        }
    }

    fun setIdlePattern(pattern: String) {
        _idlePattern.value = pattern
        analytics.logSettingChanged("idle_pattern", pattern)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putString("idle_pattern", pattern) }
        }
    }

    fun setNotificationFlashEnabled(enabled: Boolean) {
        _notificationFlashEnabled.value = enabled
        analytics.logSettingChanged("notification_flash_enabled", enabled)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putBoolean("notification_flash_enabled", enabled) }
        }
    }

    fun setStrobeEnabled(enabled: Boolean) {
        _strobeEnabled.value = enabled
        analytics.logSettingChanged("strobe_enabled", enabled)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putBoolean("strobe_enabled", enabled) }
        }
    }

    fun setDisableGlyphsWhenSilent(enabled: Boolean) {
        _disableGlyphsWhenSilent.value = enabled
        analytics.logSettingChanged("disable_glyphs_when_silent", enabled)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putBoolean("disable_glyphs_when_silent", enabled) }
        }
    }

    fun setDynamicGainEnabled(enabled: Boolean) {
        _dynamicGainEnabled.value = enabled
        analytics.logSettingChanged("dynamic_gain_enabled", enabled)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putBoolean("dynamic_gain_enabled", enabled) }
        }
    }

    fun setOverlayEnabled(enabled: Boolean) {
        _overlayEnabled.value = enabled
        analytics.logSettingChanged("overlay_enabled", enabled)
        MainActivity.serviceStatic?.setOverlayEnabled(enabled)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putBoolean("overlay_enabled", enabled) }
        }
    }

    fun setOverlayWidth(width: Int) {
        _overlayWidth.value = width
        MainActivity.serviceStatic?.setOverlayWidth(width)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putInt("overlay_width", width) }
        }
    }

    fun setOverlayHeight(height: Int) {
        _overlayHeight.value = height
        MainActivity.serviceStatic?.setOverlayHeight(height)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putInt("overlay_height", height) }
        }
    }

    fun setOverlayYOffset(offset: Int) {
        _overlayYOffset.value = offset
        MainActivity.serviceStatic?.setOverlayYOffset(offset)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putInt("overlay_y_offset", offset) }
        }
    }

    fun setAutoDeviceEnabled(enabled: Boolean): Int {
        _autoDeviceEnabled.value = enabled
        analytics.logSettingChanged("auto_device_enabled", enabled)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putBoolean("auto_device_enabled", enabled) }
        }
        return reloadLatencyForCurrentRoute()
    }

    fun updateConnectedDevice(routeKey: String?, name: String?): Int {
        _connectedDeviceKey.value = routeKey
        _connectedDeviceName.value = name
        return reloadLatencyForCurrentRoute()
    }

    // ── Init: all IO in parallel ──────────────────────────────────────────────

    private val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        when (key) {
            "haptic_motor_enabled" -> {
                _hapticMotorEnabled.value = prefs.getBoolean(key, false)
            }
            "selected_preset" -> {
                _selectedPreset.value = prefs.getString(key, "") ?: ""
            }
        }
    }

    // ── Haptic Visualizer State ──────────────────────────────────────────────
    private val _hapticAmplitude = MutableStateFlow(0f)
    val hapticAmplitude = _hapticAmplitude.asStateFlow()

    private val _uiAmplitude = MutableStateFlow(0f)
    val uiAmplitude = _uiAmplitude.asStateFlow()

    private val _flashlightAmplitude = MutableStateFlow(0f)
    val flashlightAmplitude = _flashlightAmplitude.asStateFlow()

    private val _isBeatDetected = MutableStateFlow(false)
    val isBeatDetected = _isBeatDetected.asStateFlow()

    private val _isFlashlightBeatDetected = MutableStateFlow(false)
    val isFlashlightBeatDetected = _isFlashlightBeatDetected.asStateFlow()

    private val hapticBeatDetector = BeatDetector()
    private val flashlightBeatDetector = BeatDetector()

    private var smoothedUiAmplitude = 0f
    private var smoothedHapticAmplitude = 0f

    init {
        viewModelScope.launch(Dispatchers.Default) {
            fftState.collect { magnitude ->
                if (magnitude.isEmpty()) {
                    _hapticAmplitude.value = 0f
                    _uiAmplitude.value = 0f
                    _flashlightAmplitude.value = 0f
                    smoothedUiAmplitude = 0f
                    _isBeatDetected.value = false
                    return@collect
                }

                val hzPerBin = 44100f / 4096f
                val binLo = (_hapticFreqMin.value / hzPerBin).toInt().coerceIn(0, magnitude.lastIndex)
                val binHi = (_hapticFreqMax.value / hzPerBin).toInt().coerceIn(binLo, magnitude.lastIndex)

                // 1. Calculate Amplitude
                var sumSquares = 0f
                var sum = 0f
                for (i in binLo..binHi) {
                    sumSquares += magnitude[i] * magnitude[i]
                    sum += magnitude[i]
                }
                val count = binHi - binLo + 1
                val rms = if (count > 0) kotlin.math.sqrt(sumSquares / count) else 0f
                
                // --- APPLY SETTINGS TO UI PREVIEW ---
                val finalValue = if (_hapticMode.value == HapticMode.BASS_TO_AMPLITUDE) {
                    val currentGain = _hapticMultiplier.value * 4f * _hapticAudioGain.value
                    val target = rms * currentGain
                    
                    smoothedHapticAmplitude = target
                    
                    smoothedHapticAmplitude.toDouble().pow(_hapticGamma.value.toDouble()).toFloat()
                } else {
                    val currentGain = _hapticMultiplier.value * 4f
                    rms * currentGain
                }
                
                _hapticAmplitude.value = finalValue.coerceIn(0f, 1.2f)

                // Flashlight Amplitude Calculation
                val fBinLo = (_flashlightFreqMin.value / hzPerBin).toInt().coerceIn(0, magnitude.lastIndex)
                val fBinHi = (_flashlightFreqMax.value / hzPerBin).toInt().coerceIn(fBinLo, magnitude.lastIndex)
                var fSumSquares = 0f
                var fSum = 0f
                for (i in fBinLo..fBinHi) {
                    fSumSquares += magnitude[i] * magnitude[i]
                    fSum += magnitude[i]
                }
                val fCount = fBinHi - fBinLo + 1
                val fRms = if (fCount > 0) kotlin.math.sqrt(fSumSquares / fCount) else 0f
                _flashlightAmplitude.value = (fRms * 8f).coerceIn(0f, 1.2f)

                // UI Amplitude (70-130 Hz) for global reactive UI elements
                val uiBinLo = (70f / hzPerBin).toInt().coerceIn(0, magnitude.lastIndex)
                val uiBinHi = (130f / hzPerBin).toInt().coerceIn(uiBinLo, magnitude.lastIndex)
                var uiSumSquares = 0f
                for (i in uiBinLo..uiBinHi) {
                    uiSumSquares += magnitude[i] * magnitude[i]
                }
                val uiCount = uiBinHi - uiBinLo + 1
                val uiRms = if (uiCount > 0) kotlin.math.sqrt(uiSumSquares / uiCount) else 0f
                val rawTarget = (uiRms * 10f).coerceIn(0f, 1.0f).toDouble().pow(3.0).toFloat()
                val target = (rawTarget * 1.3f) - 0.3f

                // Asymmetric smoothing: very fast attack, slower decay
                if (target > smoothedUiAmplitude) {
                    smoothedUiAmplitude = smoothedUiAmplitude * 0.1f + target * 0.9f
                } else {
                    smoothedUiAmplitude = smoothedUiAmplitude * 0.85f + target * 0.15f
                }
                _uiAmplitude.value = smoothedUiAmplitude

                // 2. Beat Detection (matching HapticEngine.kt logic)
                if (_hapticMode.value == HapticMode.BEAT_DETECTION) {
                    hapticBeatDetector.sensitivity = _hapticBeatSensitivity.value
                    if (hapticBeatDetector.detect(magnitude, binLo, binHi)) {
                        _isBeatDetected.value = true
                        // Auto-reset beat after a short duration for the UI flash
                        viewModelScope.launch {
                            delay(50)
                            _isBeatDetected.value = false
                        }
                    }
                } else {
                    _isBeatDetected.value = false
                }

                if (_flashlightMode.value == TorchMode.BEAT_DETECTION) {
                    flashlightBeatDetector.sensitivity = _flashlightBeatSensitivity.value
                    if (flashlightBeatDetector.detect(magnitude, fBinLo, fBinHi)) {
                        _isFlashlightBeatDetected.value = true
                        viewModelScope.launch {
                            delay(50)
                            _isFlashlightBeatDetected.value = false
                        }
                    }
                } else {
                    _isFlashlightBeatDetected.value = false
                }
            }
        }

        ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(prefListener)
        // Run EVERYTHING heavy off-thread immediately
        viewModelScope.launch(Dispatchers.Default) {
            val prefs = ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)

            _developerModeEnabled.value = prefs.getBoolean("developer_mode_v2", false)
            _spoofedDevice.value = prefs.getInt("spoofed_device", DeviceProfile.DEVICE_NP1)

            val actualDevice = DeviceProfile.detectDevice()
            val device = if (_developerModeEnabled.value) _spoofedDevice.value else actualDevice
            selectedDevice.value = device

            // Load I/O in parallel using IO dispatcher
            launch(Dispatchers.IO) {
                // Force update on first run if config is missing
                val internalFile = File(ctx.filesDir, "zones.config")
                val hasAsset = try { ctx.assets.open("zones.config").use { true } } catch (_: Exception) { false }

                if (!internalFile.exists() && !hasAsset) {
                    performUpdateAction()
                }

                val gamma = AudioCaptureService.loadGamma(ctx)
                val spectrumGain = prefs.getFloat("spectrum_gain", 4.0f)
                val maxBrightness = prefs.getInt("max_brightness", 4095).coerceIn(0, 4095)
                val latency = AudioCaptureService.loadLatencyCompensationMs(
                    ctx,
                    device,
                    activeLatencyRouteKey()
                )
                val presets = AudioCaptureService.loadLatencyPresets(ctx)
                val infos = AudioCaptureService.loadPresetInfos(ctx, device)
                val configVersion = AudioCaptureService.loadZonesConfigVersion(ctx)

                // Update UI state once ready
                _gammaValue.value = gamma
                _spectrumGain.value = spectrumGain
                _maxBrightness.value = maxBrightness
                _latencyMs.value = latency
                _latencyPresets.value = presets
                _configVersion.value = configVersion
                commitPresetInfos(infos)
                val prefs = ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)

                _idleBreathingEnabled.value = prefs.getBoolean("idle_breathing_enabled", false)
                _idlePattern.value = prefs.getString("idle_pattern", "pulse") ?: "pulse"
                _notificationFlashEnabled.value = prefs.getBoolean("notification_flash_enabled", false)
                _strobeEnabled.value = prefs.getBoolean("strobe_enabled", false)
                _configVersion.value = AudioCaptureService.loadZonesConfigVersion(ctx)
                _disableGlyphsWhenSilent.value = prefs.getBoolean("disable_glyphs_when_silent", false)
                _dynamicGainEnabled.value = prefs.getBoolean("dynamic_gain_enabled", false)
                _overlayEnabled.value = prefs.getBoolean("overlay_enabled", false)
                _overlayWidth.value = prefs.getInt("overlay_width", 120)
                _overlayHeight.value = prefs.getInt("overlay_height", 12)
                _overlayYOffset.value = prefs.getInt("overlay_y_offset", 2)
                _m3eEnabled.value = prefs.getBoolean("m3e_enabled", true)

                val theme = prefs.getString("selected_theme", "Default") ?: "Default"
                _selectedTheme.value = when (theme) {
                    "Normal", "OLED Black" -> "Default"
                    "Nothing Light", "Nothing Red" -> "Nothing"
                    "Material You Light" -> "Material You"
                    else -> theme
                }
                _selectedFont.value = prefs.getString("selected_font", "NDot") ?: "NDot"
                _selectedPreset.value = prefs.getString("selected_preset", "") ?: ""

                _hapticMotorEnabled.value = prefs.getBoolean("haptic_motor_enabled", false)
                val modeName = prefs.getString("haptic_mode", HapticMode.BASS_TO_AMPLITUDE.name)
                _hapticMode.value = HapticMode.valueOf(modeName ?: HapticMode.BASS_TO_AMPLITUDE.name)
                _hapticFreqMin.value = prefs.getInt("haptic_freq_min", 60).toFloat()
                _hapticFreqMax.value = prefs.getInt("haptic_freq_max", 250).toFloat()
                _hapticMultiplier.value = prefs.getFloat("haptic_multiplier", 1.0f).coerceIn(0.3f, 1.5f)
                _hapticAudioGain.value = prefs.getFloat("haptic_audio_gain", 1.0f)
                _hapticGamma.value = prefs.getFloat("haptic_gamma", 2.0f)
                _hapticBeatSensitivity.value = prefs.getFloat("haptic_beat_sensitivity", 1.0f).coerceIn(0.3f, 6.0f)
                _hapticBeatGamma.value = prefs.getFloat("haptic_beat_gamma", 8.0f).coerceIn(4f, 15f)

                _flashlightEnabled.value = prefs.getBoolean("flashlight_enabled", false)
                val torchModeName = prefs.getString("flashlight_mode", TorchMode.AMPLITUDE.name)
                _flashlightMode.value = TorchMode.valueOf(torchModeName ?: TorchMode.AMPLITUDE.name)
                _flashlightFreqMin.value = prefs.getInt("flashlight_freq_min", 60).toFloat()
                _flashlightFreqMax.value = prefs.getInt("flashlight_freq_max", 250).toFloat()
                _flashlightIntensityLevels.value = FlashlightEngine.detectTorchIntensityLevels(ctx)
                _flashlightThreshold.value = prefs.getFloat(
                    "flashlight_threshold",
                    if (_flashlightIntensityLevels.value > 1) 1.0f else 0.15f
                )
                _flashlightSpeedMs.value = loadFlashlightSpeedMs(prefs)
                _flashlightBeatSensitivity.value = prefs.getFloat("flashlight_beat_sensitivity", 1.0f)
            }

            startRunningStatePoller()
        }
    }

    fun initDatabase() {
        val uId = _userId.value ?: return
        
        viewModelScope.launch {
            try {
                val profile = userRepository.getUserProfile(uId)
                if (profile != null) {
                    _userProfile.value = profile
                    _userNickname.value = profile.displayName
                } else {
                    // Create initial profile
                    val newProfile = UserProfile(
                        userId = uId,
                        displayName = _userNickname.value,
                        totalVisualizedTime = _totalVisualizedTime.value
                    )
                    userRepository.saveUserProfile(newProfile)
                    _userProfile.value = newProfile
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to load/create profile", e)
            }
        }

        FirebaseDatabase.getInstance("https://bnmv-67120-default-rtdb.europe-west1.firebasedatabase.app")
            .getReference("config/dev_password")
            .get()
            .addOnSuccessListener { snapshot ->
                _devPassword.value = snapshot.getValue(String::class.java)
            }

        viewModelScope.launch {
            announcementRepository.getLatestAnnouncement().collect { announcement ->
                _latestAnnouncement.value = announcement
                if (announcement != null) {
                    val sharedPrefs = ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                    val lastSeenId = sharedPrefs.getString("last_seen_announcement_id", "")
                    if (announcement.id.toString() != lastSeenId) {
                        _showAnnouncementModal.value = true
                    }
                }
            }
        }

        checkRemoteConfigVersion()
        if (!BuildConfig.DEBUG) {
            checkAppUpdate()
        }
    }

    // ─── Off-thread service state polling ─────────────────────────────────────
    //
    // Previous code ran this on Dispatchers.Main (LaunchedEffect default), waking
    // the UI thread 2× per second for a comparison + potential state write.
    // Moving to Dispatchers.Default keeps the main thread completely idle when
    // nothing has changed.

    private fun startRunningStatePoller() {
        viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                delay(1000L) // Polling once per second is enough for a UI label
                val actual = AudioCaptureService.isRunning()
                if (_runningState.value != actual) {
                    _runningState.value = actual
                }
            }
        }
    }

    // ── Public helpers called by the Activity ─────────────────────────────────

    /** Reloads preset list from disk; safe to call from the main thread. */
    fun refreshPresets() {
        viewModelScope.launch(Dispatchers.IO) {
            refreshPresetsInternal()
        }
    }

    private suspend fun refreshPresetsInternal() {
        // First clear to ensure UI refresh if somehow the data is merged
        val infos = AudioCaptureService.loadPresetInfos(ctx, selectedDevice.value)

        // Switch to Main to update StateFlow safely
        withContext(Dispatchers.Main) {
            _presetInfos.value = infos
            if (infos.none { it.key == _selectedPreset.value }) {
                _selectedPreset.value = infos.firstOrNull()?.key.orEmpty()
            }
        }
    }

    /** Updates preset list in state and persists it; save is off main thread. */
    fun updateLatencyPresets(presets: List<Int>) {
        _latencyPresets.value = presets
        viewModelScope.launch(Dispatchers.IO) {
            AudioCaptureService.saveLatencyPresets(ctx, presets)
        }
    }

    /** Persists gamma to SharedPreferences without blocking the main thread. */
    fun persistGamma(clamped: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            AudioCaptureService.saveGamma(ctx, clamped)
        }
    }

    fun reloadLatencyForCurrentRoute(): Int {
        val latency = AudioCaptureService.loadLatencyCompensationMs(
            ctx,
            selectedDevice.value,
            activeLatencyRouteKey()
        )
        _latencyMs.value = latency
        return latency
    }

    private fun activeLatencyRouteKey(): String? {
        return _connectedDeviceKey.value.takeIf { _autoDeviceEnabled.value }
    }

    private fun commitPresetInfos(infos: List<AudioCaptureService.PresetInfo>) {
        _presetInfos.value = infos
        if (infos.none { it.key == _selectedPreset.value }) {
            _selectedPreset.value = infos.firstOrNull()?.key.orEmpty()
        }
    }

    // ── Thanks Messages ──────────────────────────────────────────────────────
    fun onDevDepressed() {
        showThanks(ctx.getString(R.string.thanks_nothing))
    }

    override fun onCleared() {
        ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(prefListener)
        super.onCleared()
    }
}

// ─── Activity ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    // viewModels() returns the same instance across configuration changes.
    private val viewModel: MainViewModel by viewModels()

    private val audioManager by lazy {
        getSystemService(AUDIO_SERVICE) as AudioManager
    }

    private val projectionManager by lazy {
        getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private var service: AudioCaptureService? = null
    private var bound = false
    private var pendingResultCode = 0
    private var pendingData: Intent? = null
    private var hasPendingToken = false
    private var pendingVisualizerStart = false
    private var showProjectionInfoDialog by mutableStateOf(false)

    companion object {
        const val EXTRA_REQUEST_START = "com.better.nothing.music.vizualizer.extra.REQUEST_START"
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
        @RequiresPermission(Manifest.permission.RECORD_AUDIO)
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            Log.d("BetterViz", "Service connected: $name")
            val s = (binder as AudioCaptureService.LocalBinder).service
            service = s
            serviceStatic = s
            bound = true
            refreshConnectedAudioRoute()
            applyServiceSettings()
            if (hasPendingToken && pendingData != null) {
                val data = pendingData ?: return
                service?.startCapture(pendingResultCode, data)
                pendingResultCode = 0
                pendingData = null
                hasPendingToken = false
            } else if (pendingVisualizerStart) {
                when (viewModel.captureSource.value) {
                    AudioCaptureService.CaptureSource.MIC -> service?.startMicCapture()
                    AudioCaptureService.CaptureSource.VIZUALIZER -> service?.startVizualizerCapture()
                    AudioCaptureService.CaptureSource.SHIZUKU -> service?.startShizukuCapture()
                    else -> {}
                }
                pendingVisualizerStart = false
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            serviceStatic = null
            bound = false
            // Use the lightweight static check; the poller will also catch any change.
            viewModel.setRunning(AudioCaptureService.isRunning())
        }
    }

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == RESULT_OK && data != null) {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    deliverProjectionToken(result.resultCode, data)
                } else {
                    pendingVisualizerStart = false
                    viewModel.setRunning(false)
                    Toast.makeText(this@MainActivity, getString(R.string.audio_permission_required), Toast.LENGTH_SHORT).show()
                }
            } else {
                pendingVisualizerStart = false
                viewModel.setRunning(false)
                Toast.makeText(this@MainActivity, getString(R.string.screen_capture_denied), Toast.LENGTH_SHORT).show()
            }
        }

    private val notificationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) toggleVisualizer()
            else {
                pendingVisualizerStart = false
                viewModel.setRunning(false)
                Toast.makeText(
                    this,
                    getString(R.string.notifications_required),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    private val audioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) toggleVisualizer()
            else {
                pendingVisualizerStart = false
                viewModel.setRunning(false)
                Toast.makeText(
                    this,
                    getString(R.string.audio_permission_required),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    private val mediaSessionManager by lazy {
        getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    }

    private var activeMediaController: MediaController? = null
    private val mediaCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: android.media.MediaMetadata?) {
            val bitmap = metadata?.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata?.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART)
            viewModel.updateMusicColor(bitmap)
        }

        override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) {
            if (state?.state == android.media.session.PlaybackState.STATE_PLAYING) {
                updateActiveMediaController()
            }
        }
    }

    private val sessionsChangedListener = MediaSessionManager.OnActiveSessionsChangedListener { 
        updateActiveMediaController()
    }

    private fun updateActiveMediaController() {
        try {
            if (!isNotificationServiceEnabled()) {
                viewModel.updateMusicColor(null)
                return
            }

            val componentName = ComponentName(this, com.better.nothing.music.vizualizer.service.GlyphNotificationListener::class.java)
            val sessions = mediaSessionManager.getActiveSessions(componentName)
            
            val controller = sessions.firstOrNull { 
                it.playbackState?.state == PlaybackState.STATE_PLAYING 
            } ?: sessions.firstOrNull()

            if (controller?.packageName != activeMediaController?.packageName || activeMediaController == null) {
                activeMediaController?.unregisterCallback(mediaCallback)
                activeMediaController = controller
                activeMediaController?.registerCallback(mediaCallback)
            }

            val metadata = controller?.metadata
            val bitmap = getArtworkBitmap(metadata)
            viewModel.updateMusicColor(bitmap)
        } catch (e: SecurityException) {
            Log.e("BetterViz", "No notification access to get media sessions")
            viewModel.updateMusicColor(null)
        } catch (e: Exception) {
            Log.e("BetterViz", "Error updating media controller", e)
            viewModel.updateMusicColor(null)
        }
    }

    private fun getArtworkBitmap(metadata: MediaMetadata?): Bitmap? {
        if (metadata == null) return null
        
        // 1. Try direct bitmaps
        metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)?.let { return it }
        metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)?.let { return it }
        
        // 2. Try URIs
        val uriString = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ART_URI)
        
        if (uriString != null) {
            try {
                val uri = Uri.parse(uriString)
                contentResolver.openInputStream(uri)?.use { stream ->
                    return BitmapFactory.decodeStream(stream)
                }
            } catch (e: Exception) {
                Log.e("BetterViz", "Failed to decode URI artwork: $uriString", e)
            }
        }
        
        // 3. Last resort: icon
        return metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
    }

    private val overlayPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (Settings.canDrawOverlays(this)) {
                viewModel.setOverlayEnabled(true)
            } else {
                Toast.makeText(this, getString(R.string.overlay_permission_denied), Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val analytics = AnalyticsHelper(this)
        analytics.logScreenView("main_screen")

        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            auth.signInAnonymously()
                .addOnSuccessListener {
                    initDatabase()
                }
                .addOnFailureListener { e ->
                    Log.e("BetterViz", "Anonymous sign-in failed", e)
                    initDatabase()
                }
        } else {
            initDatabase()
        }

        setContent {
            val selectedTheme by viewModel.selectedTheme.collectAsStateWithLifecycle()
            val selectedFont by viewModel.selectedFont.collectAsStateWithLifecycle()
            val m3eEnabled by viewModel.m3eEnabled.collectAsStateWithLifecycle()
            val uiAmplitude by viewModel.uiAmplitude.collectAsStateWithLifecycle()

            BetterVizTheme(
                themeName = selectedTheme,
                fontName = selectedFont,
                m3eEnabled = m3eEnabled,
                uiAmplitudeProvider = { uiAmplitude },
                musicPrimaryColor = viewModel.musicPrimaryColor.collectAsStateWithLifecycle().value
            ) {
                LaunchedEffect(selectedTheme) {
                    if (selectedTheme == "Music") {
                        updateActiveMediaController()
                    }
                }
                // Collect each StateFlow independently. Compose only recomposes the
                // subtree(s) that actually read a value when it changes — collecting
                // them as separate `by` delegates achieves this granularity.
                val tab            by viewModel.selectedTab.collectAsStateWithLifecycle()
                val isRunning      by viewModel.runningState.collectAsStateWithLifecycle()
                val latencyMs      by viewModel.latencyMs.collectAsStateWithLifecycle()
                val latencyPresets by viewModel.latencyPresets.collectAsStateWithLifecycle()
                val gammaValue     by viewModel.gammaValue.collectAsStateWithLifecycle()
                val maxBrightness  by viewModel.maxBrightness.collectAsStateWithLifecycle()
                val presets        by viewModel.presetInfos.collectAsStateWithLifecycle()
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
                val isBeatDetected by viewModel.isBeatDetected.collectAsStateWithLifecycle()

                val flashlightEnabled by viewModel.flashlightEnabled.collectAsStateWithLifecycle()
                val flashlightMode by viewModel.flashlightMode.collectAsStateWithLifecycle()
                val flashlightFreqMin by viewModel.flashlightFreqMin.collectAsStateWithLifecycle()
                val flashlightFreqMax by viewModel.flashlightFreqMax.collectAsStateWithLifecycle()
                val flashlightThreshold by viewModel.flashlightThreshold.collectAsStateWithLifecycle()
                val flashlightSpeedMs by viewModel.flashlightSpeedMs.collectAsStateWithLifecycle()
                val flashlightBeatSensitivity by viewModel.flashlightBeatSensitivity.collectAsStateWithLifecycle()
                val flashlightIntensityLevels by viewModel.flashlightIntensityLevels.collectAsStateWithLifecycle()
                val flashlightAmplitude by viewModel.flashlightAmplitude.collectAsStateWithLifecycle()
                val isFlashlightBeatDetected by viewModel.isFlashlightBeatDetected.collectAsStateWithLifecycle()

                val idleBreathingEnabled by viewModel.idleBreathingEnabled.collectAsStateWithLifecycle()
                val idlePattern by viewModel.idlePattern.collectAsStateWithLifecycle()
                val notificationFlashEnabled by viewModel.notificationFlashEnabled.collectAsStateWithLifecycle()
                val strobeEnabled by viewModel.strobeEnabled.collectAsStateWithLifecycle()
                val disableGlyphsWhenSilent by viewModel.disableGlyphsWhenSilent.collectAsStateWithLifecycle()
                val selectedDevice by viewModel.selectedDevice.collectAsStateWithLifecycle()
                val isEditingPreset by viewModel.isEditingPreset.collectAsStateWithLifecycle()
                val isShowingAbout by viewModel.isShowingAbout.collectAsStateWithLifecycle()
                val isShowingLicense by viewModel.isShowingLicense.collectAsStateWithLifecycle()
                val showUpdateDialog by viewModel.showUpdateDialog.collectAsStateWithLifecycle()
                val appUpdateStatus by viewModel.appUpdateStatus.collectAsStateWithLifecycle()

                if (showUpdateDialog) {
                    val status = appUpdateStatus
                    if (status is MainViewModel.AppUpdateStatus.Available) {
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = viewModel::dismissUpdateDialog,
                            title = { androidx.compose.material3.Text(stringResource(R.string.update_available_title, status.version)) },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    androidx.compose.material3.Text(stringResource(R.string.update_available_msg))
                                    if (status.changelog != null) {
                                        androidx.compose.foundation.layout.Box(
                                            modifier = Modifier
                                                .heightIn(max = 200.dp)
                                                .verticalScroll(rememberScrollState())
                                                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                                                .padding(8.dp)
                                        ) {
                                            androidx.compose.material3.Text(
                                                text = status.changelog,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    } else {
                                        androidx.compose.material3.Text(stringResource(R.string.no_changelog))
                                    }
                                }
                            },
                            confirmButton = {
                                val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                                androidx.compose.material3.Button(onClick = {
                                    if (status.apkUrl != null) {
                                        viewModel.downloadAndInstallUpdate(status.apkUrl, status.version)
                                    } else {
                                        uriHandler.openUri(status.url)
                                        viewModel.dismissUpdateDialog()
                                    }
                                }) {
                                    androidx.compose.material3.Text(if (status.apkUrl != null) stringResource(R.string.install) else stringResource(R.string.download))
                                }
                            },
                            dismissButton = {
                                androidx.compose.material3.TextButton(onClick = viewModel::dismissUpdateDialog) {
                                    androidx.compose.material3.Text(stringResource(R.string.not_now))
                                }
                            }
                        )
                    } else if (status is MainViewModel.AppUpdateStatus.Downloading) {
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = {}, // Prevent dismiss while downloading
                            title = { androidx.compose.material3.Text(stringResource(R.string.downloading_update)) },
                            text = {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    LinearProgressIndicator(
                                        progress = { status.progress },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    androidx.compose.material3.Text("${(status.progress * 100).toInt()}%")
                                }
                            },
                            confirmButton = {},
                            dismissButton = {}
                        )
                    }
                }

                if (isEditingPreset) {
                    androidx.compose.ui.window.Dialog(
                        onDismissRequest = viewModel::hideEditor,
                        properties = androidx.compose.ui.window.DialogProperties(
                            usePlatformDefaultWidth = false,
                            decorFitsSystemWindows = false
                        )
                    ) {
                        CustomPresetEditorScreen(
                            onDismiss = viewModel::hideEditor,
                            onSave = viewModel::saveCustomPreset,
                            onShare = viewModel::sharePresetToCommunity,
                            selectedDevice = selectedDevice
                        )
                    }
                }

                if (isShowingAbout) {
                    androidx.compose.ui.window.Dialog(
                        onDismissRequest = viewModel::hideAbout,
                        properties = androidx.compose.ui.window.DialogProperties(
                            usePlatformDefaultWidth = false,
                            decorFitsSystemWindows = false
                        )
                    ) {
                        Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
                            AboutScreen(viewModel = viewModel, onDismiss = viewModel::hideAbout)
                        }
                    }
                }

                if (isShowingLicense) {
                    androidx.compose.ui.window.Dialog(
                        onDismissRequest = viewModel::hideLicense,
                        properties = androidx.compose.ui.window.DialogProperties(
                            usePlatformDefaultWidth = false,
                            decorFitsSystemWindows = false
                        )
                    ) {
                        Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
                            LicenseScreen(onDismiss = viewModel::hideLicense)
                        }
                    }
                }

                val isShowingCommunity by viewModel.isShowingCommunity.collectAsStateWithLifecycle()
                val isShowingLeaderboard by viewModel.isShowingLeaderboard.collectAsStateWithLifecycle()
                val leaderboardEntries by viewModel.leaderboardEntries.collectAsStateWithLifecycle()
                val communityPresets by viewModel.communityPresets.collectAsStateWithLifecycle()
                val communityError by viewModel.communityError.collectAsStateWithLifecycle()
                val userId by viewModel.userId.collectAsStateWithLifecycle()
                val thanksMessage by viewModel.thanksMessage.collectAsStateWithLifecycle()
                val latestAnnouncement by viewModel.latestAnnouncement.collectAsStateWithLifecycle()
                val showAnnouncementModal by viewModel.showAnnouncementModal.collectAsStateWithLifecycle()
                val showAnnouncementEditor by viewModel.showAnnouncementEditor.collectAsStateWithLifecycle()
                val showAnnouncementHistory by viewModel.showAnnouncementHistory.collectAsStateWithLifecycle()
                val announcementHistory by viewModel.announcementHistory.collectAsStateWithLifecycle()

                if (showAnnouncementModal && latestAnnouncement != null) {
                    AnnouncementModal(
                        announcement = latestAnnouncement!!,
                        onDismiss = viewModel::dismissAnnouncement
                    )
                }

                if (showAnnouncementEditor) {
                    androidx.compose.ui.window.Dialog(
                        onDismissRequest = viewModel::hideAnnouncementEditor,
                        properties = androidx.compose.ui.window.DialogProperties(
                            usePlatformDefaultWidth = false,
                            decorFitsSystemWindows = false
                        )
                    ) {
                        AnnouncementEditorScreen(
                            onDismiss = viewModel::hideAnnouncementEditor,
                            onPost = viewModel::postAnnouncement
                        )
                    }
                }

                if (showAnnouncementHistory) {
                    androidx.compose.ui.window.Dialog(
                        onDismissRequest = viewModel::hideAnnouncementHistory,
                        properties = androidx.compose.ui.window.DialogProperties(
                            usePlatformDefaultWidth = false,
                            decorFitsSystemWindows = false
                        )
                    ) {
                        AnnouncementHistoryScreen(
                            announcements = announcementHistory,
                            onDismiss = viewModel::hideAnnouncementHistory
                        )
                    }
                }

                if (thanksMessage != null) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = viewModel::dismissThanksMessage,
                        icon = {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.app_icon),
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = Color.Unspecified
                                )
                            }
                        },
                        title = { 
                            androidx.compose.material3.Text(
                                stringResource(R.string.app_name_caps), 
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 2.sp,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) 
                        },
                        text = { 
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Spacer(modifier = Modifier.height(16.dp))
                                androidx.compose.material3.Text(
                                    thanksMessage!!.uppercase(),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    lineHeight = 32.sp
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                androidx.compose.material3.Text(
                                    stringResource(R.string.appreciate_support),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    letterSpacing = 1.sp
                                )
                            }
                        },
                        confirmButton = {
                            androidx.compose.material3.Button(
                                onClick = viewModel::dismissThanksMessage,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                shape = RoundedCornerShape(24.dp),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                androidx.compose.material3.Text(
                                    stringResource(R.string.youre_welcome),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        },
                        shape = RoundedCornerShape(32.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = 8.dp
                    )
                }
                
                if (isShowingCommunity) {
                    androidx.compose.ui.window.Dialog(
                        onDismissRequest = viewModel::hideCommunity,
                        properties = androidx.compose.ui.window.DialogProperties(
                            usePlatformDefaultWidth = false,
                            decorFitsSystemWindows = false
                        )
                    ) {
                        CommunityPresetsScreen(
                            presets = communityPresets,
                            currentUserId = userId,
                            error = communityError,
                            onDownload = viewModel::downloadPreset,
                            onDelete = viewModel::deleteCommunityPreset,
                            onDismiss = viewModel::hideCommunity
                        )
                    }
                }

                if (isShowingLeaderboard) {
                    androidx.compose.ui.window.Dialog(
                        onDismissRequest = viewModel::hideLeaderboard,
                        properties = androidx.compose.ui.window.DialogProperties(
                            usePlatformDefaultWidth = false,
                            decorFitsSystemWindows = false
                        )
                    ) {
                        LeaderboardScreen(
                            entries = leaderboardEntries,
                            onDismiss = viewModel::hideLeaderboard
                        )
                    }
                }

                if (showProjectionInfoDialog) {
                    MediaProjectionInfoDialog(
                        onDismiss = {
                            pendingVisualizerStart = false
                            showProjectionInfoDialog = false
                        },
                        onContinue = {
                            markProjectionInfoShown()
                            showProjectionInfoDialog = false
                            launchProjection()
                        }
                    )
                }

                BetterVizApp(
                    viewModel = viewModel,
                    tab = tab,
                    onTabSelected = viewModel::selectTab,
                    isRunning = isRunning,
                    latencyMs = latencyMs,
                    onLatencyChanged = ::onLatencyChanged,
                    latencyPresets = latencyPresets,
                    onLatencyPresetsChanged = viewModel::updateLatencyPresets,
                    gammaValue = gammaValue,
                    onGammaChanged = ::onGammaChanged,
                    maxBrightness = maxBrightness,
                    onMaxBrightnessChanged = ::onMaxBrightnessChanged,
                    presets = presets,
                    selectedPreset = selectedPreset,
                    onPresetSelected = ::onPresetSelected,
                    onToggleVisualizer = ::toggleVisualizer,
                    onAutoDeviceToggle = ::onAutoDeviceToggle,
                    hapticMotorEnabled = hapticMotorEnabled,
                    onHapticMotorEnabledChanged = ::onHapticMotorEnabledChanged,
                    hapticMode = hapticMode,
                    onHapticModeChanged = ::onHapticModeChanged,
                    hapticFreqMin = hapticFreqMin,
                    hapticFreqMax = hapticFreqMax,
                    onHapticFreqRangeChanged = ::onHapticFreqRangeChanged,
                    hapticMultiplier = hapticMultiplier,
                    onHapticMultiplierChanged = ::onHapticMultiplierChanged,
                    hapticAudioGain = hapticAudioGain,
                    onHapticAudioGainChanged = ::onHapticAudioGainChanged,
                    hapticGamma = hapticGamma,
                    onHapticGammaChanged = ::onHapticGammaChanged,
                    hapticBeatSensitivity = hapticBeatSensitivity,
                    onHapticBeatSensitivityChanged = ::onHapticBeatSensitivityChanged,
                    hapticBeatGamma = hapticBeatGamma,
                    onHapticBeatGammaChanged = ::onHapticBeatGammaChanged,
                    hapticAmplitude = hapticAmplitude,
                    isBeatDetected = isBeatDetected,
                    flashlightEnabled = flashlightEnabled,
                    onFlashlightEnabledChanged = ::onFlashlightEnabledChanged,
                    flashlightMode = flashlightMode,
                    onFlashlightModeChanged = ::onFlashlightModeChanged,
                    flashlightFreqMin = flashlightFreqMin,
                    flashlightFreqMax = flashlightFreqMax,
                    onFlashlightFreqRangeChanged = ::onFlashlightFreqRangeChanged,
                    flashlightThreshold = flashlightThreshold,
                    onFlashlightThresholdChanged = ::onFlashlightThresholdChanged,
                    flashlightSpeedMs = flashlightSpeedMs,
                    onFlashlightSpeedMsChanged = ::onFlashlightSpeedMsChanged,
                    flashlightBeatSensitivity = flashlightBeatSensitivity,
                    onFlashlightBeatSensitivityChanged = ::onFlashlightBeatSensitivityChanged,
                    flashlightIntensityLevels = flashlightIntensityLevels,
                    flashlightAmplitude = flashlightAmplitude,
                    isFlashlightBeatDetected = isFlashlightBeatDetected,
                    idleBreathingEnabled = idleBreathingEnabled,
                    onIdleBreathingEnabledChanged = ::onIdleBreathingEnabledChanged,
                    idlePattern = idlePattern,
                    onIdlePatternChanged = ::onIdlePatternChanged,
                    notificationFlashEnabled = notificationFlashEnabled,
                    onNotificationFlashEnabledChanged = ::onNotificationFlashEnabledChanged,
                    strobeEnabled = strobeEnabled,
                    onStrobeEnabledChanged = ::onStrobeEnabledChanged,
                    disableGlyphsWhenSilent = disableGlyphsWhenSilent,
                    onDisableGlyphsWhenSilentChanged = ::onDisableGlyphsWhenSilentChanged,
                    overlayEnabled = viewModel.overlayEnabled.collectAsStateWithLifecycle().value,
                    onOverlayEnabledChanged = ::onOverlayEnabledChanged,
                    selectedDevice = selectedDevice,
                    currentThemeColor = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        if (savedInstanceState == null) {
            mainHandler.post { handleLaunchIntent(intent) }
        }
    }

    private fun initDatabase() {
        viewModel.initDatabase()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, mainHandler)
        try {
            val componentName = ComponentName(this, com.better.nothing.music.vizualizer.service.GlyphNotificationListener::class.java)
            mediaSessionManager.addOnActiveSessionsChangedListener(sessionsChangedListener, componentName)
        } catch (e: SecurityException) {
            Log.e("BetterViz", "No notification access for media sessions")
        }
        refreshConnectedAudioRoute()
    }

    override fun onStop() {
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        mediaSessionManager.removeOnActiveSessionsChangedListener(sessionsChangedListener)
        activeMediaController?.unregisterCallback(mediaCallback)
        activeMediaController = null
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // Single source of truth: push the real state into the ViewModel.
        // The poller will keep it in sync while the app is in the foreground.
        viewModel.setRunning(AudioCaptureService.isRunning())
        refreshConnectedAudioRoute()
        if (viewModel.selectedTheme.value == "Music") {
            updateActiveMediaController()
        }
    }

    override fun onDestroy() {
        if (bound) {
            unbindService(serviceConnection)
            bound = false
        }
        super.onDestroy()
    }

    // ── Settings delegates ────────────────────────────────────────────────────
    // Each function: (1) clamps, (2) updates ViewModel state (triggers UI),
    // (3) persists to disk off main thread via ViewModel, (4) forwards to service.

    private fun onLatencyChanged(value: Int) {
        viewModel.setLatencyMs(value)
        service?.setLatencyCompensationMs(value)
    }

    private fun onAutoDeviceToggle(enabled: Boolean) {
        val latency = viewModel.setAutoDeviceEnabled(enabled)
        service?.setLatencyCompensationMs(latency)
    }

    private fun onGammaChanged(value: Float) {
        viewModel.setGammaValue(value)
        viewModel.persistGamma(value)            // Dispatchers.IO — never blocks main
        service?.setGamma(value)
    }

    private fun onSpectrumGainChanged(value: Float) {
        viewModel.setSpectrumGain(value)
        viewModel.viewModelScope.launch(Dispatchers.IO) {
            getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putFloat("spectrum_gain", value) }
        }
        service?.setSpectrumGain(value)
    }

    private fun onMaxBrightnessChanged(value: Int) {
        viewModel.setMaxBrightness(value)
        service?.setMaxBrightness(value)
    }

    private fun onHapticMotorEnabledChanged(enabled: Boolean) {
        viewModel.setHapticMotorEnabled(enabled)
        service?.setHapticEnabled(enabled)
    }

    private fun onHapticModeChanged(mode: HapticMode) {
        viewModel.setHapticMode(mode)
        service?.setHapticMode(mode)
    }

    private fun onHapticFreqRangeChanged(min: Float, max: Float) {
        viewModel.setHapticFreqRange(min, max)
        service?.setHapticFreqRange(min, max)
    }

    private fun onHapticMultiplierChanged(multiplier: Float) {
        viewModel.setHapticMultiplier(multiplier)
        service?.setHapticMultiplier(multiplier)
    }

    private fun onHapticAudioGainChanged(gain: Float) {
        viewModel.setHapticAudioGain(gain)
        service?.setHapticAudioGain(gain)
    }

    private fun onHapticGammaChanged(gamma: Float) {
        viewModel.setHapticGamma(gamma)
        service?.setHapticGamma(gamma)
    }

    private fun onHapticBeatSensitivityChanged(sensitivity: Float) {
        viewModel.setHapticBeatSensitivity(sensitivity)
        service?.setHapticBeatSensitivity(sensitivity)
    }

    private fun onHapticBeatGammaChanged(gamma: Float) {
        viewModel.setHapticBeatGamma(gamma)
        service?.setHapticBeatGamma(gamma)
    }

    private fun onFlashlightEnabledChanged(enabled: Boolean) {
        viewModel.setFlashlightEnabled(enabled)
        service?.setFlashlightEnabled(enabled)
    }

    private fun onFlashlightModeChanged(mode: TorchMode) {
        viewModel.setFlashlightMode(mode)
        service?.setFlashlightMode(mode)
    }

    private fun onFlashlightFreqRangeChanged(min: Float, max: Float) {
        viewModel.setFlashlightFreqRange(min, max)
        service?.setFlashlightFreqRange(min, max)
    }

    private fun onFlashlightThresholdChanged(threshold: Float) {
        viewModel.setFlashlightThreshold(threshold)
        service?.setFlashlightThreshold(threshold)
    }

    private fun onFlashlightSpeedMsChanged(speedMs: Float) {
        viewModel.setFlashlightSpeedMs(speedMs)
        service?.setFlashlightSpeedMs(speedMs)
    }

    private fun onFlashlightBeatSensitivityChanged(sensitivity: Float) {
        viewModel.setFlashlightBeatSensitivity(sensitivity)
        service?.setFlashlightBeatSensitivity(sensitivity)
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
            Toast.makeText(this, getString(R.string.notification_access_required), Toast.LENGTH_LONG).show()
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
        } else {
            viewModel.setOverlayEnabled(enabled)
            service?.setOverlayEnabled(enabled)
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val pkgName = packageName
        val flat = android.provider.Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        if (!flat.isNullOrEmpty()) {
            val names = flat.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            for (name in names) {
                val cn = ComponentName.unflattenFromString(name)
                if (cn != null) {
                    if (pkgName == cn.packageName) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun onPresetSelected(key: String) {
        viewModel.setSelectedPreset(key)
        service?.setPreset(key)
    }

    // ── Visualizer lifecycle ──────────────────────────────────────────────────

    private fun toggleVisualizer() {
        if (AudioCaptureService.isRunning()) {
            stopEverything()
        } else {
            when (viewModel.captureSource.value) {
                AudioCaptureService.CaptureSource.MIC,
                AudioCaptureService.CaptureSource.VIZUALIZER -> startStandardVisualizer()
                AudioCaptureService.CaptureSource.SHIZUKU -> startShizukuVisualizer()
                else -> requestProjection()
            }
        }
    }

    private fun startShizukuVisualizer() {
        // Notification permission is still good to have for the FGS
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }

        // Even with Shizuku, AudioRecord usually needs the base permission granted to the app
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        val serviceIntent = Intent(this, AudioCaptureService::class.java).apply {
            putExtra(AudioCaptureService.EXTRA_PRESET_KEY, viewModel.currentPreset())
        }
        ContextCompat.startForegroundService(this, serviceIntent)
        if (!bound) bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE)
        
        if (bound && service != null) {
            applyServiceSettings()
            service?.startShizukuCapture()
        } else {
            pendingVisualizerStart = true
        }
        viewModel.setRunning(true)
    }

    private fun startStandardVisualizer() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        val serviceIntent = Intent(this, AudioCaptureService::class.java).apply {
            putExtra(AudioCaptureService.EXTRA_PRESET_KEY, viewModel.currentPreset())
        }
        ContextCompat.startForegroundService(this, serviceIntent)
        if (!bound) bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE)
        if (bound && service != null) {
            applyServiceSettings()
            if (viewModel.captureSource.value == AudioCaptureService.CaptureSource.VIZUALIZER) {
                service?.startVizualizerCapture()
            } else {
                service?.startMicCapture()
            }
        } else {
            pendingVisualizerStart = true
        }
        viewModel.setRunning(true)
        TileService.requestListeningState(
            this,
            ComponentName(this, VisualizerTileService::class.java)
        )
    }

    private fun requestProjection() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }

        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        if (shouldShowProjectionInfo()) {
            showProjectionInfoDialog = true
        } else {
            launchProjection()
        }
    }

    private fun launchProjection() {
        pendingVisualizerStart = false
        projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun deliverProjectionToken(resultCode: Int, data: Intent) {
        val serviceIntent = Intent(this, AudioCaptureService::class.java).apply {
            putExtra(AudioCaptureService.EXTRA_PRESET_KEY, viewModel.currentPreset())
        }
        ContextCompat.startForegroundService(this, serviceIntent)
        if (!bound) bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE)
        if (bound && service != null) {
            applyServiceSettings()
            service?.startCapture(resultCode, data)
        } else {
            pendingResultCode = resultCode
            pendingData       = data
            hasPendingToken   = true
        }
        viewModel.setRunning(true)
        TileService.requestListeningState(
            this,
            ComponentName(this, VisualizerTileService::class.java)
        )
    }

    /** Reads latest values directly from ViewModel StateFlows — always current. */
    private fun applyServiceSettings() {
        service?.setDevice(viewModel.selectedDevice.value)
        service?.setLatencyCompensationMs(viewModel.latencyMs.value)
        service?.setGamma(viewModel.gammaValue.value)
        service?.setSpectrumGain(viewModel.spectrumGain.value)
        service?.setMaxBrightness(viewModel.maxBrightness.value)
        service?.setIdleBreathingEnabled(viewModel.idleBreathingEnabled.value)
        service?.setIdlePattern(viewModel.idlePattern.value)
        service?.setNotificationFlashEnabled(viewModel.notificationFlashEnabled.value)
        service?.setStrobeEnabled(viewModel.strobeEnabled.value)
        service?.setDynamicGainEnabled(viewModel.dynamicGainEnabled.value)
        service?.setOverlayEnabled(viewModel.overlayEnabled.value)
        service?.setOverlayWidth(viewModel.overlayWidth.value)
        service?.setOverlayHeight(viewModel.overlayHeight.value)
        service?.setOverlayYOffset(viewModel.overlayYOffset.value)
        viewModel.musicPrimaryColor.value?.let { service?.setOverlayColor(it.toArgb()) }

        service?.setHapticEnabled(viewModel.hapticMotorEnabled.value)
        service?.setHapticMode(viewModel.hapticMode.value)
        service?.setHapticFreqRange(viewModel.hapticFreqMin.value, viewModel.hapticFreqMax.value)
        service?.setHapticMultiplier(viewModel.hapticMultiplier.value)
        service?.setHapticAudioGain(viewModel.hapticAudioGain.value)
        service?.setHapticGamma(viewModel.hapticGamma.value)
        service?.setHapticBeatSensitivity(viewModel.hapticBeatSensitivity.value)
        service?.setHapticBeatGamma(viewModel.hapticBeatGamma.value)
        service?.setDisableGlyphsWhenSilent(viewModel.disableGlyphsWhenSilent.value)

        service?.setFlashlightEnabled(viewModel.flashlightEnabled.value)
        service?.setFlashlightMode(viewModel.flashlightMode.value)
        service?.setFlashlightFreqRange(viewModel.flashlightFreqMin.value, viewModel.flashlightFreqMax.value)
        service?.setFlashlightThreshold(viewModel.flashlightThreshold.value)
        service?.setFlashlightSpeedMs(viewModel.flashlightSpeedMs.value)
        service?.setFlashlightBeatSensitivity(viewModel.flashlightBeatSensitivity.value)

        service?.setCaptureSource(viewModel.captureSource.value)
        val preset = viewModel.currentPreset()
        if (preset.isNotBlank()) service?.setPreset(preset)
    }

    private fun refreshConnectedAudioRoute() {
        val route = resolvePreferredAudioRoute()
        val latency = viewModel.updateConnectedDevice(
            routeKey = route?.storageKey,
            name = route?.displayName ?: getString(R.string.internal_speaker)
        )
        service?.setLatencyCompensationMs(latency)
    }

    private fun stopEverything() {
        service?.stopCapture()
        if (bound) {
            unbindService(serviceConnection)
            bound = false
        }
        service           = null
        pendingResultCode = 0
        pendingData       = null
        hasPendingToken   = false
        stopService(Intent(this, AudioCaptureService::class.java))
        TileService.requestListeningState(
            this,
            ComponentName(this, VisualizerTileService::class.java)
        )
    }

    private fun handleLaunchIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_REQUEST_START, false) != true) return
        intent.putExtra(EXTRA_REQUEST_START, false)
        if (!AudioCaptureService.isRunning()) {
            toggleVisualizer()
        } else {
            TileService.requestListeningState(
                this,
                ComponentName(this, VisualizerTileService::class.java)
            )
        }
    }

    private fun shouldShowProjectionInfo(): Boolean {
        return !getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
            .getBoolean(PREF_PROJECTION_INFO_SHOWN, false)
    }

    private fun markProjectionInfoShown() {
        getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
            .edit { putBoolean(PREF_PROJECTION_INFO_SHOWN, true) }
    }

    private fun resolvePreferredAudioRoute(): AudioRoute? {
        val outputs = audioManager
            .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter(::isUsefulOutputRoute)

        val preferredDevice = outputs.firstOrNull { it.isBluetoothOutput() }
            ?: outputs.firstOrNull { it.isWiredOutput() }
            ?: outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            ?: outputs.firstOrNull()

        return preferredDevice?.toAudioRoute()
    }
}

private fun isUsefulOutputRoute(device: AudioDeviceInfo): Boolean {
    return when (device.type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_BLE_BROADCAST,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> true
        else -> false
    }
}

private fun AudioDeviceInfo.isBluetoothOutput(): Boolean {
    return type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
            || type == AudioDeviceInfo.TYPE_BLE_HEADSET
            || type == AudioDeviceInfo.TYPE_BLE_SPEAKER
            || type == AudioDeviceInfo.TYPE_BLE_BROADCAST
}

private fun AudioDeviceInfo.isWiredOutput(): Boolean {
    return type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
            || type == AudioDeviceInfo.TYPE_WIRED_HEADSET
            || type == AudioDeviceInfo.TYPE_USB_HEADSET
}

private fun AudioDeviceInfo.toAudioRoute(): AudioRoute {
    val routeName = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Internal Speaker" // This is just for key generation/display, but let's leave it for now or pass context if needed.
        else -> productName?.toString()?.takeIf { it.isNotBlank() } ?: "Unknown Output"
    }
    val normalizedName = routeName.lowercase()
        .replace(Regex("[^a-z0-9._-]+"), "_")
        .trim('_')
        .ifBlank { "unknown_output" }
    val normalizedAddress = address
        .lowercase()
        ?.replace(Regex("[^a-z0-9._-]+"), "_")
        ?.trim('_')
        ?.takeIf { it.isNotBlank() }
    val routeKey = listOf(type.toString(), normalizedAddress ?: normalizedName)
        .joinToString("_")

    return AudioRoute(
        storageKey = routeKey,
        displayName = routeName,
    )
}


private val HeavyEasingSpec = tween<Float>(
    durationMillis = 600,
    easing = EaseOutQuart
)

@Composable
private fun MediaProjectionInfoDialog(
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            androidx.compose.material3.Text(stringResource(R.string.mediaprojection_permission))
        },
        text = {
            androidx.compose.material3.Text(
                stringResource(R.string.mediaprojection_description)
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onContinue) {
                androidx.compose.material3.Text(stringResource(R.string.continue_label))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                androidx.compose.material3.Text(stringResource(R.string.not_now))
            }
        }
    )
}

@Composable
private fun BetterVizApp(
    viewModel: MainViewModel,
    tab: Tab,
    onTabSelected: (Tab) -> Unit,
    isRunning: Boolean,
    latencyMs: Int,
    onLatencyChanged: (Int) -> Unit,
    latencyPresets: List<Int>,
    onLatencyPresetsChanged: (List<Int>) -> Unit,
    gammaValue: Float,
    onGammaChanged: (Float) -> Unit,
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
    hapticAmplitude: Float,
    isBeatDetected: Boolean,
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
    flashlightAmplitude: Float,
    isFlashlightBeatDetected: Boolean,
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
    selectedDevice: Int,
    currentThemeColor: Color,
) {
    val autoDeviceEnabled by viewModel.autoDeviceEnabled.collectAsStateWithLifecycle()
    val connectedDeviceName by viewModel.connectedDeviceName.collectAsStateWithLifecycle()

    val availableTabs = remember(selectedDevice) {
        if (selectedDevice == DeviceProfile.DEVICE_UNKNOWN) {
            listOf(Tab.Audio, Tab.Haptics, Tab.Flashlight, Tab.Settings)
        } else {
            listOf(Tab.Audio, Tab.Glyphs, Tab.Haptics, Tab.Flashlight, Tab.Settings)
        }
    }

    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // ─── Polling: Update Live Preview ────────────────────────────────────────
    LaunchedEffect(isRunning) {
        if (isRunning) {
            while (true) {
                MainActivity.serviceStatic?.let { s ->
                    viewModel.updateVisualizerState(s.currentLightState)
                    viewModel.updateFftState(s.latestMagnitudes)
                    viewModel.updateSessionDuration(s.getCaptureDurationMs())
                }
                delay(16)
            }
        } else {
            viewModel.updateVisualizerState(floatArrayOf())
            viewModel.updateFftState(floatArrayOf())
            viewModel.updateSessionDuration(0)
        }
    }

    val pagerState = rememberPagerState(
        initialPage = availableTabs.indexOf(tab).coerceAtLeast(0),
        pageCount = { availableTabs.size }
    )

    // ─── Haptics: Trigger exactly at 50% threshold ────────────────────────────
    // snapshotFlow ignores the "settle" and fires as soon as the index integer flips
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect {
            // Only vibrate if the user is actually swiping
            if (pagerState.isScrollInProgress) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            }
        }
    }

    // ─── Sync Pager -> ViewModel ──────────────────────────────────────────────
    LaunchedEffect(pagerState.settledPage) {
        val targetTab = availableTabs.getOrNull(pagerState.settledPage)
        if (targetTab != null && targetTab != tab) {
            onTabSelected(targetTab)
        }
    }

    // ─── Sync ViewModel -> Pager ──────────────────────────────────────────────
    LaunchedEffect(tab) {
        val targetPage = availableTabs.indexOf(tab)
        if (targetPage != -1 && targetPage != pagerState.currentPage) {
            pagerState.animateScrollToPage(targetPage, animationSpec = HeavyEasingSpec)
        }
    }

    // ─── Sync Theme Color -> Service ────────────────────────────────────────
    val selectedTheme by viewModel.selectedTheme.collectAsStateWithLifecycle()
    val musicPrimaryColor by viewModel.musicPrimaryColor.collectAsStateWithLifecycle()

    LaunchedEffect(currentThemeColor, selectedTheme, musicPrimaryColor) {
        if (selectedTheme == "Music" && musicPrimaryColor != null) {
            MainActivity.serviceStatic?.setOverlayColor(musicPrimaryColor!!.toArgb())
        } else {
            MainActivity.serviceStatic?.setOverlayColor(currentThemeColor.toArgb())
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            StartStopButton(running = isRunning, onClick = onToggleVisualizer)
        },
        bottomBar = {
            NativeBottomBar(
                selectedTab = availableTabs[pagerState.currentPage], // Snap highlight to current page
                visibleTabs = availableTabs,
                onTabSelected = { targetTab ->
                    val index = availableTabs.indexOf(targetTab)
                    if (index != -1 && index != pagerState.currentPage) {
                        scope.launch {
                            pagerState.animateScrollToPage(index, animationSpec = HeavyEasingSpec)
                        }
                    }
                }
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                userScrollEnabled = true,
                pageSpacing = 10.dp
            ) { pageIndex ->
                val currentTab = availableTabs[pageIndex]
                val pageOffset = ((pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction).absoluteValue

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val fraction = 1f - pageOffset.coerceIn(0f, 1f)
                            // Your signature bouncy scaling
                            val scale = 0.8f + (1f - 0.8f) * fraction
                            scaleX = scale
                            scaleY = scale
                            // Only show if it's the current page or being transitioned
                            alpha = fraction * fraction // Square the fraction for faster fade-out
                        }
                ) {
                    when (currentTab) {
                        Tab.Audio -> {
                            val fftData by viewModel.fftState.collectAsStateWithLifecycle()
                            val captureSource by viewModel.captureSource.collectAsStateWithLifecycle()
                            val sessionDuration by viewModel.sessionDuration.collectAsStateWithLifecycle()
                            val shizukuUnlocked by viewModel.shizukuSourceUnlocked.collectAsStateWithLifecycle()
                            AudioScreen(
                                isRunning = isRunning,
                                sessionDuration = sessionDuration,
                                latencyMs = latencyMs,
                                onLatencyChanged = onLatencyChanged,
                                latencyPresets = latencyPresets,
                                onLatencyPresetsChanged = viewModel::updateLatencyPresets,
                                autoDeviceEnabled = autoDeviceEnabled,
                                onAutoDeviceToggle = onAutoDeviceToggle,
                                connectedDeviceName = connectedDeviceName,
                                fftData = fftData,
                                captureSource = captureSource,
                                onCaptureSourceChanged = viewModel::setCaptureSource,
                                shizukuUnlocked = shizukuUnlocked,
                                dynamicGainEnabled = viewModel.dynamicGainEnabled.collectAsStateWithLifecycle().value,
                                onDynamicGainToggle = viewModel::setDynamicGainEnabled
                            )
                        }
                        Tab.Glyphs -> GlyphsScreen(
                            gammaValue = gammaValue,
                            onGammaChanged = onGammaChanged,
                            maxBrightness = maxBrightness,
                            onMaxBrightnessChanged = onMaxBrightnessChanged,
                            presets = presets,
                            selectedPreset = selectedPreset,
                            onPresetSelected = onPresetSelected,
                            isRunning = isRunning,
                            selectedDevice = selectedDevice,
                            viewModel = viewModel,
                        )
                        Tab.Haptics -> HapticsScreen(
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
                            hapticAmplitudeProvider = { hapticAmplitude },
                            isBeatDetectedProvider = { isBeatDetected },
                        )
                        Tab.Flashlight -> FlashlightScreen(
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
                            flashlightAmplitudeProvider = { flashlightAmplitude },
                            isBeatDetectedProvider = { isFlashlightBeatDetected },
                        )
                        Tab.Settings -> SettingsScreen(
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
                            onOverlayEnabledChanged = onOverlayEnabledChanged,
                        )
                    }
                }
            }
        }
    }
}
