package com.better.nothing.music.vizualizer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.better.nothing.music.vizualizer.ui.SecondaryScreens.AboutScreen
import com.better.nothing.music.vizualizer.ui.SecondaryScreens.CustomPresetEditorScreen
import com.better.nothing.music.vizualizer.ui.SecondaryScreens.LicenseScreen
import com.better.nothing.music.vizualizer.ui.SecondaryScreens.StatsScreen

@Composable
internal fun MainOverlays(
    viewModel: MainViewModel,
    selectedDevice: Int
) {
    val isShowingEditor by viewModel.isShowingEditor.collectAsStateWithLifecycle()
    val isShowingAbout by viewModel.isShowingAbout.collectAsStateWithLifecycle()
    val isShowingLicense by viewModel.isShowingLicense.collectAsStateWithLifecycle()
    val isShowingStats by viewModel.isShowingStats.collectAsStateWithLifecycle()

    if (isShowingEditor) {
        val fftState by viewModel.fftState.collectAsStateWithLifecycle()
        CustomPresetEditorScreen(
            selectedDevice = selectedDevice,
            fftState = fftState,
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

    if (isShowingStats) {
        StatsScreen(viewModel = viewModel, onDismiss = { viewModel.hideStats() })
    }
}
