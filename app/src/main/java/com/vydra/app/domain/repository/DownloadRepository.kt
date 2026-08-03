package com.vydra.app.domain.repository

import com.vydra.app.data.local.dao.DownloadDao
import com.vydra.app.data.local.entity.DownloadEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(
    private val downloadDao: DownloadDao
) {
    fun getAllDownloads(): Flow<List<DownloadEntity>> = downloadDao.getAllDownloads()

    fun getDownloadsByStatus(status: String): Flow<List<DownloadEntity>> =
        downloadDao.getDownloadsByStatus(status)

    fun searchDownloads(query: String): Flow<List<DownloadEntity>> =
        downloadDao.searchDownloads(query)

    fun getActiveDownloadCount(): Flow<Int> = downloadDao.getActiveDownloadCount()

    suspend fun getDownloadById(id: Long): DownloadEntity? = downloadDao.getDownloadById(id)

    suspend fun getDownloadByTaskId(taskId: String): DownloadEntity? =
        downloadDao.getDownloadByTaskId(taskId)

    suspend fun insertDownload(download: DownloadEntity): Long =
        downloadDao.insertDownload(download)

    suspend fun updateDownload(download: DownloadEntity) = downloadDao.updateDownload(download)

    suspend fun deleteDownload(download: DownloadEntity) = downloadDao.deleteDownload(download)

    suspend fun deleteDownloadById(id: Long) = downloadDao.deleteDownloadById(id)

    suspend fun clearCompleted() = downloadDao.clearCompleted()

    suspend fun clearAll() = downloadDao.clearAll()
}
