package com.better.nothing.music.vizualizer.ui

import com.better.nothing.music.vizualizer.R
import com.better.nothing.music.vizualizer.model.DeviceProfile
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Air
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi

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
    val scrollState = rememberScrollState()

    val selectedTheme by viewModel.selectedTheme.collectAsStateWithLifecycle()
    val selectedFont by viewModel.selectedFont.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Spacer(modifier = Modifier.height(50.dp))
        ScreenTitle(text = stringResource(R.string.settings_title))

        // ── Typography ──────────────────────────────────────────────────────
        Column {
            Text(
                text = stringResource(R.string.typography),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                val fontOptions = listOf("NDot", "NType")
                fontOptions.forEachIndexed { index, font ->
                    SegmentedButton(
                        selected = selectedFont == font,
                        onClick = { viewModel.setSelectedFont(font) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = fontOptions.size),
                        label = { Text(font) }
                    )
                }
            }

            Text(
                text = stringResource(R.string.typography_help_text),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // ── App Theme ───────────────────────────────────────────────────────
        Column {
            Text(
                text = stringResource(R.string.app_theme),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 12.dp),
            )

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
                    Card(
                        onClick = { viewModel.setSelectedTheme(key) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) Color.White else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            contentColor = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(4.dp),
                                maxLines = 1,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Text(
                text = stringResource(R.string.theme_help_text),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // ── Experimental Features ───────────────────────────────────────────
        val devModeEnabled by viewModel.developerModeEnabled.collectAsStateWithLifecycle()
        var experimentalExpanded by remember { mutableStateOf(false) }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(
                onClick = { experimentalExpanded = !experimentalExpanded },
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = stringResource(R.string.experimental_features),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Icon(
                        imageVector = if (experimentalExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = Color.Gray
                    )
                }
            }

            if (experimentalExpanded) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    maxItemsInEachRow = 2,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth(),
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
                                        style = MaterialTheme.typography.titleSmall,
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
                                                .background(Color.Transparent)
                                                .clickable { patternExpanded = true }
                                        )

                                        DropdownMenu(
                                            expanded = patternExpanded,
                                            onDismissRequest = { patternExpanded = false },
                                            modifier = Modifier.fillMaxWidth(0.9f)
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
                                if (idleBreathingEnabled) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                                }

                                val spoofedDevice by viewModel.spoofedDevice.collectAsStateWithLifecycle()
                                var spoofExpanded by remember { mutableStateOf(false) }

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = stringResource(R.string.spoof_device),
                                        style = MaterialTheme.typography.titleSmall,
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
                                                .background(Color.Transparent)
                                                .clickable { spoofExpanded = true }
                                        )

                                        DropdownMenu(
                                            expanded = spoofExpanded,
                                            onDismissRequest = { spoofExpanded = false },
                                            modifier = Modifier.fillMaxWidth(0.9f)
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
                                    Text(
                                        text = stringResource(R.string.spoof_device_description),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
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
        val context = LocalContext.current

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

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Visualizer Configuration",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 4.dp),
            )

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Zones Configuration",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "The zones.config file defines how frequencies map to Glyph LEDs. You can update it from GitHub to get the latest presets and device support.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Current: $configVersion",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (remoteVersion != null && remoteVersion != "Unknown") {
                                val isUpdateAvailable = remoteVersion != configVersion
                                Text(
                                    text = if (isUpdateAvailable) "Latest: $remoteVersion" else "Up to date",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isUpdateAvailable) Color(0xFFFF4444) else Color.Gray
                                )
                            }
                        }

                        if (remoteVersion != null && remoteVersion != "Unknown" && remoteVersion != configVersion) {
                            Text(
                                text = "Update Available!",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFF4444),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

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
                            modifier = Modifier.weight(1.4f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (configStatus is MainViewModel.ConfigUpdateStatus.Updating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Updating...")
                            } else {
                                Text(if (isUpdateAvailable) "Update" else "Check for Updates")
                            }
                        }

                        Button(
                            onClick = { filePickerLauncher.launch("*/*") },
                            enabled = configStatus is MainViewModel.ConfigUpdateStatus.Idle,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileOpen,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Local", maxLines = 1)
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "About",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 4.dp),
            )

            Card(
                onClick = { viewModel.showAbout() },
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.about_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "Credits, version info, and more",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
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
    Card(
        onClick = { onCheckedChange(!checked) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (checked) Color.White else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            contentColor = if (checked) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
        ),
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
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
