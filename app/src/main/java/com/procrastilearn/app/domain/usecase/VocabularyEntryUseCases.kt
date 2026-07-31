package com.procrastilearn.app.domain.usecase

import javax.inject.Inject

class VocabularyEntryUseCases
    @Inject
    constructor(
        val add: AddVocabularyItemUseCase,
        val getByWord: GetVocabularyItemByWordUseCase,
        val override: OverrideVocabularyItemUseCase,
    )
