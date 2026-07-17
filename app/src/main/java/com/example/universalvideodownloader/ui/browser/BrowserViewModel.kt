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
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URL

data class HlsVariant(
    val originalEvent: CapturedNetworkEvent,
    val url: String,
    val resolution: String?,
    val bandwidth: Int?
)

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
    
    private val okHttpClient = OkHttpClient()
    
    private val _qualityOptions = MutableStateFlow<List<HlsVariant>>(emptyList())
    val qualityOptions: StateFlow<List<HlsVariant>> = _qualityOptions.asStateFlow()

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

    fun parseAndShowQualities(event: CapturedNetworkEvent, context: android.content.Context) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val fallbackVariants = listOf(HlsVariant(event, event.url, "Asıl Kalite (Bilinmiyor)", null))
            
            if (!event.url.contains(".m3u8") || event.url.startsWith("blob:")) {
                _qualityOptions.value = fallbackVariants
                return@launch
            }
            
            try {
                val requestBuilder = Request.Builder().url(event.url)
                try {
                    val headerJson = org.json.JSONObject(event.headers)
                    val keys = headerJson.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        requestBuilder.addHeader(key, headerJson.getString(key))
                    }
                } catch (e: Exception) {
                    requestBuilder.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64 AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36)")
                }

                val request = requestBuilder.build()
                val response = okHttpClient.newCall(request).execute()
                val body = response.body?.string() ?: ""
                
                if (body.contains("#EXT-X-STREAM-INF")) {
                    val lines = body.lines()
                    val variants = mutableListOf<HlsVariant>()
                    var currentResolution: String? = null
                    var currentBandwidth: Int? = null
                    
                    for (line in lines) {
                        if (line.startsWith("#EXT-X-STREAM-INF")) {
                            val resMatch = Regex("RESOLUTION=(\\d+x\\d+)").find(line)
                            val bwMatch = Regex("BANDWIDTH=(\\d+)").find(line)
                            currentResolution = resMatch?.groupValues?.get(1)
                            currentBandwidth = bwMatch?.groupValues?.get(1)?.toIntOrNull()
                        } else if (!line.startsWith("#") && line.trim().isNotEmpty()) {
                            val variantUrl = if (line.startsWith("http")) line else URL(URL(event.url), line).toString()
                            variants.add(HlsVariant(event, variantUrl, currentResolution, currentBandwidth))
                            currentResolution = null
                            currentBandwidth = null
                        }
                    }
                    _qualityOptions.value = if (variants.isNotEmpty()) variants else fallbackVariants
                } else {
                    _qualityOptions.value = fallbackVariants
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _qualityOptions.value = fallbackVariants
            }
        }
    }

    fun clearQualityOptions() {
        _qualityOptions.value = emptyList()
    }

    fun startDownload(event: CapturedNetworkEvent, variantUrl: String, context: android.content.Context) {
        val workManager = androidx.work.WorkManager.getInstance(context)
        val data = androidx.work.workDataOf(
            "CANDIDATE_ID" to event.url.hashCode().toString(),
            "VIDEO_URL" to variantUrl,
            "TYPE" to if (event.url.contains(".m3u8") || variantUrl.contains(".m3u8")) "HLS" else "MP4"
        )
        val request = androidx.work.OneTimeWorkRequestBuilder<com.example.universalvideodownloader.data.download.VideoDownloadWorker>()
            .setInputData(data)
            .addTag("video_download")
            .build()
        workManager.enqueue(request)
        android.widget.Toast.makeText(context, "İndirme başlatıldı...", android.widget.Toast.LENGTH_SHORT).show()
    }
}
