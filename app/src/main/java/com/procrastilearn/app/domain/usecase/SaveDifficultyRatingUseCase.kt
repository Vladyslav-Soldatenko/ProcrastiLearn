package com.procrastilearn.app.domain.usecase

import com.procrastilearn.app.domain.model.StudyDirection
import com.procrastilearn.app.domain.repository.VocabularyStudyRepository
import io.github.openspacedrepetition.Rating
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

class SaveDifficultyRatingUseCase
    @Inject
    constructor(
        private val repository: VocabularyStudyRepository,
    ) {
        @Suppress("TooGenericExceptionCaught")
        suspend operator fun invoke(
            vocabId: Long,
            rating: Rating,
            direction: StudyDirection = StudyDirection.FORWARD,
        ): Result<Unit> =
            try {
                repository.reviewVocabularyItem(vocabId, rating, direction)
                Result.success(Unit)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Result.failure(exception)
            }
    }
