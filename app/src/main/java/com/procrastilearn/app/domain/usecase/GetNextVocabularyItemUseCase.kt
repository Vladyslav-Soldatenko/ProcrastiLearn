package com.procrastilearn.app.domain.usecase

import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.domain.repository.VocabularyStudyRepository
import javax.inject.Inject

class GetNextVocabularyItemUseCase
    @Inject
    constructor(
        private val repository: VocabularyStudyRepository,
    ) {
        suspend operator fun invoke(): Result<VocabularyItem> =
            runCatching {
                repository.getNextVocabularyItem()
            }
    }
