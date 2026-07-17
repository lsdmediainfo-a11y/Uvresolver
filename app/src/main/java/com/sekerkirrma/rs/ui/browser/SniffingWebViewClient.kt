package com.sekerkirrma.rs.ui.browser

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.sekerkirrma.rs.domain.sniffer.AdBlocker
import com.sekerkirrma.rs.domain.sniffer.UniversalInterceptor

class SniffingWebViewClient(
    private val onVideoDetected: (String, Map<String, String>) -> Unit
) : WebViewClient() {

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        val url = request?.url?.toString()
        if (url != null) {
            // Block ads to prevent popups and false stream detections
            if (AdBlocker.isAd(url)) {
                return AdBlocker.createEmptyResource()
            }

            // Check if the URL corresponds to a video stream
            val headers = request.requestHeaders ?: emptyMap()
            if (UniversalInterceptor.isMediaRequest(url, headers)) {
                onVideoDetected(url, headers)
            }
        }
        return super.shouldInterceptRequest(view, request)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        view?.evaluateJavascript(UniversalInterceptor.getInjectorPayload(), null)
    }
}
