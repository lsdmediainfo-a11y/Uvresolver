package com.example.universalvideodownloader.ui.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.universalvideodownloader.data.local.DownloadEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onPlayVideo: (String) -> Unit = {},
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val allDownloads by viewModel.allDownloads.collectAsState()
    
    val activeTasks = allDownloads.filter { it.status == "DOWNLOADING" || it.status == "PENDING" }
    val completedTasks = allDownloads.filter { it.status == "COMPLETED" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("İndirilenler", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }
            
            if (activeTasks.isNotEmpty()) {
                item {
                    Text("Devam Eden İndirmeler (${activeTasks.size})", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                items(activeTasks) { task ->
                    ActiveDownloadItem(task)
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }

            if (completedTasks.isNotEmpty()) {
                item {
                    Text("Tamamlanan Videolar", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                items(completedTasks) { entity ->
                    val file = File(context.getExternalFilesDir(null), entity.outputName)
                    CompletedDownloadItem(
                        entity = entity,
                        fileExists = file.exists(),
                        onDelete = {
                            if (file.exists()) file.delete()
                            viewModel.cancelDownload(entity.id)
                        },
                        onPlay = {
                            if (file.exists()) {
                                onPlayVideo("file://${file.absolutePath}")
                            }
                        }
                    )
                }
            } else if (activeTasks.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Henüz indirilen bir video yok.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@Composable
fun ActiveDownloadItem(task: DownloadEntity) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(task.outputName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = if (task.status == "DOWNLOADING") "İşleniyor" else "Sırada",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun CompletedDownloadItem(entity: DownloadEntity, fileExists: Boolean, onDelete: () -> Unit, onPlay: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = fileExists) { onPlay() }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(
                        if (fileExists) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer, 
                        RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PlayArrow, 
                    contentDescription = "Oynat", 
                    tint = if (fileExists) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entity.outputName,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (fileExists) "Tamamlandı" else "Dosya Bulunamadı (Silinmiş)",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (fileExists) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Sil", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
