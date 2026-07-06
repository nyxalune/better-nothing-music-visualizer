package com.better.nothing.music.vizualizer.ui.SecondaryScreens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.better.nothing.music.vizualizer.BuildConfig
import com.better.nothing.music.vizualizer.R
import com.better.nothing.music.vizualizer.ui.BodyText
import com.better.nothing.music.vizualizer.ui.CardHeader
import com.better.nothing.music.vizualizer.ui.ExpressiveCard
import com.better.nothing.music.vizualizer.ui.MainViewModel
import com.better.nothing.music.vizualizer.ui.ScreenTitle
import com.better.nothing.music.vizualizer.ui.SectionHeader
import java.util.concurrent.TimeUnit

@Composable
internal fun AboutScreen(
    viewModel: MainViewModel,
    onDismiss: (() -> Unit)? = null
) {
    val scrollState = rememberScrollState()
    val uriHandler = LocalUriHandler.current
    val haptics = LocalHapticFeedback.current
    val configVersion by viewModel.configVersion.collectAsStateWithLifecycle()
    val appUpdateStatus by viewModel.appUpdateStatus.collectAsStateWithLifecycle()
    
    val totalTime by viewModel.totalVisualizedTime.collectAsStateWithLifecycle()
    val idleTime by viewModel.totalIdleTime.collectAsStateWithLifecycle()
    val activeTime by viewModel.totalActiveTime.collectAsStateWithLifecycle()
    val glyphTime by viewModel.totalGlyphTime.collectAsStateWithLifecycle()
    val hapticTime by viewModel.totalHapticTime.collectAsStateWithLifecycle()
    val flashlightTime by viewModel.totalFlashlightTime.collectAsStateWithLifecycle()
    
    var depressedClickCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        if (appUpdateStatus is MainViewModel.AppUpdateStatus.Idle) {
            viewModel.checkAppUpdate()
        }
    }

    val credits = listOf(
        CreditEntry("Aleks-Levet", stringResource(R.string.credit_alekslevet_role), "Aleks-Levet"),
        CreditEntry("Oliver Lebaigue", stringResource(R.string.credit_oliver_role), githubUsername = "oliver-lebaigue-bright-bench"),
        CreditEntry("rKyzen (aka Shivank Dan)", stringResource(R.string.credit_rkyzen_role), "rKyzen"),
        CreditEntry("Nicouschulas", stringResource(R.string.credit_nicouschulas_role), "Nicouschulas"),
        CreditEntry("SebiAi", stringResource(R.string.credit_sebiai_role), "SebiAi"),
        CreditEntry("Earendel-lab", stringResource(R.string.credit_earnedel_role), "Earendel-lab"),
        CreditEntry("あけ なるかみ", stringResource(R.string.credit_ake_role), null),
        CreditEntry("Interlastic", stringResource(R.string.credit_interlastic_role), "Interlastic"),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 8.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

        if (onDismiss == null) {
            Spacer(modifier = Modifier.height(50.dp))
        } else {
            Spacer(modifier = Modifier.height(16.dp))
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back)
                )
            }
        }
        ScreenTitle(text = stringResource(R.string.about_title))

        ExpressiveCard {
            CardHeader(title = "Application")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    modifier = Modifier
                        .size(64.dp)
                        .clickable {
                            depressedClickCount++
                            if (depressedClickCount >= 10) {
                                viewModel.onDevDepressed()
                                depressedClickCount = 0
                            }
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.app_icon),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = Color.Unspecified
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.version_info, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
            )

            // Version detail
            InfoRow(
                icon = Icons.Default.Info,
                title = "Configuration Version",
                subtitle = configVersion
            )

            // GitHub Action
            InfoRow(
                icon = Icons.Default.Code,
                title = "GitHub Repository",
                subtitle = "View source and contributions",
                onClick = { uriHandler.openUri("https://github.com/Aleks-Levet/better-nothing-music-visualizer") }
            )

            // License Action
            InfoRow(
                icon = Icons.Default.Gavel,
                title = stringResource(R.string.license_agreement),
                subtitle = stringResource(R.string.read_license),
                onClick = { viewModel.showLicense() }
            )

            // Analytics Disclaimer
            InfoRow(
                icon = Icons.Default.BarChart,
                title = stringResource(R.string.analytics_disclaimer_title),
                subtitle = stringResource(R.string.analytics_disclaimer_text)
            )

            // Update Action
            val statusText = when (val status = appUpdateStatus) {
                is MainViewModel.AppUpdateStatus.Checking -> "Checking for updates..."
                is MainViewModel.AppUpdateStatus.Available -> "Update available: ${status.version}"
                is MainViewModel.AppUpdateStatus.Downloading -> "Downloading: ${(status.progress * 100).toInt()}%"
                is MainViewModel.AppUpdateStatus.UpToDate -> "Latest version installed"
                is MainViewModel.AppUpdateStatus.Error -> "Error: ${status.message}"
                else -> "Check for software updates"
            }

            InfoRow(
                icon = Icons.Default.Sync,
                title = "Software Update",
                subtitle = statusText,
                onClick = {
                    val status = appUpdateStatus
                    if (status is MainViewModel.AppUpdateStatus.Available) {
                        if (status.apkUrl != null) {
                            viewModel.downloadAndInstallUpdate(status.apkUrl, status.version)
                        } else {
                            uriHandler.openUri(status.url)
                        }
                    } else if (status !is MainViewModel.AppUpdateStatus.Downloading) {
                        viewModel.checkAppUpdate()
                    }
                },
                trailingContent = {
                    val status = appUpdateStatus
                    if (status is MainViewModel.AppUpdateStatus.Checking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else if (status is MainViewModel.AppUpdateStatus.Downloading) {
                        CircularProgressIndicator(
                            progress = { status.progress },
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else if (status is MainViewModel.AppUpdateStatus.Available) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ) {
                            Text(
                                "UPDATE",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Icon(
                            Icons.Default.ChevronRight,
                            null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                }
            )
        }

        SectionHeader(text = "Usage Statistics")
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            // Main visualizer time
            StatCard(
                icon = Icons.Default.Timer,
                label = "Total Visualized",
                value = formatTime(totalTime),
                color = MaterialTheme.colorScheme.primary
            )
            
            // Active vs Idle breakdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SmallStatCard(
                    label = "Active Music",
                    value = formatTime(activeTime),
                    icon = Icons.Default.BarChart,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                SmallStatCard(
                    label = "Idle Pulse",
                    value = formatTime(idleTime),
                    icon = Icons.Default.Sync,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Feature specific times
            ExpressiveCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatRow(
                        icon = ImageVector.vectorResource(id = R.drawable.ic_nav_glyphs),
                        label = "Glyph Interface",
                        value = formatTime(glyphTime)
                    )
                    StatRow(
                        icon = Icons.Default.Vibration,
                        label = "Haptic Visualization",
                        value = formatTime(hapticTime)
                    )
                    StatRow(
                        icon = Icons.Default.FlashOn,
                        label = "Flashlight Visualization",
                        value = formatTime(flashlightTime)
                    )
                    
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                    )

                    Button(
                        onClick = { viewModel.showLeaderboard() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                    ) {
                        Icon(Icons.Default.EmojiEvents, null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("View Global Leaderboard", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }

        ExpressiveCard {
            BodyText(text = stringResource(R.string.about_intro), size = 15.sp)
        }

        SectionHeader(text = stringResource(R.string.about_section_why))
        ExpressiveCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BodyText(text = stringResource(R.string.about_why_1), size = 14.sp)
                BodyText(text = stringResource(R.string.about_why_2), size = 14.sp)
                BodyText(text = stringResource(R.string.about_why_3), size = 14.sp)
                BodyText(text = stringResource(R.string.about_why_4), size = 14.sp)
            }
        }

        SectionHeader(text = stringResource(R.string.credits))
        credits.forEach { credit ->
            ExpressiveCard(
                modifier = Modifier.let { m ->
                    if (credit.githubUsername != null) {
                        m.clickable { uriHandler.openUri("https://github.com/${credit.githubUsername}") }
                    } else m
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            credit.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (credit.role.isNotBlank()) {
                            Text(
                                credit.role,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                        if (credit.githubUsername != null) {
                            Text(
                                "@${credit.githubUsername}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (credit.githubUsername != null) {
                        Icon(
                            Icons.Default.ChevronRight,
                            null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(70.dp))
    }
}

@Composable
private fun StatCard(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color
) {
    ExpressiveCard(
        containerColor = color.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = color,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, modifier = Modifier.size(24.dp), tint = Color.White)
                }
            }
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = color,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                )
            }
        }
    }
}

@Composable
private fun SmallStatCard(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    ExpressiveCard(
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = color
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun StatRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

private fun formatTime(ms: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(ms)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

private data class CreditEntry(
    val name: String,
    val role: String,
    val githubUsername: String?,
)

@Composable
private fun InfoRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onClick()
            } else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        if (trailingContent != null) {
            trailingContent()
        } else if (onClick != null) {
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
        }
    }
}
