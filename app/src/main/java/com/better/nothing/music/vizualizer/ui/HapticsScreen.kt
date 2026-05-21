package com.better.nothing.music.vizualizer.ui

import com.better.nothing.music.vizualizer.R
import com.better.nothing.music.vizualizer.model.HapticMode
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import kotlin.math.*

// Linear position (0..1) to Logarithmic Frequency (20..2000)
fun lerpLog(value: Float, min: Float, max: Float): Float {
    val logMin = ln(min)
    val logMax = ln(max)
    return exp(logMin + (logMax - logMin) * value)
}

// Logarithmic Frequency (20..2000) back to Linear position (0..1)
fun invLerpLog(freq: Float, min: Float, max: Float): Float {
    val logMin = ln(min)
    val logMax = ln(max)
    return (ln(freq) - logMin) / (logMax - logMin)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HapticsScreen(
    hapticMotorEnabled: Boolean,
    onHapticMotorEnabledChanged: (Boolean) -> Unit,
    hapticMode: HapticMode,
    onHapticModeChanged: (HapticMode) -> Unit,
    hapticFreqMin: Float,
    hapticFreqMax: Float,
    onHapticFreqRangeChanged: (Float, Float) -> Unit,
    hapticMultiplier: Float,
    onHapticMultiplierChanged: (Float) -> Unit,
    hapticGamma: Float,
    onHapticGammaChanged: (Float) -> Unit,
    richTapFrequency: Int,
    onRichTapFrequencyChanged: (Int) -> Unit,
    hapticAmplitude: Float,
    isBeatDetected: Boolean,
    flashlightEnabled: Boolean,
    onFlashlightEnabledChanged: (Boolean) -> Unit,
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
    flashlightAmplitude: Float,
) {
    val scrollState = rememberScrollState()
    val haptics = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Spacer(modifier = Modifier.height(50.dp))
        ScreenTitle(text = stringResource(R.string.haptics_header))

        AnimatedToggleCard(
            title = stringResource(R.string.haptics_motor_title),
            checked = hapticMotorEnabled,
            onCheckedChange = { enabled ->
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onHapticMotorEnabledChanged(enabled)
            },
            modifier = Modifier.fillMaxWidth(),
        )

        AnimatedVisibility(
            visible = hapticMotorEnabled,
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
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.haptics_mode_label),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        ExpressiveSegmentedButtonRow(
                            items = HapticMode.entries,
                            selectedItem = hapticMode,
                            onItemSelection = onHapticModeChanged,
                            labelProvider = { mode ->
                                stringResource(
                                    when (mode) {
                                        HapticMode.BASS_TO_AMPLITUDE -> R.string.haptics_mode_bass
                                        HapticMode.BEAT_DETECTION -> R.string.haptics_mode_beat
                                        HapticMode.RICHTAP_BASS -> R.string.haptics_mode_richtap
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.haptics_frequency_label, hapticFreqMin.toInt(), hapticFreqMax.toInt()),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        val currentRange = invLerpLog(hapticFreqMin, 20f, 1000f)..invLerpLog(hapticFreqMax, 20f, 1000f)

                        ExpressiveRangeSlider(
                            value = currentRange,
                            onValueChange = { newRange ->
                                val newMin = lerpLog(newRange.start, 20f, 1000f)
                                val newMax = lerpLog(newRange.endInclusive, 20f, 1000f)

                                if (newMax - newMin >= 10f) {
                                    onHapticFreqRangeChanged(newMin, newMax)
                                }
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )

                        BodyText(
                            text = stringResource(R.string.haptics_frequency_desc),
                            size = 12.sp
                        )
                    }
                }

                if (hapticMode == HapticMode.BASS_TO_AMPLITUDE || hapticMode == HapticMode.RICHTAP_BASS || hapticMode == HapticMode.BEAT_DETECTION) {
                    Card(
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.haptics_amplitude_label, hapticMultiplier),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            ExpressiveSlider(
                                value = hapticMultiplier,
                                onValueChange = onHapticMultiplierChanged,
                                valueRange = 0.5f..1.5f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    if (hapticMode == HapticMode.BASS_TO_AMPLITUDE || hapticMode == HapticMode.BEAT_DETECTION) {
                        Card(
                            shape = RoundedCornerShape(28.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val label = if (hapticMode == HapticMode.BEAT_DETECTION) stringResource(R.string.haptics_speed_label, hapticGamma) else stringResource(R.string.haptics_gamma_label, hapticGamma)
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                val currentRange = if (hapticMode == HapticMode.BEAT_DETECTION) 4f..10f else 1f..4.0f
                                ExpressiveSlider(
                                    value = hapticGamma.coerceIn(currentRange),
                                    onValueChange = onHapticGammaChanged,
                                    valueRange = currentRange,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    } else if (hapticMode == HapticMode.RICHTAP_BASS) {
                        Card(
                            shape = RoundedCornerShape(28.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.haptics_richtap_freq_label, richTapFrequency),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                ExpressiveSlider(
                                    value = richTapFrequency.toFloat(),
                                    onValueChange = { onRichTapFrequencyChanged(it.toInt()) },
                                    valueRange = 0f..100f,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                if (hapticMode == HapticMode.BEAT_DETECTION) {
                    BodyText(
                        text = stringResource(R.string.haptics_beat_detection_desc),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }

                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Haptic Monitor",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        val animatedAmplitude by animateFloatAsState(
                            targetValue = hapticAmplitude * 4,
                            label = "hapticAmplitude"
                        )
                        val flashColor by animateColorAsState(
                            targetValue = if (isBeatDetected) Color.White else MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            label = "flashColor"
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.size(100.dp)) {
                                val baseRadius = size.minDimension * 0.15f
                                val dynamicRadius = (size.minDimension * 0.45f) * animatedAmplitude

                                drawCircle(
                                    color = flashColor,
                                    radius = (baseRadius + dynamicRadius).coerceAtMost(size.minDimension / 2)
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- Flashlight Visualizer Section ---
        AnimatedToggleCard(
            title = "Flashlight Visualizer",
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
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Frequency Range: ${flashlightFreqMin.toInt()} - ${flashlightFreqMax.toInt()} Hz",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
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
                            text = "The Hz range that the flashlight will react to. Lower for bass, higher for snare/vocals.",
                            size = 12.sp
                        )
                    }
                }

                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Sensitivity Threshold: ${String.format("%.2f", flashlightThreshold)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        ExpressiveSlider(
                            value = flashlightThreshold,
                            onValueChange = onFlashlightThresholdChanged,
                            valueRange = 0f..0.8f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        BodyText(text = "Higher threshold filters out background noise/drums.", size = 11.sp)
                    }
                }

                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Smoothing: ${String.format("%.2f", flashlightSmoothing)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        ExpressiveSlider(
                            value = flashlightSmoothing,
                            onValueChange = onFlashlightSmoothingChanged,
                            valueRange = 0f..0.95f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        BodyText(text = "Higher smoothing makes the light pulse softly instead of strobing.", size = 11.sp)
                    }
                }

                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Intensity Gamma: ${String.format("%.1f", flashlightGamma)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        ExpressiveSlider(
                            value = flashlightGamma,
                            onValueChange = onFlashlightGammaChanged,
                            valueRange = 1.0f..4.0f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Amplitude Multiplier: $flashlightMultiplier",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        ExpressiveSlider(
                            value = flashlightMultiplier,
                            onValueChange = onFlashlightMultiplierChanged,
                            valueRange = 0.5f..1.5f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Flashlight Monitor",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        val animatedAmplitude by animateFloatAsState(
                            targetValue = flashlightAmplitude * 4,
                            label = "flashlightAmplitude"
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.size(100.dp)) {
                                val baseRadius = size.minDimension * 0.15f
                                val dynamicRadius = (size.minDimension * 0.45f) * animatedAmplitude

                                drawCircle(
                                    color = Color.Yellow.copy(alpha = 0.8f),
                                    radius = (baseRadius + dynamicRadius).coerceAtMost(size.minDimension / 2)
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
