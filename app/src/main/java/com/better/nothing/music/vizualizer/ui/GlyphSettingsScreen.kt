package com.better.nothing.music.vizualizer.ui

import android.annotation.SuppressLint
import com.better.nothing.music.vizualizer.R
import com.better.nothing.music.vizualizer.service.AudioCaptureService
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.pow

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GlyphsScreen(
    gammaValue: Float,
    onGammaChanged: (Float) -> Unit,
    maxBrightness: Int,
    onMaxBrightnessChanged: (Int) -> Unit,
    presets: List<AudioCaptureService.PresetInfo>,
    selectedPreset: String,
    onPresetSelected: (String) -> Unit,
    isRunning: Boolean,
    selectedDevice: Int,
    viewModel: MainViewModel,
) {
    val mainScrollState = rememberScrollState()

    val selectedInfo = remember(selectedPreset, presets) {
        presets.firstOrNull { it.key == selectedPreset } ?: presets.firstOrNull()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(mainScrollState)
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Spacer(modifier = Modifier.height(50.dp))

        ScreenTitle(text = stringResource(R.string.glyph_controls))

        // Header with external toggle for glyph visualization
        val hapticsLocal = androidx.compose.ui.platform.LocalHapticFeedback.current
        val DEFAULT_BR = 4095
        val lastNonZero = remember { mutableIntStateOf(if (maxBrightness > 0) maxBrightness else DEFAULT_BR) }
        androidx.compose.runtime.LaunchedEffect(maxBrightness) {
            if (maxBrightness > 0) lastNonZero.intValue = maxBrightness
        }

        val glyphEnabled = maxBrightness > 0
        AnimatedToggleCard(
            title = "Glyph visualisation",
            checked = glyphEnabled,
            onCheckedChange = { switchEnabled ->
                hapticsLocal.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                if (switchEnabled) {
                    onMaxBrightnessChanged(lastNonZero.intValue)
                } else {
                    onMaxBrightnessChanged(0)
                }
            },
            disabledTopSpacerFraction = 0.4f,
            modifier = Modifier.fillMaxWidth(),
        )

        AnimatedVisibility(
            visible = glyphEnabled,
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
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                BrightnessCard(
                    maxBrightness = maxBrightness,
                    enabled = glyphEnabled,
                    lastNonZero = lastNonZero.intValue,
                    onLastNonZeroChanged = { v -> lastNonZero.intValue = v },
                    onMaxBrightnessChanged = onMaxBrightnessChanged
                )

                Text(
                    text = stringResource(R.string.gamma_control),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GammaPreviewCard(gammaValue = gammaValue)
                    BodyText(
                        text = stringResource(R.string.gamma_description),
                        modifier = Modifier.weight(1f),
                        size = 14.sp,
                        lineHeight = 22.sp,
                    )
                }

                GammaCard(gammaValue = gammaValue, onGammaChanged = onGammaChanged)

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.visualizer_presets),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    TextButton(
                        onClick = { viewModel.showCommunity() },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Public, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Community")
                    }
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val favorites by viewModel.favoritePresets.collectAsStateWithLifecycle()
                    
                    presets.forEach { preset ->
                        key(preset.key) {
                            val isFavorite = favorites.contains(preset.key)
                            NativeFilterChip(
                                label = preset.key,
                                selected = preset.key == selectedPreset,
                                onClick = { onPresetSelected(preset.key) },
                                onLongClick = { viewModel.toggleFavorite(preset.key) },
                                trailingIcon = {
                                    if (isFavorite) {
                                        Icon(
                                            Icons.Default.Star,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = if (preset.key == selectedPreset) Color.Black else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            )
                        }
                    }

                    NativeFilterChip(
                        label = "+ Create New",
                        selected = false,
                        onClick = { viewModel.showEditor() },
                    )
                }

                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        ),
                ) {
                    Crossfade(
                        targetState = selectedInfo?.description,
                        label = "desc_fade",
                        animationSpec = spring(stiffness = Spring.StiffnessMedium)
                    ) { description ->
                        Text(
                            text = description ?: stringResource(R.string.glyph_no_config),
                            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
                            color = Color(0xFFFFFFFF),
                            modifier = Modifier
                                .padding(20.dp)
                                .fillMaxWidth(),
                        )
                    }
                }

                if (isRunning) {
                    val vizState by viewModel.visualizerState.collectAsStateWithLifecycle()
                    GlyphPreview(
                        vizState = vizState,
                        device = selectedDevice,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(70.dp))
    }
}


@Composable
fun BrightnessCard(
    maxBrightness: Int,
    enabled: Boolean,
    lastNonZero: Int,
    onLastNonZeroChanged: (Int) -> Unit,
    onMaxBrightnessChanged: (Int) -> Unit,
) {
    androidx.compose.ui.platform.LocalHapticFeedback.current

    val MIN_BRIGHTNESS = 50
    val MAX_BRIGHTNESS = 4500

    // Quadratic mapping: slider position (0..1) -> value = min + (max-min) * pos^2
    fun linearToPos(linear: Int): Float {
        val clamped = linear.coerceIn(MIN_BRIGHTNESS, MAX_BRIGHTNESS)
        val ratio = (clamped - MIN_BRIGHTNESS).toFloat() / (MAX_BRIGHTNESS - MIN_BRIGHTNESS).toFloat()
        return kotlin.math.sqrt(ratio.coerceIn(0f, 1f))
    }

    fun posToLinear(pos: Float): Int {
        val p = pos.coerceIn(0f, 1f)
        val valf = MIN_BRIGHTNESS + (MAX_BRIGHTNESS - MIN_BRIGHTNESS) * (p * p)
        return kotlin.math.round(valf).toInt()
    }

    val posValue = remember(maxBrightness, lastNonZero) { linearToPos(if (maxBrightness > 0) maxBrightness else lastNonZero) }

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 0.dp),
            verticalArrangement = Arrangement.spacedBy(17.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Brightness:",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "${if (maxBrightness > 0) maxBrightness else lastNonZero}/${MAX_BRIGHTNESS}" + (if (maxBrightness == 4095) " (default)" else ""),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            ExpressiveSlider(
                value = posValue,
                onValueChange = { newPos ->
                    val newLinearValue = posToLinear(newPos)
                    onLastNonZeroChanged(newLinearValue)
                    if (enabled) onMaxBrightnessChanged(newLinearValue)
                },
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun GammaCard(
    gammaValue: Float,
    onGammaChanged: (Float) -> Unit,
) {
    Card(
        shape    = RoundedCornerShape(28.dp),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 0.dp),
            verticalArrangement = Arrangement.spacedBy(17.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.light_gamma),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = String.format("%.1f", gammaValue),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            ExpressiveSlider(
                value = gammaValue,
                onValueChange = onGammaChanged,
                valueRange = 0.4f..3.5f,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
fun GammaPreviewCard(gammaValue: Float) {
    val animatedGamma by animateFloatAsState(
        targetValue  = gammaValue,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessLow,
        ),
        label = "gamma_curve",
    )

    val curvePath = remember { Path() }

    val gridColor = MaterialTheme.colorScheme.outline
    val accent    = MaterialTheme.colorScheme.primary

    Card(
        shape    = RoundedCornerShape(28.dp),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.size(130.dp, 130.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(18.dp)) {
            val pad       = 8f
            val right  = size.width - pad
            val bottom = size.height - pad
            val w = right - pad
            val h = bottom - pad

            drawLine(gridColor, Offset(pad, bottom), Offset(right, bottom), strokeWidth = 4f, cap = StrokeCap.Round)
            drawLine(gridColor, Offset(pad, bottom), Offset(pad, pad),    strokeWidth = 4f, cap = StrokeCap.Round)

            val hStep = h / 4f
            val vStep = w / 4f
            repeat(3) { i ->
                drawLine(gridColor, Offset(pad,         bottom - hStep * (i + 1)), Offset(right, bottom - hStep * (i + 1)), strokeWidth = 1f)
                drawLine(gridColor, Offset(pad + vStep * (i + 1), bottom),         Offset(pad + vStep * (i + 1),
                    pad
                ),     strokeWidth = 1f)
            }

            curvePath.reset()
            curvePath.moveTo(pad, bottom)
            val steps = 50
            for (step in 1..steps) {
                val x = step / steps.toFloat()
                val y = x.pow(animatedGamma)
                curvePath.lineTo(pad + x * w, bottom - y * h)
            }
            drawPath(curvePath, accent, style = Stroke(width = 8f, cap = StrokeCap.Round))
        }
    }
}
