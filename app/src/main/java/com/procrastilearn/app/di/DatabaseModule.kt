package com.procrastilearn.app.di

import android.content.Context
import androidx.room.Room
import com.procrastilearn.app.data.local.dao.VocabularyDao
import com.procrastilearn.app.data.local.database.AppDatabase
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
            ).build()

    @Provides
    @Singleton
    fun provideVocabularyDao(database: AppDatabase): VocabularyDao = database.vocabularyDao()
}
