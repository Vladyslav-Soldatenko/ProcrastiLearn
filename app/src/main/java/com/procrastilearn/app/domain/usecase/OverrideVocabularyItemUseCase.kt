package com.procrastilearn.app.domain.usecase

import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.domain.repository.VocabularyRepository
import javax.inject.Inject

class OverrideVocabularyItemUseCase
    @Inject
    constructor(
        private val repository: VocabularyRepository,
    ) {
        suspend operator fun invoke(
            existingItem: VocabularyItem,
            newWord: String,
            newTranslation: String,
        ): Result<Unit> =
            runCatching {
                val sanitizedWord = newWord.trim()
                val sanitizedTranslation = newTranslation.trim()
                require(sanitizedWord.isNotBlank()) { "Word cannot be blank" }
                require(sanitizedTranslation.isNotBlank()) { "Translation cannot be blank" }

                val updatedItem =
                    existingItem.copy(
                        word = sanitizedWord,
                        translation = sanitizedTranslation,
                    )
                repository.updateVocabularyItem(updatedItem)
                repository.resetVocabularyProgress(updatedItem)
            }
    }
