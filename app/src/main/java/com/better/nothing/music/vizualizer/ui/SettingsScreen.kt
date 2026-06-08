package com.better.nothing.music.vizualizer.ui

import com.better.nothing.music.vizualizer.R
import com.better.nothing.music.vizualizer.model.DeviceProfile
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
) {
    val m3eEnabled by viewModel.m3eEnabled.collectAsStateWithLifecycle()
    val dynamicGainEnabled by viewModel.dynamicGainEnabled.collectAsStateWithLifecycle()
    val batterySaverEnabled by viewModel.batterySaverEnabled.collectAsStateWithLifecycle()
    val batterySaverThreshold by viewModel.batterySaverThreshold.collectAsStateWithLifecycle()
    val overlayWidth by viewModel.overlayWidth.collectAsStateWithLifecycle()
    val overlayHeight by viewModel.overlayHeight.collectAsStateWithLifecycle()
    val overlayYOffset by viewModel.overlayYOffset.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    val selectedTheme by viewModel.selectedTheme.collectAsStateWithLifecycle()
    val selectedFont by viewModel.selectedFont.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val localContext = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(modifier = Modifier.height(50.dp))
        ScreenTitle(text = stringResource(R.string.settings_title))

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

        // ── Typography ──────────────────────────────────────────────────────
        ExpressiveCard {
            CardHeader(title = stringResource(R.string.typography))
            
            ExpressiveSegmentedButtonRow(
                items = listOf("NDot", "NType"),
                selectedItem = selectedFont,
                onItemSelection = { viewModel.setSelectedFont(it) },
                labelProvider = { if (it == "NDot") stringResource(R.string.font_ndot) else stringResource(R.string.font_ntype) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))
            BodyText(
                text = stringResource(R.string.typography_help_text),
                size = 12.sp
            )
        }

        // ── App Theme ───────────────────────────────────────────────────────
        ExpressiveCard {
            CardHeader(title = stringResource(R.string.app_theme))

            val themeOptions = listOf(
                Triple("Default", stringResource(R.string.theme_normal), Icons.Default.BrightnessAuto),
                Triple("Liquorice Black", stringResource(R.string.theme_liquorice), Icons.Default.DarkMode),
                Triple("Nothing", stringResource(R.string.theme_nothing), Icons.Default.Settings),
                Triple("Material You", stringResource(R.string.theme_material_you), Icons.Default.Palette),
                Triple("Music", stringResource(R.string.theme_music), Icons.Default.MusicNote)
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
                                Toast.makeText(localContext, localContext.getString(R.string.music_theme_notification_access), Toast.LENGTH_LONG).show()
                                localContext.startActivity(android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
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

        // ── Idle Breathing ──────────────────────────────────────────────────
        ExpressiveCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Air, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
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
                    )
                )
            }

            AnimatedVisibility(visible = idleBreathingEnabled) {
                Column(modifier = Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

                    ExpressiveSegmentedButtonRow(
                        items = patternOptions.map { it.first },
                        selectedItem = idlePattern,
                        onItemSelection = onIdlePatternChanged,
                        labelProvider = { key -> patternOptions.find { it.first == key }?.second ?: key },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // ── Strobe Mode ─────────────────────────────────────────────────────
        ExpressiveCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Vibration, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(
                            text = stringResource(R.string.strobe_mode),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.strobe_mode_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                Switch(
                    checked = strobeEnabled,
                    onCheckedChange = onStrobeEnabledChanged,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }

        // ── Developer Mode ──────────────────────────────────────────────────
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
                                Toast.makeText(localContext, localContext.getString(R.string.incorrect_password), Toast.LENGTH_SHORT).show()
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
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
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
                    )
                )
            }

            AnimatedVisibility(visible = devModeEnabled) {
                Column(modifier = Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // 1. Announcements
                    ExpressiveCard(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(Icons.Default.Campaign, null, tint = MaterialTheme.colorScheme.primary)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.global_announcements), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.global_announcements_desc), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                            Button(
                                onClick = { viewModel.showAnnouncementEditor() },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(stringResource(R.string.create), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    // 2. Shizuku Source Toggle
                    val shizukuUnlocked by viewModel.shizukuSourceUnlocked.collectAsStateWithLifecycle()
                    ExpressiveCard(
                        containerColor = if (shizukuUnlocked) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        border = BorderStroke(1.dp, if (shizukuUnlocked) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Default.Terminal, null, tint = if (shizukuUnlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                Column {
                                    Text(stringResource(R.string.unlock_shizuku_source), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(stringResource(R.string.unlock_shizuku_source_desc), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                }
                            }
                            Switch(
                                checked = shizukuUnlocked,
                                onCheckedChange = { viewModel.setShizukuSourceUnlocked(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                )
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
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Default.Dns, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                Text(stringResource(R.string.spoof_device), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            }
                            Icon(
                                if (showSpoofing) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                        }

                        AnimatedVisibility(visible = showSpoofing) {
                            Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                                        NativeFilterChip(
                                            label = DeviceProfile.deviceName(dev).replace("Nothing Phone ", ""),
                                            selected = spoofedDevice == dev,
                                            onClick = { viewModel.setSpoofedDevice(dev) }
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
                }
            }
        }

        // ── Experimental Features ───────────────────────────────────────────
        var experimentalExpanded by remember { mutableStateOf(false) }

        ExpressiveCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { experimentalExpanded = !experimentalExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
                Column(modifier = Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        maxItemsInEachRow = 2,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FeatureCard(
                            title = stringResource(R.string.expressive_ui),
                            icon = Icons.Default.AutoAwesome,
                            checked = m3eEnabled,
                            onCheckedChange = { viewModel.setM3EEnabled(it) },
                            modifier = Modifier.weight(1f)
                        )
                        FeatureCard(
                            title = stringResource(R.string.notification_flash_title),
                            icon = Icons.Default.FlashOn,
                            checked = notificationFlashEnabled,
                            onCheckedChange = onNotificationFlashEnabledChanged,
                            modifier = Modifier.weight(1f)
                        )
                        FeatureCard(
                            title = stringResource(R.string.nav_overlay),
                            icon = Icons.Default.Layers,
                            checked = overlayEnabled,
                            onCheckedChange = onOverlayEnabledChanged,
                            modifier = Modifier.weight(1f)
                        )
                        FeatureCard(
                            title = stringResource(R.string.disable_glyphs_when_silent_title),
                            icon = Icons.AutoMirrored.Filled.VolumeOff,
                            checked = disableGlyphsWhenSilent,
                            onCheckedChange = onDisableGlyphsWhenSilentChanged,
                            modifier = Modifier.weight(1f)
                        )
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

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        maxItemsInEachRow = 2,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FeatureCard(
                            title = stringResource(R.string.dynamic_gain),
                            icon = Icons.AutoMirrored.Filled.TrendingUp,
                            checked = dynamicGainEnabled,
                            onCheckedChange = { viewModel.setDynamicGainEnabled(it) },
                            modifier = Modifier.weight(1f)
                        )
                        FeatureCard(
                            title = stringResource(R.string.battery_saver),
                            icon = Icons.Default.BatteryChargingFull,
                            checked = batterySaverEnabled,
                            onCheckedChange = { viewModel.setBatterySaverEnabled(it) },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    AnimatedVisibility(visible = batterySaverEnabled) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.saver_threshold),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "$batterySaverThreshold%",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            ExpressiveSlider(
                                value = batterySaverThreshold.toFloat(),
                                onValueChange = { viewModel.setBatterySaverThreshold(it.toInt()) },
                                valueRange = 5f..50f,
                                steps = 9,
                                modifier = Modifier.fillMaxWidth()
                            )
                            BodyText(
                                text = stringResource(R.string.battery_saver_desc),
                                size = 11.sp
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
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

@Composable
private fun FeatureCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    )
    val contentColor by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    )

    Surface(
        onClick = { onCheckedChange(!checked) },
        shape = MaterialTheme.shapes.medium,
        color = backgroundColor,
        contentColor = contentColor,
        modifier = modifier.height(64.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier.weight(1f),
                lineHeight = 14.sp
            )
        }
    }
}
