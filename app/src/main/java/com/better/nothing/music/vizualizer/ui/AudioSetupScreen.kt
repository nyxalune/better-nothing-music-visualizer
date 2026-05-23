package com.better.nothing.music.vizualizer.ui

import com.better.nothing.music.vizualizer.BuildConfig
import com.better.nothing.music.vizualizer.service.AudioCaptureService
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

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
    captureSource: AudioCaptureService.CaptureSource = AudioCaptureService.CaptureSource.INTERNAL,
    onCaptureSourceChanged: (AudioCaptureService.CaptureSource) -> Unit = {},
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onAutoDeviceToggle(true)
        }
    }

    val recordAudioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onCaptureSourceChanged(AudioCaptureService.CaptureSource.MIC)
        }
    }

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
            onAutoDeviceToggle(false)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(modifier = Modifier.height(50.dp))

        ScreenTitle(text = stringResource(R.string.audio_screen_title))

        CaptureSourceCard(
            selectedSource = captureSource,
            onSourceSelected = { source ->
                if (source == AudioCaptureService.CaptureSource.MIC) {
                    val status = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                    if (status == PackageManager.PERMISSION_GRANTED) {
                        onCaptureSourceChanged(source)
                    } else {
                        recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                } else {
                    onCaptureSourceChanged(source)
                }
            }
        )

        val descriptionText = if (isRunning) {
            stringResource(R.string.audio_description_running)
        } else {
            stringResource(R.string.audio_description_idle)
        }
        
        ExpressiveCard(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)) {
            BodyText(text = descriptionText, size = 14.sp)
        }

        AnimatedVisibility(visible = isRunning) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                ExpressiveCard {
                    CardHeader(title = "Auto-Memorize Device")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (autoDeviceEnabled)
                                    stringResource(R.string.saving_latency_for, connectedDeviceName ?: stringResource(R.string.internal_speaker))
                                else stringResource(R.string.manual_mode_global_latency),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(
                            checked = autoDeviceEnabled,
                            onCheckedChange = handleAutoToggle,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }

                LatencyCard(
                    latencyMs = latencyMs,
                    onLatencyChanged = onLatencyChanged,
                    latencyPresets = latencyPresets,
                    onLatencyPresetsChanged = onLatencyPresetsChanged,
                )

                FFTSpectrumCard(fftData = fftData)
                
                ExpressiveCard(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)) {
                    BodyText(
                        text = stringResource(R.string.latency_compensation_description),
                        size = 12.sp
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(70.dp))
    }
}

@Composable
fun CaptureSourceCard(
    selectedSource: AudioCaptureService.CaptureSource,
    onSourceSelected: (AudioCaptureService.CaptureSource) -> Unit
) {
    ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
        CardHeader(title = "Capture Source")

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val sources = mutableListOf(
                AudioCaptureService.CaptureSource.INTERNAL to Icons.Default.PhoneAndroid,
                AudioCaptureService.CaptureSource.MIC to Icons.Default.Mic
            )
            if (BuildConfig.SHOW_SHIZUKU) {
                sources.add(AudioCaptureService.CaptureSource.SHIZUKU to Icons.Default.Terminal)
            }

            sources.forEach { (source, icon) ->
                val isSelected = selectedSource == source
                val backgroundColor by animateColorAsState(
                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
                val contentColor by animateColorAsState(
                    if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    onClick = { onSourceSelected(source) },
                    shape = RoundedCornerShape(16.dp),
                    color = backgroundColor,
                    contentColor = contentColor,
                    modifier = Modifier.weight(1f).height(64.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(source.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
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

    ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
        CardHeader(title = stringResource(R.string.latency_compensation)) {
            Text(
                text = "${latencyMs}ms",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
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
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            draggingIndex = index
                            onLatencyChanged(preset)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${preset}ms",
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        ExpressiveSlider(
            value = latencyMs.toFloat(),
            onValueChange = { updateLatency(it.toInt()) },
            valueRange = 0f..500f,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(-10, -1, 1, 10).forEach { amount ->
                FineTuneButton(
                    amount = amount,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        updateLatency(latencyMs + amount)
                    }
                )
            }
        }
    }
}

@Composable
fun FFTSpectrumCard(fftData: FloatArray) {
    var touchX by remember { mutableStateOf<Float?>(null) }

    ExpressiveCard(modifier = Modifier.fillMaxWidth()) {
        CardHeader(title = stringResource(R.string.live_spectrum))

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
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

            val decayedData = remember { mutableStateOf(floatArrayOf()) }
            LaunchedEffect(fftData) {
                if (fftData.isEmpty()) return@LaunchedEffect

                val current = decayedData.value
                if (current.size != fftData.size) {
                    decayedData.value = fftData.copyOf()
                    return@LaunchedEffect
                }

                val decay = 0.75f
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
                
                // Draw Grid
                val gridColor = Color.White.copy(alpha = 0.05f)
                drawLine(gridColor, Offset(0f, h*0.25f), Offset(w, h*0.25f), 1f)
                drawLine(gridColor, Offset(0f, h*0.5f), Offset(w, h*0.5f), 1f)
                drawLine(gridColor, Offset(0f, h*0.75f), Offset(w, h*0.75f), 1f)

                val minFreq = 20f
                val maxFreq = 20000f
                val sampleRate = 44100f
                val numBins = data.size
                val hzPerBin = sampleRate / (2 * (numBins - 1))

                val logMin = log10(minFreq)
                val logMax = log10(maxFreq)

                val barPath = Path()
                var first = true

                val gradient = Brush.verticalGradient(
                    colors = listOf(primaryColor.copy(alpha = 0.6f), primaryColor.copy(alpha = 0.05f)),
                    startY = 0f,
                    endY = h
                )

                val points = 250
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

                    val scaledMag = (mag * 60f).coerceIn(0f, 1f)
                    val y = h - (scaledMag * (h - 20f)) - 10f
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
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                touchX?.let { tx ->
                    val x = tx.coerceIn(0f, w)
                    drawLine(
                        color = Color.White.copy(alpha = 0.3f),
                        start = Offset(x, 0f),
                        end = Offset(x, h),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f))
                    )
                }
            }

            touchX?.let { tx ->
                val fraction = (tx / constraints.maxWidth.toFloat()).coerceIn(0f, 1f)
                val logMin = log10(20f)
                val logMax = log10(20000f)
                val logFreq = logMin + fraction * (logMax - logMin)
                val freq = 10f.pow(logFreq)

                val text = if (freq >= 1000) String.format(Locale.US, "%.1fkHz", freq / 1000f) else String.format(Locale.US, "%dHz", freq.toInt())
                val txDp = with(density) { tx.toDp() }

                Surface(
                    modifier = Modifier
                        .offset(
                            x = (txDp - 30.dp).coerceIn(4.dp, width - 64.dp),
                            y = 12.dp
                        ),
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(8.dp),
                    tonalElevation = 4.dp
                ) {
                    Text(
                        text = text,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        
        // Frequency labels
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val freqLabels = listOf("20Hz", "100Hz", "1kHz", "10kHz", "20kHz")
            freqLabels.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
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
    var isAnimating by remember { mutableStateOf(false) }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collectLatest { interaction ->
            when (interaction) {
                is PressInteraction.Press -> isAnimating = true
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    delay(100)
                    isAnimating = false
                }
            }
        }
    }

    val animatedWeight by animateFloatAsState(
        targetValue = if (isAnimating) 1.2f else 1.0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "weight"
    )

    val containerColor by animateColorAsState(
        targetValue = if (isAnimating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    )

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        modifier = Modifier
            .weight(animatedWeight)
            .fillMaxHeight()
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = if (amount > 0) "+$amount" else "$amount",
                style = MaterialTheme.typography.labelMedium,
                color = if (isAnimating) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
