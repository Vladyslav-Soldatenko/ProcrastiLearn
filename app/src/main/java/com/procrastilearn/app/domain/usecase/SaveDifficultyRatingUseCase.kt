package com.procrastilearn.app.domain.usecase

import com.procrastilearn.app.domain.repository.VocabularyRepository
import io.github.openspacedrepetition.Rating
import javax.inject.Inject

class SaveDifficultyRatingUseCase
    @Inject
    constructor(
        private val repository: VocabularyRepository,
    ) {
        suspend operator fun invoke(
            vocabId: Long,
            rating: Rating,
        ): Result<Unit> =
            runCatching {
                repository.reviewVocabularyItem(vocabId, rating)
            }
    }
