package com.better.nothing.music.vizualizer.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.better.nothing.music.vizualizer.BuildConfig
import com.better.nothing.music.vizualizer.R
import com.better.nothing.music.vizualizer.logic.*
import com.better.nothing.music.vizualizer.model.*
import com.better.nothing.music.vizualizer.service.AudioCaptureService
import com.better.nothing.music.vizualizer.util.AnalyticsHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.AuthCredential
import androidx.compose.ui.graphics.Color
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.pow
import kotlin.math.sqrt

enum class Tab(val label: String, val labelRes: Int) {
    Audio("Audio", R.string.tab_audio), 
    Glyphs("Glyphs", R.string.tab_glyphs), 
    Haptics("Haptics", R.string.tab_haptics), 
    Flashlight("Flashlight", R.string.tab_flashlight), 
    Settings("Settings", R.string.tab_settings);
}

data class AudioRoute(
    val storageKey: String,
    val displayName: String,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val ctx = application
    val communityRepository = CommunityRepository()
    val announcementRepository = AnnouncementRepository()
    val leaderboardRepository = LeaderboardRepository()
    val userRepository = UserRepository()
    val analytics = AnalyticsHelper(application)

    val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile = _userProfile.asStateFlow()

    val _totalVisualizedTime = MutableStateFlow(0L)
    val totalVisualizedTime = _totalVisualizedTime.asStateFlow()

    val _totalGlyphTime = MutableStateFlow(0L)
    val totalGlyphTime = _totalGlyphTime.asStateFlow()

    val _totalHapticTime = MutableStateFlow(0L)
    val totalHapticTime = _totalHapticTime.asStateFlow()

    val _totalFlashlightTime = MutableStateFlow(0L)
    val totalFlashlightTime = _totalFlashlightTime.asStateFlow()

    val _userNickname = MutableStateFlow("Anonymous")
    val userNickname = _userNickname.asStateFlow()

    val _userId = MutableStateFlow<String?>(null)
    val userId = _userId.asStateFlow()

    val _isAnonymous = MutableStateFlow(true)
    val isAnonymous = _isAnonymous.asStateFlow()

    sealed class AppUpdateStatus {
        object Idle : AppUpdateStatus()
        object Checking : AppUpdateStatus()
        data class Available(val version: String, val url: String, val apkUrl: String? = null) : AppUpdateStatus()
        data class Downloading(val progress: Float) : AppUpdateStatus()
        object UpToDate : AppUpdateStatus()
        data class Error(val message: String) : AppUpdateStatus()
    }
    private val _appUpdateStatus = MutableStateFlow<AppUpdateStatus>(AppUpdateStatus.Idle)
    val appUpdateStatus = _appUpdateStatus.asStateFlow()

    private val _isShowingAbout = MutableStateFlow(false)
    val isShowingAbout = _isShowingAbout.asStateFlow()
    fun showAbout() { _isShowingAbout.value = true }
    fun hideAbout() { _isShowingAbout.value = false }

    private val _isShowingLicense = MutableStateFlow(false)
    val isShowingLicense = _isShowingLicense.asStateFlow()
    fun showLicense() { _isShowingLicense.value = true }
    fun hideLicense() { _isShowingLicense.value = false }

    private val _isShowingCommunity = MutableStateFlow(false)
    val isShowingCommunity = _isShowingCommunity.asStateFlow()
    fun showCommunity() { _isShowingCommunity.value = true }
    fun hideCommunity() { _isShowingCommunity.value = false }

    private val _isShowingEditor = MutableStateFlow(false)
    val isShowingEditor = _isShowingEditor.asStateFlow()
    fun showEditor() { _isShowingEditor.value = true }
    fun hideEditor() { _isShowingEditor.value = false }

    private val _dynamicGainEnabled = MutableStateFlow(true)
    val dynamicGainEnabled = _dynamicGainEnabled.asStateFlow()
    fun setDynamicGainEnabled(enabled: Boolean) {
        _dynamicGainEnabled.value = enabled
        MainActivity.serviceStatic?.setDynamicGainEnabled(enabled)
    }

    private val _overlayWidth = MutableStateFlow(120)
    val overlayWidth = _overlayWidth.asStateFlow()
    fun setOverlayWidth(width: Int) {
        _overlayWidth.value = width
        MainActivity.serviceStatic?.setOverlayWidth(width)
    }

    private val _overlayHeight = MutableStateFlow(12)
    val overlayHeight = _overlayHeight.asStateFlow()
    fun setOverlayHeight(height: Int) {
        _overlayHeight.value = height
        MainActivity.serviceStatic?.setOverlayHeight(height)
    }

    private val _overlayYOffset = MutableStateFlow(2)
    val overlayYOffset = _overlayYOffset.asStateFlow()
    fun setOverlayYOffset(offset: Int) {
        _overlayYOffset.value = offset
        MainActivity.serviceStatic?.setOverlayYOffset(offset)
    }

    private val _selectedTheme = MutableStateFlow("Default")
    val selectedTheme = _selectedTheme.asStateFlow()
    fun setSelectedTheme(theme: String) { _selectedTheme.value = theme }

    private val _selectedFont = MutableStateFlow("Default")
    val selectedFont = _selectedFont.asStateFlow()
    fun setSelectedFont(font: String) { _selectedFont.value = font }

    fun checkAppUpdate() {
        _appUpdateStatus.value = AppUpdateStatus.UpToDate
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

    private val _isShowingLeaderboard = MutableStateFlow(false)
    val isShowingLeaderboard = _isShowingLeaderboard.asStateFlow()
    fun showLeaderboard() { _isShowingLeaderboard.value = true }
    fun hideLeaderboard() { _isShowingLeaderboard.value = false }

    fun deleteCustomPreset(key: String) {
        viewModelScope.launch {
            try {
                communityRepository.deletePreset(key)
                analytics.logEvent("preset_deleted", android.os.Bundle().apply { putString("preset_id", key) })
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to delete preset", e)
            }
        }
    }

    fun saveCustomPreset(name: String, zones: List<AudioProcessor.ZoneSpec>, presetKey: String? = null) {
        val uid = _userId.value ?: return
        viewModelScope.launch {
            try {
                val preset = CommunityPreset(
                    name = name,
                    author = _userNickname.value,
                    authorId = uid,
                    phoneModel = phoneModelForDevice(selectedDevice.value),
                    zones = zones.map { ZoneData.fromZoneSpec(it) },
                    timestamp = System.currentTimeMillis()
                )
                communityRepository.uploadPreset(preset)
                analytics.logPresetShared(name)
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, "Preset uploaded to community!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to upload preset", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun checkRemoteConfigVersion() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url =
                    URL("https://raw.githubusercontent.com/Aleks-Levet/better-nothing-music-visualizer/main/zones.config?t=${System.currentTimeMillis()}")
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
    fun updateProfilePicture(uri: android.net.Uri) {
        val uid = _userId.value ?: return
        viewModelScope.launch {
            try {
                val base64 = userRepository.uploadProfilePicture(uid, uri, ctx)
                val profile = _userProfile.value?.copy(profilePictureUrl = base64)
                    ?: UserProfile(userId = uid, profilePictureUrl = base64)
                userRepository.saveUserProfile(profile)
                _userProfile.value = profile
                analytics.logProfileUpdate("profile_picture")
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to update profile picture", e)
            }
        }
    }

    fun selectDefaultAvatar(resId: Int) {
        val uid = _userId.value ?: return
        viewModelScope.launch {
            try {
                val base64 = userRepository.uploadAvatarFromResource(uid, resId, ctx)
                val profile = _userProfile.value?.copy(profilePictureUrl = base64)
                    ?: UserProfile(userId = uid, profilePictureUrl = base64)
                userRepository.saveUserProfile(profile)
                _userProfile.value = profile
                analytics.logProfileUpdate("avatar")
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to select avatar", e)
            }
        }
    }
    fun setUserNickname(nickname: String) {
        _userNickname.value = nickname
        val uid = _userId.value ?: return
        viewModelScope.launch {
            try {
                val profile = _userProfile.value?.copy(displayName = nickname)
                    ?: UserProfile(userId = uid, displayName = nickname)
                userRepository.saveUserProfile(profile)
                _userProfile.value = profile
                analytics.logProfileUpdate("nickname")
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to update nickname", e)
            }
        }
    }

    private val _overlayEnabled = MutableStateFlow(false)
    val overlayEnabled = _overlayEnabled.asStateFlow()

    fun setOverlayEnabled(enabled: Boolean) {
        _overlayEnabled.value = enabled
        MainActivity.serviceStatic?.setOverlayEnabled(enabled)
    }

    val _idleBreathingEnabled = MutableStateFlow(false)
    val idleBreathingEnabled = _idleBreathingEnabled.asStateFlow()

    fun setIdleBreathingEnabled(enabled: Boolean) {
        _idleBreathingEnabled.value = enabled
        MainActivity.serviceStatic?.setIdleBreathingEnabled(enabled)
    }

    val _idlePattern = MutableStateFlow("pulse")
    val idlePattern = _idlePattern.asStateFlow()

    fun setIdlePattern(pattern: String) {
        _idlePattern.value = pattern
        MainActivity.serviceStatic?.setIdlePattern(pattern)
    }

    val _notificationFlashEnabled = MutableStateFlow(false)
    val notificationFlashEnabled = _notificationFlashEnabled.asStateFlow()

    fun setNotificationFlashEnabled(enabled: Boolean) {
        _notificationFlashEnabled.value = enabled
        MainActivity.serviceStatic?.setNotificationFlashEnabled(enabled)
    }

    val _strobeEnabled = MutableStateFlow(false)
    val strobeEnabled = _strobeEnabled.asStateFlow()

    fun setStrobeEnabled(enabled: Boolean) {
        _strobeEnabled.value = enabled
        MainActivity.serviceStatic?.setStrobeEnabled(enabled)
    }

    val _disableGlyphsWhenSilent = MutableStateFlow(false)
    val disableGlyphsWhenSilent = _disableGlyphsWhenSilent.asStateFlow()

    fun setDisableGlyphsWhenSilent(enabled: Boolean) {
        _disableGlyphsWhenSilent.value = enabled
        MainActivity.serviceStatic?.setDisableGlyphsWhenSilent(enabled)
    }

    val _musicThemeColor = MutableStateFlow(Color.White)
    val musicThemeColor = _musicThemeColor.asStateFlow()

    fun setMusicArtwork(bitmap: Bitmap?) {
        // Implementation for artwork extraction and color update
    }

    val _isAdmin = MutableStateFlow(false)
    val isAdmin = _isAdmin.asStateFlow()

    fun syncStats() {
        val uid = _userId.value ?: return
        viewModelScope.launch {
            try {
                var profile = userRepository.getUserProfile(uid)
                if (profile == null) {
                    profile = UserProfile(
                        userId = uid,
                        displayName = _userNickname.value,
                        createdAt = System.currentTimeMillis()
                    )
                    userRepository.saveUserProfile(profile)
                }
                _userProfile.value = profile
                _userNickname.value = profile.displayName
                _totalVisualizedTime.value = profile.totalVisualizedTime
                
                // Check if admin
                _isAdmin.value = uid == "OLIVER_UID" || uid == "ALEKS_UID"
                
                analytics.logStatsSynced(
                    profile.totalVisualizedTime,
                    0, 0, 0 // Add other stats if tracked
                )
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to sync stats", e)
            }
        }
    }

    fun isNotificationAccessGranted(): Boolean {
        val flat = android.provider.Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")
        return flat?.contains(ctx.packageName) == true
    }


    val _devPassword = MutableStateFlow<String?>(null)

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

    val _thanksMessage = MutableStateFlow<String?>(null)
    val thanksQueue = mutableListOf<String>()

    fun dismissThanksMessage() {
        if (thanksQueue.isNotEmpty()) {
            _thanksMessage.value = thanksQueue.removeAt(0)
        } else {
            _thanksMessage.value = null
        }
    }

    fun showThanks(message: String) {
        if (_thanksMessage.value == null) {
            _thanksMessage.value = message
        } else {
            thanksQueue.add(message)
        }
    }

    val _favoritePresets = MutableStateFlow<Set<String>>(emptySet())
    val favoritePresets = _favoritePresets.asStateFlow()

    val _captureSource = MutableStateFlow(AudioCaptureService.CaptureSource.INTERNAL)
    val captureSource = _captureSource.asStateFlow()

    val _latestAnnouncement = MutableStateFlow<Announcement?>(null)
    val latestAnnouncement = _latestAnnouncement.asStateFlow()

    val _showAnnouncementModal = MutableStateFlow(false)
    val showAnnouncementModal = _showAnnouncementModal.asStateFlow()

    val _showAnnouncementEditor = MutableStateFlow(false)
    val showAnnouncementEditor = _showAnnouncementEditor.asStateFlow()

    val _showAnnouncementHistory = MutableStateFlow(false)
    val showAnnouncementHistory = _showAnnouncementHistory.asStateFlow()

    val _showSpoofingSettings = MutableStateFlow(false)
    val showSpoofingSettings = _showSpoofingSettings.asStateFlow()

    val _shizukuSourceUnlocked = MutableStateFlow(false)
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

    val _spoofLocale = MutableStateFlow<String?>(null)
    val spoofLocale = _spoofLocale.asStateFlow()

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

        val auth = FirebaseAuth.getInstance()
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                _userId.value = user.uid
                _isAnonymous.value = user.isAnonymous
                analytics.setUserId(user.uid)
                prefs.edit().putString("user_id", user.uid).apply()
            }
        }

        if (auth.currentUser == null) {
            auth.signInAnonymously().addOnFailureListener { e ->
                Log.e("MainViewModel", "Firebase Auth failed", e)
                var uId = prefs.getString("user_id", null)
                if (uId == null) {
                    uId = java.util.UUID.randomUUID().toString()
                    prefs.edit().putString("user_id", uId).apply()
                }
                _userId.value = uId
            }
        } else {
            _userId.value = auth.currentUser?.uid
        }

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

    val _m3eEnabled = MutableStateFlow(true)
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
    val _selectedTab = MutableStateFlow(Tab.Audio)
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

    fun checkShizukuPermission(): Boolean {
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

    val _developerModeEnabled = MutableStateFlow(false)
    val developerModeEnabled = _developerModeEnabled.asStateFlow()

    val _spoofedDevice = MutableStateFlow(DeviceProfile.DEVICE_NP1)
    val spoofedDevice = _spoofedDevice.asStateFlow()

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
            androidx.core.os.LocaleListCompat.getEmptyLocaleList()
        } else {
            androidx.core.os.LocaleListCompat.forLanguageTags(localeTag)
        }
        androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(appLocales)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putString("spoof_locale", localeTag) }
        }
    }

    fun updateSelectedDevice() {
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
    val _latencyMs = MutableStateFlow(0)
    val latencyMs = _latencyMs.asStateFlow()

    val _latencyPresets = MutableStateFlow(listOf(0, 150, 300, 500))
    val latencyPresets = _latencyPresets.asStateFlow()

    /**
     * Updates the current system latency and persists it to disk.
     */
    fun setLatencyMs(value: Int) {
        _latencyMs.value = value
        analytics.logLatencyChanged(value, activeLatencyRouteKey())
        viewModelScope.launch(Dispatchers.IO) {
            val key = activeLatencyRouteKey()
            if (key != null) {
                ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                    .edit { putInt("latency_$key", value) }
            }
        }
        MainActivity.serviceStatic?.setLatencyMs(value)
    }

    val _autoDeviceMemorize = MutableStateFlow(true)
    val autoDeviceMemorize = _autoDeviceMemorize.asStateFlow()

    fun setAutoDeviceMemorize(enabled: Boolean) {
        _autoDeviceMemorize.value = enabled
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putBoolean("auto_device_memorize", enabled) }
        }
    }

    // ── Gamma ─────────────────────────────────────────────────────────────────
    val _gammaValue = MutableStateFlow(1.0f)
    val gammaValue = _gammaValue.asStateFlow()

    fun setGammaValue(value: Float) {
        _gammaValue.value = value
        MainActivity.serviceStatic?.setGamma(value)
    }

    val _spectrumGain = MutableStateFlow(1.0f)
    val spectrumGain = _spectrumGain.asStateFlow()

    fun setSpectrumGain(value: Float) {
        _spectrumGain.value = value
        MainActivity.serviceStatic?.setSpectrumGain(value)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putFloat("spectrum_gain", value) }
        }
    }

    val _maxBrightness = MutableStateFlow(4095)
    val maxBrightness = _maxBrightness.asStateFlow()

    fun setMaxBrightness(value: Int) {
        val clamped = value.coerceIn(0, 4500)
        _maxBrightness.value = clamped
        MainActivity.serviceStatic?.setMaxBrightness(clamped)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putInt("max_brightness", clamped) }
        }
    }

    val _runningState = MutableStateFlow(false)
    val runningState = _runningState.asStateFlow()

    fun setRunning(running: Boolean) {
        _runningState.value = running
    }

    val _selectedPreset = MutableStateFlow("Default")
    val selectedPreset = _selectedPreset.asStateFlow()

    fun currentPreset() = _selectedPreset.value

    fun setSelectedPreset(preset: String) {
        _selectedPreset.value = preset
        analytics.logPresetSelected(preset, false)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putString("selected_preset", preset) }
        }
        MainActivity.serviceStatic?.setSelectedPreset(preset)
    }

    val _presetInfos = MutableStateFlow<List<AudioCaptureService.PresetInfo>>(emptyList())
    val presetInfos = _presetInfos.asStateFlow()

    // ── Haptics ───────────────────────────────────────────────────────────────
    val _hapticMotorEnabled = MutableStateFlow(false)
    val hapticMotorEnabled = _hapticMotorEnabled.asStateFlow()

    val _hapticMode = MutableStateFlow(HapticMode.BASS_TO_AMPLITUDE)
    val hapticMode = _hapticMode.asStateFlow()

    val _hapticFreqMin = MutableStateFlow(20f)
    val hapticFreqMin = _hapticFreqMin.asStateFlow()

    val _hapticFreqMax = MutableStateFlow(250f)
    val hapticFreqMax = _hapticFreqMax.asStateFlow()

    val _hapticMultiplier = MutableStateFlow(1.0f)
    val hapticMultiplier = _hapticMultiplier.asStateFlow()

    val _hapticAudioGain = MutableStateFlow(1.0f)
    val hapticAudioGain = _hapticAudioGain.asStateFlow()

    val _hapticGamma = MutableStateFlow(2.0f)
    val hapticGamma = _hapticGamma.asStateFlow()

    val _hapticBeatSensitivity = MutableStateFlow(1.5f)
    val hapticBeatSensitivity = _hapticBeatSensitivity.asStateFlow()

    val _hapticBeatGamma = MutableStateFlow(8.0f)
    val hapticBeatGamma = _hapticBeatGamma.asStateFlow()

    fun setHapticMotorEnabled(enabled: Boolean) {
        _hapticMotorEnabled.value = enabled
        analytics.logSettingChanged("haptic_motor_enabled", enabled)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putBoolean("haptic_motor_enabled", enabled) }
        }
        MainActivity.serviceStatic?.setHapticMotorEnabled(enabled)
    }

    fun setHapticMode(mode: HapticMode) {
        _hapticMode.value = mode
        analytics.logSettingChanged("haptic_mode", mode.name)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putString("haptic_mode", mode.name) }
        }
        MainActivity.serviceStatic?.setHapticMode(mode)
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
        MainActivity.serviceStatic?.setHapticFreqRange(min, max)
    }

    fun setHapticMultiplier(value: Float) {
        _hapticMultiplier.value = value
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putFloat("haptic_multiplier", value) }
        }
        MainActivity.serviceStatic?.setHapticMultiplier(value)
    }

    fun setHapticAudioGain(value: Float) {
        _hapticAudioGain.value = value
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putFloat("haptic_audio_gain", value) }
        }
        MainActivity.serviceStatic?.setHapticAudioGain(value)
    }

    fun setHapticGamma(value: Float) {
        _hapticGamma.value = value
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putFloat("haptic_gamma", value) }
        }
        MainActivity.serviceStatic?.setHapticGamma(value)
    }

    fun setHapticBeatSensitivity(value: Float) {
        _hapticBeatSensitivity.value = value
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putFloat("haptic_beat_sensitivity", value) }
        }
        MainActivity.serviceStatic?.setHapticBeatSensitivity(value)
    }

    fun setHapticBeatGamma(value: Float) {
        _hapticBeatGamma.value = value
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putFloat("haptic_beat_gamma", value) }
        }
        MainActivity.serviceStatic?.setHapticBeatGamma(value)
    }

    // ── Flashlight ────────────────────────────────────────────────────────────
    val _flashlightEnabled = MutableStateFlow(false)
    val flashlightEnabled = _flashlightEnabled.asStateFlow()

    val _flashlightMode = MutableStateFlow(TorchMode.AMPLITUDE)
    val flashlightMode = _flashlightMode.asStateFlow()

    val _flashlightFreqMin = MutableStateFlow(20f)
    val flashlightFreqMin = _flashlightFreqMin.asStateFlow()

    val _flashlightFreqMax = MutableStateFlow(250f)
    val flashlightFreqMax = _flashlightFreqMax.asStateFlow()

    val _flashlightThreshold = MutableStateFlow(0.15f)
    val flashlightThreshold = _flashlightThreshold.asStateFlow()

    val _flashlightSpeedMs = MutableStateFlow(80f)
    val flashlightSpeedMs = _flashlightSpeedMs.asStateFlow()

    val _flashlightBeatSensitivity = MutableStateFlow(1.5f)
    val flashlightBeatSensitivity = _flashlightBeatSensitivity.asStateFlow()

    val _flashlightIntensityLevels = MutableStateFlow(1)
    val flashlightIntensityLevels = _flashlightIntensityLevels.asStateFlow()

    fun setFlashlightEnabled(enabled: Boolean) {
        _flashlightEnabled.value = enabled
        analytics.logSettingChanged("flashlight_enabled", enabled)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putBoolean("flashlight_enabled", enabled) }
        }
        MainActivity.serviceStatic?.setFlashlightEnabled(enabled)
    }

    fun setFlashlightMode(mode: TorchMode) {
        _flashlightMode.value = mode
        analytics.logSettingChanged("flashlight_mode", mode.name)
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putString("flashlight_mode", mode.name) }
        }
        MainActivity.serviceStatic?.setFlashlightMode(mode)
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
        MainActivity.serviceStatic?.setFlashlightFreqRange(min, max)
    }

    fun setFlashlightThreshold(value: Float) {
        _flashlightThreshold.value = value
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putFloat("flashlight_threshold", value) }
        }
        MainActivity.serviceStatic?.setFlashlightThreshold(value)
    }

    fun setFlashlightSpeedMs(value: Float) {
        _flashlightSpeedMs.value = value
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putFloat("flashlight_speed_ms", value) }
        }
        MainActivity.serviceStatic?.setFlashlightSpeedMs(value)
    }

    fun setFlashlightBeatSensitivity(value: Float) {
        _flashlightBeatSensitivity.value = value
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putFloat("flashlight_beat_sensitivity", value) }
        }
        MainActivity.serviceStatic?.setFlashlightBeatSensitivity(value)
    }

    fun setFlashlightIntensityLevels(levels: Int) {
        _flashlightIntensityLevels.value = levels
        reloadFlashlightSpeedForLevels()
    }

    fun reloadFlashlightSpeedForLevels() {
        val prefs = ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
        val defaultVal = if (_flashlightIntensityLevels.value > 1) 350f else 80f
        val saved = prefs.getFloat("flashlight_speed_ms", defaultVal)

        val min = if (_flashlightIntensityLevels.value > 1) 150f else 20f
        val max = if (_flashlightIntensityLevels.value > 1) 700f else 150f

        _flashlightSpeedMs.value = saved.coerceIn(min, max)
    }

    fun flashlightSpeedForUi(gamma: Float): Float {
        val min = if (_flashlightIntensityLevels.value > 1) 150f else 20f
        val max = if (_flashlightIntensityLevels.value > 1) 700f else 150f
        val normalized = (gamma - min) / (max - min)

        // For binary (1 level), lower = faster.
        // For multi (e.g. NP2), higher = longer fade out.
        if (_flashlightIntensityLevels.value <= 1) {
            return 150f - (normalized * 130f)
        }
        return gamma.coerceIn(20f, 150f)
    }

    // ── Logic ─────────────────────────────────────────────────────────────────
    val _visualizerState = MutableStateFlow(floatArrayOf())
    val visualizerState = _visualizerState.asStateFlow()

    fun setVisualizerState(state: FloatArray) {
        _visualizerState.value = state
    }

    val _hapticAmplitude = MutableStateFlow(0f)
    val hapticAmplitude = _hapticAmplitude.asStateFlow()

    val _uiAmplitude = MutableStateFlow(0f)
    val uiAmplitude = _uiAmplitude.asStateFlow()

    val _flashlightAmplitude = MutableStateFlow(0f)
    val flashlightAmplitude = _flashlightAmplitude.asStateFlow()

    val _isBeatDetected = MutableStateFlow(false)
    val isBeatDetected = _isBeatDetected.asStateFlow()

    val _isFlashlightBeatDetected = MutableStateFlow(false)
    val isFlashlightBeatDetected = _isFlashlightBeatDetected.asStateFlow()

    val hapticBeatDetector = BeatDetector()
    val flashlightBeatDetector = BeatDetector()

    var smoothedUiAmplitude = 0f
    var smoothedHapticAmplitude = 0f

    val _fftState = MutableStateFlow(floatArrayOf())
    val fftState = _fftState.asStateFlow()

    fun setFftState(state: FloatArray) {
        _fftState.value = state
    }

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

                val hzPerBin = 44100f / 2048f
                val binLo = (_hapticFreqMin.value / hzPerBin).toInt().coerceIn(0, magnitude.lastIndex)
                val binHi = (_hapticFreqMax.value / hzPerBin).toInt().coerceIn(binLo, magnitude.lastIndex)

                var sumSquares = 0f
                for (i in binLo..binHi) {
                    sumSquares += magnitude[i] * magnitude[i]
                }
                val count = binHi - binLo + 1
                val rms = if (count > 0) kotlin.math.sqrt(sumSquares / count) else 0f
                val targetHaptic = (rms * _hapticAudioGain.value * 12f).coerceIn(0f, 1.0f).toDouble().pow(_hapticGamma.value.toDouble()).toFloat()

                // Asymmetric smoothing for haptics
                if (targetHaptic > smoothedHapticAmplitude) {
                    smoothedHapticAmplitude = smoothedHapticAmplitude * 0.15f + targetHaptic * 0.85f
                } else {
                    smoothedHapticAmplitude = smoothedHapticAmplitude * 0.7f + targetHaptic * 0.3f
                }

                val finalValue = (smoothedHapticAmplitude * _hapticMultiplier.value)
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

        val prefs = ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
        _developerModeEnabled.value = prefs.getBoolean("developer_mode_v2", false)
        _spoofedDevice.value = prefs.getInt("spoofed_device", DeviceProfile.DEVICE_NP1)
        _autoDeviceMemorize.value = prefs.getBoolean("auto_device_memorize", true)
        _m3eEnabled.value = prefs.getBoolean("m3e_enabled", true)
        _gammaValue.value = prefs.getFloat("gamma_value", 1.0f)
        _spectrumGain.value = prefs.getFloat("spectrum_gain", 1.0f)
        _maxBrightness.value = prefs.getInt("max_brightness", 4095)
        _selectedPreset.value = prefs.getString("selected_preset", "Default") ?: "Default"

        _hapticMotorEnabled.value = prefs.getBoolean("haptic_motor_enabled", false)
        _hapticMode.value = HapticMode.valueOf(prefs.getString("haptic_mode", HapticMode.BASS_TO_AMPLITUDE.name)!!)
        _hapticFreqMin.value = prefs.getInt("haptic_freq_min", 20).toFloat()
        _hapticFreqMax.value = prefs.getInt("haptic_freq_max", 250).toFloat()
        _hapticMultiplier.value = prefs.getFloat("haptic_multiplier", 1.0f)
        _hapticAudioGain.value = prefs.getFloat("haptic_audio_gain", 1.0f)
        _hapticGamma.value = prefs.getFloat("haptic_gamma", 2.0f)
        _hapticBeatSensitivity.value = prefs.getFloat("haptic_beat_sensitivity", 1.5f)
        _hapticBeatGamma.value = prefs.getFloat("haptic_beat_gamma", 8.0f)

        _flashlightEnabled.value = prefs.getBoolean("flashlight_enabled", false)
        _flashlightMode.value = TorchMode.valueOf(prefs.getString("flashlight_mode", TorchMode.AMPLITUDE.name)!!)
        _flashlightFreqMin.value = prefs.getInt("flashlight_freq_min", 20).toFloat()
        _flashlightFreqMax.value = prefs.getInt("flashlight_freq_max", 250).toFloat()
        _flashlightThreshold.value = prefs.getFloat("flashlight_threshold", 0.15f)
        _flashlightBeatSensitivity.value = prefs.getFloat("flashlight_beat_sensitivity", 1.5f)
        reloadFlashlightSpeedForLevels()

        updateSelectedDevice()
        refreshPresets()
        initDatabase()
    }

    fun initDatabase() {
        // Mock
    }

    fun phoneModelForDevice(device: Int): String {
        return when (device) {
            DeviceProfile.DEVICE_NP1 -> "Nothing Phone (1)"
            DeviceProfile.DEVICE_NP2 -> "Nothing Phone (2)"
            DeviceProfile.DEVICE_NP2A -> "Nothing Phone (2a)"
            DeviceProfile.DEVICE_NP3A -> "Nothing Phone (3a)"
            DeviceProfile.DEVICE_NP4A -> "Nothing Phone (4a)"
            DeviceProfile.DEVICE_NP4APRO -> "Nothing Phone (4a) Pro"
            DeviceProfile.DEVICE_NP3 -> "Nothing Phone (3)"
            else -> "Unknown Device"
        }
    }

    // ── Updates ───────────────────────────────────────────────────────────────
    val _configVersion = MutableStateFlow("Unknown")
    val configVersion = _configVersion.asStateFlow()

    val _remoteConfigVersion = MutableStateFlow<String?>(null)
    val remoteConfigVersion = _remoteConfigVersion.asStateFlow()

    sealed class ConfigUpdateStatus {
        object Idle : ConfigUpdateStatus()
        object Updating : ConfigUpdateStatus()
        data class Success(val message: String) : ConfigUpdateStatus()
        data class Error(val message: String) : ConfigUpdateStatus()
    }
    val _configUpdateStatus = MutableStateFlow<ConfigUpdateStatus>(ConfigUpdateStatus.Idle)
    val configUpdateStatus = _configUpdateStatus.asStateFlow()

    fun resetConfigUpdateStatus() { _configUpdateStatus.value = ConfigUpdateStatus.Idle }

    fun refreshPresets() {
        viewModelScope.launch(Dispatchers.IO) {
            refreshPresetsInternal()
        }
    }

    fun refreshPresetsInternal() {
        val json = AudioCaptureService.loadZonesConfigText(ctx)
        if (json != null) {
            val root = JSONObject(json)
            val version = root.optString("version", "Unknown")
            _configVersion.value = version
            val list = AudioCaptureService.loadPresetInfos(ctx, selectedDevice.value)
            _presetInfos.value = list
        }
    }

    fun updateLatencyPresets(newPresets: List<Int>) {
        _latencyPresets.value = newPresets
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit {
                    putString("latency_presets", newPresets.joinToString(","))
                }
        }
    }

    fun persistGamma(gamma: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
                .edit { putFloat("gamma_value", gamma) }
        }
    }

    fun reloadLatencyForCurrentRoute(): Int {
        val key = activeLatencyRouteKey()
        if (key != null) {
            val prefs = ctx.getSharedPreferences("viz_prefs", Context.MODE_PRIVATE)
            val saved = prefs.getInt("latency_$key", 0)
            _latencyMs.value = saved
            return saved
        }
        return 0
    }

    fun activeLatencyRouteKey(): String? {
        return MainActivity.serviceStatic?.getActiveAudioRouteKey()
    }

    fun signOut() {
        FirebaseAuth.getInstance().signOut()
        _userProfile.value = null
        // Re-trigger anonymous sign-in if needed or just let the init logic handle it
        FirebaseAuth.getInstance().signInAnonymously()
    }

    fun linkWithCredential(credential: AuthCredential) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        viewModelScope.launch {
            try {
                user.linkWithCredential(credential).await()
                syncStats()
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, ctx.getString(R.string.account_linked), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Linking failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, ctx.getString(R.string.auth_failed, e.localizedMessage), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun signInWithEmail(email: String, psw: String) {
        viewModelScope.launch {
            try {
                FirebaseAuth.getInstance().signInWithEmailAndPassword(email, psw).await()
                syncStats()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, ctx.getString(R.string.auth_failed, e.localizedMessage), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun signUpWithEmail(email: String, psw: String) {
        val user = FirebaseAuth.getInstance().currentUser
        viewModelScope.launch {
            try {
                if (user != null && user.isAnonymous) {
                    val credential = EmailAuthProvider.getCredential(email, psw)
                    user.linkWithCredential(credential).await()
                } else {
                    FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, psw).await()
                }
                syncStats()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, ctx.getString(R.string.auth_failed, e.localizedMessage), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun onDevDepressed() {
        // Mock
    }

    override fun onCleared() {
        super.onCleared()
    }
}
