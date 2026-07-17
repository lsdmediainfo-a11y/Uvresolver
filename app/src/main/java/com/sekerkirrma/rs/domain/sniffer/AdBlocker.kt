package com.sekerkirrma.rs.domain.sniffer

import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

object AdBlocker {
    // A curated list of common ad and tracking domains often found on streaming sites
    private val adDomains = listOf(
        "googleads.g.doubleclick.net", "pagead2.googlesyndication.com", 
        "adserver", "popads.net", "popcash.net", "adsterra.com", "exoclick.com", 
        "propellerads.com", "onclickads.net", "adcash.com", "juicyads.com", 
        "trafficjunky.com", "ero-advertising.com", "adxpremium.com", 
        "adskeeper.com", "mgid.com", "outbrain.com", "taboola.com",
        "scorecardresearch.com", "tracking", "analytics", "syndication",
        "ad.directrev.com", "cpmstar.com", "adk2x.com", "bidvertiser.com",
        "hilltopads.com", "adplexity.com"
    )

    fun isAd(url: String): Boolean {
        val lowerUrl = url.lowercase()
        return adDomains.any { lowerUrl.contains(it) }
    }

    fun createEmptyResource(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain", 
            "UTF-8", 
            ByteArrayInputStream(ByteArray(0))
        )
    }
}
