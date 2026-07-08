package com.procrastilearn.app.domain.usecase

import com.procrastilearn.app.domain.model.AiTranslationDirection
import com.procrastilearn.app.domain.repository.PendingWordRepository
import javax.inject.Inject

class QueuePendingWordUseCase
    @Inject
    constructor(
        private val repository: PendingWordRepository,
    ) {
        suspend operator fun invoke(
            word: String,
            direction: AiTranslationDirection,
        ) {
            val trimmed = word.trim()
            require(trimmed.isNotBlank()) { "Word cannot be blank" }
            repository.queuePendingWord(trimmed, direction)
        }
    }
