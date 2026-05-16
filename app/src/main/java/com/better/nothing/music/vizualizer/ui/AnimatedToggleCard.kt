package com.better.nothing.music.vizualizer.ui

import android.annotation.SuppressLint
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import kotlin.math.roundToInt

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
internal fun AnimatedToggleCard(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    titleStyle: TextStyle? = null,
    titleColor: Color? = null,
    shape: Shape = RoundedCornerShape(32.dp),
    colors: CardColors? = null,
    contentPadding: Dp = 24.dp,
    disabledTopSpacerFraction: Float = 0.4f,
    disabledTitleScaleFactor: Float = 1.2f,
    disabledSwitchScaleFactor: Float = 1.5f,
    disabledTitleSpacing: Dp = 50.dp,
    animationDurationMs: Int = 500,
) {
    // Tweak these defaults here when tuning the shared motion/scale behavior.
    val motionDurationMs = animationDurationMs
    val offTopSpacerFraction = disabledTopSpacerFraction
    val offTitleScale = disabledTitleScaleFactor
    val offSwitchScale = disabledSwitchScaleFactor
    val offTitleSpacing = disabledTitleSpacing
    val defaultTitleStyle = MaterialTheme.typography.headlineMedium.let { style ->
        style.copy(
            fontSize = style.fontSize * 0.94f,
            lineHeight = style.lineHeight * 0.94f
        )
    }
    val resolvedTitleStyle = titleStyle ?: defaultTitleStyle
    val resolvedTitleColor = titleColor ?: MaterialTheme.colorScheme.onBackground
    val resolvedColors = colors ?: CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)

    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(
            durationMillis = motionDurationMs,
            easing = FastOutSlowInEasing
        ),
        label = "toggle_card_progress"
    )
    val titleScale = offTitleScale - ((offTitleScale - 1f) * progress)
    val switchScale = offSwitchScale - ((offSwitchScale - 1f) * progress)
    val titleSpacing = lerp(offTitleSpacing, 0.dp, progress)
    val topSpacer = lerp(screenHeight * offTopSpacerFraction, 0.dp, progress)

    Spacer(modifier = Modifier.height(topSpacer))

    Card(
        shape = shape,
        colors = resolvedColors,
        modifier = modifier.fillMaxWidth(),
    ) {
        AnimatedToggleCardLayout(
            title = title,
            checked = checked,
            onCheckedChange = onCheckedChange,
            titleStyle = resolvedTitleStyle,
            titleColor = resolvedTitleColor,
            progress = progress,
            titleScale = titleScale,
            switchScale = switchScale,
            titleToSwitchSpacing = titleSpacing,
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding)
        )
    }
}

@Composable
private fun AnimatedToggleCardLayout(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    titleStyle: TextStyle,
    titleColor: Color,
    progress: Float,
    titleScale: Float,
    switchScale: Float,
    titleToSwitchSpacing: Dp,
    modifier: Modifier = Modifier,
) {
    val spacingPx = with(androidx.compose.ui.platform.LocalDensity.current) {
        titleToSwitchSpacing.roundToPx()
    }

    Layout(
        modifier = modifier,
        content = {
            Text(
                text = title,
                style = titleStyle,
                color = titleColor,
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
        val scaledSwitchWidth = (switchPlaceable.width * switchScale).roundToInt()
        val scaledSwitchHeight = (switchPlaceable.height * switchScale).roundToInt()
        val enabledHeight = maxOf(scaledTextHeight, scaledSwitchHeight)

        val disabledTextX = ((width - scaledTextWidth) / 2f).roundToInt()
        val disabledTextY = 0
        val enabledTextX = 0
        val enabledTextY = ((enabledHeight - scaledTextHeight) / 2f).roundToInt()

        val disabledSwitchX = ((width - scaledSwitchWidth) / 2f).roundToInt()
        val disabledSwitchY = scaledTextHeight + spacingPx
        val enabledSwitchX = width - scaledSwitchWidth
        val enabledSwitchY = ((enabledHeight - scaledSwitchHeight) / 2f).roundToInt()

        val textVisualX = lerpInt(disabledTextX, enabledTextX, progress)
        val textVisualY = lerpInt(disabledTextY, enabledTextY, progress)
        val switchX = lerpInt(disabledSwitchX, enabledSwitchX, progress)
        val switchY = lerpInt(disabledSwitchY, enabledSwitchY, progress)

        val textPlacementX = textVisualX + ((scaledTextWidth - textPlaceable.width) / 2f).roundToInt()
        val textPlacementY = textVisualY + ((scaledTextHeight - textPlaceable.height) / 2f).roundToInt()
        val switchPlacementX = switchX + ((scaledSwitchWidth - switchPlaceable.width) / 2f).roundToInt()
        val switchPlacementY = switchY + ((scaledSwitchHeight - switchPlaceable.height) / 2f).roundToInt()
        val layoutHeight = maxOf(
            textVisualY + scaledTextHeight,
            switchY + scaledSwitchHeight
        ).coerceAtLeast(enabledHeight)

        layout(width, layoutHeight) {
            textPlaceable.placeWithLayer(textPlacementX, textPlacementY) {
                scaleX = titleScale *.95f
                scaleY = titleScale *.95f
                transformOrigin = TransformOrigin.Center
            }
            switchPlaceable.placeWithLayer(switchPlacementX, switchPlacementY) {
                scaleX = switchScale
                scaleY = switchScale
                transformOrigin = TransformOrigin.Center
            }
        }
    }
}

private fun lerpInt(start: Int, end: Int, progress: Float): Int {
    return (start + (end - start) * progress).roundToInt()
}
