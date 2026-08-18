package com.procrastilearn.app.di

import android.content.Context
import androidx.room.Room
import com.procrastilearn.app.data.local.dao.PendingWordDao
import com.procrastilearn.app.data.local.dao.UndoSnapshotDao
import com.procrastilearn.app.data.local.dao.VocabularyDao
import com.procrastilearn.app.data.local.dao.VocabularyReviewDao
import com.procrastilearn.app.data.local.dao.VocabularyStatsDao
import com.procrastilearn.app.data.local.database.AppDatabase
import com.procrastilearn.app.data.local.database.MIGRATION_1_2
import com.procrastilearn.app.data.local.database.MIGRATION_2_3
import com.procrastilearn.app.data.local.database.MIGRATION_3_4
import com.procrastilearn.app.data.local.database.MIGRATION_4_5
import com.procrastilearn.app.data.local.database.MIGRATION_5_6
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase =
        Room
            .databaseBuilder(
                context,
                AppDatabase::class.java,
                "app_database",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .build()

    @Provides
    @Singleton
    fun provideVocabularyDao(database: AppDatabase): VocabularyDao = database.vocabularyDao()

    @Provides
    @Singleton
    fun provideVocabularyReviewDao(database: AppDatabase): VocabularyReviewDao = database.vocabularyReviewDao()

    @Provides
    @Singleton
    fun provideVocabularyStatsDao(database: AppDatabase): VocabularyStatsDao = database.vocabularyStatsDao()

    @Provides
    @Singleton
    fun providePendingWordDao(database: AppDatabase): PendingWordDao = database.pendingWordDao()

    @Provides
    @Singleton
    fun provideUndoSnapshotDao(database: AppDatabase): UndoSnapshotDao = database.undoSnapshotDao()
}
