package com.procrastilearn.app.domain.repository

import com.procrastilearn.app.domain.model.VocabularyItem
import kotlinx.coroutines.flow.Flow

interface VocabularyCatalogRepository {
    fun getAllVocabulary(): Flow<List<VocabularyItem>>

    suspend fun getVocabularyItemByWord(word: String): VocabularyItem?

    suspend fun addVocabularyItem(item: VocabularyItem)

    suspend fun updateVocabularyItem(item: VocabularyItem)

    suspend fun deleteVocabularyItem(item: VocabularyItem)

    suspend fun deleteVocabularyItems(items: List<VocabularyItem>)

    suspend fun resetVocabularyProgress(item: VocabularyItem)

    suspend fun setBidirectional(
        ids: Set<Long>,
        bidirectional: Boolean,
    )

    suspend fun reorderVocabulary(orderedIds: List<Long>)
}
