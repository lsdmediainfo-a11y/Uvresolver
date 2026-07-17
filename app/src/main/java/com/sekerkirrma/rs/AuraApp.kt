package com.sekerkirrma.rs

import android.util.Log
import androidx.work.Configuration
import androidx.hilt.work.HiltWorkerFactory
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class AuraApp : android.app.Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun getWorkManagerConfiguration(): Configuration = 
        Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        initYoutubeDL()
    }

    private fun initYoutubeDL() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("AuraApp", "Starting YoutubeDL and FFmpeg initialization...")
                YoutubeDL.getInstance().init(this@AuraApp)
                Log.d("AuraApp", "YoutubeDL initialized successfully.")
                FFmpeg.getInstance().init(this@AuraApp)
                Log.d("AuraApp", "FFmpeg initialized successfully.")
            } catch (e: YoutubeDLException) {
                Log.e("AuraApp", "Failed to initialize YoutubeDL/FFmpeg", e)
            } catch (e: Exception) {
                Log.e("AuraApp", "Unknown error during YoutubeDL/FFmpeg init", e)
            }
        }
    }
}
