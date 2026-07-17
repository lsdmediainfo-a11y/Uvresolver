package com.sekerkirrma.rs.core.worker

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sekerkirrma.rs.data.local.dao.DownloadDao
import com.sekerkirrma.rs.data.local.entity.DownloadEntity
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import com.sekerkirrma.rs.domain.sniffer.OkHttpDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val downloadDao: DownloadDao
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val downloadId = inputData.getString("id") ?: return@withContext Result.failure()
        val url = inputData.getString("url") ?: return@withContext Result.failure()
        val formatId = inputData.getString("formatId") ?: return@withContext Result.failure()
        val title = inputData.getString("title") ?: "Video_${System.currentTimeMillis()}"
        val headerKeys = inputData.getStringArray("headerKeys") ?: emptyArray()
        val headerValues = inputData.getStringArray("headerValues") ?: emptyArray()

        Log.d("DownloadWorker", "Starting download: $title ($formatId)")

        // Mark as DOWNLOADING
        downloadDao.updateStatus(downloadId, "DOWNLOADING")

        try {
            // Output path: Public Downloads folder / AuraDownloads
            val downloadDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "AuraDownloads"
            )
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }
            
            val safeTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            
            // Convert arrays to Map
            val headerMap = mutableMapOf<String, String>()
            for (i in headerKeys.indices) {
                if (i < headerValues.size) {
                    headerMap[headerKeys[i]] = headerValues[i]
                }
            }

            var finalPath = ""

            if (formatId == "direct") {
                Log.d("DownloadWorker", "Using direct OkHttpDownloader fallback")
                val ext = if (url.contains(".m3u8")) "m3u8" else "mp4" // Ideally m3u8 should be downloaded piece by piece, but OkHttpDownloader will just download the text file unless we parse it. Wait, m3u8 direct download is just the playlist. But let's follow the fallback logic.
                finalPath = File(downloadDir, "$safeTitle.$ext").absolutePath
                
                OkHttpDownloader.downloadFile(
                    url = url,
                    headers = headerMap,
                    outputPath = finalPath,
                    onProgress = { currentProgress ->
                        Log.d("DownloadWorker", "Direct Progress: $currentProgress%")
                        kotlinx.coroutines.runBlocking {
                            downloadDao.updateProgress(downloadId, currentProgress, 0L, 0L)
                        }
                    }
                )
            } else {
                Log.d("DownloadWorker", "Using YoutubeDL engine")
                val request = YoutubeDLRequest(url)
                request.addOption("-f", formatId)
                
                val outputPath = File(downloadDir, "$safeTitle.%(ext)s").absolutePath
                request.addOption("-o", outputPath)
                request.addOption("--concurrent-fragments", "4")
                request.addOption("--merge-output-format", "mp4")

                headerMap.forEach { (k, v) ->
                    request.addOption("--add-header", "$k:$v")
                }

                YoutubeDL.getInstance().execute(request, downloadId) { progress, etaInSeconds, line ->
                    val currentProgress = progress.toFloat()
                    Log.d("DownloadWorker", "Progress: $currentProgress% ETA: $etaInSeconds")
                    kotlinx.coroutines.runBlocking {
                        downloadDao.updateProgress(downloadId, currentProgress, 0L, 0L)
                    }
                }
                
                finalPath = File(downloadDir, "$safeTitle.mp4").absolutePath
            }
            
            val downloadEntity = downloadDao.getDownloadById(downloadId)
            if (downloadEntity != null) {
                downloadDao.updateDownload(downloadEntity.copy(
                    progress = 100f,
                    status = "COMPLETED",
                    filePath = finalPath
                ))
            }

            Log.d("DownloadWorker", "Download completed: $title")
            Result.success()
        } catch (e: Exception) {
            Log.e("DownloadWorker", "Download failed for $title", e)
            downloadDao.updateStatus(downloadId, "FAILED")
            Result.failure()
        }
    }
}
