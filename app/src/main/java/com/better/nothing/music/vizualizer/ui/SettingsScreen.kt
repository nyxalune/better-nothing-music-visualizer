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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
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
    strobeEnabled: Boolean,
    onStrobeEnabledChanged: (Boolean) -> Unit,
    disableGlyphsWhenSilent: Boolean,
    onDisableGlyphsWhenSilentChanged: (Boolean) -> Unit,
) {
    val m3eEnabled = LocalM3EEnabled.current
    val scrollState = rememberScrollState()

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
                Triple("Default", stringResource(R.string.theme_normal), Icons.Default.BrightnessAuto),
                Triple("Liquorice Black", stringResource(R.string.theme_liquorice), Icons.Default.DarkMode),
                Triple("Nothing", stringResource(R.string.theme_nothing), Icons.Default.Settings),
                Triple("Material You", stringResource(R.string.theme_material_you), Icons.Default.Palette)
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                maxItemsInEachRow = 2,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                themeOptions.forEach { (key, label, icon) ->
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
                        border = if (isSelected) BorderStroke(1.dp, contentColor.copy(alpha = 0.5f)) else null,
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
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
                            text = "Idle Breathing",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Pulse Glyphs when no audio is playing",
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
                        text = "Idle Pattern",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    val patternOptions = listOf(
                        "pulse" to "Pulse",
                        "wave" to "Wave",
                        "scanner" to "Cylon",
                        "static" to "Static",
                        "zebra" to "Zebra"
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

        // ── Developer Mode ──────────────────────────────────────────────────
        val devModeEnabled by viewModel.developerModeEnabled.collectAsStateWithLifecycle()
        var showPasswordDialog by remember { mutableStateOf(false) }
        var passwordInput by remember { mutableStateOf("") }
        val context = LocalContext.current

        if (showPasswordDialog) {
            AlertDialog(
                onDismissRequest = { 
                    showPasswordDialog = false
                    passwordInput = ""
                },
                title = { Text("Developer Access") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Please enter the developer password to enable advanced features and device spoofing.")
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text("Password") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (passwordInput == "BNMV") {
                                viewModel.setDeveloperModeEnabled(true)
                                showPasswordDialog = false
                                passwordInput = ""
                            } else {
                                Toast.makeText(context, "Incorrect Password", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Unlock")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { 
                        showPasswordDialog = false
                        passwordInput = ""
                    }) {
                        Text("Cancel")
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
                            text = "Developer Mode",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Advanced testing and device spoofing",
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
                Column(modifier = Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.spoof_device),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
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
                            title = "Expressive UI",
                            icon = Icons.Default.AutoAwesome,
                            checked = m3eEnabled,
                            onCheckedChange = { viewModel.setM3EEnabled(it) },
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
                            title = "Strobe Mode",
                            icon = Icons.Default.Vibration,
                            checked = strobeEnabled,
                            onCheckedChange = onStrobeEnabledChanged,
                            modifier = Modifier.weight(1f)
                        )
                        FeatureCard(
                            title = "Silent Auto-Off",
                            icon = Icons.AutoMirrored.Filled.VolumeOff,
                            checked = disableGlyphsWhenSilent,
                            onCheckedChange = onDisableGlyphsWhenSilentChanged,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // ── About ────────────────────────────────────────────
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
