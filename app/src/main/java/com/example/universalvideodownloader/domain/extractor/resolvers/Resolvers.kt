package com.example.universalvideodownloader.domain.extractor.resolvers

import com.example.universalvideodownloader.domain.extractor.*

// 1. VideoElementResolver
class VideoElementResolver : MediaResolver {
    override val id = "VideoElementResolver"
    override val priority = 100
    override suspend fun resolve(context: ResolveContext): ResolverOutcome = ResolverOutcome.NotMatched
}

// 2. DirectFileResolver
class DirectFileResolver(private val contentVerifier: ContentVerifier) : MediaResolver {
    override val id = "DirectFileResolver"
    override val priority = 90
    
    override suspend fun resolve(context: ResolveContext): ResolverOutcome {
        val url = context.event.url
        val lowerUrl = url.lowercase()
        val isDirectExtension = lowerUrl.endsWith(".mp4") || lowerUrl.endsWith(".webm") || lowerUrl.endsWith(".mkv") || 
                                lowerUrl.contains(".mp4?") || lowerUrl.contains(".webm?")
        
        val candidate = MediaCandidate(
            id = url.hashCode().toString(),
            url = url,
            title = "Direct Video",
            mimeType = if (lowerUrl.contains(".webm")) "video/webm" else "video/mp4",
            extension = if (lowerUrl.contains(".webm")) "webm" else "mp4",
            mediaType = MediaType.DIRECT_FILE,
            requestContext = MediaRequestContext(
                mediaUrl = url,
                pageUrl = context.session?.pageUrl ?: "",
                frameUrl = null,
                userAgent = "Mozilla/5.0", // Fallback
                cookie = null,
                referer = context.session?.pageUrl,
                origin = null,
                authorization = null,
                extraHeaders = emptyMap(),
                capturedAt = System.currentTimeMillis()
            ),
            detectedAt = System.currentTimeMillis(),
            evidence = listOf()
        )

        if (isDirectExtension) {
            return ResolverOutcome.Resolved(candidate.copy(evidence = listOf(MediaEvidence.EXTENSION_MATCH)))
        }
        
        val verifiedType = contentVerifier.verify(candidate)
        if (verifiedType == MediaType.DIRECT_FILE) {
            return ResolverOutcome.Resolved(candidate.copy(evidence = listOf(MediaEvidence.BYTE_SIGNATURE_MATCH)))
        }
        
        return ResolverOutcome.NotMatched
    }
}

// 3. HlsManifestResolver
class HlsManifestResolver(
    private val hlsParser: com.example.universalvideodownloader.domain.extractor.parsers.HlsParser
) : MediaResolver {
    override val id = "HlsManifestResolver"
    override val priority = 80
    
    override suspend fun resolve(context: ResolveContext): ResolverOutcome = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val url = context.event.url
        val isM3u8 = url.contains(".m3u8")
        
        if (!isM3u8) return@withContext ResolverOutcome.NotMatched
        
        try {
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            
            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val content = connection.inputStream.bufferedReader().use { it.readText() }
                
                try {
                    // Try parsing to verify it's valid and DRM free
                    hlsParser.parseMasterPlaylist(url, content)
                    
                    val candidate = MediaCandidate(
                        id = url.hashCode().toString(),
                        url = url,
                        title = "HLS Stream",
                        mimeType = "application/vnd.apple.mpegurl",
                        extension = "m3u8",
                        mediaType = MediaType.HLS_MASTER,
                        requestContext = MediaRequestContext(
                            mediaUrl = url,
                            pageUrl = context.session?.pageUrl ?: "",
                            frameUrl = null,
                            userAgent = "Mozilla/5.0",
                            cookie = null,
                            referer = context.session?.pageUrl,
                            origin = null,
                            authorization = null,
                            extraHeaders = emptyMap(),
                            capturedAt = System.currentTimeMillis()
                        ),
                        detectedAt = System.currentTimeMillis(),
                        evidence = listOf(MediaEvidence.EXTENSION_MATCH, MediaEvidence.MANIFEST_PARSED)
                    )
                    
                    return@withContext ResolverOutcome.Resolved(candidate)
                    
                } catch (e: Exception) {
                    return@withContext ResolverOutcome.Failed(e.message ?: "Parsing error")
                }
            }
        } catch (e: Exception) {
            return@withContext ResolverOutcome.Failed(e.message ?: "Network error")
        }
        
        ResolverOutcome.NotMatched
    }
}

// 4. DashManifestResolver
class DashManifestResolver(
    private val dashParser: com.example.universalvideodownloader.domain.extractor.parsers.DashParser
) : MediaResolver {
    override val id = "DashManifestResolver"
    override val priority = 70
    
    override suspend fun resolve(context: ResolveContext): ResolverOutcome = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val url = context.event.url
        val isMpd = url.contains(".mpd")
        
        if (!isMpd) return@withContext ResolverOutcome.NotMatched
        
        try {
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            
            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val content = connection.inputStream.bufferedReader().use { it.readText() }
                
                try {
                    val representations = dashParser.parseMpd(content)
                    
                    if (representations.isEmpty()) {
                        return@withContext ResolverOutcome.Failed("No valid DASH representations found")
                    }
                    
                    val candidate = MediaCandidate(
                        id = url.hashCode().toString(),
                        url = url,
                        title = "DASH Stream",
                        mimeType = "application/dash+xml",
                        extension = "mpd",
                        mediaType = MediaType.DASH_MANIFEST,
                        requestContext = MediaRequestContext(
                            mediaUrl = url,
                            pageUrl = context.session?.pageUrl ?: "",
                            frameUrl = null,
                            userAgent = "Mozilla/5.0",
                            cookie = null,
                            referer = context.session?.pageUrl,
                            origin = null,
                            authorization = null,
                            extraHeaders = emptyMap(),
                            capturedAt = System.currentTimeMillis()
                        ),
                        detectedAt = System.currentTimeMillis(),
                        evidence = listOf(MediaEvidence.EXTENSION_MATCH, MediaEvidence.MANIFEST_PARSED)
                    )
                    
                    return@withContext ResolverOutcome.Resolved(candidate)
                    
                } catch (e: Exception) {
                    return@withContext ResolverOutcome.Failed(e.message ?: "Parsing error")
                }
            }
        } catch (e: Exception) {
            return@withContext ResolverOutcome.Failed(e.message ?: "Network error")
        }
        
        ResolverOutcome.NotMatched
    }
}

// 5. ContentSignatureResolver
class ContentSignatureResolver : MediaResolver {
    override val id = "ContentSignatureResolver"
    override val priority = 60
    override suspend fun resolve(context: ResolveContext): ResolverOutcome = ResolverOutcome.NotMatched
}

// 6. NetworkPatternResolver
class NetworkPatternResolver : MediaResolver {
    override val id = "NetworkPatternResolver"
    override val priority = 50
    override suspend fun resolve(context: ResolveContext): ResolverOutcome = ResolverOutcome.NotMatched
}

// 7. IframeContextResolver
class IframeContextResolver : MediaResolver {
    override val id = "IframeContextResolver"
    override val priority = 40
    override suspend fun resolve(context: ResolveContext): ResolverOutcome = ResolverOutcome.NotMatched
}

// 8. JsonApiResolver
class JsonApiResolver : MediaResolver {
    override val id = "JsonApiResolver"
    override val priority = 30
    override suspend fun resolve(context: ResolveContext): ResolverOutcome = ResolverOutcome.NotMatched
}


