package com.procrastilearn.app.domain.usecase

import com.procrastilearn.app.domain.repository.VocabularyStudyRepository
import javax.inject.Inject

class CheckVocabularyAvailabilityUseCase
    @Inject
    constructor(
        private val repository: VocabularyStudyRepository,
    ) {
        suspend operator fun invoke(): Boolean = repository.hasAvailableItems()
    }
