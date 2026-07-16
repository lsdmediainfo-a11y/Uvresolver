package com.example.universalvideodownloader.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class ShortcutItem(val title: String, val url: String, val color: androidx.compose.ui.graphics.Color)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToBrowser: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    
    val shortcuts = listOf(
        ShortcutItem("Instagram", "https://instagram.com", MaterialTheme.colorScheme.tertiary),
        ShortcutItem("Facebook", "https://facebook.com", MaterialTheme.colorScheme.primary),
        ShortcutItem("X (Twitter)", "https://twitter.com", MaterialTheme.colorScheme.secondary),
        ShortcutItem("TikTok", "https://tiktok.com", MaterialTheme.colorScheme.error),
        ShortcutItem("Vimeo", "https://vimeo.com", MaterialTheme.colorScheme.inversePrimary),
        ShortcutItem("Dailymotion", "https://dailymotion.com", MaterialTheme.colorScheme.surfaceTint)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Video Downloader",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(32.dp))
        
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("URL veya anahtar kelime girin...") },
            trailingIcon = {
                IconButton(onClick = { 
                    if (searchQuery.isNotBlank()) onNavigateToBrowser(searchQuery)
                }) {
                    Icon(Icons.Default.Search, contentDescription = "Ara")
                }
            },
            shape = RoundedCornerShape(24.dp),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "Popüler Siteler",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.Start)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(shortcuts.size) { index ->
                val item = shortcuts[index]
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = item.color.copy(alpha = 0.2f)),
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clickable { onNavigateToBrowser(item.url) }
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = item.color
                        )
                    }
                }
            }
        }
    }
}
