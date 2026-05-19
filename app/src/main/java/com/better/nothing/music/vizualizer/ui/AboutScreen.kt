package com.better.nothing.music.vizualizer.ui

import com.better.nothing.music.vizualizer.R
import com.better.nothing.music.vizualizer.BuildConfig

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width

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
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        if (onDismiss == null) {
            Spacer(modifier = Modifier.height(50.dp))
        } else {
            Spacer(modifier = Modifier.height(16.dp))
            androidx.compose.material3.IconButton(onClick = onDismiss) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
        }
        ScreenTitle(text = stringResource(R.string.about_title))

        BodyText(
            text = stringResource(R.string.about_intro)
        )

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    uriHandler.openUri("https://github.com/Aleks-Levet/better-nothing-music-visualizer")
                },
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.github_repository),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.view_source),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFAAAAAA),
                )
            }
        }

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.version_info, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = stringResource(R.string.zones_config_version, configVersion),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                BodyText(
                    text = stringResource(R.string.media_projection_info),
                    size = 14.sp,
                    lineHeight = 20.sp,
                )

                Spacer(modifier = Modifier.height(8.dp))

                when (val status = appUpdateStatus) {
                    is MainViewModel.AppUpdateStatus.Idle -> {
                        Button(
                            onClick = { viewModel.checkAppUpdate() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(stringResource(R.string.check_for_app_updates))
                        }
                    }
                    is MainViewModel.AppUpdateStatus.Checking -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.checking_for_updates),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    is MainViewModel.AppUpdateStatus.Available -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = stringResource(R.string.update_available, status.version),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFFF4444),
                                fontWeight = FontWeight.Bold
                            )
                            Button(
                                onClick = { uriHandler.openUri(status.url) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(stringResource(R.string.update_now))
                            }
                        }
                    }
                    is MainViewModel.AppUpdateStatus.UpToDate -> {
                        Text(
                            text = stringResource(R.string.app_up_to_date),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                    }
                    is MainViewModel.AppUpdateStatus.Error -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = status.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Button(
                                onClick = { viewModel.checkAppUpdate() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(stringResource(R.string.check_for_app_updates))
                            }
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BodyText(text = stringResource(R.string.about_section_why), size = 20.sp)
            BodyText(text = stringResource(R.string.about_why_1))
            BodyText(text = stringResource(R.string.about_why_2))
            BodyText(text = stringResource(R.string.about_why_3))
            BodyText(text = stringResource(R.string.about_why_4))
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BodyText(text = stringResource(R.string.credits), size = 20.sp)
            credits.forEach { credit ->
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .let { base ->
                            if (credit.githubUsername != null) {
                                base.clickable {
                                    uriHandler.openUri("https://github.com/${credit.githubUsername}")
                                }
                            } else {
                                base
                            }
                        },
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = credit.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (credit.role.isNotBlank()) {
                            BodyText(
                                text = credit.role,
                                size = 14.sp,
                                lineHeight = 20.sp,
                            )
                        }
                        if (credit.githubUsername != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = "@${credit.githubUsername}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = stringResource(R.string.open_github_profile),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFFAAAAAA),
                                )
                            }
                        }
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
