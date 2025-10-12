package com.procrastilearn.app.di

import com.procrastilearn.app.data.AppRepository
import com.procrastilearn.app.data.AppRepositoryImpl
import com.procrastilearn.app.data.repository.AppPreferencesRepositoryImpl
import com.procrastilearn.app.data.repository.VocabularyRepositoryImpl
import com.procrastilearn.app.data.translation.AiTranslationProvider
import com.procrastilearn.app.data.translation.OpenAiTranslationProvider
import com.procrastilearn.app.domain.repository.AppPreferencesRepository
import com.procrastilearn.app.domain.repository.VocabularyRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Binds
    @Singleton
    abstract fun bindAppRepository(impl: AppRepositoryImpl): AppRepository

    @Binds
    @Singleton
    abstract fun bindVocabularyRepository(impl: VocabularyRepositoryImpl): VocabularyRepository

    @Binds
    @Singleton
    abstract fun bindAppPreferencesRepository(impl: AppPreferencesRepositoryImpl): AppPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindAiTranslationProvider(impl: OpenAiTranslationProvider): AiTranslationProvider

    companion object {
        @Provides
        @Singleton
        fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
    }
}
