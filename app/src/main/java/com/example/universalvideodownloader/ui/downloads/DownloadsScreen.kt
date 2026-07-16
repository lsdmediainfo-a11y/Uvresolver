package com.example.universalvideodownloader.ui.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.delay
import java.io.File
import android.content.Intent
import androidx.core.content.FileProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(onPlayVideo: (String) -> Unit = {}) {
    val context = LocalContext.current
    var completedFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var activeTasks by remember { mutableStateOf<List<WorkInfo>>(emptyList()) }

    // Dosyaları ve aktif işleri düzenli olarak yenile
    LaunchedEffect(Unit) {
        val workManager = WorkManager.getInstance(context)
        while (true) {
            val dir = context.getExternalFilesDir(null)
            if (dir != null && dir.exists()) {
                completedFiles = dir.listFiles()?.filter { it.name.endsWith(".mp4") }?.sortedByDescending { it.lastModified() } ?: emptyList()
            }
            
            val infos = workManager.getWorkInfosByTag("video_download").get()
            activeTasks = infos.filter { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
            
            delay(2000)
        }
    }

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

            if (completedFiles.isNotEmpty()) {
                item {
                    Text("Tamamlanan Videolar", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                items(completedFiles) { file ->
                    CompletedDownloadItem(
                        file = file,
                        onDelete = {
                            if (file.exists()) file.delete()
                            // Zorunlu güncelleme için (2 saniye beklemeden)
                            completedFiles = completedFiles.filter { it.absolutePath != file.absolutePath }
                        },
                        onPlay = {
                            onPlayVideo("file://${file.absolutePath}")
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
fun ActiveDownloadItem(task: WorkInfo) {
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
                Text("Video İndiriliyor...", fontWeight = FontWeight.Bold)
                Text(
                    text = if (task.state == WorkInfo.State.RUNNING) "İşleniyor" else "Sırada",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun CompletedDownloadItem(file: File, onDelete: () -> Unit, onPlay: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Oynat", tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${file.length() / (1024 * 1024)} MB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Sil", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
