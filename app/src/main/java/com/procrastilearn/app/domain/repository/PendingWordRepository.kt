package com.procrastilearn.app.domain.repository

import com.procrastilearn.app.domain.model.AiTranslationDirection
import com.procrastilearn.app.domain.model.PendingWord
import com.procrastilearn.app.domain.model.PendingWordStatus
import kotlinx.coroutines.flow.Flow

interface PendingWordRepository {
    fun observePendingWords(): Flow<List<PendingWord>>

    suspend fun getAllPendingWordsSnapshot(): List<PendingWord>

    suspend fun queuePendingWord(
        word: String,
        direction: AiTranslationDirection,
        status: PendingWordStatus = PendingWordStatus.PENDING,
        lastError: String? = null,
    )

    suspend fun updatePendingWord(pendingWord: PendingWord)

    suspend fun retryPendingWord(id: Long)

    suspend fun deletePendingWord(pendingWord: PendingWord)

    suspend fun deletePendingWordById(id: Long)
}
