package com.sekerkirrma.rs.domain.sniffer

object UniversalInterceptor {

    private val VIDEO_EXTENSIONS = listOf(
        ".m3u8", ".mpd", ".mp4", ".ts", ".flv", ".mkv", ".webm"
    )

    private val EXCLUDE_EXTENSIONS = listOf(
        ".jpg", ".jpeg", ".png", ".gif", ".webp", ".css", ".ttf", ".woff", ".woff2", ".svg", ".ico"
    )

    private val PLAYER_TOKENS = listOf(
        "videojs", "jwplayer", "player.js", "cdn", "vtt", "blob"
    )

    private val VIDEO_REGEX = Regex("(?i).*(?:\\.m3u8|\\.mpd|\\.mp4|\\.webm|\\.mkv)(?:\\?.*)?$")
    private val TOKEN_REGEX = Regex("(?i)(?:token|signature|expire|policy)=[^&]+")

    fun isMediaRequest(url: String, headers: Map<String, String>? = null): Boolean {
        val lowerUrl = url.lowercase()

        // 1. Check exclusions to optimize performance
        if (EXCLUDE_EXTENSIONS.any { lowerUrl.contains(it) }) return false

        // 2. Exact match using strict extensions or REGEX
        if (VIDEO_EXTENSIONS.any { lowerUrl.contains(it) }) return true
        if (VIDEO_REGEX.matches(lowerUrl)) return true

        // 3. Check for specific Player Tokens or query params often used by streaming servers
        if (TOKEN_REGEX.containsMatchIn(lowerUrl) && PLAYER_TOKENS.any { lowerUrl.contains(it) }) {
            return true
        }

        // 4. Sometimes headers hint at video (e.g. Range requests for video buffering)
        // Though headers in shouldInterceptRequest are request headers, not response.
        if (headers?.containsKey("Range") == true) {
            // Might be a media fetch if it's not a common asset.
            // But we don't want to falsely flag every ranged request.
            if (!lowerUrl.contains(".js") && !lowerUrl.contains(".json")) {
                return true
            }
        }

        return false
    }

    /**
     * JS payload injected into every frame/iframe to hijack HTML5 <video> tags.
     */
    fun getInjectorPayload(): String {
        return """
            (function() {
                if (window.hasInjectedVideoSniffer) return;
                window.hasInjectedVideoSniffer = true;

                function interceptVideo() {
                    var videos = document.getElementsByTagName('video');
                    for (var i = 0; i < videos.length; i++) {
                        var v = videos[i];
                        if (v.src && v.src !== "") {
                            AndroidSniffer.onVideoFound(v.src);
                        }
                        var sources = v.getElementsByTagName('source');
                        for (var j = 0; j < sources.length; j++) {
                            if (sources[j].src) {
                                AndroidSniffer.onVideoFound(sources[j].src);
                            }
                        }
                    }
                }

                // Run immediately
                interceptVideo();

                // Observe for dynamically added video tags
                var observer = new MutationObserver(function(mutations) {
                    mutations.forEach(function(mutation) {
                        if (mutation.addedNodes) {
                            interceptVideo();
                        }
                    });
                });
                observer.observe(document.body, { childList: true, subtree: true });

                // Intercept fetch API to sniff m3u8 requests inside blob or JS
                var originalFetch = window.fetch;
                window.fetch = function() {
                    var url = arguments[0];
                    if (typeof url === 'string' && (url.indexOf('.m3u8') !== -1 || url.indexOf('.mp4') !== -1)) {
                        AndroidSniffer.onVideoFound(url);
                    }
                    return originalFetch.apply(this, arguments);
                };

            })();
        """.trimIndent()
    }
}
