package com.sekerkirrma.rs.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey
    val id: String, // We'll use UUID or a hashed URL
    val url: String,
    val title: String,
    val formatId: String,
    val progress: Float = 0f,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val status: String = "PENDING", // PENDING, DOWNLOADING, COMPLETED, FAILED
    val filePath: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
