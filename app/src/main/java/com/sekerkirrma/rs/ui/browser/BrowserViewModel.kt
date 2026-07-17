package com.sekerkirrma.rs.ui.browser

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class BrowserViewModel @Inject constructor() : ViewModel() {

    private val _currentUrl = MutableStateFlow("https://google.com")
    val currentUrl: StateFlow<String> = _currentUrl.asStateFlow()

    private val _detectedVideoUrl = MutableStateFlow<String?>(null)
    val detectedVideoUrl: StateFlow<String?> = _detectedVideoUrl.asStateFlow()

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
    }
}
