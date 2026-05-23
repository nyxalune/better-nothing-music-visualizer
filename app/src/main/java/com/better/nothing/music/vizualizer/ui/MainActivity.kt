/*
////
//////
////////
//////////
////////////
// TO DO LIST HEREEEE:::::
////////////////
//https://taskweb.pages.dev/?board=mauv5VZ29Gw1vnbExSXb#
////////////////
//////////////
////////////
//////////
////////
//////
////
//
*/

package com.better.nothing.music.vizualizer.ui

import com.better.nothing.music.vizualizer.R
import com.better.nothing.music.vizualizer.BuildConfig
import com.better.nothing.music.vizualizer.model.HapticMode
import com.better.nothing.music.vizualizer.model.DeviceProfile
import com.better.nothing.music.vizualizer.logic.AudioProcessor
import com.better.nothing.music.vizualizer.service.AudioCaptureService
import com.better.nothing.music.vizualizer.service.HapticsTileService
import com.better.nothing.music.vizualizer.service.VisualizerTileService
import com.better.nothing.music.vizualizer.util.AnalyticsHelper
import com.better.nothing.music.vizualizer.logic.CommunityRepository
import com.better.nothing.music.vizualizer.model.CommunityPreset
import com.better.nothing.music.vizualizer.model.ZoneData
import com.better.nothing.music.vizualizer.ui.CommunityPresetsScreen

import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresPermission
import androidx.compose.animation.core.EaseOutQuart
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
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


// ─── Tab ─────────────────────────────────────────────────────────────────────
// Promoted to internal so MainViewModel can reference it without reflection.

enum class Tab(val label: String) {
    Audio("Audio"), Glyphs("Glyphs"), Haptics("Haptics"), Settings("Settings");

}

private data class AudioRoute(
    val storageKey: String,
    val displayName: String,
)

// ─── ViewModel ────────────────────────────────────────────────────────────────
//
// All mutable state lives here as MutableStateFlow so that:
//   • State survives configuration changes — no full UI rebuild on rotation.
//   • Collectors only recomposes the subtree that reads a particular flow.
//   • All IO / CPU work is dispatched off the main thread.

internal class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val ctx = application
    private val communityRepository = CommunityRepository()
    private val analytics = AnalyticsHelper(application)

    private val _favoritePresets = MutableStateFlow<Set<String>>(emptySet())
    val favoritePresets = _favoritePresets.asStateFlow()

    private val _captureSource = MutableStateFlow(AudioCaptureService.CaptureSource.INTERNAL)
    val captureSource = _captureSource.asStateFlow()

    init {
        val prefs = ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
        _favoritePresets.value = prefs.getStringSet("favorite_presets", emptySet()) ?: emptySet()
        
        val savedSource = prefs.getString("capture_source", AudioCaptureService.CaptureSource.INTERNAL.name)
        _captureSource.value = AudioCaptureService.CaptureSource.valueOf(savedSource ?: AudioCaptureService.CaptureSource.INTERNAL.name)
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

    // ── Tab ───────────────────────────────────────────────────────────────────
    private val _selectedTab = MutableStateFlow(Tab.Audio)
    val selectedTab = _selectedTab.asStateFlow()
    fun selectTab(tab: Tab) { _selectedTab.value = tab }

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
                return true
            } else if (Shizuku.shouldShowRequestPermissionRationale()) {
                Toast.makeText(ctx, "Shizuku permission is required for this source", Toast.LENGTH_LONG).show()
                return false
            } else {
                Shizuku.requestPermission(1001)
                return false
            }
        } catch (e: Exception) {
            Toast.makeText(ctx, "Shizuku not running or not installed", Toast.LENGTH_LONG).show()
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

    fun setDeveloperModeEnabled(enabled: Boolean) {
        _developerModeEnabled.value = enabled
        updateSelectedDevice()
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putBoolean("developer_mode_enabled", enabled) }
        }
    }

    fun setSpoofedDevice(device: Int) {
        _spoofedDevice.value = device
        if (_developerModeEnabled.value) {
            updateSelectedDevice()
        }
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putInt("spoofed_device", device) }
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

    private val _configVersion = MutableStateFlow("Unknown")
    val configVersion = _configVersion.asStateFlow()

    private val _remoteConfigVersion = MutableStateFlow<String?>(null)
    val remoteConfigVersion = _remoteConfigVersion.asStateFlow()

    private val _appRemoteVersion = MutableStateFlow<String?>(null)
    val appRemoteVersion = _appRemoteVersion.asStateFlow()

    private val _disableGlyphsWhenSilent = MutableStateFlow(false)
    val disableGlyphsWhenSilent = _disableGlyphsWhenSilent.asStateFlow()

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
        data class Available(val version: String, val url: String) : AppUpdateStatus()
        object UpToDate : AppUpdateStatus()
        data class Error(val message: String) : AppUpdateStatus()
    }

    // FIXED: Proper thread handling for network and UI updates
    fun updateZonesConfig() {
        // 1. Set loading state immediately on Main Thread
        _configUpdateStatus.value = ConfigUpdateStatus.Updating

        viewModelScope.launch {
            try {
                // 2. Perform network/download on IO Thread
                val success = withContext(Dispatchers.IO) {
                    performUpdateAction()
                }

                // 3. Back on Main Thread automatically after withContext
                if (success) {
                    _configUpdateStatus.value = ConfigUpdateStatus.Success("Successfully updated zones.config")
                }
                // Errors are handled inside performUpdateAction setting the status directly now,
                // or we could return Result object. To keep it simple with existing code:
            } catch (e: Exception) {
                // Catch unexpected errors
                _configUpdateStatus.value = ConfigUpdateStatus.Error("Error updating: ${e.message}")
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
                    _configUpdateStatus.value = ConfigUpdateStatus.Error("Failed to download: ${connection.responseCode}")
                }
                false
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                _configUpdateStatus.value = ConfigUpdateStatus.Error("Error updating: ${e.message}")
            }
            false
        }
    }

    fun importZonesConfig(uri: Uri) {
        _configUpdateStatus.value = ConfigUpdateStatus.Updating
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
                    _configUpdateStatus.value = ConfigUpdateStatus.Success("Successfully imported zones.config")
                } else {
                    _configUpdateStatus.value = ConfigUpdateStatus.Error("Failed to read selected file")
                }
            } catch (e: Exception) {
                _configUpdateStatus.value = ConfigUpdateStatus.Error("Error importing: ${e.message}")
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
                    
                    val latestVersion = tagName.removePrefix("v")
                    val currentVersion = BuildConfig.VERSION_NAME

                    _appRemoteVersion.value = latestVersion

                    if (isNewerVersion(currentVersion, latestVersion)) {
                        _appUpdateStatus.value = AppUpdateStatus.Available(latestVersion, htmlUrl)
                        _showUpdateDialog.value = true
                    } else {
                        _appUpdateStatus.value = AppUpdateStatus.UpToDate
                    }
                } else {
                    val errorMsg = "Check failed: ${connection.responseCode}"
                    _appUpdateStatus.value = AppUpdateStatus.Error(errorMsg)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(ctx, errorMsg, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                val errorMsg = "Update check error: ${e.message}"
                _appUpdateStatus.value = AppUpdateStatus.Error(errorMsg)
                Log.e("MainViewModel", "App update check failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, errorMsg, Toast.LENGTH_SHORT).show()
                }
            }
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

    private val _selectedFont = MutableStateFlow("NDot")
    val selectedFont = _selectedFont.asStateFlow()

    fun setSelectedTheme(theme: String) {
        _selectedTheme.value = theme
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putString("selected_theme", theme) }
        }
    }

    fun setSelectedFont(font: String) {
        _selectedFont.value = font
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

    fun updateVisualizerState(state: FloatArray) {
        _visualizerState.value = state
    }

    fun updateFftState(state: FloatArray) {
        _fftState.value = state
    }

    private val _isEditingPreset = MutableStateFlow(false)
    val isEditingPreset = _isEditingPreset.asStateFlow()

    fun showEditor() { _isEditingPreset.value = true }
    fun hideEditor() { _isEditingPreset.value = false }

    private val _isShowingAbout = MutableStateFlow(false)
    val isShowingAbout = _isShowingAbout.asStateFlow()

    fun showAbout() { _isShowingAbout.value = true }
    fun hideAbout() { _isShowingAbout.value = false }

    private val _isShowingTimeline = MutableStateFlow(false)
    val isShowingTimeline = _isShowingTimeline.asStateFlow()

    fun showTimeline() { _isShowingTimeline.value = true }
    fun hideTimeline() { _isShowingTimeline.value = false }

    private val _isShowingCommunity = MutableStateFlow(false)
    val isShowingCommunity = _isShowingCommunity.asStateFlow()

    fun showCommunity() { _isShowingCommunity.value = true }
    fun hideCommunity() { _isShowingCommunity.value = false }

    private val _communityError = MutableStateFlow<String?>(null)
    val communityError = _communityError.asStateFlow()

    val communityPresets = communityRepository.getPresets()
        .onEach { _communityError.value = null }
        .catch { e -> _communityError.value = e.message }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun sharePresetToCommunity(name: String, author: String, zones: List<AudioProcessor.ZoneSpec>) {
        viewModelScope.launch {
            val preset = CommunityPreset(
                name = name,
                author = author,
                phoneModel = phoneModelForDevice(selectedDevice.value),
                zones = zones.map { ZoneData.fromZoneSpec(it) }
            )
            communityRepository.uploadPreset(preset)
            analytics.logPresetShared(name)
        }
    }

    fun downloadPreset(preset: CommunityPreset) {
        saveCustomPreset(preset.name, preset.zones.map { it.toZoneSpec() })
        analytics.logCommunityPresetDownloaded(preset.name, preset.author)
        viewModelScope.launch {
            communityRepository.incrementDownloadCount(preset.id)
            _isShowingCommunity.value = false
        }
    }

    fun saveCustomPreset(name: String, zones: List<AudioProcessor.ZoneSpec>) {
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
                presetJson.put("description", "Custom: $name")
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

    private val _hapticGamma = MutableStateFlow(2.0f)
    val hapticGamma = _hapticGamma.asStateFlow()

    private val _richTapFrequency = MutableStateFlow(50)
    val richTapFrequency = _richTapFrequency.asStateFlow()

    fun setHapticMotorEnabled(enabled: Boolean) {
        _hapticMotorEnabled.value = enabled
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putBoolean("haptic_motor_enabled", enabled) }
            HapticsTileService.requestRefresh(ctx)
        }
    }

    fun setHapticMode(mode: HapticMode) {
        _hapticMode.value = mode
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
        _hapticMultiplier.value = multiplier
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putFloat("haptic_multiplier", multiplier) }
        }
    }

    fun setHapticGamma(gamma: Float) {
        _hapticGamma.value = gamma
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putFloat("haptic_gamma", gamma) }
        }
    }

    fun setRichTapFrequency(frequency: Int) {
        _richTapFrequency.value = frequency
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putInt("richtap_frequency", frequency) }
        }
    }

    // ── Flashlight Settings ──────────────────────────────────────────────────
    private val _flashlightEnabled = MutableStateFlow(false)
    val flashlightEnabled = _flashlightEnabled.asStateFlow()

    private val _flashlightFreqMin = MutableStateFlow(60f)
    val flashlightFreqMin = _flashlightFreqMin.asStateFlow()

    private val _flashlightFreqMax = MutableStateFlow(250f)
    val flashlightFreqMax = _flashlightFreqMax.asStateFlow()

    private val _flashlightMultiplier = MutableStateFlow(1.0f)
    val flashlightMultiplier = _flashlightMultiplier.asStateFlow()

    private val _flashlightThreshold = MutableStateFlow(0.15f)
    val flashlightThreshold = _flashlightThreshold.asStateFlow()

    private val _flashlightSmoothing = MutableStateFlow(0.7f)
    val flashlightSmoothing = _flashlightSmoothing.asStateFlow()

    private val _flashlightGamma = MutableStateFlow(2.2f)
    val flashlightGamma = _flashlightGamma.asStateFlow()

    fun setFlashlightEnabled(enabled: Boolean) {
        _flashlightEnabled.value = enabled
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putBoolean("flashlight_enabled", enabled) }
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

    fun setFlashlightMultiplier(multiplier: Float) {
        _flashlightMultiplier.value = multiplier
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putFloat("flashlight_multiplier", multiplier) }
        }
    }

    fun setFlashlightThreshold(threshold: Float) {
        _flashlightThreshold.value = threshold
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putFloat("flashlight_threshold", threshold) }
        }
    }

    fun setFlashlightSmoothing(smoothing: Float) {
        _flashlightSmoothing.value = smoothing
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putFloat("flashlight_smoothing", smoothing) }
        }
    }

    fun setFlashlightGamma(gamma: Float) {
        _flashlightGamma.value = gamma
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putFloat("flashlight_gamma", gamma) }
        }
    }

    fun setIdleBreathingEnabled(enabled: Boolean) {
        _idleBreathingEnabled.value = enabled
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putBoolean("idle_breathing_enabled", enabled) }
        }
    }

    fun setIdlePattern(pattern: String) {
        _idlePattern.value = pattern
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putString("idle_pattern", pattern) }
        }
    }

    fun setNotificationFlashEnabled(enabled: Boolean) {
        _notificationFlashEnabled.value = enabled
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putBoolean("notification_flash_enabled", enabled) }
        }
    }

    fun setDisableGlyphsWhenSilent(enabled: Boolean) {
        _disableGlyphsWhenSilent.value = enabled
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putBoolean("disable_glyphs_when_silent", enabled) }
        }
    }

    fun setAutoDeviceEnabled(enabled: Boolean): Int {
        _autoDeviceEnabled.value = enabled
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

    private val _flashlightAmplitude = MutableStateFlow(0f)
    val flashlightAmplitude = _flashlightAmplitude.asStateFlow()

    private val _isBeatDetected = MutableStateFlow(false)
    val isBeatDetected = _isBeatDetected.asStateFlow()

    private var prevEnergy = 0f
    private val deltaHistory = FloatArray(61)
    private var deltaIndex = 0
    private var deltaCount = 0
    private var lastTriggerMs = 0L
    private var thresholdMask = 0f

    init {
        viewModelScope.launch(Dispatchers.Default) {
            fftState.collect { magnitude ->
                if (magnitude.isEmpty()) {
                    _hapticAmplitude.value = 0f
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
                // Increased gain from 4f to 8f and added a higher floor/ceiling
                _hapticAmplitude.value = (rms * 8f).coerceIn(0f, 1.2f)

                // Flashlight Amplitude Calculation
                val fBinLo = (_flashlightFreqMin.value / hzPerBin).toInt().coerceIn(0, magnitude.lastIndex)
                val fBinHi = (_flashlightFreqMax.value / hzPerBin).toInt().coerceIn(fBinLo, magnitude.lastIndex)
                var fSumSquares = 0f
                for (i in fBinLo..fBinHi) {
                    fSumSquares += magnitude[i] * magnitude[i]
                }
                val fCount = fBinHi - fBinLo + 1
                val fRms = if (fCount > 0) kotlin.math.sqrt(fSumSquares / fCount) else 0f
                _flashlightAmplitude.value = (fRms * 8f).coerceIn(0f, 1.2f)

                // 2. Beat Detection (matching HapticEngine.kt logic)
                if (_hapticMode.value == HapticMode.BEAT_DETECTION) {
                    val energy = kotlin.math.ln(1f + sum)
                    val delta = energy - prevEnergy
                    prevEnergy = energy

                    // Push delta
                    deltaHistory[deltaIndex] = delta.coerceAtLeast(0.0001f)
                    deltaIndex = (deltaIndex + 1) % deltaHistory.size
                    if (deltaCount < deltaHistory.size) deltaCount++

                    // Median
                    val sorted = deltaHistory.copyOf(deltaCount).apply { sort() }
                    val median = if (deltaCount == 0) 0.01f else if (deltaCount % 2 == 1) sorted[deltaCount / 2] else (sorted[deltaCount / 2 - 1] + sorted[deltaCount / 2]) * 0.5f
                    
                    val threshold = kotlin.math.max(median * 2.2f, thresholdMask)
                    val now = SystemClock.elapsedRealtime()
                    
                    if (delta > threshold && delta > 0.025f && (now - lastTriggerMs) >= 60L) {
                        _isBeatDetected.value = true
                        lastTriggerMs = now
                        thresholdMask = delta * 0.8f
                        // Auto-reset beat after a short duration for the UI flash
                        viewModelScope.launch {
                            delay(50)
                            _isBeatDetected.value = false
                        }
                    }
                    thresholdMask *= 0.85f
                } else {
                    _isBeatDetected.value = false
                }
            }
        }

        ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(prefListener)
        // Run EVERYTHING heavy off-thread immediately
        viewModelScope.launch(Dispatchers.Default) {
            val prefs = ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)

            _developerModeEnabled.value = prefs.getBoolean("developer_mode_enabled", false)
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
                _autoDeviceEnabled.value = prefs.getBoolean("auto_device_enabled", true)
                _glyphTabEnabled.value = prefs.getBoolean("glyph_tab_enabled", true)
                _hapticsTabEnabled.value = prefs.getBoolean("haptics_tab_enabled", true)

                _idleBreathingEnabled.value = prefs.getBoolean("idle_breathing_enabled", false)
                _idlePattern.value = prefs.getString("idle_pattern", "pulse") ?: "pulse"
                _notificationFlashEnabled.value = prefs.getBoolean("notification_flash_enabled", false)
                _configVersion.value = AudioCaptureService.loadZonesConfigVersion(ctx)
                _disableGlyphsWhenSilent.value = prefs.getBoolean("disable_glyphs_when_silent", false)

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
                _hapticMultiplier.value = prefs.getFloat("haptic_multiplier", 1.0f)
                _hapticGamma.value = prefs.getFloat("haptic_gamma", 2.0f)
                _richTapFrequency.value = prefs.getInt("richtap_frequency", 50)

                _flashlightEnabled.value = prefs.getBoolean("flashlight_enabled", false)
                _flashlightFreqMin.value = prefs.getInt("flashlight_freq_min", 60).toFloat()
                _flashlightFreqMax.value = prefs.getInt("flashlight_freq_max", 250).toFloat()
                _flashlightMultiplier.value = prefs.getFloat("flashlight_multiplier", 1.0f)
                _flashlightThreshold.value = prefs.getFloat("flashlight_threshold", 0.15f)
                _flashlightSmoothing.value = prefs.getFloat("flashlight_smoothing", 0.7f)
                _flashlightGamma.value = prefs.getFloat("flashlight_gamma", 2.2f)

                checkRemoteConfigVersion()
                if (!BuildConfig.DEBUG) {
                    checkAppUpdate()
                }
            }

            startRunningStatePoller()
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
                if (viewModel.captureSource.value == AudioCaptureService.CaptureSource.MIC) {
                    service?.startMicCapture()
                } else if (viewModel.captureSource.value == AudioCaptureService.CaptureSource.SHIZUKU) {
                    service?.startShizukuCapture()
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
                    Toast.makeText(this@MainActivity, "Audio recording permission is required", Toast.LENGTH_SHORT).show()
                }
            } else {
                pendingVisualizerStart = false
                viewModel.setRunning(false)
                Toast.makeText(this@MainActivity, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
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
                    "Notifications are required while the visualizer is active",
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
                    "Audio recording permission is required",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val selectedTheme by viewModel.selectedTheme.collectAsStateWithLifecycle()
            val selectedFont by viewModel.selectedFont.collectAsStateWithLifecycle()

            BetterVizTheme(themeName = selectedTheme, fontName = selectedFont) {
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
                val hapticGamma by viewModel.hapticGamma.collectAsStateWithLifecycle()
                val richTapFrequency by viewModel.richTapFrequency.collectAsStateWithLifecycle()
                val hapticAmplitude by viewModel.hapticAmplitude.collectAsStateWithLifecycle()
                val isBeatDetected by viewModel.isBeatDetected.collectAsStateWithLifecycle()

                val flashlightEnabled by viewModel.flashlightEnabled.collectAsStateWithLifecycle()
                val flashlightFreqMin by viewModel.flashlightFreqMin.collectAsStateWithLifecycle()
                val flashlightFreqMax by viewModel.flashlightFreqMax.collectAsStateWithLifecycle()
                val flashlightMultiplier by viewModel.flashlightMultiplier.collectAsStateWithLifecycle()
                val flashlightThreshold by viewModel.flashlightThreshold.collectAsStateWithLifecycle()
                val flashlightSmoothing by viewModel.flashlightSmoothing.collectAsStateWithLifecycle()
                val flashlightGamma by viewModel.flashlightGamma.collectAsStateWithLifecycle()
                val flashlightAmplitude by viewModel.flashlightAmplitude.collectAsStateWithLifecycle()

                val idleBreathingEnabled by viewModel.idleBreathingEnabled.collectAsStateWithLifecycle()
                val idlePattern by viewModel.idlePattern.collectAsStateWithLifecycle()
                val notificationFlashEnabled by viewModel.notificationFlashEnabled.collectAsStateWithLifecycle()
                val disableGlyphsWhenSilent by viewModel.disableGlyphsWhenSilent.collectAsStateWithLifecycle()
                val selectedDevice by viewModel.selectedDevice.collectAsStateWithLifecycle()
                val isEditingPreset by viewModel.isEditingPreset.collectAsStateWithLifecycle()
                val isShowingAbout by viewModel.isShowingAbout.collectAsStateWithLifecycle()
                val isShowingTimeline by viewModel.isShowingTimeline.collectAsStateWithLifecycle()
                val showUpdateDialog by viewModel.showUpdateDialog.collectAsStateWithLifecycle()
                val appUpdateStatus by viewModel.appUpdateStatus.collectAsStateWithLifecycle()

                if (showUpdateDialog && appUpdateStatus is MainViewModel.AppUpdateStatus.Available) {
                    val update = appUpdateStatus as MainViewModel.AppUpdateStatus.Available
                    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = viewModel::dismissUpdateDialog,
                        title = { androidx.compose.material3.Text("Update Available") },
                        text = { androidx.compose.material3.Text("A new version (${update.version}) is available. Do you want to download it now?") },
                        confirmButton = {
                            androidx.compose.material3.Button(onClick = {
                                uriHandler.openUri(update.url)
                                viewModel.dismissUpdateDialog()
                            }) {
                                androidx.compose.material3.Text("Download")
                            }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(onClick = viewModel::dismissUpdateDialog) {
                                androidx.compose.material3.Text("Not now")
                            }
                        }
                    )
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

                if (isShowingTimeline) {
                    androidx.compose.ui.window.Dialog(
                        onDismissRequest = viewModel::hideTimeline,
                        properties = androidx.compose.ui.window.DialogProperties(
                            usePlatformDefaultWidth = false,
                            decorFitsSystemWindows = false
                        )
                    ) {
                        Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
                            TimelineScreen(onDismiss = viewModel::hideTimeline)
                        }
                    }
                }

                val isShowingCommunity by viewModel.isShowingCommunity.collectAsStateWithLifecycle()
                val communityPresets by viewModel.communityPresets.collectAsStateWithLifecycle()
                val communityError by viewModel.communityError.collectAsStateWithLifecycle()
                
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
                            error = communityError,
                            onDownload = viewModel::downloadPreset,
                            onDismiss = viewModel::hideCommunity
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
                    hapticGamma = hapticGamma,
                    onHapticGammaChanged = ::onHapticGammaChanged,
                    richTapFrequency = richTapFrequency,
                    onRichTapFrequencyChanged = ::onRichTapFrequencyChanged,
                    hapticAmplitude = hapticAmplitude,
                    isBeatDetected = isBeatDetected,
                    flashlightEnabled = flashlightEnabled,
                    onFlashlightEnabledChanged = ::onFlashlightEnabledChanged,
                    flashlightFreqMin = flashlightFreqMin,
                    flashlightFreqMax = flashlightFreqMax,
                    onFlashlightFreqRangeChanged = ::onFlashlightFreqRangeChanged,
                    flashlightMultiplier = flashlightMultiplier,
                    onFlashlightMultiplierChanged = ::onFlashlightMultiplierChanged,
                    flashlightThreshold = flashlightThreshold,
                    onFlashlightThresholdChanged = ::onFlashlightThresholdChanged,
                    flashlightSmoothing = flashlightSmoothing,
                    onFlashlightSmoothingChanged = ::onFlashlightSmoothingChanged,
                    flashlightGamma = flashlightGamma,
                    onFlashlightGammaChanged = ::onFlashlightGammaChanged,
                    flashlightAmplitude = flashlightAmplitude,
                    idleBreathingEnabled = idleBreathingEnabled,
                    onIdleBreathingEnabledChanged = ::onIdleBreathingEnabledChanged,
                    idlePattern = idlePattern,
                    onIdlePatternChanged = ::onIdlePatternChanged,
                    notificationFlashEnabled = notificationFlashEnabled,
                    onNotificationFlashEnabledChanged = ::onNotificationFlashEnabledChanged,
                    disableGlyphsWhenSilent = disableGlyphsWhenSilent,
                    onDisableGlyphsWhenSilentChanged = ::onDisableGlyphsWhenSilentChanged,
                    selectedDevice = selectedDevice,
                )
            }
        }

        if (savedInstanceState == null) {
            mainHandler.post { handleLaunchIntent(intent) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, mainHandler)
        refreshConnectedAudioRoute()
    }

    override fun onStop() {
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // Single source of truth: push the real state into the ViewModel.
        // The poller will keep it in sync while the app is in the foreground.
        viewModel.setRunning(AudioCaptureService.isRunning())
        refreshConnectedAudioRoute()
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

    private fun onHapticGammaChanged(gamma: Float) {
        viewModel.setHapticGamma(gamma)
        service?.setHapticGamma(gamma)
    }

    private fun onRichTapFrequencyChanged(frequency: Int) {
        viewModel.setRichTapFrequency(frequency)
        service?.setRichTapFrequency(frequency)
    }

    private fun onFlashlightEnabledChanged(enabled: Boolean) {
        viewModel.setFlashlightEnabled(enabled)
        service?.setFlashlightEnabled(enabled)
    }

    private fun onFlashlightFreqRangeChanged(min: Float, max: Float) {
        viewModel.setFlashlightFreqRange(min, max)
        service?.setFlashlightFreqRange(min, max)
    }

    private fun onFlashlightMultiplierChanged(multiplier: Float) {
        viewModel.setFlashlightMultiplier(multiplier)
        service?.setFlashlightMultiplier(multiplier)
    }

    private fun onFlashlightThresholdChanged(threshold: Float) {
        viewModel.setFlashlightThreshold(threshold)
        service?.setFlashlightThreshold(threshold)
    }

    private fun onFlashlightSmoothingChanged(smoothing: Float) {
        viewModel.setFlashlightSmoothing(smoothing)
        service?.setFlashlightSmoothing(smoothing)
    }

    private fun onFlashlightGammaChanged(gamma: Float) {
        viewModel.setFlashlightGamma(gamma)
        service?.setFlashlightGamma(gamma)
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
            Toast.makeText(this, "Please enable notification access for this feature", Toast.LENGTH_LONG).show()
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
            return
        }
        viewModel.setNotificationFlashEnabled(enabled)
        service?.setNotificationFlashEnabled(enabled)
    }

    private fun onDisableGlyphsWhenSilentChanged(enabled: Boolean) {
        viewModel.setDisableGlyphsWhenSilent(enabled)
        service?.setDisableGlyphsWhenSilent(enabled)
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
                AudioCaptureService.CaptureSource.MIC -> startMicVisualizer()
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

    private fun startMicVisualizer() {
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
            service?.startMicCapture()
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

        service?.setHapticEnabled(viewModel.hapticMotorEnabled.value)
        service?.setHapticMode(viewModel.hapticMode.value)
        service?.setHapticFreqRange(viewModel.hapticFreqMin.value, viewModel.hapticFreqMax.value)
        service?.setHapticMultiplier(viewModel.hapticMultiplier.value)
        service?.setHapticGamma(viewModel.hapticGamma.value)
        service?.setDisableGlyphsWhenSilent(viewModel.disableGlyphsWhenSilent.value)

        service?.setFlashlightEnabled(viewModel.flashlightEnabled.value)
        service?.setFlashlightFreqRange(viewModel.flashlightFreqMin.value, viewModel.flashlightFreqMax.value)
        service?.setFlashlightMultiplier(viewModel.flashlightMultiplier.value)
        service?.setFlashlightThreshold(viewModel.flashlightThreshold.value)
        service?.setFlashlightSmoothing(viewModel.flashlightSmoothing.value)

        service?.setCaptureSource(viewModel.captureSource.value)
        val preset = viewModel.currentPreset()
        if (preset.isNotBlank()) service?.setPreset(preset)
    }

    private fun refreshConnectedAudioRoute() {
        val route = resolvePreferredAudioRoute()
        val latency = viewModel.updateConnectedDevice(
            routeKey = route?.storageKey,
            name = route?.displayName ?: "Internal Speaker"
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
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Internal Speaker"
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
            androidx.compose.material3.Text("MediaProjection permission")
        },
        text = {
            androidx.compose.material3.Text(
                "This permission lets the app access the device audio stream through Android's capture system so the Glyph visualizer can react in real time. No unnecessary recordings are stored, and the app does not save your media or private data."
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onContinue) {
                androidx.compose.material3.Text("Continue")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                androidx.compose.material3.Text("Not now")
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
    hapticGamma: Float,
    onHapticGammaChanged: (Float) -> Unit,
    richTapFrequency: Int,
    onRichTapFrequencyChanged: (Int) -> Unit,
    hapticAmplitude: Float,
    isBeatDetected: Boolean,
    flashlightEnabled: Boolean,
    onFlashlightEnabledChanged: (Boolean) -> Unit,
    flashlightFreqMin: Float,
    flashlightFreqMax: Float,
    onFlashlightFreqRangeChanged: (Float, Float) -> Unit,
    flashlightMultiplier: Float,
    onFlashlightMultiplierChanged: (Float) -> Unit,
    flashlightThreshold: Float,
    onFlashlightThresholdChanged: (Float) -> Unit,
    flashlightSmoothing: Float,
    onFlashlightSmoothingChanged: (Float) -> Unit,
    flashlightGamma: Float,
    onFlashlightGammaChanged: (Float) -> Unit,
    flashlightAmplitude: Float,
    idleBreathingEnabled: Boolean,
    onIdleBreathingEnabledChanged: (Boolean) -> Unit,
    idlePattern: String,
    onIdlePatternChanged: (String) -> Unit,
    notificationFlashEnabled: Boolean,
    onNotificationFlashEnabledChanged: (Boolean) -> Unit,
    disableGlyphsWhenSilent: Boolean,
    onDisableGlyphsWhenSilentChanged: (Boolean) -> Unit,
    selectedDevice: Int,
) {
    val autoDeviceEnabled by viewModel.autoDeviceEnabled.collectAsStateWithLifecycle()
    val connectedDeviceName by viewModel.connectedDeviceName.collectAsStateWithLifecycle()

    val availableTabs = remember(selectedDevice) {
        if (selectedDevice == DeviceProfile.DEVICE_UNKNOWN) {
            listOf(Tab.Audio, Tab.Haptics, Tab.Settings)
        } else {
            listOf(Tab.Audio, Tab.Glyphs, Tab.Haptics, Tab.Settings)
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
                }
                delay(16)
            }
        } else {
            viewModel.updateVisualizerState(floatArrayOf())
            viewModel.updateFftState(floatArrayOf())
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
        val bottomPadding = innerPadding.calculateBottomPadding()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = bottomPadding)
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
                            alpha = 0.4f + (1f - 0.4f) * fraction
                        }
                ) {
                    when (currentTab) {
                        Tab.Audio -> {
                            val fftData by viewModel.fftState.collectAsStateWithLifecycle()
                            val captureSource by viewModel.captureSource.collectAsStateWithLifecycle()
                            AudioScreen(
                                isRunning = isRunning,
                                latencyMs = latencyMs,
                                onLatencyChanged = onLatencyChanged,
                                latencyPresets = latencyPresets,
                                onLatencyPresetsChanged = onLatencyPresetsChanged,
                                autoDeviceEnabled = autoDeviceEnabled,
                                onAutoDeviceToggle = onAutoDeviceToggle,
                                connectedDeviceName = connectedDeviceName,
                                fftData = fftData,
                                captureSource = captureSource,
                                onCaptureSourceChanged = viewModel::setCaptureSource
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
                            hapticGamma = hapticGamma,
                            onHapticGammaChanged = onHapticGammaChanged,
                            richTapFrequency = richTapFrequency,
                            onRichTapFrequencyChanged = onRichTapFrequencyChanged,
                            hapticAmplitude = hapticAmplitude,
                            isBeatDetected = isBeatDetected,
                            flashlightEnabled = flashlightEnabled,
                            onFlashlightEnabledChanged = onFlashlightEnabledChanged,
                            flashlightFreqMin = flashlightFreqMin,
                            flashlightFreqMax = flashlightFreqMax,
                            onFlashlightFreqRangeChanged = onFlashlightFreqRangeChanged,
                            flashlightMultiplier = flashlightMultiplier,
                            onFlashlightMultiplierChanged = onFlashlightMultiplierChanged,
                            flashlightThreshold = flashlightThreshold,
                            onFlashlightThresholdChanged = onFlashlightThresholdChanged,
                            flashlightSmoothing = flashlightSmoothing,
                            onFlashlightSmoothingChanged = onFlashlightSmoothingChanged,
                            flashlightGamma = flashlightGamma,
                            onFlashlightGammaChanged = onFlashlightGammaChanged,
                            flashlightAmplitude = flashlightAmplitude,
                        )
                        Tab.Settings -> SettingsScreen(
                            viewModel = viewModel,
                            idleBreathingEnabled = idleBreathingEnabled,
                            onIdleBreathingEnabledChanged = onIdleBreathingEnabledChanged,
                            idlePattern = idlePattern,
                            onIdlePatternChanged = onIdlePatternChanged,
                            notificationFlashEnabled = notificationFlashEnabled,
                            onNotificationFlashEnabledChanged = onNotificationFlashEnabledChanged,
                            disableGlyphsWhenSilent = disableGlyphsWhenSilent,
                            onDisableGlyphsWhenSilentChanged = onDisableGlyphsWhenSilentChanged,
                        )
                    }
                }
            }
        }
    }
}
