package com.vydra.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.vydra.app.data.local.dao.DownloadDao
import com.vydra.app.data.local.entity.DownloadEntity

@Database(
    entities = [DownloadEntity::class],
    version = 1,
    exportSchema = false
)
abstract class VydraDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
}
