package com.sekerkirrma.rs.domain.sniffer

import com.sekerkirrma.rs.domain.model.VideoFormatItem
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object M3u8Parser {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun parse(url: String, headers: Map<String, String>): List<VideoFormatItem> {
        val requestBuilder = Request.Builder().url(url)
        headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
        
        val request = requestBuilder.build()
        val response = client.newCall(request).execute()
        
        if (!response.isSuccessful) {
            throw Exception("Failed to fetch m3u8: ${response.code}")
        }
        
        val body = response.body?.string() ?: throw Exception("Empty body")
        val formats = mutableListOf<VideoFormatItem>()
        
        val lines = body.split("\n")
        var currentResolution = "Unknown"
        
        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                // Example: #EXT-X-STREAM-INF:BANDWIDTH=1200000,RESOLUTION=1280x720
                val resMatch = Regex("RESOLUTION=(\\d+x\\d+)").find(line)
                if (resMatch != null) {
                    currentResolution = resMatch.groupValues[1]
                } else {
                    currentResolution = "Unknown"
                }
            } else if (!line.startsWith("#") && line.isNotEmpty()) {
                // This is a stream URL. It could be relative or absolute.
                formats.add(
                    VideoFormatItem(
                        formatId = "m3u8_${currentResolution}", // Unique ID
                        resolution = currentResolution,
                        ext = "mp4", // usually we mux to mp4
                        fileSizeStr = "~", // Hard to know from m3u8
                        fps = null,
                        isAudioOnly = false
                    )
                )
                currentResolution = "Unknown"
            }
        }
        
        // Distinct resolutions
        return formats.distinctBy { it.resolution }
    }
}
