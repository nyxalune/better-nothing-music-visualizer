package com.better.nothing.music.vizualizer.ui

import com.better.nothing.music.vizualizer.model.CommunityPreset
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityPresetsScreen(
    presets: List<CommunityPreset>?,
    error: String?,
    onDownload: (CommunityPreset) -> Unit,
    onDismiss: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Community Presets", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        when {
            error != null -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Error: $error", color = Color.Red, modifier = Modifier.padding(16.dp))
                        Button(onClick = onDismiss) { Text("Back") }
                    }
                }
            }
            presets == null -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            presets.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("No presets found. Be the first to share one!", color = Color.Gray)
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.padding(padding).fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(presets) { preset ->
                        PresetCard(preset, onDownload)
                    }
                }
            }
        }
    }
}

@Composable
fun PresetCard(preset: CommunityPreset, onDownload: (CommunityPreset) -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(preset.name, fontSize = 18.sp, color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text("by ${preset.author} • ${preset.phoneModel}", fontSize = 14.sp, color = Color.Gray)
                
                val date = remember(preset.timestamp) {
                    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(preset.timestamp))
                }
                Text("$date • ${preset.downloads} downloads", fontSize = 12.sp, color = Color.DarkGray)
            }
            
            IconButton(
                onClick = { onDownload(preset) },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.Black
                )
            ) {
                Icon(Icons.Default.Download, contentDescription = "Download")
            }
        }
    }
}
