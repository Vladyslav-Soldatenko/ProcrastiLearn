package com.procrastilearn.app.domain.usecase

import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.domain.repository.VocabularyStudyRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

class GetNextVocabularyItemUseCase
    @Inject
    constructor(
        private val repository: VocabularyStudyRepository,
    ) {
        @Suppress("TooGenericExceptionCaught")
        suspend operator fun invoke(): Result<VocabularyItem> =
            try {
                Result.success(repository.getNextVocabularyItem())
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Result.failure(exception)
            }
    }
