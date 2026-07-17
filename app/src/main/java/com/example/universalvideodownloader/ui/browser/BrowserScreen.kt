package com.example.universalvideodownloader.ui.browser

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.offset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    initialUrl: String? = null,
    viewModel: BrowserViewModel = hiltViewModel()
) {
    LaunchedEffect(initialUrl) {
        if (initialUrl != null && initialUrl != viewModel.inputUrl.value && initialUrl != viewModel.url.value) {
            viewModel.loadUrl(initialUrl)
        }
    }
    val inputUrl by viewModel.inputUrl.collectAsState()
    val activeUrl by viewModel.url.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val canGoBack by viewModel.canGoBack.collectAsState()
    val canGoForward by viewModel.canGoForward.collectAsState()
    val currentSession by viewModel.currentSession.collectAsState()
    val captureCount = currentSession?.activeEvents?.count { !it.isAd } ?: 0
    val adCount = currentSession?.activeEvents?.count { it.isAd } ?: 0
    val isVideoPlaying = currentSession?.isVideoPlaying == true
    var showBottomSheet by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    // Animasyon
    val infiniteTransition = rememberInfiniteTransition()
    val bounceY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (captureCount > 0) -15f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    val qualityOptions by viewModel.qualityOptions.collectAsState()

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { 
                        TextField(
                            value = inputUrl,
                            onValueChange = { viewModel.updateInputUrl(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(
                                onGo = { viewModel.loadUrl(inputUrl) }
                            ),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { /* TODO: WebView'e geri bildirim gönderilecek */ },
                            enabled = canGoBack
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Geri")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.loadUrl(activeUrl) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Yenile")
                        }
                    }
                )
                if (isLoading && progress > 0f) {
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        floatingActionButton = {
            if (captureCount > 0) {
                ExtendedFloatingActionButton(
                    onClick = { showBottomSheet = true },
                    modifier = Modifier.offset(y = bounceY.dp),
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    icon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = "İndirme") },
                    text = { 
                        val text = if (isVideoPlaying) "Video Bulundu ($captureCount)" else "Medya Yakalandı ($captureCount)"
                        val adText = if (adCount > 0) " | $adCount Reklam Gizlendi" else ""
                        Text(text + adText)
                    }
                )
            } else {
                FloatingActionButton(onClick = { showBottomSheet = true }) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "İndirme")
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            CustomWebView(
                url = activeUrl,
                onPageStarted = { viewModel.onPageStarted(it) },
                onPageFinished = { viewModel.onPageFinished(it) },
                onProgressChanged = { viewModel.onProgressChanged(it) },
                onNavigationStateChanged = { back, forward -> 
                    viewModel.updateNavigationState(back, forward) 
                },
                onEventCaptured = { viewModel.onEventCaptured(it) }
            )
        }

        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Yakalanan Videolar", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    currentSession?.activeEvents?.filter { !it.isAd }?.sortedByDescending { it.score }?.forEach { event ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            onClick = {
                                viewModel.parseAndShowQualities(event, context)
                                showBottomSheet = false
                            }
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(event.url, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
                                Text("Tür: ${if (event.url.contains(".m3u8")) "HLS" else "MP4"} | Puan: ${event.score}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if ((currentSession?.activeEvents?.count { !it.isAd } ?: 0) == 0) {
                        Text("Henüz video yakalanamadı. Lütfen sayfada bir video oynatmayı deneyin.")
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }

        if (qualityOptions.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = { viewModel.clearQualityOptions() },
                title = { Text("Kalite Seçimi") },
                text = {
                    Column {
                        qualityOptions.forEach { variant ->
                            TextButton(
                                onClick = {
                                    viewModel.startDownload(variant.originalEvent, variant.url, context)
                                    viewModel.clearQualityOptions()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val resText = variant.resolution ?: "Bilinmeyen Çözünürlük"
                                val bwText = variant.bandwidth?.let { "${it / 1000} kbps" } ?: ""
                                Text("$resText $bwText")
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearQualityOptions() }) {
                        Text("İptal")
                    }
                }
            )
        }
    }
}
