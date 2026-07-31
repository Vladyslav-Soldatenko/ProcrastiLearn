package com.procrastilearn.app.domain.usecase

import com.procrastilearn.app.domain.model.UndoResult
import com.procrastilearn.app.domain.repository.VocabularyRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class UndoLastRatingUseCase
    @Inject
    constructor(
        private val repository: VocabularyRepository,
    ) {
        suspend operator fun invoke(): Result<UndoResult?> =
            runCatching {
                repository.undoLastRating()
            }

        fun observeUndoCount(): Flow<Int> = repository.observeUndoCount()
    }
