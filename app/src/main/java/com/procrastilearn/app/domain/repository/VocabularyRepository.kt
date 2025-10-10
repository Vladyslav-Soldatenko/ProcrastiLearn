package com.procrastilearn.app.domain.repository

import com.procrastilearn.app.domain.model.VocabularyItem
import io.github.openspacedrepetition.Rating
import kotlinx.coroutines.flow.Flow

interface VocabularyRepository {
    suspend fun getNextVocabularyItem(): VocabularyItem

    fun observeCurrentItem(): Flow<VocabularyItem>

    fun getAllVocabulary(): Flow<List<VocabularyItem>>

    suspend fun addVocabularyItem(item: VocabularyItem)

    suspend fun updateVocabularyItem(item: VocabularyItem)

    suspend fun deleteVocabularyItem(item: VocabularyItem)

    suspend fun resetVocabularyProgress(item: VocabularyItem)

    suspend fun hasAvailableItems(): Boolean

    suspend fun reviewVocabularyItem(
        id: Long,
        rating: Rating,
    )
}
