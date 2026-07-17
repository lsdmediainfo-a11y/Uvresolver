package com.sekerkirrma.rs.ui.downloads

import androidx.lifecycle.ViewModel
import com.sekerkirrma.rs.data.local.dao.DownloadDao
import com.sekerkirrma.rs.data.local.entity.DownloadEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadDao: DownloadDao
) : ViewModel() {

    val allDownloads: Flow<List<DownloadEntity>> = downloadDao.getAllDownloads()
}
