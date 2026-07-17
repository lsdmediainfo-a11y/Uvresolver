package com.example.universalvideodownloader.data.download

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.FileOutputStream
import okhttp3.OkHttpClient
import okhttp3.Request
import org.chromium.net.CronetEngine
import org.chromium.net.UrlRequest
import org.chromium.net.UrlResponseInfo
import org.chromium.net.CronetException
import java.nio.ByteBuffer
import java.nio.channels.Channels
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@HiltWorker
class VideoDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val cronetEngine: CronetEngine,
    private val okHttpClient: OkHttpClient
) : CoroutineWorker(appContext, workerParams) {

    // Bölüm 18: İlk sürümde OkHttp kullanılacak. İleride Cronet'e (fallback OkHttp) geçirilecek.
    private val client = OkHttpClient.Builder().build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // WorkManager 10KB Data Limit Koruması: Büyük JSON'lar yerine Database'den SessionID ile Context okunur
        val candidateId = inputData.getString("CANDIDATE_ID") ?: return@withContext Result.failure()
        val videoUrl = inputData.getString("VIDEO_URL") ?: return@withContext Result.failure()
        val type = inputData.getString("TYPE") ?: "MP4"
        
        Log.d("DownloadWorker", "OkHttp İndirme Başlıyor: $videoUrl")
        
        try {
            val baseFileName = "video_${System.currentTimeMillis()}"
            val partFileName = "$baseFileName.${if(type == "HLS") "ts" else "mp4"}.part"
            val finalFileName = "$baseFileName.${if(type == "HLS") "ts" else "mp4"}"
            val outputFile = File(applicationContext.getExternalFilesDir(null), partFileName)
            val finalFile = File(applicationContext.getExternalFilesDir(null), finalFileName)
            
            val headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64 AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36)",
                "Referer" to videoUrl
            )
            
            if (type == "HLS") {
                Log.d("DownloadWorker", "HLS İndirme Başlıyor (TS Birleştirme): $videoUrl")
                downloadHls(videoUrl, outputFile, headers)
            } else if (type == "DASH") {
                Log.d("DownloadWorker", "DASH henüz tam desteklenmiyor, atlanıyor.")
                return@withContext Result.failure()
            } else {
            
            try {
                Log.d("DownloadWorker", "Cronet (HTTP/3) ile indiriliyor: $videoUrl")
                downloadWithCronet(cronetEngine, videoUrl, outputFile, headers)
            } catch (e: Exception) {
                Log.e("DownloadWorker", "Cronet hatası, OkHttp fallback devreye giriyor...", e)
                downloadWithOkHttp(videoUrl, outputFile, headers)
            }
            
            } // End of else block
            
            if (outputFile.exists()) {
                outputFile.renameTo(finalFile)
            }
            
            Log.d("DownloadWorker", "İndirme Tamamlandı: ${finalFile.absolutePath}")
            Result.success()
        } catch (e: Exception) {
            Log.e("DownloadWorker", "Bağlantı kesintisi veya hata", e)
            Result.retry()
        }
    }

    private fun downloadHls(playlistUrl: String, outputFile: File, headers: Map<String, String>) {
        val requestBuilder = Request.Builder().url(playlistUrl)
        headers.forEach { (k, v) -> requestBuilder.header(k, v) }
        val response = okHttpClient.newCall(requestBuilder.build()).execute()
        
        if (!response.isSuccessful) throw Exception("HLS Playlist fetch failed")
        
        val body = response.body?.string() ?: return
        val lines = body.lines()
        
        FileOutputStream(outputFile, true).use { output ->
            for (line in lines) {
                if (line.trim().isEmpty() || line.startsWith("#")) continue
                
                val tsUrl = if (line.startsWith("http")) line else java.net.URL(java.net.URL(playlistUrl), line).toString()
                
                var retries = 0
                var success = false
                while (retries < 3 && !success) {
                    try {
                        val tsReq = Request.Builder().url(tsUrl)
                        headers.forEach { (k, v) -> tsReq.header(k, v) }
                        val tsResp = okHttpClient.newCall(tsReq.build()).execute()
                        if (tsResp.isSuccessful) {
                            tsResp.body?.byteStream()?.use { input ->
                                input.copyTo(output)
                            }
                            success = true
                        } else {
                            throw Exception("TS fetch failed: ${tsResp.code}")
                        }
                    } catch (e: Exception) {
                        retries++
                        Thread.sleep(1000)
                        if (retries >= 3) throw Exception("TS piece failed after 3 retries: $tsUrl", e)
                    }
                }
            }
        }
    }

    private suspend fun downloadWithCronet(engine: CronetEngine, url: String, outputFile: File, headers: Map<String, String>) = suspendCancellableCoroutine<Boolean> { continuation ->
        val outputStream = FileOutputStream(outputFile, true)
        val channel = Channels.newChannel(outputStream)
        
        val callback = object : UrlRequest.Callback() {
            override fun onRedirectReceived(request: UrlRequest, info: UrlResponseInfo, newLocationUrl: String) {
                request.followRedirect()
            }

            override fun onResponseStarted(request: UrlRequest, info: UrlResponseInfo) {
                if (info.httpStatusCode in 200..299) {
                    request.read(ByteBuffer.allocateDirect(32768))
                } else {
                    request.cancel()
                    continuation.resumeWithException(Exception("HTTP Error: ${info.httpStatusCode}"))
                }
            }

            override fun onReadCompleted(request: UrlRequest, info: UrlResponseInfo, byteBuffer: ByteBuffer) {
                byteBuffer.flip()
                channel.write(byteBuffer)
                byteBuffer.clear()
                request.read(byteBuffer)
            }

            override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) {
                channel.close()
                outputStream.close()
                continuation.resume(true)
            }

            override fun onFailed(request: UrlRequest, info: UrlResponseInfo, error: CronetException) {
                channel.close()
                outputStream.close()
                continuation.resumeWithException(error)
            }
            
            override fun onCanceled(request: UrlRequest, info: UrlResponseInfo) {
                channel.close()
                outputStream.close()
                continuation.resume(false)
            }
        }

        val requestBuilder = engine.newUrlRequestBuilder(url, callback, java.util.concurrent.Executors.newSingleThreadExecutor())
        headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
        
        val request = requestBuilder.build()
        continuation.invokeOnCancellation { request.cancel() }
        request.start()
    }
    
    private fun downloadWithOkHttp(url: String, outputFile: File, headers: Map<String, String>) {
        val requestBuilder = Request.Builder().url(url)
        headers.forEach { (k, v) -> requestBuilder.header(k, v) }
        val request = requestBuilder.build()
        val response = okHttpClient.newCall(request).execute()
        if (response.isSuccessful) {
            response.body?.byteStream()?.use { input ->
                FileOutputStream(outputFile, true).use { output ->
                    input.copyTo(output)
                }
            }
        } else {
            throw Exception("OkHttp Error: ${response.code}")
        }
    }
}
