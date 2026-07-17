package com.sekerkirrma.rs.domain.extractor

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.util.Log
import com.sekerkirrma.rs.domain.sniffer.AdBlocker

/**
 * Pure Tachiyomi-style Network Interceptor.
 * ONLY analyzes network requests. NO HTML scraping, NO DOM evaluation.
 */
class VideoInterceptor : WebViewClient() {

    private val MEDIA_REGEX = Regex("(?i).*(?:\\.m3u8|\\.mpd|\\.mp4)(?:\\?.*)?$")
    
    // Ignore images, fonts, css
    private val IGNORED_EXTENSIONS = listOf(".jpg", ".jpeg", ".png", ".gif", ".webp", ".css", ".woff", ".woff2", ".ttf")

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        val url = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)

        // Block known ad trackers to clean up network traffic
        if (AdBlocker.isAd(url)) {
            return AdBlocker.createEmptyResource()
        }

        val lowerUrl = url.lowercase()
        
        // Fast fail for common non-media assets
        if (IGNORED_EXTENSIONS.any { lowerUrl.contains(it) }) {
            return super.shouldInterceptRequest(view, request)
        }

        // Pure Network Sniffing based on Regex
        if (MEDIA_REGEX.matches(lowerUrl) || lowerUrl.contains(".m3u8") || lowerUrl.contains(".mp4") || lowerUrl.contains(".mpd")) {
            Log.d("AuraSniffer", "Yakalalanan Medya: $url")
            val headers = request.requestHeaders ?: emptyMap()
            // Fire and forget
            ExtractorDelegate.onMediaDetected(url, headers)
        }

        return super.shouldInterceptRequest(view, request)
    }
}
