package com.better.nothing.music.vizualizer.ui

import com.better.nothing.music.vizualizer.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.better.nothing.music.vizualizer.model.TorchMode

@Composable
fun FlashlightScreen(
    flashlightEnabled: Boolean,
    onFlashlightEnabledChanged: (Boolean) -> Unit,
    flashlightMode: TorchMode,
    onFlashlightModeChanged: (TorchMode) -> Unit,
    flashlightFreqMin: Float,
    flashlightFreqMax: Float,
    onFlashlightFreqRangeChanged: (Float, Float) -> Unit,
    flashlightMultiplier: Float,
    onFlashlightMultiplierChanged: (Float) -> Unit,
    flashlightThreshold: Float,
    onFlashlightThresholdChanged: (Float) -> Unit,
    flashlightSmoothing: Float,
    onFlashlightSmoothingChanged: (Float) -> Unit,
    flashlightGamma: Float,
    onFlashlightGammaChanged: (Float) -> Unit,
    flashlightBeatSensitivity: Float,
    onFlashlightBeatSensitivityChanged: (Float) -> Unit,
    flashlightAmplitudeProvider: () -> Float,
    isBeatDetectedProvider: () -> Boolean,
) {
    val scrollState = rememberScrollState()
    val haptics = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ... (title and toggle) ...
        Spacer(modifier = Modifier.height(50.dp))
        ScreenTitle(text = stringResource(R.string.flashlight_header))
        
        AnimatedToggleCard(
            title = stringResource(R.string.flashlight_sync_title),
            checked = flashlightEnabled,
            onCheckedChange = { enabled ->
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onFlashlightEnabledChanged(enabled)
            },
            modifier = Modifier.fillMaxWidth(),
        )

        AnimatedVisibility(
            visible = flashlightEnabled,
            enter = fadeIn(animationSpec = tween(durationMillis = 320)) +
                slideInVertically(
                    animationSpec = tween(durationMillis = 420),
                    initialOffsetY = { fullHeight -> fullHeight / 3 }
                ),
            exit = fadeOut(animationSpec = tween(durationMillis = 220)) +
                slideOutVertically(
                    animationSpec = tween(durationMillis = 280),
                    targetOffsetY = { fullHeight -> fullHeight / 5 }
                )
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ... (sliders) ...
                ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
                    CardHeader(
                        title = stringResource(
                            R.string.flashlight_frequency_label,
                            flashlightFreqMin.toInt(),
                            flashlightFreqMax.toInt()
                        )
                    )

                    val currentRange = invLerpLog(flashlightFreqMin, 20f, 1000f)..invLerpLog(flashlightFreqMax, 20f, 1000f)

                    ExpressiveRangeSlider(
                        value = currentRange,
                        onValueChange = { newRange ->
                            val newMin = lerpLog(newRange.start, 20f, 1000f)
                            val newMax = lerpLog(newRange.endInclusive, 20f, 1000f)

                            if (newMax - newMin >= 10f) {
                                onFlashlightFreqRangeChanged(newMin, newMax)
                            }
                        },
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth()
                    )

                    BodyText(
                        text = stringResource(R.string.flashlight_frequency_desc),
                        size = 12.sp
                    )
                }

                ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
                    CardHeader(title = stringResource(R.string.flashlight_multiplier_label, flashlightMultiplier))
                    ExpressiveSlider(
                        value = flashlightMultiplier,
                        onValueChange = onFlashlightMultiplierChanged,
                        valueRange = 0.5f..1.5f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
                    CardHeader(title = stringResource(R.string.flashlight_mode_label))
                    ExpressiveSegmentedButtonRow(
                        items = TorchMode.entries,
                        selectedItem = flashlightMode,
                        onItemSelection = onFlashlightModeChanged,
                        labelProvider = { mode ->
                            stringResource(
                                when (mode) {
                                    TorchMode.AMPLITUDE -> R.string.flashlight_mode_amplitude
                                    TorchMode.BEAT_DETECTION -> R.string.flashlight_mode_beat
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (flashlightMode == TorchMode.AMPLITUDE) {
                    ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
                        CardHeader(title = stringResource(R.string.flashlight_sensitivity_label, flashlightThreshold))
                        ExpressiveSlider(
                            value = flashlightThreshold,
                            onValueChange = onFlashlightThresholdChanged,
                            valueRange = 0f..0.8f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        BodyText(text = stringResource(R.string.flashlight_sensitivity_desc), size = 11.sp)
                    }

                    ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
                        CardHeader(title = stringResource(R.string.flashlight_smoothing_label, flashlightSmoothing))
                        ExpressiveSlider(
                            value = flashlightSmoothing,
                            onValueChange = onFlashlightSmoothingChanged,
                            valueRange = 0f..0.95f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        BodyText(text = stringResource(R.string.flashlight_smoothing_desc), size = 11.sp)
                    }

                    ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
                        CardHeader(title = stringResource(R.string.flashlight_gamma_label, flashlightGamma))
                        ExpressiveSlider(
                            value = flashlightGamma,
                            onValueChange = onFlashlightGammaChanged,
                            valueRange = 1.0f..4.0f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (flashlightMode == TorchMode.BEAT_DETECTION) {
                    ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
                        CardHeader(title = stringResource(R.string.flashlight_beat_sensitivity_label, flashlightBeatSensitivity))
                        ExpressiveSlider(
                            value = flashlightBeatSensitivity,
                            onValueChange = onFlashlightBeatSensitivityChanged,
                            valueRange = 0.3f..6.0f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                ExpressiveCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                ) {
                    CardHeader(title = stringResource(R.string.flashlight_monitor_label))

                    val flashlightAmplitude = flashlightAmplitudeProvider()
                    val isBeatDetected = isBeatDetectedProvider()
                    val isHigh = if (flashlightMode == TorchMode.AMPLITUDE) {
                        flashlightAmplitude > (flashlightThreshold + 0.05f)
                    } else {
                        isBeatDetected
                    }

                    val flashColor by animateColorAsState(
                        targetValue = if (isHigh) Color.Yellow else Color.Yellow.copy(alpha = 0.3f),
                        animationSpec = if (isHigh) snap() else spring(stiffness = Spring.StiffnessVeryLow),
                        label = "flashColor"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .background(
                                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                                    colors = listOf(flashColor.copy(alpha = 0.08f * flashlightAmplitude), Color.Transparent),
                                    radius = 350f
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        MorphingPolygon(
                            isBeatDetected = isHigh,
                            amplitude = flashlightAmplitude,
                            color = flashColor,
                            modifier = Modifier.size(110.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(70.dp))
    }
}
