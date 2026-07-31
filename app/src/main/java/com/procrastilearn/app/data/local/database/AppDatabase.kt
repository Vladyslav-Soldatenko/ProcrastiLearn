package com.procrastilearn.app.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.procrastilearn.app.data.local.dao.PendingWordDao
import com.procrastilearn.app.data.local.dao.UndoSnapshotDao
import com.procrastilearn.app.data.local.dao.VocabularyDao
import com.procrastilearn.app.data.local.dao.VocabularyReviewDao
import com.procrastilearn.app.data.local.dao.VocabularyStatsDao
import com.procrastilearn.app.data.local.entity.PendingWordEntity
import com.procrastilearn.app.data.local.entity.UndoSnapshotEntity
import com.procrastilearn.app.data.local.entity.VocabularyEntity

@Database(
    entities = [VocabularyEntity::class, PendingWordEntity::class, UndoSnapshotEntity::class],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vocabularyDao(): VocabularyDao

    abstract fun vocabularyReviewDao(): VocabularyReviewDao

    abstract fun vocabularyStatsDao(): VocabularyStatsDao

    abstract fun pendingWordDao(): PendingWordDao

    abstract fun undoSnapshotDao(): UndoSnapshotDao
}
