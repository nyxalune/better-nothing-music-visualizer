@file:OptIn(ExperimentalMaterial3Api::class)

package com.better.nothing.music.vizualizer.ui

import android.view.MotionEvent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Shapes
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import com.better.nothing.music.vizualizer.R
import kotlin.math.exp
import kotlin.math.ln
import android.graphics.Path as AndroidPath

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

@Composable
fun MorphingPolygon(
    isBeatDetected: Boolean,
    amplitude: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "polygonRotation")
    val baseRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "baseRotation"
    )

    val polygonBase = remember {
        RoundedPolygon.star(
            numVerticesPerRadius = 12,
            innerRadius = 0.85f,
            rounding = CornerRounding(0.2f)
        )
    }

    var sourcePoly by remember { mutableStateOf(polygonBase) }
    var targetPoly by remember { mutableStateOf(polygonBase) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(isBeatDetected) {
        if (isBeatDetected) {
            sourcePoly = targetPoly
            targetPoly = RoundedPolygon.star(
                numVerticesPerRadius = (3..24).random(),
                innerRadius = (25..85).random() / 100f,
                rounding = CornerRounding((4..20).random() / 100f)
            )
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    // Smooth amplitude to avoid jitter, but kept responsive
    val animatedAmplitude by animateFloatAsState(
        targetValue = (amplitude * 5f).coerceAtMost(1.2f),
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "animatedAmplitude"
    )

    val morph = remember(sourcePoly, targetPoly) {
        Morph(sourcePoly, targetPoly)
    }

    val path = remember { AndroidPath() }

    Canvas(modifier = modifier) {
        val size = size.minDimension
        // Base scale 0.15 + up to 0.85 from amplitude
        val scale = size * (0.15f + (animatedAmplitude * 0.7f))
        
        path.reset()
        val matrix = androidx.compose.ui.graphics.Matrix()
        matrix.scale(scale, scale)
        matrix.translate(size / (2 * scale), size / (2 * scale))
        
        morph.toPath(progress.value, path)
        val composePath = path.asComposePath()
        composePath.transform(matrix)

        rotate(baseRotation) {
            // Draw subtle bloom/shadow
            if (animatedAmplitude > 0.1f) {
                drawPath(
                    path = composePath,
                    color = color.copy(alpha = 0.2f * animatedAmplitude),
                    style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            drawPath(
                path = composePath,
                color = color,
                style = Fill
            )
        }
    }
}

@Composable
fun ExpressiveSplitButton(
    primaryText: String,
    primaryIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onPrimaryClick: () -> Unit,
    secondaryText: String,
    secondaryIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onSecondaryClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Primary Action
        Surface(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                onPrimaryClick()
            },
            enabled = enabled,
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.weight(1.5f).fillMaxHeight()
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(primaryIcon, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(primaryText, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }

        // Secondary Action
        Surface(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onSecondaryClick()
            },
            enabled = enabled,
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).fillMaxHeight()
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(secondaryIcon, null, modifier = Modifier.size(20.dp))
                if (secondaryText.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(secondaryText, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
fun ScreenTitle(text: String) {
    val uiAmp = LocalUIAmplitude.current
    val shift = (uiAmp * 12).dp
    
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Text(
            text  = text,
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
            letterSpacing = (-1).sp,
            fontWeight = FontWeight.Bold
        )
        // Add a subtle accent line or dot pattern that reacts to UI Amplitude
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .width(44.dp + shift)
                .height(5.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.5.dp))
        )
    }
}

@Composable
fun ExpressiveCard(
    modifier: Modifier = Modifier,
    shape: CornerBasedShape = MaterialTheme.shapes.large,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
    content: @Composable ColumnScope.() -> Unit
) {
    val m3eEnabled = LocalM3EEnabled.current
    Card(
        modifier = modifier
            .padding(vertical = LocalAppSpacing.current.between / 2),
        shape = if (m3eEnabled) shape else RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = border,
        elevation = CardDefaults.cardElevation(defaultElevation = if (m3eEnabled) 2.dp else 0.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(if (m3eEnabled) LocalAppSpacing.current.inner else 12.dp)
                .animateContentSize(
                    animationSpec = if (m3eEnabled) {
                        spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    } else {
                        spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    }
                ),
            content = content
        )
    }
}

@Composable
fun ColumnScope.CardHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailingContent: @Composable (RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        if (trailingContent != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                trailingContent()
            }
        }
    }
    Spacer(modifier = Modifier.height(LocalAppSpacing.current.between))
}

@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun BodyText(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 16.sp,
    lineHeight: TextUnit = 24.sp,
) {
    Text(
        text  = text,
        // Hoist TextStyle out of every recomposition; only reallocated when
        // size or lineHeight actually changes.
        style = remember(size, lineHeight) {
            TextStyle(
                fontSize   = size,
                lineHeight = lineHeight,
                fontWeight = FontWeight.Normal,
            )
        },
        color    = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
        modifier = modifier,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NativeFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        animationSpec = tween(300),
        label = "chip_bg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(300),
        label = "chip_content"
    )
    val uiAmp = LocalUIAmplitude.current
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.05f + (uiAmp * 0.05f) else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
        label = "chip_scale"
    )

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        contentColor = contentColor,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = modifier
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .combinedClickable(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onClick()
                },
                onLongClick = onLongClick?.let {
                    {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        it()
                    }
                }
            )
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
            trailingIcon?.invoke()
        }
    }
}

@Composable
fun StartStopButton(
    running: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed         by interactionSource.collectIsPressedAsState()
    val haptics           = LocalHapticFeedback.current

    val scale by animateFloatAsState(
        targetValue   = if (isPressed) 0.92f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessLow
        ),
        label = "buttonScale"
    )

    val containerColor by animateColorAsState(
        targetValue   = if (running) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        animationSpec = tween(600, easing = EaseInOutCubic),
        label         = "containerColor"
    )

    val contentColor by animateColorAsState(
        targetValue   = if (running) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
        animationSpec = tween(600, easing = EaseInOutCubic),
        label         = "contentColor"
    )

    val glowAlpha by animateFloatAsState(
        targetValue = if (running) 0.4f else 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Box(
        modifier = modifier
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        // Pulse Glow
        if (running) {
            Box(
                modifier = Modifier
                    .size(width = 170.dp, height = 64.dp)
                    .background(
                        color = MaterialTheme.colorScheme.error.copy(alpha = glowAlpha),
                        shape = RoundedCornerShape(22.dp)
                    )
            )
        }
        FloatingActionButton(
            onClick           = {
                haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                onClick()
            },
            interactionSource = interactionSource,
            shape             = RoundedCornerShape(18.dp),
            modifier          = Modifier
                .height(56.dp)
                .widthIn(min = 160.dp),
            containerColor = containerColor,
            contentColor   = contentColor,
            elevation = FloatingActionButtonDefaults.bottomAppBarFabElevation(0.dp)
        ) {
            Row(
                modifier             = Modifier.padding(horizontal = 24.dp),
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                AnimatedContent(
                    targetState  = running,
                    transitionSpec = { (scaleIn() + fadeIn()).togetherWith(scaleOut() + fadeOut()) },
                    label        = "iconTransition"
                ) { isRunning ->
                    Icon(
                        imageVector     = if (isRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier        = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text  = stringResource(if (running) R.string.stop_visualizer else R.string.start_visualizer).uppercase(),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight    = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                )
            }
        }
    }
}

@Composable
fun NativeBottomBar(
    selectedTab: Tab,
    visibleTabs: List<Tab>,
    onTabSelected: (Tab) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        NavigationBar(
            modifier = Modifier
                .height(80.dp)
                .padding(horizontal = 8.dp),
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            windowInsets = NavigationBarDefaults.windowInsets
        ) {
            visibleTabs.forEach { tab ->
                val isSelected = tab == selectedTab
                val uiAmp = LocalUIAmplitude.current
                val iconScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.25f + (uiAmp * 0.15f) else 1.0f,
                    animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
                    label = "nav_icon_scale"
                )

                NavigationBarItem(
                    selected = isSelected,
                    onClick = {
                        if (!isSelected) {
                            onTabSelected(tab)
                        }
                    },
                    label = {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    icon = {
                        Box(contentAlignment = Alignment.Center) {
                            val iconModifier = Modifier
                                .size(24.dp)
                                .graphicsLayer(scaleX = iconScale, scaleY = iconScale)

                            when (tab) {
                                Tab.Audio -> Icon(Icons.AutoMirrored.Filled.VolumeUp, tab.label, modifier = iconModifier)
                                Tab.Glyphs -> Icon(painter = painterResource(R.drawable.ic_nav_glyphs), contentDescription = tab.label, modifier = iconModifier)
                                Tab.Haptics -> Icon(Icons.Filled.Vibration, tab.label, modifier = iconModifier)
                                Tab.Flashlight -> Icon(Icons.Filled.FlashlightOn, tab.label, modifier = iconModifier)
                                Tab.Settings -> Icon(Icons.Filled.Settings, tab.label, modifier = iconModifier)
                            }
                        }
                    },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ExpressiveSegmentedButtonRow(
    items: List<T>,
    selectedItem: T,
    onItemSelection: (T) -> Unit,
    labelProvider: @Composable (T) -> String,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    SingleChoiceSegmentedButtonRow(
        modifier = modifier
    ) {
        items.forEachIndexed { index, item ->
            SegmentedButton(
                selected = item == selectedItem,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onItemSelection(item)
                },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = items.size),
                label = {
                    Text(
                        text = labelProvider(item),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressiveSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current

    val isPressed by interactionSource.collectIsPressedAsState()
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isActive = isPressed || isDragged

    val wasActive = remember { mutableStateOf(false) }

    // Trigger haptic on Press/Release (skip initial state)
    LaunchedEffect(isActive) {
        if (!wasActive.value && isActive) {
            // Trigger on press (transition from false to true)
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        } else if (wasActive.value && !isActive) {
            // Trigger on release (transition from true to false)
            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
        wasActive.value = isActive
    }

    // The "Expressive" factor (1.0 to 1.8)
    val animationFactor by animateFloatAsState(
        targetValue = if (isActive && LocalM3EEnabled.current) 2.1f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "expressive_bounce"
    )

    val view = LocalView.current
    Slider(
        value = value,
        onValueChange = { newValue ->
            onValueChange(newValue)
        },
        valueRange = valueRange,
        steps = steps,
        interactionSource = interactionSource,
        modifier = modifier
            .height(56.dp)
            .pointerInput(isActive) {
                if (isActive) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            event.changes.forEach { if (it.pressed) it.consume() }
                        }
                    }
                }
            }
            .pointerInteropFilter { motionEvent ->
                if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                }
                false
            },
        thumb = {
            // THUMB: Gets THINNER as animationFactor increases
            // Width: 4dp -> 2dp | Height: 44dp -> 48dp
            val thumbWidth = 4.dp / animationFactor

            Box(
                modifier = Modifier
                    .size(width = thumbWidth, height = 44.dp * (animationFactor * 0.8f).coerceAtLeast(1f))
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(2.dp) // Keeps same corner radius
                    )
            )
        },
        track = { sliderState ->
            // TRACK: Gets THICKER
            // Radius: We want it to look like a pill when thin, but less rounded when thick
            val trackHeight = 16.dp * animationFactor

            SliderDefaults.Track(
                sliderState = sliderState,
                modifier = Modifier
                    .height(trackHeight),
                thumbTrackGapSize = 4.dp,
                trackInsideCornerSize = 2.dp,
                colors = SliderDefaults.colors(
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    )
}

@Composable
fun ExpressiveRangeSlider(
    value: ClosedFloatingPointRange<Float>,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier
) {
    val startInteractionSource = remember { MutableInteractionSource() }
    val endInteractionSource = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current

    val startActive by startInteractionSource.collectIsPressedAsState()
    val startDragged by startInteractionSource.collectIsDraggedAsState()
    val endActive by endInteractionSource.collectIsPressedAsState()
    val endDragged by endInteractionSource.collectIsDraggedAsState()

    val isAnyActive = startActive || startDragged || endActive || endDragged
    val wasActive = remember { mutableStateOf(false) }

    // Trigger haptic on Press/Release (skip initial state)
    LaunchedEffect(isAnyActive) {
        if (!wasActive.value && isAnyActive) {
            // Trigger on press (transition from false to true)
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        } else if (wasActive.value && !isAnyActive) {
            // Trigger on release (transition from true to false)
            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
        wasActive.value = isAnyActive
    }

    // Animation and Haptic logic remains the same...
    val animationFactor by animateFloatAsState(
        targetValue = if (isAnyActive && LocalM3EEnabled.current) 2.1f else 1.0f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
        label = "track_bloom"
    )

    val startThumbFactor by animateFloatAsState(if ((startActive || startDragged) && LocalM3EEnabled.current) 2.1f else 1.0f)
    val endThumbFactor by animateFloatAsState(if ((endActive || endDragged) && LocalM3EEnabled.current) 2.1f else 1.0f)

    val view = LocalView.current
    RangeSlider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        startInteractionSource = startInteractionSource,
        endInteractionSource = endInteractionSource,
        modifier = modifier
            .height(64.dp)
            .pointerInput(isAnyActive) {
                if (isAnyActive) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            event.changes.forEach { if (it.pressed) it.consume() }
                        }
                    }
                }
            }
            .pointerInteropFilter { motionEvent ->
                if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                }
                false
            },
        startThumb = { ExpressiveThumb(factor = startThumbFactor) },
        endThumb = { ExpressiveThumb(factor = endThumbFactor) },
        track = { rangeSliderState ->
            val trackHeight = 12.dp * animationFactor
            SliderDefaults.Track(
                rangeSliderState = rangeSliderState,
                modifier = Modifier.height(trackHeight),
                thumbTrackGapSize = 4.dp,
                drawStopIndicator = null,
                colors = SliderDefaults.colors(
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    )
}

@Composable
private fun ExpressiveThumb(factor: Float) {
    // The thumb gets thinner and taller when grabbed
    val thumbWidth = 4.dp / factor
    val thumbHeight = 40.dp * (factor * 0.8f).coerceAtLeast(1f)

    Box(
        modifier = Modifier
            .size(width = thumbWidth, height = thumbHeight)
            .background(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(2.dp)
            )
    )
}

val NTypeFontFamily = FontFamily(
    Font(R.font.ntype82)
)

val NDotFontFamily = FontFamily(
    Font(resId = R.font.ndot57, weight = FontWeight.Normal)
)

val NDot55FontFamily = FontFamily(
    Font(resId = R.font.ndot55, weight = FontWeight.Normal)
)

@Immutable
data class AppSpacing(
    val edge: Dp = 6.dp,       // Global screen side padding
    val between: Dp = 12.dp,    // Vertical space between cards
    val inner: Dp = 20.dp,      // Padding inside cards (Expressive style)
    val buttonGap: Dp = 4.dp    // Gap between connected buttons
)

val LocalAppSpacing = staticCompositionLocalOf { AppSpacing() }
val LocalM3EEnabled = staticCompositionLocalOf { true }
val LocalUIAmplitude = staticCompositionLocalOf { 0f }

@Composable
fun BetterVizTheme(
    themeName: String = "Default",
    fontName: String = "NDot",
    m3eEnabled: Boolean = true,
    uiAmplitude: Float = 0f,
    content: @Composable () -> Unit
) {
    val useNType = fontName == "NType"
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()

    val targetColorScheme = when (themeName) {
        "Material You" -> {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        "Nothing" -> {
            if (isDark) {
                // Nothing Red (Branded Dark)
                androidx.compose.material3.darkColorScheme(
                    background = Color.Black,
                    surface = Color(0xFF0D0D0D),
                    primary = Color(0xFFD71921),    // Authentic Nothing Red
                    secondary = Color(0xFFD71921),
                    error = Color(0xFFD71921),
                    onBackground = Color.White,
                    onSurface = Color.White,
                    onPrimary = Color.White,
                    onSecondary = Color.White,
                    onError = Color.White,
                    surfaceVariant = Color(0xFF1A1A1A),
                    onSurfaceVariant = Color(0xFFB3B3B3),
                    outline = Color(0xFF333333)
                )
            } else {
                // Nothing Light (Branded Light)
                androidx.compose.material3.lightColorScheme(
                    background = Color.White,
                    surface = Color(0xFFF5F5F5),
                    primary = Color(0xFF000000),
                    secondary = Color(0xFF626262),
                    error = Color(0xFFD71921),
                    onBackground = Color.Black,
                    onSurface = Color.Black,
                    onPrimary = Color.White,
                    onSecondary = Color.White,
                    onError = Color.White,
                    surfaceVariant = Color(0xFFE0E0E0),
                    onSurfaceVariant = Color(0xFF757575),
                    outline = Color(0xFFBDBDBD)
                )
            }
        }
        "Nothing Red" -> {
            // Fallback for old selection, same as Nothing Dark
            androidx.compose.material3.darkColorScheme(
                background = Color.Black,
                surface = Color(0xFF0D0D0D),
                primary = Color(0xFFD71921),
                secondary = Color(0xFFD71921),
                error = Color(0xFFD71921),
                onBackground = Color.White,
                onSurface = Color.White,
                onPrimary = Color.White,
                onSecondary = Color.White,
                onError = Color.White,
                surfaceVariant = Color(0xFF1A1A1A),
                onSurfaceVariant = Color(0xFFB3B3B3),
                outline = Color(0xFF333333)
            )
        }
        "Liquorice Black" -> {
            androidx.compose.material3.darkColorScheme(
                background = Color(0xFF0F0F0F),
                surface = Color(0xFF1A1A1A),
                primary = Color(0xFFD8D3DA),
                secondary = Color(0xFFA0FFA3),
                error = Color(0xFFC83B3B),
                onBackground = Color.White,
                onSurface = Color.White,
                onPrimary = Color(0xFF1C1A1D),
                onSecondary = Color(0xFF1C5A21),
                onError = Color.White,
                surfaceVariant = Color(0xFF242424),
                onSurfaceVariant = Color(0xFF676767),
                outline = Color(0xFF2C2C2C)
            )
        }
        else -> { // Default / OLED Black
            androidx.compose.material3.darkColorScheme(
                background = Color.Black,
                surface = Color(0xFF1A1A1A),
                primary = Color(0xFFD8D3DA),
                secondary = Color(0xFFA0FFA3),
                error = Color(0xFFC83B3B),
                onBackground = Color.White,
                onSurface = Color.White,
                onPrimary = Color(0xFF1C1A1D),
                onSecondary = Color(0xFF1C5A21),
                onError = Color.White,
                surfaceVariant = Color(0xFF242424),
                onSurfaceVariant = Color(0xFF676767),
                outline = Color(0xFF2C2C2C)
            )
        }
    }

    val colorScheme = targetColorScheme.copy(
        primary = animateColorAsState(targetColorScheme.primary, tween(500)).value,
        onPrimary = animateColorAsState(targetColorScheme.onPrimary, tween(500)).value,
        secondary = animateColorAsState(targetColorScheme.secondary, tween(500)).value,
        onSecondary = animateColorAsState(targetColorScheme.onSecondary, tween(500)).value,
        error = animateColorAsState(targetColorScheme.error, tween(500)).value,
        onError = animateColorAsState(targetColorScheme.onError, tween(500)).value,
        background = animateColorAsState(targetColorScheme.background, tween(500)).value,
        onBackground = animateColorAsState(targetColorScheme.onBackground, tween(500)).value,
        surface = animateColorAsState(targetColorScheme.surface, tween(500)).value,
        onSurface = animateColorAsState(targetColorScheme.onSurface, tween(500)).value,
        surfaceVariant = animateColorAsState(targetColorScheme.surfaceVariant, tween(500)).value,
        onSurfaceVariant = animateColorAsState(targetColorScheme.onSurfaceVariant, tween(500)).value,
        outline = animateColorAsState(targetColorScheme.outline, tween(500)).value,
    )

    val typography = Typography(
        // HEADERS
        displayLarge = TextStyle(
            fontFamily = if (useNType) NTypeFontFamily else NDot55FontFamily,
            fontSize = 45.sp,
            lineHeight = 55.sp,
            fontWeight = FontWeight.Normal
        ),
        headlineMedium = TextStyle(
            fontFamily = if (useNType) NTypeFontFamily else NDotFontFamily,
            fontSize = 30.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.Normal
        ),

        // SUB-HEADERS
        titleLarge = TextStyle(
            fontSize = 21.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Normal
        ),
        titleMedium = TextStyle(
            fontSize = 17.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Normal
        ),

        // BODY & LABELS (Keep system font for high legibility at small sizes)
        bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
        labelLarge = TextStyle(
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium
        ),
        labelMedium = TextStyle(
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium
        ),
    )
    CompositionLocalProvider(
        LocalAppSpacing provides AppSpacing(),
        LocalM3EEnabled provides m3eEnabled,
        LocalUIAmplitude provides uiAmplitude
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = Shapes(
                extraLarge = RoundedCornerShape(32.dp),
                large = RoundedCornerShape(28.dp),
                medium = RoundedCornerShape(20.dp),
                small = RoundedCornerShape(14.dp),
            ),
            typography = typography,
            content = content,
        )
    }
}
