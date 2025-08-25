package com.procrastilearn.app.domain.usecase

import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.domain.repository.VocabularyRepository
import javax.inject.Inject

class AddVocabularyItemUseCase
    @Inject
    constructor(
        private val repository: VocabularyRepository,
    ) {
        @Suppress("TooGenericExceptionCaught")
        suspend operator fun invoke(
            word: String,
            translation: String,
        ): Result<Unit> {
            return try {
                // Basic validation
                if (word.isBlank() || translation.isBlank()) {
                    return Result.failure(IllegalArgumentException("Word and translation cannot be empty"))
                }

                val vocabularyItem =
                    VocabularyItem(
                        word = word.trim(),
                        translation = translation.trim(),
                    )

                repository.addVocabularyItem(vocabularyItem)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
