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
import android.util.Log
import javax.inject.Inject

@HiltViewModel
class BrowserViewModel @Inject constructor() : ViewModel() {

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
                    val request = YoutubeDLRequest(url)
                    // Add headers so YoutubeDL can bypass referer checks
                    headers.forEach { (k, v) -> request.addOption("--add-header", "$k:$v") }
                    
                    var parsedFormats = emptyList<VideoFormatItem>()
                    try {
                        val info = YoutubeDL.getInstance().getInfo(request)
                        parsedFormats = info.formats?.mapNotNull { format ->
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
                                resolution = resolution!!,
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
                    if (parsedFormats.isEmpty() && url.contains(".m3u8")) {
                        try {
                            parsedFormats = M3u8Parser.parse(url, headers)
                        } catch (e: Exception) {
                            Log.e("BrowserViewModel", "M3u8 fallback parsing failed", e)
                            throw Exception("Failed to parse video formats with all extractors.")
                        }
                    }

                    if (parsedFormats.isEmpty()) {
                        throw Exception("No playable formats found.")
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
}
