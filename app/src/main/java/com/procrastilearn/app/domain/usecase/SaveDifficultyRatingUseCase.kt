package com.procrastilearn.app.domain.usecase

import com.procrastilearn.app.domain.model.StudyDirection
import com.procrastilearn.app.domain.repository.VocabularyStudyRepository
import io.github.openspacedrepetition.Rating
import javax.inject.Inject

class SaveDifficultyRatingUseCase
    @Inject
    constructor(
        private val repository: VocabularyStudyRepository,
    ) {
        suspend operator fun invoke(
            vocabId: Long,
            rating: Rating,
            direction: StudyDirection = StudyDirection.FORWARD,
        ): Result<Unit> =
            runCatching {
                repository.reviewVocabularyItem(vocabId, rating, direction)
            }
    }
