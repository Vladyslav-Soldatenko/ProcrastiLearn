package com.procrastilearn.app.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.procrastilearn.app.data.local.dao.PendingWordDao
import com.procrastilearn.app.data.local.dao.VocabularyDao
import com.procrastilearn.app.data.local.entity.PendingWordEntity
import com.procrastilearn.app.data.local.entity.VocabularyEntity

@Database(
    entities = [VocabularyEntity::class, PendingWordEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vocabularyDao(): VocabularyDao

    abstract fun pendingWordDao(): PendingWordDao
}
