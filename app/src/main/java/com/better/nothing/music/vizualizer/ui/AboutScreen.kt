package com.better.nothing.music.vizualizer.ui

import com.better.nothing.music.vizualizer.R
import com.better.nothing.music.vizualizer.BuildConfig

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Sync
import androidx.compose.ui.graphics.Color

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun AboutScreen(
    viewModel: MainViewModel,
    onDismiss: (() -> Unit)? = null
) {
    val scrollState = rememberScrollState()
    val uriHandler = LocalUriHandler.current
    val configVersion by viewModel.configVersion.collectAsStateWithLifecycle()
    val appUpdateStatus by viewModel.appUpdateStatus.collectAsStateWithLifecycle()
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
            .padding(horizontal = 8.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (onDismiss == null) {
            Spacer(modifier = Modifier.height(50.dp))
        } else {
            Spacer(modifier = Modifier.height(16.dp))
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
        }
        ScreenTitle(text = stringResource(R.string.about_title))

        ExpressiveCard {
            CardHeader(title = "Application Info")
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    modifier = Modifier
                        .size(56.dp)
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
                        text = "Better Nothing Music Visualizer",
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

            Spacer(modifier = Modifier.height(16.dp))

            // Version details
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Configuration Version",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = configVersion,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Actions: GitHub and Updates
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { uriHandler.openUri("https://github.com/Aleks-Levet/better-nothing-music-visualizer") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(Icons.Default.Code, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("GitHub")
                }

                Button(
                    onClick = { viewModel.checkAppUpdate() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    enabled = appUpdateStatus !is MainViewModel.AppUpdateStatus.Checking
                ) {
                    if (appUpdateStatus is MainViewModel.AppUpdateStatus.Checking) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(Icons.Default.Sync, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Check for Updates")
                    }
                }
            }
            
            if (appUpdateStatus is MainViewModel.AppUpdateStatus.Available) {
                val status = appUpdateStatus as MainViewModel.AppUpdateStatus.Available
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { uriHandler.openUri(status.url) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Download ${status.version}")
                }
            }
        }

        ExpressiveCard(
            modifier = Modifier.clickable { viewModel.showTimeline() },
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Timeline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Project Timeline",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "View the app roadmap",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
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
                        Text(credit.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (credit.role.isNotBlank()) {
                            Text(credit.role, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        }
                        if (credit.githubUsername != null) {
                            Text("@${credit.githubUsername}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (credit.githubUsername != null) {
                        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(70.dp))
    }
}

private data class CreditEntry(
    val name: String,
    val role: String,
    val githubUsername: String?,
)
