package com.example.myapplication.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.myapplication.data.local.dao.VocabularyDao
import com.example.myapplication.data.local.database.AppDatabase
import com.example.myapplication.data.local.entity.VocabularyEntity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        vocabularyDaoProvider: Provider<VocabularyDao>
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "app_database"
        ) .fallbackToDestructiveMigration()
            .fallbackToDestructiveMigrationOnDowngrade()
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    // Prepopulate database with initial data
                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                        populateInitialData(vocabularyDaoProvider.get())
                    }
                }
            })
            .build()
    }

    @Provides
    @Singleton
    fun provideVocabularyDao(database: AppDatabase): VocabularyDao {
        return database.vocabularyDao()
    }

    private suspend fun populateInitialData(vocabularyDao: VocabularyDao) {
        val initialVocabulary = listOf(
            VocabularyEntity(word = "Cat", translation = "кот"),
            VocabularyEntity(word = "Dog", translation = "собака"),
            VocabularyEntity(word = "Water", translation = "вода"),
            VocabularyEntity(word = "Book", translation = "книга"),
            VocabularyEntity(word = "Phone", translation = "телефон"),
            VocabularyEntity(word = "Computer", translation = "компьютер"),
            VocabularyEntity(word = "Happiness", translation = "счастье"),
            VocabularyEntity(word = "Friend", translation = "друг"),
            VocabularyEntity(word = "Family", translation = "семья"),
            VocabularyEntity(word = "Knowledge", translation = "знание")
        )
        vocabularyDao.insertAllVocabulary(initialVocabulary)
    }
}