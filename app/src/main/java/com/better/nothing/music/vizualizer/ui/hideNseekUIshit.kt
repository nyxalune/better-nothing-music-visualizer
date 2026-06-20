package com.better.nothing.music.vizualizer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun MainOverlays(
    viewModel: MainViewModel,
    selectedDevice: Int
) {
    val isShowingEditor by viewModel.isShowingEditor.collectAsStateWithLifecycle()
    val isShowingAbout by viewModel.isShowingAbout.collectAsStateWithLifecycle()
    val isShowingLicense by viewModel.isShowingLicense.collectAsStateWithLifecycle()
    val thanksMessage by viewModel.thanksMessage.collectAsStateWithLifecycle()

    if (isShowingEditor) {
        CustomPresetEditorScreen(
            selectedDevice = selectedDevice,
            onDismiss = { viewModel.hideEditor() },
            onSave = { name, zones, key -> viewModel.saveCustomPreset(name, zones, key) },
            onShare = { name, author, zones -> /* Handle share */ }
        )
    }

    if (isShowingAbout) {
        AboutScreen(onDismiss = { viewModel.hideAbout() }, viewModel = viewModel)
    }

    if (isShowingLicense) {
        LicenseScreen(onDismiss = { viewModel.hideLicense() })
    }

    if (thanksMessage != null) {
        ThanksModal(
            message = thanksMessage!!,
            onDismiss = { viewModel.dismissThanksMessage() }
        )
    }
}

@Composable
fun ThanksModal(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "BETTER NOTHING MUSIC VISUALIZER",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "WE APPRECIATE YOUR SUPPORT.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = message.uppercase(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "YOU'RE WELCOME!",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("PROCEED")
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    )
}
