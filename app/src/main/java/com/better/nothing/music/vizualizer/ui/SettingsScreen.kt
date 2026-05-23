package com.better.nothing.music.vizualizer.ui

import com.better.nothing.music.vizualizer.R
import com.better.nothing.music.vizualizer.model.DeviceProfile
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
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
    disableGlyphsWhenSilent: Boolean,
    onDisableGlyphsWhenSilentChanged: (Boolean) -> Unit,
) {
    val m3eEnabled = LocalM3EEnabled.current
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    val selectedTheme by viewModel.selectedTheme.collectAsStateWithLifecycle()
    val selectedFont by viewModel.selectedFont.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(modifier = Modifier.height(50.dp))
        ScreenTitle(text = stringResource(R.string.settings_title))

        // ── Typography ──────────────────────────────────────────────────────
        ExpressiveCard {
            CardHeader(title = stringResource(R.string.typography))
            
            ExpressiveSegmentedButtonRow(
                items = listOf("NDot", "NType"),
                selectedItem = selectedFont,
                onItemSelection = { viewModel.setSelectedFont(it) },
                labelProvider = { it },
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
                "Default" to stringResource(R.string.theme_normal),
                "Liquorice Black" to stringResource(R.string.theme_liquorice),
                "Nothing" to stringResource(R.string.theme_nothing),
                "Material You" to stringResource(R.string.theme_material_you)
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                maxItemsInEachRow = 2,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                themeOptions.forEach { (key, label) ->
                    val isSelected = selectedTheme == key
                    val backgroundColor by animateColorAsState(
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                    val contentColor by animateColorAsState(
                        if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        onClick = { viewModel.setSelectedTheme(key) },
                        shape = RoundedCornerShape(16.dp),
                        color = backgroundColor,
                        contentColor = contentColor,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // ── Experimental Features ───────────────────────────────────────────
        val devModeEnabled by viewModel.developerModeEnabled.collectAsStateWithLifecycle()
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
                            title = "Expressive UI",
                            icon = Icons.Default.AutoAwesome,
                            checked = m3eEnabled,
                            onCheckedChange = { viewModel.setM3EEnabled(it) },
                            modifier = Modifier.weight(1f)
                        )
                        FeatureCard(
                            title = "Idle Breathing",
                            icon = Icons.Default.Air,
                            checked = idleBreathingEnabled,
                            onCheckedChange = onIdleBreathingEnabledChanged,
                            modifier = Modifier.weight(1f)
                        )
                        FeatureCard(
                            title = "Notif. Flash",
                            icon = Icons.Default.FlashOn,
                            checked = notificationFlashEnabled,
                            onCheckedChange = onNotificationFlashEnabledChanged,
                            modifier = Modifier.weight(1f)
                        )
                        FeatureCard(
                            title = "Silent Auto-Off",
                            icon = Icons.Default.VolumeOff,
                            checked = disableGlyphsWhenSilent,
                            onCheckedChange = onDisableGlyphsWhenSilentChanged,
                            modifier = Modifier.weight(1f)
                        )
                        FeatureCard(
                            title = "Dev Mode",
                            icon = Icons.Default.Code,
                            checked = devModeEnabled,
                            onCheckedChange = { viewModel.setDeveloperModeEnabled(it) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (idleBreathingEnabled || devModeEnabled) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                if (idleBreathingEnabled) {
                                    var patternExpanded by remember { mutableStateOf(false) }
                                    val patternNames = mapOf(
                                        "pulse" to "Breathing Pulse",
                                        "wave" to "Traveling Wave",
                                        "scanner" to "Cylon Scanner",
                                        "static" to "Low Static"
                                    )

                                    Column {
                                        Text(
                                            text = "Idle Pattern",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )

                                        Box {
                                            OutlinedTextField(
                                                value = patternNames[idlePattern] ?: idlePattern,
                                                onValueChange = {},
                                                readOnly = true,
                                                modifier = Modifier.fillMaxWidth(),
                                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = patternExpanded) },
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .matchParentSize()
                                                    .clickable { patternExpanded = true }
                                            )

                                            DropdownMenu(
                                                expanded = patternExpanded,
                                                onDismissRequest = { patternExpanded = false },
                                                modifier = Modifier.fillMaxWidth(0.8f)
                                            ) {
                                                patternNames.forEach { (key, name) ->
                                                    DropdownMenuItem(
                                                        text = { Text(name) },
                                                        onClick = {
                                                            onIdlePatternChanged(key)
                                                            patternExpanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                if (devModeEnabled) {
                                    val spoofedDevice by viewModel.spoofedDevice.collectAsStateWithLifecycle()
                                    var spoofExpanded by remember { mutableStateOf(false) }

                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = stringResource(R.string.spoof_device),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )

                                        Box {
                                            OutlinedTextField(
                                                value = DeviceProfile.deviceName(spoofedDevice),
                                                onValueChange = {},
                                                readOnly = true,
                                                modifier = Modifier.fillMaxWidth(),
                                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = spoofExpanded) },
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .matchParentSize()
                                                    .clickable { spoofExpanded = true }
                                            )

                                            DropdownMenu(
                                                expanded = spoofExpanded,
                                                onDismissRequest = { spoofExpanded = false },
                                                modifier = Modifier.fillMaxWidth(0.8f)
                                            ) {
                                                val devices = listOf(
                                                    DeviceProfile.DEVICE_NP1,
                                                    DeviceProfile.DEVICE_NP2,
                                                    DeviceProfile.DEVICE_NP2A,
                                                    DeviceProfile.DEVICE_NP3A,
                                                    DeviceProfile.DEVICE_NP4A,
                                                    DeviceProfile.DEVICE_NP4APRO,
                                                    DeviceProfile.DEVICE_NP3
                                                )
                                                devices.forEach { dev ->
                                                    DropdownMenuItem(
                                                        text = { Text(DeviceProfile.deviceName(dev)) },
                                                        onClick = {
                                                            viewModel.setSpoofedDevice(dev)
                                                            spoofExpanded = false
                                                        }
                                                    )
                                                }
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
            }
        }

        // ── Zones Configuration ──────────────────────────────────────────────
        val configStatus by viewModel.configUpdateStatus.collectAsStateWithLifecycle()
        val configVersion by viewModel.configVersion.collectAsStateWithLifecycle()
        val remoteVersion by viewModel.remoteConfigVersion.collectAsStateWithLifecycle()

        LaunchedEffect(Unit) {
            viewModel.checkRemoteConfigVersion()
        }

        LaunchedEffect(configStatus) {
            when (val status = configStatus) {
                is MainViewModel.ConfigUpdateStatus.Success -> {
                    Toast.makeText(context, status.message, Toast.LENGTH_SHORT).show()
                    viewModel.resetConfigUpdateStatus()
                }
                is MainViewModel.ConfigUpdateStatus.Error -> {
                    Toast.makeText(context, status.message, Toast.LENGTH_LONG).show()
                    viewModel.resetConfigUpdateStatus()
                }
                else -> {}
            }
        }

        ExpressiveCard {
            CardHeader(title = "Visualizer Configuration")
            
            BodyText(
                text = "The zones.config file defines how frequencies map to Glyph LEDs. Updating from GitHub ensures support for new devices and presets.",
                size = 13.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Version: $configVersion",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (remoteVersion != null && remoteVersion != "Unknown") {
                            val isUpdateAvailable = remoteVersion != configVersion
                            Text(
                                text = if (isUpdateAvailable) "Latest: $remoteVersion" else "Up to date",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isUpdateAvailable) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    if (remoteVersion != null && remoteVersion != "Unknown" && remoteVersion != configVersion) {
                        Surface(
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "UPDATE AVAILABLE",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val filePickerLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.GetContent()
            ) { uri ->
                uri?.let { viewModel.importZonesConfig(uri) }
            }

            val isUpdateAvailable = remoteVersion != null && remoteVersion != "Unknown" && remoteVersion != configVersion
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.updateZonesConfig() },
                    enabled = configStatus is MainViewModel.ConfigUpdateStatus.Idle,
                    modifier = Modifier.weight(1.5f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (configStatus is MainViewModel.ConfigUpdateStatus.Updating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.CloudDownload, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (isUpdateAvailable) "Update Now" else "Check GitHub")
                    }
                }

                Button(
                    onClick = { filePickerLauncher.launch("*/*") },
                    enabled = configStatus is MainViewModel.ConfigUpdateStatus.Idle,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Local")
                }
            }
        }

        // ── About ────────────────────────────────────────────────────────────
        ExpressiveCard(
            modifier = Modifier.clickable { viewModel.showAbout() }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.about_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Credits, version info and legal",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
        }

        Spacer(modifier = Modifier.height(70.dp))
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
        shape = RoundedCornerShape(16.dp),
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
