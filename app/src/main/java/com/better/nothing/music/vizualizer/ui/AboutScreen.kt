package com.better.nothing.music.vizualizer.ui

import com.better.nothing.music.vizualizer.R
import com.better.nothing.music.vizualizer.BuildConfig

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
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

        ExpressiveCard(
            modifier = Modifier.clickable { viewModel.showTimeline() },
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Timeline, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Project Timeline", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("View the app roadmap", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
            }
        }

        ExpressiveCard {
            BodyText(text = stringResource(R.string.about_intro), size = 15.sp)
        }

        ExpressiveCard(
            modifier = Modifier.clickable { uriHandler.openUri("https://github.com/Aleks-Levet/better-nothing-music-visualizer") }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Code, null, tint = MaterialTheme.colorScheme.secondary)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.github_repository), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Support development on GitHub", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
            }
        }

        ExpressiveCard {
            CardHeader(title = "App Status")
            
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.version_info, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.zones_config_version, configVersion),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            BodyText(
                text = stringResource(R.string.media_projection_info),
                size = 13.sp,
                lineHeight = 18.sp,
            )

            Spacer(modifier = Modifier.height(16.dp))

            when (val status = appUpdateStatus) {
                is MainViewModel.AppUpdateStatus.Idle, is MainViewModel.AppUpdateStatus.Error -> {
                    Button(
                        onClick = { viewModel.checkAppUpdate() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.check_for_app_updates))
                    }
                    if (status is MainViewModel.AppUpdateStatus.Error) {
                        Text(status.message, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }
                is MainViewModel.AppUpdateStatus.Checking -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.checking_for_updates), style = MaterialTheme.typography.labelLarge)
                    }
                }
                is MainViewModel.AppUpdateStatus.Available -> {
                    Button(
                        onClick = { uriHandler.openUri(status.url) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.update_now) + " (${status.version})")
                    }
                }
                is MainViewModel.AppUpdateStatus.UpToDate -> {
                    Surface(
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.app_up_to_date),
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
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
