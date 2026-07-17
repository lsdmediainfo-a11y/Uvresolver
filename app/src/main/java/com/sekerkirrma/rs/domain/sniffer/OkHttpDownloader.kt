package com.sekerkirrma.rs.domain.sniffer

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

object OkHttpDownloader {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun downloadFile(
        url: String,
        headers: Map<String, String>,
        outputPath: String,
        onProgress: (Float) -> Unit
    ) {
        val requestBuilder = Request.Builder().url(url)
        headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
        
        val request = requestBuilder.build()
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            throw Exception("Failed to download file: ${response.code}")
        }

        val body = response.body ?: throw Exception("Empty body")
        val contentLength = body.contentLength()
        val inputStream: InputStream = body.byteStream()
        val file = File(outputPath)
        
        if (file.exists()) {
            file.delete()
        }

        FileOutputStream(file).use { outputStream ->
            val buffer = ByteArray(8 * 1024)
            var bytesCopied: Long = 0
            var bytesRead: Int
            var lastProgressReport: Long = 0

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                bytesCopied += bytesRead

                if (contentLength > 0) {
                    val currentTime = System.currentTimeMillis()
                    // Report progress every 500ms
                    if (currentTime - lastProgressReport > 500) {
                        val progress = (bytesCopied.toFloat() / contentLength.toFloat()) * 100f
                        onProgress(progress)
                        lastProgressReport = currentTime
                    }
                }
            }
            outputStream.flush()
        }
        
        Log.d("OkHttpDownloader", "Download finished successfully to $outputPath")
    }
}
