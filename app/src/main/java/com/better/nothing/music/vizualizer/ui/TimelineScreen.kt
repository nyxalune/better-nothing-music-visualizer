package com.better.nothing.music.vizualizer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class TimelineItem(
    val title: String,
    val description: String,
    val isKey: Boolean,
    val status: TimelineStatus
)

private enum class TimelineStatus {
    DONE, IN_PROGRESS, PLANNED
}

@Composable
internal fun TimelineScreen(
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()
    
    val items = remember {
        listOf(
            TimelineItem(
                "Zebra Breathing Mode",
                "A new alternating pulse mode for Glyph LEDs.",
                isKey = false,
                status = TimelineStatus.PLANNED
            ),
            TimelineItem(
                "RichTap HD & Shizuku",
                "Advanced haptics and high-fidelity audio capture via Shizuku integration.",
                isKey = true,
                status = TimelineStatus.IN_PROGRESS
            ),
            TimelineItem(
                "Flashlight Visualizer",
                "The latest addition: sync your camera LED with the beat.",
                isKey = true,
                status = TimelineStatus.IN_PROGRESS
            ),
            TimelineItem(
                "Dynamic Theming",
                "Auto Dark/Light mode support and Material You color syncing.",
                isKey = false,
                status = TimelineStatus.DONE
            ),
            TimelineItem(
                "Phone (1) Compatibility",
                "Full support for the original Nothing Phone (1) Glyphs.",
                isKey = true,
                status = TimelineStatus.DONE
            ),
            TimelineItem(
                "Haptic Visualization",
                "Feel the music through the device's vibration motor.",
                isKey = true,
                status = TimelineStatus.DONE
            ),
            TimelineItem(
                "Microphone Audio Source",
                "Visualize external sounds using your phone's mic.",
                isKey = false,
                status = TimelineStatus.DONE
            ),
            TimelineItem(
                "Core Visualizer Engine",
                "High-performance FFT analysis and 12-bit brightness control.",
                isKey = false,
                status = TimelineStatus.DONE
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        androidx.compose.material3.IconButton(onClick = onDismiss) {
            androidx.compose.material3.Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back"
            )
        }
        
        ScreenTitle(text = "Project Timeline")

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                items.forEachIndexed { index, item ->
                    TimelineNode(
                        item = item,
                        isFirst = index == 0,
                        isLast = index == items.size - 1
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(70.dp))
    }
}

@Composable
private fun TimelineNode(
    item: TimelineItem,
    isFirst: Boolean,
    isLast: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(40.dp)
        ) {
            // Top Line
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(16.dp)
                    .background(if (isFirst) Color.Transparent else MaterialTheme.colorScheme.outlineVariant)
            )
            
            // Dot
            val dotSize = if (item.isKey) 14.dp else 8.dp
            val dotColor = when (item.status) {
                TimelineStatus.DONE -> MaterialTheme.colorScheme.primary
                TimelineStatus.IN_PROGRESS -> MaterialTheme.colorScheme.secondary
                TimelineStatus.PLANNED -> MaterialTheme.colorScheme.outline
            }
            
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .background(dotColor, CircleShape)
            )
            
            // Bottom Line
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .weight(1f)
                    .background(if (isLast) Color.Transparent else MaterialTheme.colorScheme.outlineVariant)
            )
        }
        
        Column(
            modifier = Modifier
                .padding(start = 16.dp, bottom = 24.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = item.title,
                style = if (item.isKey) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                fontWeight = if (item.isKey) FontWeight.Bold else FontWeight.SemiBold,
                color = if (item.status == TimelineStatus.PLANNED)
                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f) 
                    else MaterialTheme.colorScheme.onBackground
            )
            
            if (item.description.isNotEmpty()) {
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (item.status == TimelineStatus.PLANNED) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(
                        text = "Planned",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            if (item.status == TimelineStatus.IN_PROGRESS) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(
                        text = "In Progress",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            if (item.status == TimelineStatus.DONE) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(
                        text = "Done",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
