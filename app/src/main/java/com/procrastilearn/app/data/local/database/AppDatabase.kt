package com.procrastilearn.app.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.procrastilearn.app.data.local.dao.VocabularyDao
import com.procrastilearn.app.data.local.entity.VocabularyEntity

@Database(
    entities = [VocabularyEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vocabularyDao(): VocabularyDao
}
