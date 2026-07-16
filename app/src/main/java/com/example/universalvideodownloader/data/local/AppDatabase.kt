package com.example.universalvideodownloader.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        DownloadEntity::class
    ],
    version = 1,
    exportSchema = false
)
@androidx.room.TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
}
