package com.procrastilearn.app.domain.usecase

import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.domain.repository.VocabularyRepository
import javax.inject.Inject

class GetVocabularyItemByWordUseCase
    @Inject
    constructor(
        private val repository: VocabularyRepository,
    ) {
        suspend operator fun invoke(word: String): VocabularyItem? {
            if (word.isBlank()) return null
            return repository.getVocabularyItemByWord(word.trim())
        }
    }
