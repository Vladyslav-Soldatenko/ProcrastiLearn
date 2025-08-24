package com.example.myapplication.domain.usecase

import com.example.myapplication.domain.model.VocabularyItem
import com.example.myapplication.domain.repository.VocabularyRepository
import javax.inject.Inject

class GetNextVocabularyItemUseCase
    @Inject
    constructor(
        private val repository: VocabularyRepository,
    ) {
        suspend operator fun invoke(): Result<VocabularyItem> =
            runCatching {
                repository.getNextVocabularyItem()
            }
    }
