package com.example.universalvideodownloader.ui.browser

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

import com.example.universalvideodownloader.ui.browser.capture.CaptureManager
import com.example.universalvideodownloader.ui.browser.capture.CapturedNetworkEvent
import com.example.universalvideodownloader.ui.browser.capture.PlaybackCaptureSession

@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val captureManager: CaptureManager
) : ViewModel() {

    private val _url = MutableStateFlow("https://google.com")
    val url: StateFlow<String> = _url.asStateFlow()

    private val _inputUrl = MutableStateFlow("https://google.com")
    val inputUrl: StateFlow<String> = _inputUrl.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _canGoBack = MutableStateFlow(false)
    val canGoBack: StateFlow<Boolean> = _canGoBack.asStateFlow()

    private val _canGoForward = MutableStateFlow(false)
    val canGoForward: StateFlow<Boolean> = _canGoForward.asStateFlow()

    val currentSession: StateFlow<PlaybackCaptureSession?> = captureManager.currentSession

    fun updateInputUrl(newUrl: String) {
        _inputUrl.value = newUrl
    }

    fun loadUrl(targetUrl: String) {
        var finalUrl = targetUrl
        if (!finalUrl.startsWith("http://") && !finalUrl.startsWith("https://")) {
            finalUrl = if (finalUrl.contains(".") && !finalUrl.contains(" ")) {
                "https://$finalUrl"
            } else {
                "https://www.google.com/search?q=$finalUrl"
            }
        }
        _url.value = finalUrl
        _inputUrl.value = finalUrl
    }

    fun onPageStarted(currentUrl: String) {
        _isLoading.value = true
        _inputUrl.value = currentUrl
        captureManager.onNewPage(currentUrl)
    }

    fun onPageFinished(currentUrl: String) {
        _isLoading.value = false
        _progress.value = 0f
        _inputUrl.value = currentUrl
    }

    fun onProgressChanged(newProgress: Int) {
        _progress.value = newProgress / 100f
    }

    fun updateNavigationState(canBack: Boolean, canForward: Boolean) {
        _canGoBack.value = canBack
        _canGoForward.value = canForward
    }

    fun onEventCaptured(event: CapturedNetworkEvent) {
        captureManager.onEventCaptured(event)
    }

    fun startDownload(event: CapturedNetworkEvent, context: android.content.Context) {
        val workManager = androidx.work.WorkManager.getInstance(context)
        val data = androidx.work.workDataOf(
            "CANDIDATE_ID" to event.url.hashCode().toString(),
            "VIDEO_URL" to event.url,
            "TYPE" to if (event.url.contains(".m3u8")) "HLS" else "MP4"
        )
        val request = androidx.work.OneTimeWorkRequestBuilder<com.example.universalvideodownloader.data.download.VideoDownloadWorker>()
            .setInputData(data)
            .addTag("video_download")
            .build()
        workManager.enqueue(request)
        android.widget.Toast.makeText(context, "İndirme başlatıldı...", android.widget.Toast.LENGTH_SHORT).show()
    }
}
