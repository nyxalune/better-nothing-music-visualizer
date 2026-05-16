package com.better.nothing.music.vizualizer.ui

import com.better.nothing.music.vizualizer.R
import com.better.nothing.music.vizualizer.model.DeviceProfile
import com.better.nothing.music.vizualizer.service.AudioCaptureService
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.pow
import kotlin.math.roundToInt

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
    val context = LocalContext.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    val selectedInfo = remember(selectedPreset, presets) {
        presets.firstOrNull { it.key == selectedPreset } ?: presets.firstOrNull()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(mainScrollState)
            .padding(horizontal = 8.dp)
            .animateContentSize(
                spring(
                    dampingRatio = 1.15f,
                    stiffness = Spring.StiffnessVeryLow
                )
            ),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Spacer(modifier = Modifier.height(50.dp))

        ScreenTitle(text = stringResource(R.string.glyph_controls))

        if (selectedDevice == DeviceProfile.DEVICE_NP1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            if (!isGlyphDebugEnabled(context)) {
                GlyphDebugWarningCard(
                    developerModeEnabled = isDeveloperOptionsEnabled(context)
                )
            }
        }


        // Header with external toggle for glyph visualization
        val hapticsLocal = androidx.compose.ui.platform.LocalHapticFeedback.current
        val DEFAULT_BR = 4500
        val lastNonZero = remember { androidx.compose.runtime.mutableStateOf(if (maxBrightness > 0) maxBrightness else DEFAULT_BR) }
        androidx.compose.runtime.LaunchedEffect(maxBrightness) {
            if (maxBrightness > 0) lastNonZero.value = maxBrightness
        }

        val glyphEnabled = maxBrightness > 0
        val glyphHeaderMotion = spring<Float>(
            dampingRatio = 1.15f,
            stiffness = Spring.StiffnessVeryLow
        )
        val glyphHeaderDpMotion = spring<androidx.compose.ui.unit.Dp>(
            dampingRatio = 1.15f,
            stiffness = Spring.StiffnessVeryLow
        )
        val headerProgress by animateFloatAsState(
            targetValue = if (glyphEnabled) 1f else 0f,
            animationSpec = glyphHeaderMotion,
            label = "glyph_header_progress"
        )
        val titleScale = 1.2f - (0.2f * headerProgress)
        val disabledTitleSpacing by animateDpAsState(
            targetValue = if (glyphEnabled) 0.dp else 50.dp,
            animationSpec = glyphHeaderDpMotion,
            label = "glyph_disabled_title_spacing"
        )
        val disabledCardTopSpacer by animateDpAsState(
            targetValue = if (glyphEnabled) 0.dp else screenHeight * 0.4f,
            animationSpec = glyphHeaderDpMotion,
            label = "glyph_disabled_card_top_spacer"
        )

        Spacer(modifier = Modifier.height(disabledCardTopSpacer))

        // Single unified morphing component
        Card(
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    spring(
                        dampingRatio = 1.15f,
                        stiffness = Spring.StiffnessVeryLow
                    )
                ),
        ) {
            GlyphHeaderLayout(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .animateContentSize(
                        spring(
                            dampingRatio = 1.15f,
                            stiffness = Spring.StiffnessVeryLow
                        )
                    ),
                progress = headerProgress,
                titleScale = titleScale,
                titleToSwitchSpacing = disabledTitleSpacing,
                checked = glyphEnabled,
                onCheckedChange = { switchEnabled ->
                    hapticsLocal.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    if (switchEnabled) {
                        onMaxBrightnessChanged(lastNonZero.value)
                    } else {
                        onMaxBrightnessChanged(0)
                    }
                }
            )
        }

        if (glyphEnabled) {
            // Brightness card placed above gamma card
            BrightnessCard(
                maxBrightness = maxBrightness,
                enabled = glyphEnabled,
                lastNonZero = lastNonZero.value,
                onLastNonZeroChanged = { v -> lastNonZero.value = v },
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

            Text(
                text = stringResource(R.string.visualizer_presets),
                modifier = Modifier.padding(top = 20.dp),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                presets.forEach { preset ->
                    key(preset.key) {
                        NativeFilterChip(
                            label = preset.key,
                            selected = preset.key == selectedPreset,
                            onClick = { onPresetSelected(preset.key) },
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

        Spacer(modifier = Modifier.height(70.dp))

        Spacer(modifier = Modifier.height(70.dp))
    }
}

@Composable
private fun GlyphHeaderLayout(
    progress: Float,
    titleScale: Float,
    titleToSwitchSpacing: androidx.compose.ui.unit.Dp,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacingPx = with(androidx.compose.ui.platform.LocalDensity.current) {
        titleToSwitchSpacing.roundToPx()
    }

    Layout(
        modifier = modifier,
        content = {
            Text(
                text = "Glyph vizualisation",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    ) { measurables, constraints ->
        val textPlaceable = measurables[0].measure(
            constraints.copy(minWidth = 0, minHeight = 0)
        )
        val switchPlaceable = measurables[1].measure(
            constraints.copy(minWidth = 0, minHeight = 0)
        )

        val width = constraints.maxWidth
        val scaledTextWidth = (textPlaceable.width * titleScale).roundToInt()
        val scaledTextHeight = (textPlaceable.height * titleScale).roundToInt()

        val enabledHeight = maxOf(scaledTextHeight, switchPlaceable.height)
        val disabledHeight = scaledTextHeight + spacingPx + switchPlaceable.height
        val layoutHeight = lerpInt(disabledHeight, enabledHeight, progress)

        val disabledTextX = ((width - scaledTextWidth) / 2f).roundToInt()
        val disabledTextY = 0
        val enabledTextX = 0
        val enabledTextY = ((enabledHeight - scaledTextHeight) / 2f).roundToInt()

        val disabledSwitchX = ((width - switchPlaceable.width) / 2f).roundToInt()
        val disabledSwitchY = scaledTextHeight + spacingPx
        val enabledSwitchX = width - switchPlaceable.width
        val enabledSwitchY = ((enabledHeight - switchPlaceable.height) / 2f).roundToInt()

        val textVisualX = lerpInt(disabledTextX, enabledTextX, progress)
        val textVisualY = lerpInt(disabledTextY, enabledTextY, progress)
        val switchX = lerpInt(disabledSwitchX, enabledSwitchX, progress)
        val switchY = lerpInt(disabledSwitchY, enabledSwitchY, progress)

        val textPlacementX = textVisualX + ((scaledTextWidth - textPlaceable.width) / 2f).roundToInt()
        val textPlacementY = textVisualY + ((scaledTextHeight - textPlaceable.height) / 2f).roundToInt()

        layout(width, layoutHeight) {
            textPlaceable.placeWithLayer(textPlacementX, textPlacementY) {
                scaleX = titleScale
                scaleY = titleScale
                transformOrigin = TransformOrigin.Center
            }
            switchPlaceable.placeRelative(switchX, switchY)
        }
    }
}

private fun lerpInt(start: Int, end: Int, progress: Float): Int {
    return (start + (end - start) * progress).roundToInt()
}

@Composable
private fun GlyphDebugWarningCard(
    developerModeEnabled: Boolean,
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.glyph_debug_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            BodyText(
                text = stringResource(if (developerModeEnabled) R.string.glyph_debug_desc_adb_enabled else R.string.glyph_debug_desc_dev_options)
            )
            Text(
                text = stringResource(R.string.glyph_debug_command),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
            )
            if (developerModeEnabled) {
                BodyText(
                    text = stringResource(R.string.glyph_debug_instruction),
                    size = 14.sp,
                    lineHeight = 20.sp,
                )
            }
        }
        Spacer(modifier = Modifier.height(70.dp))
    }
}

private fun isDeveloperOptionsEnabled(context: Context): Boolean {
    return Settings.Global.getInt(
        context.contentResolver,
        Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
        0
    ) == 1
}

private fun isGlyphDebugEnabled(context: Context): Boolean {
    return Settings.Global.getInt(
        context.contentResolver,
        "nt_glyph_interface_debug_enable",
        0
    ) == 1
}

@Composable
fun BrightnessCard(
    maxBrightness: Int,
    enabled: Boolean,
    lastNonZero: Int,
    onLastNonZeroChanged: (Int) -> Unit,
    onMaxBrightnessChanged: (Int) -> Unit,
) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current

    val DEFAULT_BRIGHTNESS = 4500
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
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
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
                    text = "${if (maxBrightness > 0) maxBrightness else lastNonZero}/${MAX_BRIGHTNESS}" + (if (maxBrightness == DEFAULT_BRIGHTNESS) " (default)" else ""),
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
        }
    }
}

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
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
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
