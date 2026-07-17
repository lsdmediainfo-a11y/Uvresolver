package com.sekerkirrma.rs.domain.sniffer

object VideoSniffer {
    private val videoExtensions = listOf(".m3u8", ".mp4", ".ts", ".flv", ".mkv", ".webm")
    private val videoMimeTypes = listOf("application/x-mpegurl", "application/vnd.apple.mpegurl", "video/mp4", "video/mp2t", "video/webm")

    fun isVideoUrl(url: String, mimeType: String? = null): Boolean {
        // Exclude common non-video requests for performance and accuracy
        if (url.contains(".jpg") || url.contains(".png") || url.contains(".js") || url.contains(".css")) {
            return false
        }

        // Check if mimeType matches known video types
        if (mimeType != null && videoMimeTypes.any { mimeType.contains(it, ignoreCase = true) }) {
            return true
        }

        // Check URL extensions
        val lowerCaseUrl = url.lowercase()
        return videoExtensions.any { lowerCaseUrl.contains(it) }
    }
}
