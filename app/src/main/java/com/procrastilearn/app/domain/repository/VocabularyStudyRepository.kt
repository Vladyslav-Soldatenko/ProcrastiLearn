package com.procrastilearn.app.domain.repository

import com.procrastilearn.app.domain.model.StudyDirection
import com.procrastilearn.app.domain.model.UndoResult
import com.procrastilearn.app.domain.model.VocabularyItem
import io.github.openspacedrepetition.Rating
import kotlinx.coroutines.flow.Flow

interface VocabularyStudyRepository {
    suspend fun getNextVocabularyItem(): VocabularyItem

    suspend fun hasAvailableItems(): Boolean

    suspend fun reviewVocabularyItem(
        id: Long,
        rating: Rating,
        direction: StudyDirection = StudyDirection.FORWARD,
    )

    suspend fun undoLastRating(): UndoResult?

    fun observeUndoCount(): Flow<Int>

    fun observeBackwardOnlySkippedCount(): Flow<Int>
}
