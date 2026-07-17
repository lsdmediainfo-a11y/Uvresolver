package com.sekerkirrma.rs.ui.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sekerkirrma.rs.domain.model.VideoFormatItem
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

    fun onVideoDetected(url: String) {
        // We might catch many parts of an m3u8. We can keep the first one or a specific master playlist.
        // For now, if we already caught one, we might keep it or update it. Let's just update.
        if (_detectedVideoUrl.value == null || url.contains(".m3u8")) {
            _detectedVideoUrl.value = url
        }
    }

    fun clearDetectedVideo() {
        _detectedVideoUrl.value = null
        _videoFormats.value = emptyList()
        _parseError.value = null
    }

    fun parseVideoUrl(url: String) {
        viewModelScope.launch {
            _isParsing.value = true
            _parseError.value = null
            _videoFormats.value = emptyList()

            try {
                withContext(Dispatchers.IO) {
                    val request = YoutubeDLRequest(url)
                    val info = YoutubeDL.getInstance().getInfo(request)
                    
                    val parsedFormats = info.formats?.mapNotNull { format ->
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
                            fps = format.fps.takeIf { it > 0.0 },
                            isAudioOnly = isAudioOnly
                        )
                    }?.distinctBy { it.resolution + it.ext } ?: emptyList()

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
