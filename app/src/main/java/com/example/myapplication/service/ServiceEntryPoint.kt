package com.example.myapplication.service

import com.example.myapplication.domain.repository.AppPreferencesRepository
import com.example.myapplication.domain.repository.VocabularyRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Entry point for accessing Hilt dependencies from non-Hilt supported classes.
 * Think of this like a "portal" to access React Context from outside the component tree.
 *
 * This interface defines what dependencies the AccessibilityService can access.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ServiceEntryPoint {
    fun appPreferencesRepository(): AppPreferencesRepository
    fun vocabularyRepository(): VocabularyRepository
}