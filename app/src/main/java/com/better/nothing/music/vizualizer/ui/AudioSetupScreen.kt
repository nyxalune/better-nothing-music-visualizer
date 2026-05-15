package com.better.nothing.music.vizualizer.ui

import com.better.nothing.music.vizualizer.R
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.max
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest


@Composable
fun AudioScreen(
    isRunning: Boolean,
    latencyMs: Int,
    onLatencyChanged: (Int) -> Unit,
    latencyPresets: List<Int>,
    onLatencyPresetsChanged: (List<Int>) -> Unit,
    autoDeviceEnabled: Boolean,
    onAutoDeviceToggle: (Boolean) -> Unit,
    connectedDeviceName: String? = null,
    fftData: FloatArray = floatArrayOf(),
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Launcher to handle the Bluetooth permission request
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onAutoDeviceToggle(true)
        }
    }

    // Logic to handle the toggle with permission check
    val handleAutoToggle: (Boolean) -> Unit = { setEnabled ->
        if (setEnabled) {
            val status = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            )
            if (status == PackageManager.PERMISSION_GRANTED) {
                onAutoDeviceToggle(true)
            } else {
                permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else {
            onAutoDeviceToggle(true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Spacer(modifier = Modifier.height(50.dp))

        ScreenTitle(text = stringResource(R.string.audio_screen_title))

        val descriptionText = if (isRunning) {
            stringResource(R.string.audio_description_running)
        } else {
            stringResource(R.string.audio_description_idle)
        }
        BodyText(text = descriptionText)

        AnimatedVisibility(visible = isRunning) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                BodyText(
                    text = stringResource(R.string.latency_compensation_description)
                )

                AutoDeviceCard(
                    enabled = autoDeviceEnabled,
                    onToggle = handleAutoToggle,
                    deviceName = connectedDeviceName
                )

                LatencyCard(
                    latencyMs = latencyMs,
                    onLatencyChanged = onLatencyChanged,
                    latencyPresets = latencyPresets,
                    onLatencyPresetsChanged = onLatencyPresetsChanged,
                )

                FFTSpectrumCard(fftData = fftData)
            }
        }
        Spacer(modifier = Modifier.height(70.dp))
    }
}

@Composable
fun AutoDeviceCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    deviceName: String?
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.auto_memorize_device),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = if (enabled)
                        stringResource(R.string.saving_latency_for, deviceName ?: stringResource(R.string.internal_speaker))
                    else stringResource(R.string.manual_mode_global_latency),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}
@Composable
fun LatencyCard(
    latencyMs: Int,
    onLatencyChanged: (Int) -> Unit,
    latencyPresets: List<Int>,
    onLatencyPresetsChanged: (List<Int>) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var draggingIndex by remember { mutableIntStateOf(-1) }

    val visualOrder = remember(latencyPresets) {
        latencyPresets.mapIndexed { i, v -> i to v }
            .sortedBy { it.second }
            .map { it.first }
    }

    // Play a tick when presets swap positions
    var isFirstOrderChange by remember { mutableStateOf(true) }
    LaunchedEffect(visualOrder) {
        if (isFirstOrderChange) {
            isFirstOrderChange = false
            return@LaunchedEffect
        }
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
    }

    val activeIndex = if (draggingIndex != -1) draggingIndex else latencyPresets.indexOf(latencyMs)

    val updateLatency = { newValue: Int ->
        val clampedValue = newValue.coerceIn(0, 500)
        if (draggingIndex == -1) draggingIndex = latencyPresets.indexOf(latencyMs)

        onLatencyChanged(clampedValue)

        if (draggingIndex != -1) {
            val currentList = latencyPresets.toMutableList()
            val isColliding = currentList.mapIndexed { i, v -> i to v }
                .any { (i, v) -> i != draggingIndex && v == clampedValue }

            if (!isColliding) {
                currentList[draggingIndex] = clampedValue
                onLatencyPresetsChanged(currentList)
            }
        }
    }

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.latency_compensation),
                color = Color(0xFFE6E1E3),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(), // Necessary to see the alignment effect
                textAlign = TextAlign.Center
            )

            // --- Presets Selector ---
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp))
                    .padding(4.dp)
            ) {
                val spacing = 4.dp
                val itemWidth = (maxWidth - (spacing * (latencyPresets.size - 1))) / latencyPresets.size

                latencyPresets.forEachIndexed { index, preset ->
                    val isSelected = index == activeIndex
                    val visualIndex = visualOrder.indexOf(index)
                    val targetOffset = (itemWidth + spacing) * visualIndex

                    val animatedX by animateDpAsState(
                        targetValue = targetOffset,
                        animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow),
                        label = "swap"
                    )

                    Box(
                        modifier = Modifier
                            .width(itemWidth)
                            .fillMaxHeight()
                            .offset(x = animatedX)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                draggingIndex = index
                                onLatencyChanged(preset)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${preset}ms",
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }

            ExpressiveSlider(
                value = latencyMs.toFloat(),
                onValueChange = { updateLatency(it.toInt()) },
                valueRange = 0f..500f,
                modifier = Modifier.fillMaxWidth()
            )

            // --- FIXED: Fine-Tuning Row ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(-10, -1, 1, 10).forEach { amount ->
                    // Call it directly. Since we are inside a Row,
                    // the RowScope receiver is automatically available.
                    FineTuneButton(
                        amount = amount,
                        // If your FineTuneButton doesn't accept a modifier yet,
                        // you'll need to update its definition (see below).
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            updateLatency(latencyMs + amount)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FFTSpectrumCard(fftData: FloatArray) {
    var touchX by remember { mutableStateOf<Float?>(null) }

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.live_spectrum),
                color = Color(0xFFE6E1E3),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { touchX = it.x },
                            onDrag = { change, _ -> 
                                change.consume()
                                touchX = change.position.x 
                            },
                            onDragEnd = { touchX = null },
                            onDragCancel = { touchX = null }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                touchX = it.x
                                tryAwaitRelease()
                                touchX = null
                            }
                        )
                    }
            ) {
                val primaryColor = MaterialTheme.colorScheme.primary
                val width = maxWidth
                val density = LocalDensity.current

                // --- DECAY LOGIC ---
                val decayedData = remember { mutableStateOf(floatArrayOf()) }
                LaunchedEffect(fftData) {
                    if (fftData.isEmpty()) return@LaunchedEffect
                    
                    val current = decayedData.value
                    if (current.size != fftData.size) {
                        decayedData.value = fftData.copyOf()
                        return@LaunchedEffect
                    }
                    
                    val decay = 0.8f
                    val next = FloatArray(fftData.size)
                    for (i in fftData.indices) {
                        val newVal = fftData[i]
                        val prevVal = current[i]
                        if (newVal > prevVal) {
                            next[i] = newVal
                        } else {
                            next[i] = (decay * prevVal) + ((1f - decay) * newVal)
                        }
                    }
                    decayedData.value = next
                }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val data = decayedData.value
                    if (data.isEmpty()) return@Canvas

                    val w = size.width
                    val h = size.height
                    val barPath = Path()
                    
                    val minFreq = 20f
                    val maxFreq = 20000f
                    val sampleRate = 44100f
                    val numBins = data.size
                    val hzPerBin = sampleRate / (2 * (numBins - 1))

                    val logMin = log10(minFreq)
                    val logMax = log10(maxFreq)

                    var first = true
                    
                    val gradient = Brush.verticalGradient(
                        colors = listOf(primaryColor.copy(alpha = 0.5f), Color.Transparent),
                        startY = 0f,
                        endY = h
                    )

                    val points = 200
                    for (i in 0..points) {
                        val fraction = i.toFloat() / points
                        val logFreq = logMin + fraction * (logMax - logMin)
                        val freq = 10f.pow(logFreq)
                        
                        val binIndex = freq / hzPerBin
                        val lowerBin = binIndex.toInt()
                        val upperBin = (lowerBin + 1).coerceAtMost(numBins - 1)
                        val t = binIndex - lowerBin
                        
                        val mag = if (lowerBin < numBins) {
                            (1f - t) * data[lowerBin] + t * data[upperBin]
                        } else 0f

                        val scaledMag = (mag * 50f).coerceIn(0f, 1f)
                        val y = h - (scaledMag * (h - 10f)) - 5f

                        val x = fraction * w
                        if (first) {
                            barPath.moveTo(x, y)
                            first = false
                        } else {
                            barPath.lineTo(x, y)
                        }
                    }

                    val fillPath = Path().apply {
                        addPath(barPath)
                        lineTo(w, h)
                        lineTo(0f, h)
                        close()
                    }

                    drawPath(path = fillPath, brush = gradient)
                    drawPath(
                        path = barPath,
                        color = primaryColor,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Draw touch feedback line
                    touchX?.let { tx ->
                        val x = tx.coerceIn(0f, w)
                        drawLine(
                            color = primaryColor.copy(alpha = 0.6f),
                            start = Offset(x, 0f),
                            end = Offset(x, h),
                            strokeWidth = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                        )
                    }
                }

                // Touch frequency text overlay
                touchX?.let { tx ->
                    val fraction = (tx / constraints.maxWidth.toFloat()).coerceIn(0f, 1f)
                    val logMin = log10(20f)
                    val logMax = log10(20000f)
                    val logFreq = logMin + fraction * (logMax - logMin)
                    val freq = 10f.pow(logFreq)
                    
                    val text = if (freq >= 1000) String.format(Locale.US, "%.1fkHz", freq / 1000f) else String.format(Locale.US, "%dHz", freq.toInt())
                    
                    val txDp = with(density) { tx.toDp() }
                    
                    Box(
                        modifier = Modifier
                            .offset(
                                x = txDp.coerceIn(0.dp, width - 60.dp),
                                y = 8.dp
                            )
                            .background(primaryColor, RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = text,
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Frequency labels row
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                val minFreq = 20f
                val maxFreq = 20000f
                val logMin = log10(minFreq)
                val logMax = log10(maxFreq)
                
                val labels = listOf(20f, 100f, 500f, 1000f, 5000f, 10000f, 20000f)
                
                labels.forEach { freq ->
                    val fraction = (log10(freq) - logMin) / (logMax - logMin)
                    val label = if (freq >= 1000) "${(freq / 1000).toInt()}k" else "${freq.toInt()}"
                    
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .width(24.dp)
                            .offset(x = (maxWidth * fraction.toFloat()) - 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun RowScope.FineTuneButton(
    amount: Int,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    // Logic: Force the animation to stay active for at least 100ms
    var isAnimating by remember { mutableStateOf(false) }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collectLatest { interaction ->
            when (interaction) {
                is PressInteraction.Press -> isAnimating = true
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    delay(100) // Minimum "hold" time for the animation to be visible
                    isAnimating = false
                }
            }
        }
    }

    val animatedWeight by animateFloatAsState(
        targetValue = if (isAnimating) 1.3f else 1.0f,
        animationSpec = spring(
            dampingRatio = 0.5f, // Bouncier than MediumBouncy
            stiffness = Spring.StiffnessMedium // Medium is more responsive for small buttons
        ),
        label = "weight_bounce"
    )

    val containerColor by animateColorAsState(
        targetValue = if (isAnimating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "color_fade"
    )

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        modifier = Modifier
            .weight(animatedWeight)
            .fillMaxHeight()
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = if (amount > 0) "+$amount" else "$amount",
                style = MaterialTheme.typography.labelMedium,
                color = if (isAnimating)  MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                fontWeight = if (isAnimating) FontWeight.ExtraBold else FontWeight.Medium
            )
        }
    }
}
