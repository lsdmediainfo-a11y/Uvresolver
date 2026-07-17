package com.sekerkirrma.rs.domain.model

data class VideoFormatItem(
    val formatId: String,
    val resolution: String,
    val ext: String,
    val fileSizeStr: String,
    val fps: Double? = null,
    val isAudioOnly: Boolean = false
)
