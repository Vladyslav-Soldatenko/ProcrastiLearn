package com.procrastilearn.app.service

import com.procrastilearn.app.data.local.prefs.DayCountersStore
import com.procrastilearn.app.domain.repository.AppPreferencesRepository
import com.procrastilearn.app.domain.repository.VocabularyRepository
import com.procrastilearn.app.domain.usecase.CheckVocabularyAvailabilityUseCase
import com.procrastilearn.app.domain.usecase.GetNextVocabularyItemUseCase
import com.procrastilearn.app.domain.usecase.SaveDifficultyRatingUseCase
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

    fun getNextVocabularyItemUseCase(): GetNextVocabularyItemUseCase

    fun getSaveDifficultyRatingUseCase(): SaveDifficultyRatingUseCase

    fun checkVocabularyAvailabilityUseCase(): CheckVocabularyAvailabilityUseCase

    fun dayCountersStore(): DayCountersStore
}
