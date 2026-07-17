package com.sekerkirrma.rs.ui.browser

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import com.sekerkirrma.rs.domain.extractor.ExtractorDelegate
import com.sekerkirrma.rs.domain.extractor.VideoInterceptor

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel = hiltViewModel()
) {
    val currentUrl by viewModel.currentUrl.collectAsState()
    val detectedVideoUrl by viewModel.detectedVideoUrl.collectAsState()

    var textInput by remember { mutableStateOf(currentUrl) }
    var showBottomSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        ExtractorDelegate.mediaFlow.collect { signal ->
            viewModel.onVideoDetected(signal)
        }
    }

    Scaffold(
        topBar = {
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                singleLine = true,
                placeholder = { Text("Search or type web address") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(
                    onGo = {
                        viewModel.updateCurrentUrl(textInput)
                    }
                ),
                trailingIcon = {
                    IconButton(onClick = { viewModel.updateCurrentUrl(textInput) }) {
                        Icon(Icons.Filled.Search, contentDescription = "Go")
                    }
                }
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = true, // Always show FAB
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                FloatingActionButton(
                    onClick = {
                        if (detectedVideoUrl != null) {
                            showBottomSheet = true
                            val liveCookies = android.webkit.CookieManager.getInstance().getCookie(detectedVideoUrl!!.pageUrl) ?: ""
                            val updatedSignal = detectedVideoUrl!!.copy(cookies = liveCookies)
                            viewModel.parseVideoUrl(detectedVideoUrl!!.url, updatedSignal)
                        } else {
                            Toast.makeText(context, "Lütfen önce videoyu oynatın", Toast.LENGTH_SHORT).show()
                        }
                    },
                    containerColor = if (detectedVideoUrl != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(Icons.Filled.Download, contentDescription = "Download Video")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        
                        webChromeClient = WebChromeClient()
                        webViewClient = VideoInterceptor()
                        loadUrl(currentUrl)
                    }
                },
                update = { webView ->
                    if (webView.url != currentUrl) {
                        webView.loadUrl(currentUrl)
                    }
                }
            )
        }
    }

    if (showBottomSheet) {
        val isParsing by viewModel.isParsing.collectAsState()
        val videoFormats by viewModel.videoFormats.collectAsState()
        val parseError by viewModel.parseError.collectAsState()

        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .padding(bottom = 32.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                Text("Select Resolution", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))

                if (isParsing) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Fetching formats via YoutubeDL...")
                } else if (parseError != null) {
                    Text("Error: $parseError", color = MaterialTheme.colorScheme.error)
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(videoFormats) { format ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(text = "${format.resolution} (${format.ext})", style = MaterialTheme.typography.bodyLarge)
                                        Text(text = "Size: ${format.fileSizeStr}", style = MaterialTheme.typography.bodyMedium)
                                        if (format.isAudioOnly) {
                                            Text(text = "Audio Only", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    Button(onClick = {
                                        val urlToDownload = detectedVideoUrl?.url ?: currentUrl
                                        val finalHeaders = mutableMapOf<String, String>()
                                        detectedVideoUrl?.headers?.forEach { (k, v) ->
                                            val safeKey = k.lowercase()
                                            if (safeKey == "referer" || safeKey == "user-agent" || safeKey == "origin" || safeKey == "authorization" || safeKey.startsWith("x-")) {
                                                finalHeaders[k] = v
                                            }
                                        }
                                        val liveCookies = android.webkit.CookieManager.getInstance().getCookie(detectedVideoUrl?.pageUrl ?: currentUrl) ?: ""
                                        if (liveCookies.isNotEmpty()) {
                                            finalHeaders["Cookie"] = liveCookies
                                        }

                                        viewModel.startDownload(
                                            workManager = androidx.work.WorkManager.getInstance(context),
                                            url = urlToDownload,
                                            formatId = format.formatId,
                                            title = "Video_${System.currentTimeMillis()}",
                                            headers = finalHeaders
                                        )
                                        Toast.makeText(context, "Download started", Toast.LENGTH_SHORT).show()
                                        showBottomSheet = false
                                    }) {
                                        Text("Download")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
