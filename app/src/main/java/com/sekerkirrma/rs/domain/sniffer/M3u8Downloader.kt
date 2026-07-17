package com.sekerkirrma.rs.domain.sniffer

import android.content.Context
import android.util.Log
import io.microshow.rxffmpeg.RxFFmpegInvoke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

object M3u8Downloader {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun downloadM3u8(
        context: Context,
        playlistUrl: String,
        headers: Map<String, String>,
        outputPath: String,
        onProgress: (Float) -> Unit
    ) = withContext(Dispatchers.IO) {
        
        Log.d("M3u8Downloader", "Starting M3u8 Custom Downloader for: $playlistUrl")
        
        var targetPlaylistUrl = playlistUrl
        var manifestContent = fetchContent(targetPlaylistUrl, headers)
        
        // If master playlist, pick the first stream
        if (manifestContent.contains("#EXT-X-STREAM-INF")) {
            val lines = manifestContent.split("\n")
            var streamUrl = ""
            for (line in lines) {
                if (!line.startsWith("#") && line.trim().isNotEmpty()) {
                    streamUrl = line.trim()
                    break
                }
            }
            if (streamUrl.isNotEmpty()) {
                targetPlaylistUrl = resolveUrl(targetPlaylistUrl, streamUrl)
                Log.d("M3u8Downloader", "Resolved Master Playlist. Sub playlist: $targetPlaylistUrl")
                manifestContent = fetchContent(targetPlaylistUrl, headers)
            }
        }

        // Parse TS segments
        val lines = manifestContent.split("\n")
        val tsUrls = mutableListOf<String>()
        for (line in lines) {
            if (!line.startsWith("#") && line.trim().isNotEmpty()) {
                tsUrls.add(resolveUrl(targetPlaylistUrl, line.trim()))
            }
        }

        if (tsUrls.isEmpty()) {
            throw Exception("No TS segments found in playlist")
        }

        Log.d("M3u8Downloader", "Found ${tsUrls.size} TS segments")

        // Create Cache Directory
        val cacheDir = File(context.cacheDir, "m3u8_cache_${System.currentTimeMillis()}")
        cacheDir.mkdirs()

        val concatFile = File(cacheDir, "concat.txt")
        val concatWriter = concatFile.bufferedWriter()

        var downloadedChunks = 0
        val totalChunks = tsUrls.size

        // Download TS segments concurrently
        // We will download them in chunks to avoid OOM or too many threads
        val chunkSize = 5 // 5 concurrent downloads
        val tsFiles = mutableListOf<File>()

        for (i in tsUrls.indices step chunkSize) {
            val chunkUrls = tsUrls.subList(i, minOf(i + chunkSize, tsUrls.size))
            val deferreds = chunkUrls.mapIndexed { index, url ->
                val absoluteIndex = i + index
                async {
                    val tsFile = File(cacheDir, "chunk_$absoluteIndex.ts")
                    downloadFile(url, headers, tsFile)
                    tsFile
                }
            }
            
            // Wait for this batch
            val downloadedFiles = deferreds.awaitAll()
            
            for (file in downloadedFiles) {
                concatWriter.write("file '${file.absolutePath}'\n")
                tsFiles.add(file)
                downloadedChunks++
            }
            
            val progress = (downloadedChunks.toFloat() / totalChunks.toFloat()) * 90f // 90% for downloading
            onProgress(progress)
            Log.d("M3u8Downloader", "Downloaded $downloadedChunks / $totalChunks chunks")
        }

        concatWriter.close()

        // FFmpeg Muxing using RxFFmpeg
        Log.d("M3u8Downloader", "All TS segments downloaded. Starting FFmpeg muxing...")
        onProgress(95f)
        
        val command = "ffmpeg -y -f concat -safe 0 -i ${concatFile.absolutePath} -c copy $outputPath"
        val cmdArray = command.split(" ").toTypedArray()
        
        suspendCancellableCoroutine<Unit> { continuation ->
            RxFFmpegInvoke.getInstance().runCommand(cmdArray, object : RxFFmpegInvoke.IFFmpegListener {
                override fun onFinish() {
                    Log.d("M3u8Downloader", "FFmpeg muxing finished!")
                    // Cleanup
                    cacheDir.deleteRecursively()
                    onProgress(100f)
                    if (continuation.isActive) continuation.resume(Unit)
                }

                override fun onProgress(progress: Int, progressTime: Long) {
                    // Ignored since we don't have accurate % from FFmpeg without duration
                }

                override fun onCancel() {
                    cacheDir.deleteRecursively()
                    if (continuation.isActive) continuation.resumeWithException(Exception("FFmpeg cancelled"))
                }

                override fun onError(message: String?) {
                    cacheDir.deleteRecursively()
                    if (continuation.isActive) continuation.resumeWithException(Exception("FFmpeg error: $message"))
                }
            })
        }
    }

    private fun fetchContent(url: String, headers: Map<String, String>): String {
        val requestBuilder = Request.Builder().url(url)
        headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
        val response = client.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful) throw Exception("Failed to fetch playlist: $url")
        return response.body?.string() ?: throw Exception("Empty playlist body")
    }

    private fun resolveUrl(base: String, path: String): String {
        if (path.startsWith("http")) return path
        val basePath = base.substringBeforeLast("/")
        return if (path.startsWith("/")) {
            // Root relative
            val host = try {
                java.net.URL(base).let { "${it.protocol}://${it.host}" }
            } catch (e: Exception) {
                base.substring(0, base.indexOf("/", base.indexOf("://") + 3).takeIf { it > 0 } ?: base.length)
            }
            "$host$path"
        } else {
            "$basePath/$path"
        }
    }

    private fun downloadFile(url: String, headers: Map<String, String>, file: File) {
        val requestBuilder = Request.Builder().url(url)
        headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
        val response = client.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful) throw Exception("Failed to fetch TS chunk: $url")
        
        val inputStream = response.body?.byteStream() ?: throw Exception("Empty TS chunk body")
        FileOutputStream(file).use { output ->
            inputStream.copyTo(output)
        }
    }
}
