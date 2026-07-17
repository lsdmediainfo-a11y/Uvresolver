package com.sekerkirrma.rs.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.sekerkirrma.rs.data.local.dao.DownloadDao
import com.sekerkirrma.rs.data.local.entity.DownloadEntity

@Database(entities = [DownloadEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
}
