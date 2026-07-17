package com.sekerkirrma.rs.domain.extractor

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Acts as a communication bridge between the VideoInterceptor and the UI/Download layers.
 * It ONLY passes the URL and does NOT handle any download logic.
 */
object ExtractorDelegate {
    
    data class MediaDetectedSignal(val url: String, val headers: Map<String, String>)

    private val _mediaFlow = MutableSharedFlow<MediaDetectedSignal>(extraBufferCapacity = 1)
    val mediaFlow: SharedFlow<MediaDetectedSignal> = _mediaFlow.asSharedFlow()

    fun onMediaDetected(url: String, headers: Map<String, String> = emptyMap()) {
        _mediaFlow.tryEmit(MediaDetectedSignal(url, headers))
    }
}
