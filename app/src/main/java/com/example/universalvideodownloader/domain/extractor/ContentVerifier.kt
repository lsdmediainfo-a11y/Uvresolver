package com.example.universalvideodownloader.domain.extractor

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class ContentVerifier {
    
    suspend fun verify(candidate: MediaCandidate): MediaType = withContext(Dispatchers.IO) {
        try {
            val url = URL(candidate.url)
            var connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            
            candidate.requestContext.extraHeaders.forEach { (key, value) -> connection.setRequestProperty(key, value) }
            
            var responseCode = connection.responseCode
            var contentType = connection.contentType
            
            // Eğer HEAD desteklenmiyorsa (örn: 405 Method Not Allowed) veya detay yetersizse Range kullanarak GET yap
            if (responseCode !in 200..299 || contentType == null) {
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Range", "bytes=0-4095")
                candidate.requestContext.extraHeaders.forEach { (key, value) -> connection.setRequestProperty(key, value) }
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                
                responseCode = connection.responseCode
                contentType = connection.contentType
                
                if (responseCode in 200..299) {
                    val inputStream = connection.inputStream
                    val bytes = ByteArray(4096)
                    val readLength = inputStream.read(bytes)
                    inputStream.close()
                    
                    if (readLength > 0) {
                        return@withContext detectFromSignature(bytes, readLength)
                    }
                }
            } else {
                when {
                    contentType.contains("application/vnd.apple.mpegurl") -> return@withContext MediaType.HLS_MASTER
                    contentType.contains("application/dash+xml") -> return@withContext MediaType.DASH_MANIFEST
                    contentType.contains("video/mp4") -> return@withContext MediaType.DIRECT_FILE
                    contentType.contains("video/webm") -> return@withContext MediaType.DIRECT_FILE
                }
            }
        } catch (e: Exception) {
            Log.e("ContentVerifier", "Doğrulama başarısız: ${candidate.url}", e)
        }
        
        MediaType.UNKNOWN
    }
    
    private fun detectFromSignature(bytes: ByteArray, length: Int): MediaType {
        val contentStr = String(bytes, 0, length.coerceAtMost(512), Charsets.UTF_8)
        
        return when {
            contentStr.contains("#EXTM3U") || contentStr.contains("#EXT-X-STREAM-INF") || contentStr.contains("#EXTINF") -> MediaType.HLS_MASTER
            contentStr.contains("<MPD") || contentStr.contains("<Period") || contentStr.contains("<AdaptationSet") -> MediaType.DASH_MANIFEST
            containsMp4Signature(bytes, length) -> MediaType.DIRECT_FILE
            containsWebmSignature(bytes, length) -> MediaType.DIRECT_FILE
            isMpegTs(bytes, length) -> MediaType.MEDIA_SEGMENT
            else -> MediaType.UNKNOWN
        }
    }
    
    private fun containsMp4Signature(bytes: ByteArray, length: Int): Boolean {
        if (length < 8) return false
        val str = String(bytes, 0, length.coerceAtMost(1024), Charsets.US_ASCII)
        return str.contains("ftyp") || str.contains("moov") || str.contains("mdat")
    }
    
    private fun containsWebmSignature(bytes: ByteArray, length: Int): Boolean {
        if (length < 4) return false
        // EBML header: 0x1A 0x45 0xDF 0xA3
        return bytes[0] == 0x1A.toByte() && bytes[1] == 0x45.toByte() && 
               bytes[2] == 0xDF.toByte() && bytes[3] == 0xA3.toByte()
    }
    
    private fun isMpegTs(bytes: ByteArray, length: Int): Boolean {
        if (length < 188) return false
        // Sync byte 0x47, kontrol amaçlı ilk ve 188. byte'ı kontrol ediyoruz.
        return bytes[0] == 0x47.toByte() && bytes[188] == 0x47.toByte()
    }
}
