package com.sekerkirrma.rs

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import androidx.hilt.work.HiltWorkerFactory
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AuraApp : Application(), Configuration.Provider {

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
        try {
            YoutubeDL.getInstance().init(this)
            FFmpeg.getInstance().init(this)
            Log.d("AuraApp", "YoutubeDL and FFmpeg initialized successfully.")
        } catch (e: YoutubeDLException) {
            Log.e("AuraApp", "Failed to initialize YoutubeDL/FFmpeg", e)
        }
    }
}
