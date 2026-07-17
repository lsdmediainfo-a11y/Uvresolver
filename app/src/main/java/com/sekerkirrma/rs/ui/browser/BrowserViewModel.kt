package com.sekerkirrma.rs.ui.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sekerkirrma.rs.domain.model.VideoFormatItem
import com.sekerkirrma.rs.domain.sniffer.M3u8Parser
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.sekerkirrma.rs.data.local.dao.DownloadDao
import com.sekerkirrma.rs.data.local.entity.DownloadEntity
import com.sekerkirrma.rs.core.worker.DownloadWorker
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val downloadDao: DownloadDao
) : ViewModel() {

    private val _currentUrl = MutableStateFlow("https://google.com")
    val currentUrl: StateFlow<String> = _currentUrl.asStateFlow()

    private val _detectedVideoUrl = MutableStateFlow<String?>(null)
    val detectedVideoUrl: StateFlow<String?> = _detectedVideoUrl.asStateFlow()

    private val _isParsing = MutableStateFlow(false)
    val isParsing: StateFlow<Boolean> = _isParsing.asStateFlow()

    private val _videoFormats = MutableStateFlow<List<VideoFormatItem>>(emptyList())
    val videoFormats: StateFlow<List<VideoFormatItem>> = _videoFormats.asStateFlow()
    
    private val _parseError = MutableStateFlow<String?>(null)
    val parseError: StateFlow<String?> = _parseError.asStateFlow()

    var lastDetectedHeaders: Map<String, String> = emptyMap()
        private set

    fun updateCurrentUrl(url: String) {
        // Simple URL validation/formatting
        var finalUrl = url
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            finalUrl = "https://$url"
        }
        _currentUrl.value = finalUrl
        // Clear previous detections when navigating to a new URL
        clearDetectedVideo()
    }

    fun onVideoDetected(url: String, headers: Map<String, String>) {
        if (_detectedVideoUrl.value == null || url.contains(".m3u8")) {
            _detectedVideoUrl.value = url
            lastDetectedHeaders = headers
        }
    }

    fun clearDetectedVideo() {
        _detectedVideoUrl.value = null
        lastDetectedHeaders = emptyMap()
        _videoFormats.value = emptyList()
        _parseError.value = null
    }

    fun parseVideoUrl(url: String, headers: Map<String, String> = emptyMap()) {
        viewModelScope.launch {
            _isParsing.value = true
            _parseError.value = null
            _videoFormats.value = emptyList()

            try {
                withContext(Dispatchers.IO) {
                    var parsedFormats = withTimeoutOrNull(10000) {
                        val request = YoutubeDLRequest(url)
                        // Add headers so YoutubeDL can bypass referer checks
                        headers.forEach { (k, v) -> request.addOption("--add-header", "$k:$v") }
                        
                        var formats = emptyList<VideoFormatItem>()
                        try {
                            val info = YoutubeDL.getInstance().getInfo(request)
                            formats = info.formats?.mapNotNull { format ->
                                if (format.formatId == null) return@mapNotNull null
                                
                                val resolution = if (format.width != 0 && format.height != 0) {
                                    "${format.width}x${format.height}"
                                } else if (format.formatNote != null) {
                                    format.formatNote
                                } else {
                                    "Unknown"
                                }

                                val ext = format.ext ?: "unknown"
                                val sizeStr = if (format.fileSize > 0) {
                                    "${format.fileSize / (1024 * 1024)} MB"
                                } else {
                                    "~"
                                }
                                
                                val isAudioOnly = format.vcodec == "none"

                                VideoFormatItem(
                                    formatId = format.formatId!!,
                                    resolution = resolution,
                                    ext = ext,
                                    fileSizeStr = sizeStr,
                                    fps = format.fps.takeIf { it > 0 }?.toDouble(),
                                    isAudioOnly = isAudioOnly
                                )
                            }?.distinctBy { it.resolution + it.ext } ?: emptyList()
                        } catch (e: Exception) {
                            Log.e("BrowserViewModel", "YoutubeDL parsing failed, will try fallback", e)
                        }

                        // Fallback to Aniyomi-style OkHttp parsing if it's an m3u8 link and YoutubeDL failed
                        if (formats.isEmpty() && url.contains(".m3u8")) {
                            try {
                                formats = M3u8Parser.parse(url, headers)
                            } catch (e: Exception) {
                                Log.e("BrowserViewModel", "M3u8 fallback parsing failed", e)
                            }
                        }
                        
                        formats
                    } ?: emptyList() // If timeout happens, it returns null, we make it emptyList

                    // Direct Link Fallback (If yt-dlp failed or timed out)
                    if (parsedFormats.isEmpty()) {
                        if (url.contains(".mp4") || url.contains(".m3u8")) {
                            Log.d("BrowserViewModel", "Direct link detected, falling back to direct download")
                            parsedFormats = listOf(
                                VideoFormatItem(
                                    formatId = "direct",
                                    resolution = "Direct Stream",
                                    ext = if (url.contains(".m3u8")) "m3u8" else "mp4",
                                    fileSizeStr = "~",
                                    fps = null,
                                    isAudioOnly = false
                                )
                            )
                        } else {
                            throw Exception("Failed to parse video formats (Timeout or Unsupported).")
                        }
                    }

                    _videoFormats.value = parsedFormats
                }
            } catch (e: Exception) {
                Log.e("BrowserViewModel", "Error parsing url: $url", e)
                _parseError.value = e.message ?: "Unknown error occurred"
            } finally {
                _isParsing.value = false
            }
        }
    }

    fun startDownload(workManager: WorkManager, url: String, formatId: String, title: String, headers: Map<String, String>) {
        viewModelScope.launch(Dispatchers.IO) {
            val downloadId = UUID.randomUUID().toString()
            
            // Insert PENDING status into Room immediately
            val entity = DownloadEntity(
                id = downloadId,
                url = url,
                title = title,
                formatId = formatId,
                status = "PENDING"
            )
            downloadDao.insertDownload(entity)

            // Enqueue Worker
            val inputData = Data.Builder()
                .putString("id", downloadId)
                .putString("url", url)
                .putString("formatId", formatId)
                .putString("title", title)
                .putStringArray("headerKeys", headers.keys.toTypedArray())
                .putStringArray("headerValues", headers.values.toTypedArray())
                .build()

            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(inputData)
                .build()

            workManager.enqueue(request)
        }
    }
}
