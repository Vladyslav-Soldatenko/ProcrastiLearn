package com.example.myapplication.di

import com.example.myapplication.data.AppRepository
import com.example.myapplication.data.AppRepositoryImpl
import com.example.myapplication.data.repository.AppPreferencesRepositoryImpl
import com.example.myapplication.data.repository.VocabularyRepositoryImpl
import com.example.myapplication.domain.repository.AppPreferencesRepository
import com.example.myapplication.domain.repository.VocabularyRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindAppRepository(
        impl: AppRepositoryImpl
    ): AppRepository

    @Binds
    @Singleton
    abstract fun bindVocabularyRepository(
        impl: VocabularyRepositoryImpl
    ): VocabularyRepository

    @Binds
    @Singleton
    abstract fun bindAppPreferencesRepository(
        impl: AppPreferencesRepositoryImpl
    ): AppPreferencesRepository
}