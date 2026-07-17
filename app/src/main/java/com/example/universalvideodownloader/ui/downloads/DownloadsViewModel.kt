package com.example.universalvideodownloader.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.universalvideodownloader.data.local.DownloadDao
import com.example.universalvideodownloader.data.local.DownloadEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadDao: DownloadDao
) : ViewModel() {

    val allDownloads: StateFlow<List<DownloadEntity>> = downloadDao.getAllDownloads()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        
    fun pauseDownload(id: String) {
        viewModelScope.launch { downloadDao.updateStatus(id, "PAUSED", System.currentTimeMillis()) }
    }
    
    fun resumeDownload(id: String) {
        viewModelScope.launch { downloadDao.updateStatus(id, "DOWNLOADING", System.currentTimeMillis()) }
    }
    
    fun cancelDownload(id: String) {
        viewModelScope.launch { downloadDao.updateStatus(id, "FAILED", System.currentTimeMillis()) }
    }
}
