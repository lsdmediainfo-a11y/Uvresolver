package com.sekerkirrma.rs.ui.browser

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.hilt.navigation.compose.hiltViewModel

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel = hiltViewModel()
) {
    val currentUrl by viewModel.currentUrl.collectAsState()
    val detectedVideoUrl by viewModel.detectedVideoUrl.collectAsState()

    var textInput by remember { mutableStateOf(currentUrl) }
    val context = LocalContext.current

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
                visible = detectedVideoUrl != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                FloatingActionButton(
                    onClick = {
                        // For now, just show a Toast. In Phase 3, this will open a BottomSheet
                        Toast.makeText(context, "Detected: $detectedVideoUrl", Toast.LENGTH_LONG).show()
                    }
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
                        webViewClient = SniffingWebViewClient(
                            onVideoDetected = { url ->
                                // This is called from a background thread inside WebViewClient,
                                // but StateFlow handles thread safety. However, updating ViewModels
                                // is generally fine since StateFlow emits on whatever thread, but
                                // compose collects it properly.
                                viewModel.onVideoDetected(url)
                            }
                        )
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
}
