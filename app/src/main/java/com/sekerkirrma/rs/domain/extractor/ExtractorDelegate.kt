package com.sekerkirrma.rs.domain.extractor

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Acts as a communication bridge between the VideoInterceptor and the UI/Download layers.
 * It ONLY passes the URL and does NOT handle any download logic.
 */
object ExtractorDelegate {
    
    data class MediaDetectedSignal(
        val url: String, 
        val pageUrl: String,
        val headers: Map<String, String>,
        val cookies: String
    )

    private val _mediaFlow = MutableSharedFlow<MediaDetectedSignal>(extraBufferCapacity = 1)
    val mediaFlow: SharedFlow<MediaDetectedSignal> = _mediaFlow.asSharedFlow()

    fun onMediaDetected(url: String, pageUrl: String, headers: Map<String, String> = emptyMap(), cookies: String = "") {
        _mediaFlow.tryEmit(MediaDetectedSignal(url, pageUrl, headers, cookies))
    }
}
