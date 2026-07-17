package com.example.universalvideodownloader.ui.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CustomWebView(
    url: String,
    onPageStarted: (String) -> Unit,
    onPageFinished: (String) -> Unit,
    onProgressChanged: (Int) -> Unit,
    onNavigationStateChanged: (canGoBack: Boolean, canGoForward: Boolean) -> Unit,
    onEventCaptured: (com.example.universalvideodownloader.ui.browser.capture.CapturedNetworkEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    setSupportMultipleWindows(true)
                    javaScriptCanOpenWindowsAutomatically = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
                
                // Cookie Management
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)

                // Capture Bridge
                addJavascriptInterface(com.example.universalvideodownloader.ui.browser.capture.CaptureBridge(onEventCaptured), "AndroidBridge")

                val adBlockEngine = com.example.universalvideodownloader.domain.extractor.AdBlockEngine()
                val siteProfileManager = com.example.universalvideodownloader.domain.extractor.site.SiteProfileManager()

                webViewClient = object : WebViewClient() {
                    
                    private fun injectCaptureScript(view: WebView, currentUrl: String?) {
                        try {
                            val inputStream = context.assets.open("capture_injection.js")
                            var jsCode = inputStream.bufferedReader().use { it.readText() }
                            
                            // Site özel JS Enjeksiyonu
                            if (currentUrl != null) {
                                val profile = siteProfileManager.getProfileForUrl(currentUrl)
                                if (profile?.customJsInjection != null) {
                                    jsCode += "\n" + profile.customJsInjection
                                }
                            }
                            
                            view.evaluateJavascript(jsCode, null)
                        } catch (e: Exception) {
                            Log.e("CustomWebView", "Failed to inject capture script", e)
                        }
                    }

                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest
                    ): WebResourceResponse? {
                        val url = request.url.toString()
                        
                        if (adBlockEngine.shouldBlockResource(url)) {
                            Log.d("AdBlockEngine", "Reklam engellendi: $url")
                            return WebResourceResponse("text/plain", "UTF-8", java.io.ByteArrayInputStream(ByteArray(0)))
                        }
                        
                        val isMedia = url.contains(".m3u8") || url.contains(".mp4") || 
                                      url.contains(".ts") || url.contains(".m4s") || 
                                      url.contains(".mpd")
                                      
                        if (isMedia) {
                            Log.d("CustomWebView", "NATIVE_INTERCEPT -> Discovered Media URL: $url")
                            val hdrs = request.requestHeaders
                            val jsonHeaders = org.json.JSONObject()
                            hdrs?.forEach { (k, v) -> jsonHeaders.put(k, v) }
                            
                            val event = com.example.universalvideodownloader.ui.browser.capture.CapturedNetworkEvent(
                                url = url,
                                type = "media",
                                method = request.method,
                                headers = jsonHeaders.toString(),
                                source = "native_intercept"
                            )
                            onEventCaptured(event)
                        }
                        
                        // Sadece gözlem yapar, daima null döndürür
                        return null
                    }

                    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        injectCaptureScript(view, url)
                        onPageStarted(url)
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        super.onPageFinished(view, url)
                        injectCaptureScript(view, url)
                        onPageFinished(url)
                        onNavigationStateChanged(view.canGoBack(), view.canGoForward())
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onCreateWindow(
                        view: WebView,
                        isDialog: Boolean,
                        isUserGesture: Boolean,
                        resultMsg: android.os.Message
                    ): Boolean {
                        if (adBlockEngine.shouldBlockPopup("unknown", isUserGesture)) {
                            Log.d("AdBlockEngine", "Popup engellendi (Kullanıcı etkileşimi: $isUserGesture)")
                            return false
                        }
                        
                        val newWebView = WebView(view.context)
                        val transport = resultMsg.obj as WebView.WebViewTransport
                        transport.webView = newWebView
                        resultMsg.sendToTarget()
                        return true
                    }

                    override fun onProgressChanged(view: WebView, newProgress: Int) {
                        super.onProgressChanged(view, newProgress)
                        onProgressChanged(newProgress)
                    }
                }
                
                loadUrl(url)
            }
        },
        update = { webView ->
            if (webView.url != url) {
                webView.loadUrl(url)
            }
        },
        modifier = modifier.fillMaxSize()
    )
}
