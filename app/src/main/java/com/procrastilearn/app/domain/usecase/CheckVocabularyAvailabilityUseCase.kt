package com.procrastilearn.app.domain.usecase

import com.procrastilearn.app.domain.repository.VocabularyRepository
import javax.inject.Inject

class CheckVocabularyAvailabilityUseCase
    @Inject
    constructor(
        private val repository: VocabularyRepository,
    ) {
        suspend operator fun invoke(): Boolean = repository.hasAvailableItems()
    }
