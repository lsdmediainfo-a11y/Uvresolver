package com.sekerkirrma.rs.ui.browser

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.sekerkirrma.rs.domain.sniffer.VideoSniffer

class SniffingWebViewClient(
    private val onVideoDetected: (String) -> Unit
) : WebViewClient() {

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        val url = request?.url?.toString()
        if (url != null) {
            // Check if the URL corresponds to a video stream
            if (VideoSniffer.isVideoUrl(url)) {
                onVideoDetected(url)
            }
        }
        return super.shouldInterceptRequest(view, request)
    }

    // You could also intercept based on the page finished loading,
    // or inject javascript to find <video> tags, but shouldInterceptRequest
    // is the most robust for catching network traffic.
}
