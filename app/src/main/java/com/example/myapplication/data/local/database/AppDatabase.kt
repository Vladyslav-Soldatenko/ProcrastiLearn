package com.example.myapplication.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.myapplication.data.local.dao.VocabularyDao
import com.example.myapplication.data.local.entity.VocabularyEntity

@Database(
    entities = [VocabularyEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vocabularyDao(): VocabularyDao
}
