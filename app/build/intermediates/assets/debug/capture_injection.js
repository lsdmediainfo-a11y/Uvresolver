(function() {
    if (window.__CaptureInjected) return;
    window.__CaptureInjected = true;

    function reportMedia(url, type, headers = {}, method = "GET", source = "unknown") {
        if (!url || !url.startsWith("http")) return;
        var isMedia = url.includes(".m3u8") || url.includes(".mp4") || url.includes(".ts") || 
                      url.includes(".m4s") || url.includes(".mpd") || url.includes(".m4a");
        
        if (isMedia || type === "video" || type === "audio" || source === "blob") {
            var payload = {
                url: url,
                type: type,
                method: method,
                source: source,
                headers: JSON.stringify(headers)
            };
            if (window.AndroidBridge) {
                window.AndroidBridge.onMediaDiscovered(JSON.stringify(payload));
            } else {
                console.log("Captured Media: " + JSON.stringify(payload));
            }
        }
    }

    // 1. Fetch Hook
    var originalFetch = window.fetch;
    window.fetch = async function() {
        var args = arguments;
        var url = typeof args[0] === 'string' ? args[0] : (args[0] && args[0].url ? args[0].url : "");
        var headers = (args[1] && args[1].headers) ? args[1].headers : {};
        reportMedia(url, "fetch", headers, (args[1] && args[1].method) ? args[1].method : "GET", "fetch");
        return originalFetch.apply(this, args);
    };

    // 2. XHR Hook
    var originalXhrOpen = XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open = function(method, url) {
        this.__captureUrl = url;
        this.__captureMethod = method;
        reportMedia(url, "xhr", {}, method, "xhr");
        return originalXhrOpen.apply(this, arguments);
    };

    // 3. PerformanceObserver for Resource Timings
    if (typeof PerformanceObserver !== 'undefined') {
        var observer = new PerformanceObserver(function(list) {
            list.getEntries().forEach(function(entry) {
                if (entry.initiatorType === "fetch" || entry.initiatorType === "xmlhttprequest" || entry.initiatorType === "video") {
                    reportMedia(entry.name, entry.initiatorType, {}, "GET", "performance");
                }
            });
        });
        observer.observe({entryTypes: ["resource"]});
    }

    // 4. URL.createObjectURL Hook (Blob)
    var originalCreateObjectURL = URL.createObjectURL;
    URL.createObjectURL = function(obj) {
        var url = originalCreateObjectURL(obj);
        reportMedia(url, "blob", {}, "GET", "blob");
        return url;
    };

    // 5. DOM MutationObserver (Looking for <video> and <source>)
    var mutationObserver = new MutationObserver(function(mutations) {
        mutations.forEach(function(mutation) {
            mutation.addedNodes.forEach(function(node) {
                if (node.nodeName === "VIDEO" || node.nodeName === "AUDIO") {
                    if (node.src) reportMedia(node.src, "video_tag", {}, "GET", "dom_mutation");
                    node.addEventListener('play', function() {
                        reportMedia(node.currentSrc || node.src, "video_tag", {}, "GET", "play_event");
                    });
                }
                if (node.nodeName === "SOURCE" && node.parentNode && (node.parentNode.nodeName === "VIDEO" || node.parentNode.nodeName === "AUDIO")) {
                    if (node.src) reportMedia(node.src, "source_tag", {}, "GET", "dom_mutation");
                }
            });
        });
    });
    mutationObserver.observe(document, { childList: true, subtree: true });

    // Ensure existing videos are captured
    document.querySelectorAll('video').forEach(function(v) {
        if (v.src) reportMedia(v.src, "video_tag", {}, "GET", "dom_scan");
        v.addEventListener('play', function() {
            reportMedia(v.currentSrc || v.src, "video_tag", {}, "GET", "play_event");
        });
    });
})();
