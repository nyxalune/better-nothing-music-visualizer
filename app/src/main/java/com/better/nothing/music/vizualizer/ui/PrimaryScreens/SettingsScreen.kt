package com.better.nothing.music.vizualizer.ui.PrimaryScreens

import android.content.Intent
import com.better.nothing.music.vizualizer.R
import com.better.nothing.music.vizualizer.BuildConfig
import com.better.nothing.music.vizualizer.model.DeviceProfile
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import com.better.nothing.music.vizualizer.ui.AuthDialog
import com.better.nothing.music.vizualizer.ui.BodyText
import com.better.nothing.music.vizualizer.ui.CardHeader
import com.better.nothing.music.vizualizer.ui.ExpressiveCard
import com.better.nothing.music.vizualizer.ui.ExpressiveSplitButton
import com.better.nothing.music.vizualizer.ui.ExpressiveSlider
import com.better.nothing.music.vizualizer.ui.LocalAppSpacing
import com.better.nothing.music.vizualizer.ui.MainViewModel
import com.better.nothing.music.vizualizer.ui.OptionTile
import com.better.nothing.music.vizualizer.ui.ScreenTitle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun SettingsScreen(
    viewModel: MainViewModel,
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
    padding: PaddingValues = PaddingValues(),
) {
    val m3eEnabled by viewModel.m3eEnabled.collectAsStateWithLifecycle()
    val dynamicGainEnabled by viewModel.dynamicGainEnabled.collectAsStateWithLifecycle()
    val overlayWidth by viewModel.overlayWidth.collectAsStateWithLifecycle()
    val overlayHeight by viewModel.overlayHeight.collectAsStateWithLifecycle()
    val overlayYOffset by viewModel.overlayYOffset.collectAsStateWithLifecycle()
    val isAnonymous by viewModel.isAnonymous.collectAsStateWithLifecycle()
    var showAuthDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    if (showAuthDialog) {
        AuthDialog(
            onDismiss = { showAuthDialog = false },
            onSignIn = { e, p -> viewModel.signInWithEmail(e, p) },
            onSignUp = { e, p -> viewModel.signUpWithEmail(e, p) },
        )
    }

    val selectedTheme by viewModel.selectedTheme.collectAsStateWithLifecycle()
    val selectedFont by viewModel.selectedFont.collectAsStateWithLifecycle()
    val selectedDevice by viewModel.selectedDevice.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val localContext = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var showDevModePanel by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = LocalAppSpacing.current.edge)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

        ScreenTitle(
            text = stringResource(R.string.settings_title),
            onLongPress = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                showDevModePanel = !showDevModePanel
            }
        )

        // ── Links & Info ────────────────────────────────────────────────────
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            maxItemsInEachRow = 2,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LinkCard(
                title = stringResource(R.string.app_news),
                icon = Icons.Default.Campaign,
                onClick = { viewModel.showAnnouncementHistory() },
                modifier = Modifier.weight(1f)
            )
            LinkCard(
                title = stringResource(R.string.about_title),
                icon = Icons.Default.Info,
                onClick = { viewModel.showAbout() },
                modifier = Modifier.weight(1f)
            )
            LinkCard(
                title = stringResource(R.string.discord_server),
                icon = Icons.Default.Public,
                onClick = { uriHandler.openUri("https://discord.gg/h7DYNttc8K") },
                modifier = Modifier.weight(1f)
            )
            LinkCard(
                title = stringResource(R.string.github_repository),
                icon = Icons.Default.Code,
                onClick = { uriHandler.openUri("https://github.com/Aleks-Levet/better-nothing-music-visualizer") },
                modifier = Modifier.weight(1f)
            )
        }

        // ── Profile ─────────────────────────────────────────────────────────
        ExpressiveCard {
            CardHeader(title = stringResource(R.string.profile))
            val userNickname by viewModel.userNickname.collectAsStateWithLifecycle()
            val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
            var nicknameInput by remember(userNickname) { mutableStateOf(userNickname) }

            val launcher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri ->
                uri?.let { viewModel.updateProfilePicture(it) }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        .clickable { launcher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (userProfile?.profilePictureUrl != null) {
                        AsyncImage(
                            model = userProfile?.profilePictureUrl,
                            contentDescription = stringResource(R.string.profile_picture),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(20.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    ) {
                        Icon(
                            Icons.Default.AddAPhoto,
                            contentDescription = null,
                            modifier = Modifier.padding(4.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = nicknameInput,
                        onValueChange = {
                            nicknameInput = it
                            if (it.length <= 20) viewModel.setUserNickname(it)
                        },
                        label = { Text(stringResource(R.string.leaderboard_nickname)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                        }
                    )

                    val clipboardManager = LocalClipboardManager.current
                    val currentUid = viewModel.userId.collectAsStateWithLifecycle().value
                    Text(
                        text = "UID: ${currentUid ?: "None"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier
                            .padding(top = 4.dp, start = 4.dp)
                            .clickable {
                                currentUid?.let {
                                    clipboardManager.setText(AnnotatedString(it))
                                    Toast.makeText(
                                        localContext,
                                        "UID copied to clipboard",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Choose an avatar",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            val defaultAvatars = listOf(
                R.drawable.avatar_1,
                R.drawable.avatar_2,
                R.drawable.avatar_3
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(defaultAvatars) { resId ->
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { viewModel.selectDefaultAvatar(resId) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(resId),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().padding(8.dp),
                            tint = Color.Unspecified
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            BodyText(
                text = stringResource(R.string.profile_help_text),
                size = 12.sp
            )
        }

        // ── Account ─────────────────────────────────────────────────────────
        ExpressiveCard {
            CardHeader(title = "Account")
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isAnonymous) Icons.Default.CloudOff else Icons.Default.CloudDone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isAnonymous) stringResource(R.string.anonymous_user) else stringResource(
                            R.string.authenticated_user
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isAnonymous) stringResource(R.string.sync_account_desc) else "Your data is backed up.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                if (isAnonymous) {
                    Button(
                        onClick = { showAuthDialog = true },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.sign_in))
                    }
                } else {
                    IconButton(onClick = { viewModel.signOut() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.Logout,
                            null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        // ── App Theme ───────────────────────────────────────────────────────
        var themeExpanded by remember { mutableStateOf(false) }

        ExpressiveCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        themeExpanded = !themeExpanded
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.app_theme),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(
                    imageVector = if (themeExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
            }

            AnimatedVisibility(visible = themeExpanded) {
                Column(
                    modifier = Modifier.padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Typography
                    Text(
                        text = stringResource(R.string.typography),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    ExpressiveSplitButton(
                        items = listOf("NDot", "NType"),
                        selectedItem = selectedFont,
                        onItemSelection = { viewModel.setSelectedFont(it) },
                        labelProvider = {
                            if (it == "NDot") stringResource(R.string.font_ndot) else stringResource(
                                R.string.font_ntype
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    BodyText(
                        text = stringResource(R.string.typography_help_text),
                        size = 12.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Theme Options
                    val themeOptions = listOf(
                        Triple(
                            "Default",
                            stringResource(R.string.theme_normal),
                            Icons.Default.BrightnessAuto
                        ),
                        Triple(
                            "Liquorice Black",
                            stringResource(R.string.theme_liquorice),
                            Icons.Default.DarkMode
                        ),
                        Triple(
                            "Nothing",
                            stringResource(R.string.theme_nothing),
                            Icons.Default.Settings
                        ),
                        Triple(
                            "Material You",
                            stringResource(R.string.theme_material_you),
                            Icons.Default.Palette
                        ),
                        Triple(
                            "Music",
                            stringResource(R.string.theme_music),
                            Icons.Default.MusicNote
                        )
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        maxItemsInEachRow = 2,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        themeOptions.forEach { (key, label, icon) ->
                            val isSelected = selectedTheme == key
                            OptionTile(
                                label = label,
                                icon = icon,
                                isSelected = isSelected,
                                onClick = {
                                    if (key == "Music" && !viewModel.isNotificationAccessGranted()) {
                                        val message =
                                            localContext.getString(R.string.music_theme_notification_access)
                                        Toast.makeText(localContext, message, Toast.LENGTH_LONG)
                                            .show()
                                        localContext.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                                    } else {
                                        viewModel.setSelectedTheme(key)
                                    }
                                },
                                modifier = Modifier.height(64.dp),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // ── Idle Breathing ──────────────────────────────────────────────────
        if (selectedDevice != DeviceProfile.DEVICE_UNKNOWN) {
            ExpressiveCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Air,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.idle_breathing_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.idle_breathing_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                    Switch(
                        checked = idleBreathingEnabled,
                        onCheckedChange = onIdleBreathingEnabledChanged,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.size(height = 24.dp, width = 48.dp)
                    )
                }

                AnimatedVisibility(visible = idleBreathingEnabled) {
                    Column(
                        modifier = Modifier.padding(top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.idle_pattern),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )

                        val patternOptions = listOf(
                            "pulse" to stringResource(R.string.idle_pattern_pulse),
                            "wave" to stringResource(R.string.idle_pattern_wave),
                            "scanner" to stringResource(R.string.idle_pattern_cylon),
                            "static" to stringResource(R.string.idle_pattern_static),
                            "zebra" to stringResource(R.string.idle_pattern_zebra)
                        )

                        ExpressiveSplitButton(
                            items = patternOptions.map { it.first },
                            selectedItem = idlePattern,
                            onItemSelection = onIdlePatternChanged,
                            labelProvider = { key ->
                                patternOptions.find { it.first == key }?.second ?: key
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // ── Developer Mode ──────────────────────────────────────────────────
        if (showDevModePanel) {
        val devModeEnabled by viewModel.developerModeEnabled.collectAsStateWithLifecycle()
        var showPasswordDialog by remember { mutableStateOf(false) }
        var passwordInput by remember { mutableStateOf("") }

        if (showPasswordDialog) {
            AlertDialog(
                onDismissRequest = { 
                    showPasswordDialog = false
                    passwordInput = ""
                },
                title = { Text(stringResource(R.string.developer_access)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.developer_access_desc))
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text(stringResource(R.string.password)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                autoCorrect = false
                            )
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (viewModel.verifyDeveloperPassword(passwordInput)) {
                                viewModel.setDeveloperModeEnabled(true)
                                showPasswordDialog = false
                                passwordInput = ""
                            } else {
                                val message = localContext.getString(R.string.incorrect_password)
                                Toast.makeText(localContext, message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text(stringResource(R.string.unlock))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { 
                        showPasswordDialog = false
                        passwordInput = ""
                    }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

            ExpressiveCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.developer_mode),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.developer_mode_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                    Switch(
                        checked = devModeEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                showPasswordDialog = true
                            } else {
                                viewModel.setDeveloperModeEnabled(false)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.size(height = 24.dp, width = 48.dp)
                    )
                }

                AnimatedVisibility(visible = devModeEnabled) {
                    Column(
                        modifier = Modifier.padding(top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 1. Announcements
                        val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
                        ExpressiveCard(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(
                                    Icons.Default.Campaign,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.global_announcements),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        stringResource(R.string.global_announcements_desc),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                                if (isAdmin || BuildConfig.DEBUG) {
                                    Button(
                                        onClick = { viewModel.showAnnouncementEditor() },
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(
                                            horizontal = 12.dp,
                                            vertical = 8.dp
                                        )
                                    ) {
                                        Text(
                                            stringResource(R.string.create),
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                            }
                        }

                        // 2. Shizuku Source Toggle
                        val shizukuUnlocked by viewModel.shizukuSourceUnlocked.collectAsStateWithLifecycle()
                        ExpressiveCard(
                            containerColor = if (shizukuUnlocked) MaterialTheme.colorScheme.primary.copy(
                                alpha = 0.1f
                            ) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            border = BorderStroke(
                                1.dp,
                                if (shizukuUnlocked) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outline.copy(
                                    alpha = 0.1f
                                )
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Default.Terminal,
                                        null,
                                        tint = if (shizukuUnlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(
                                            alpha = 0.6f
                                        )
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            stringResource(R.string.unlock_shizuku_source),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            stringResource(R.string.unlock_shizuku_source_desc),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                                Switch(
                                    checked = shizukuUnlocked,
                                    onCheckedChange = { viewModel.setShizukuSourceUnlocked(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.size(height = 24.dp, width = 48.dp)
                                )
                            }
                        }

                        // 3. Device Spoofing Toggle
                        val showSpoofing by viewModel.showSpoofingSettings.collectAsStateWithLifecycle()
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.setShowSpoofingSettings(!showSpoofing) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Dns,
                                        null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        stringResource(R.string.spoof_device),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Icon(
                                    if (showSpoofing) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                )
                            }

                            AnimatedVisibility(visible = showSpoofing) {
                                Column(
                                    modifier = Modifier.padding(top = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    val spoofedDevice by viewModel.spoofedDevice.collectAsStateWithLifecycle()
                                    val devices = listOf(
                                        DeviceProfile.DEVICE_NP1,
                                        DeviceProfile.DEVICE_NP2,
                                        DeviceProfile.DEVICE_NP2A,
                                        DeviceProfile.DEVICE_NP3A,
                                        DeviceProfile.DEVICE_NP4A,
                                        DeviceProfile.DEVICE_NP4APRO,
                                        DeviceProfile.DEVICE_NP3
                                    )

                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        devices.forEach { dev ->
                                            ExpressiveSplitButton(
                                                items = devices,
                                                selectedItem = spoofedDevice,
                                                onItemSelection = { dev -> viewModel.setSpoofedDevice(dev) },
                                                labelProvider = { dev ->
                                                    DeviceProfile.deviceName(dev).replace("Nothing Phone ", "")
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                    BodyText(
                                        text = stringResource(R.string.spoof_device_description),
                                        size = 11.sp
                                    )
                                }
                            }
                        }

                        // 4. Locale Spoofing
                        val currentSpoofLocale by viewModel.spoofLocale.collectAsStateWithLifecycle()
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Language,
                                        null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        stringResource(R.string.spoof_locale),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            val locales = listOf(
                                null to "System",
                                "en" to "EN",
                                "fr" to "FR",
                                "it" to "IT",
                                "de" to "DE",
                                "es" to "ES",
                                "ru" to "RU",
                                "tr" to "TR",
                                "pt-BR" to "PT-BR",
                                "zh-CN" to "ZH-CN",
                                "ja" to "JA",
                                "hi" to "HI",
                                "cy" to "CY"
                            )

                            ExpressiveSplitButton(
                                items = locales.map { it.first },
                                selectedItem = currentSpoofLocale,
                                onItemSelection = { tag -> viewModel.setSpoofLocale(tag) },
                                labelProvider = { tag ->
                                    // Find the matching visual label from our list of pairs
                                    locales.firstOrNull { it.first == tag }?.second.orEmpty()
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            BodyText(
                                text = stringResource(R.string.spoof_locale_description),
                                size = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // ── Experimental Features ───────────────────────────────────────────
        var experimentalExpanded by remember { mutableStateOf(false) }

        ExpressiveCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        experimentalExpanded = !experimentalExpanded
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.experimental_features),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(
                    imageVector = if (experimentalExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
            }

            AnimatedVisibility(visible = experimentalExpanded) {
                Column(
                    modifier = Modifier.padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        maxItemsInEachRow = 2,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OptionTile(
                            label = stringResource(R.string.expressive_ui),
                            icon = Icons.Default.AutoAwesome,
                            isSelected = m3eEnabled,
                            onClick = { viewModel.setM3EEnabled(!m3eEnabled) }
                        )
                        if (selectedDevice != DeviceProfile.DEVICE_UNKNOWN) {
                            OptionTile(
                                label = stringResource(R.string.notification_flash_title),
                                icon = Icons.Default.FlashOn,
                                isSelected = notificationFlashEnabled,
                                onClick = { onNotificationFlashEnabledChanged(!notificationFlashEnabled) }
                            )
                        }
                        OptionTile(
                            label = stringResource(R.string.nav_overlay),
                            icon = Icons.Default.Layers,
                            isSelected = overlayEnabled,
                            onClick = { onOverlayEnabledChanged(!overlayEnabled) }
                        )
                        if (selectedDevice != DeviceProfile.DEVICE_UNKNOWN) {
                            OptionTile(
                                label = stringResource(R.string.disable_glyphs_when_silent_title),
                                icon = Icons.AutoMirrored.Filled.VolumeOff,
                                isSelected = disableGlyphsWhenSilent,
                                onClick = { onDisableGlyphsWhenSilentChanged(!disableGlyphsWhenSilent) }
                            )
                        }
                        OptionTile(
                            label = stringResource(R.string.dynamic_gain),
                            icon = Icons.AutoMirrored.Filled.TrendingUp,
                            isSelected = dynamicGainEnabled,
                            onClick = { viewModel.setDynamicGainEnabled(!dynamicGainEnabled) }
                        )
                        if (selectedDevice != DeviceProfile.DEVICE_UNKNOWN) {
                            OptionTile(
                                label = stringResource(R.string.strobe_mode),
                                icon = Icons.Default.Vibration,
                                isSelected = strobeEnabled,
                                onClick = { onStrobeEnabledChanged(!strobeEnabled) }
                            )
                        }
                    }

                    AnimatedVisibility(visible = overlayEnabled) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Width Slider
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.overlay_width),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${overlayWidth}dp",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            ExpressiveSlider(
                                value = overlayWidth.toFloat(),
                                onValueChange = { viewModel.setOverlayWidth(it.toInt()) },
                                valueRange = 40f..360f,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Height Slider
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.overlay_height),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${overlayHeight}dp",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            ExpressiveSlider(
                                value = overlayHeight.toFloat(),
                                onValueChange = { viewModel.setOverlayHeight(it.toInt()) },
                                valueRange = 4f..64f,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Y Offset Slider
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.vertical_position),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${overlayYOffset}dp",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            ExpressiveSlider(
                                value = overlayYOffset.toFloat(),
                                onValueChange = { viewModel.setOverlayYOffset(it.toInt()) },
                                valueRange = -20f..80f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(70.dp))
    }
}

@Composable
private fun LinkCard(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    Surface(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            onClick()
        },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.height(64.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                lineHeight = 14.sp
            )
            Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
        }
    }
}
