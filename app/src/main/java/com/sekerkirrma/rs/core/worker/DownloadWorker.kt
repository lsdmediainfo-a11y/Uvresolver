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
            val request = YoutubeDLRequest(url)
            
            // Format selection
            request.addOption("-f", formatId)
            
            // Output path: Public Downloads folder / AuraDownloads
            val downloadDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "AuraDownloads"
            )
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }
            
            // Safe title for filename
            val safeTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val outputPath = File(downloadDir, "$safeTitle.%(ext)s").absolutePath
            
            request.addOption("-o", outputPath)
            
            // Multi-threading for fragmented media (m3u8/DASH)
            request.addOption("--concurrent-fragments", "4")
            
            // Mux into MP4 using bundled FFmpeg
            request.addOption("--merge-output-format", "mp4")

            // Add headers if any (for bypassing protections)
            for (i in headerKeys.indices) {
                if (i < headerValues.size) {
                    request.addOption("--add-header", "${headerKeys[i]}:${headerValues[i]}")
                }
            }

            // Execute YoutubeDL
            YoutubeDL.getInstance().execute(request, downloadId) { progress, etaInSeconds, line ->
                // This callback provides progress from 0.0 to 100.0
                val currentProgress = progress.toFloat()
                
                // We'll update the database with progress
                // Since this might be called very frequently, we might want to throttle it,
                // but Room can handle it in a background thread if it's not thousands per second.
                // For safety, let's just update Room. We'll leave bytes 0 for now as YoutubeDL gives % progress.
                // We launch it in a fire-and-forget way or blockingly update.
                // Actually, this callback runs on a background thread from YoutubeDL.
                // Since we are in a suspend function, we can't easily launch coroutines without a scope,
                // but we can use runBlocking or let it go. WorkManager has `setProgress`.
                
                // WorkManager's native progress tracking
                // setProgress(workDataOf("progress" to currentProgress))
                
                Log.d("DownloadWorker", "Progress: $currentProgress% ETA: $etaInSeconds")
                
                // Just use the DAO to update the DB directly from this thread since YoutubeDL uses a background thread.
                // However, `updateProgress` is a suspend function. 
                kotlinx.coroutines.runBlocking {
                    downloadDao.updateProgress(downloadId, currentProgress, 0L, 0L)
                }
            }
            
            // If we reach here, it succeeded
            // Find the actual generated file path. YoutubeDL usually replaces %(ext)s.
            // We can just assume it's mp4.
            val finalPath = File(downloadDir, "$safeTitle.mp4").absolutePath
            
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
