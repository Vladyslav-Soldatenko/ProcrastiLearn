package com.procrastilearn.app.domain.repository

import com.procrastilearn.app.domain.model.AiTranslationDirection
import com.procrastilearn.app.domain.model.PendingWord
import kotlinx.coroutines.flow.Flow

interface PendingWordRepository {
    fun observePendingWords(): Flow<List<PendingWord>>

    suspend fun getAllPendingWordsSnapshot(): List<PendingWord>

    suspend fun queuePendingWord(
        word: String,
        direction: AiTranslationDirection,
    )

    suspend fun deletePendingWord(pendingWord: PendingWord)

    suspend fun deletePendingWordById(id: Long)
}
