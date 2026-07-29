package com.procrastilearn.app.domain.usecase

import javax.inject.Inject

// The three use cases needed to add a word, check whether it's already saved, and
// override it if so - always used together wherever a caller lets the user add or
// edit vocabulary: AddWordViewModel, VocabularyFunctions, and PendingWordSyncManager
// (which only needs [add] and [getByWord]).
class VocabularyEntryUseCases
    @Inject
    constructor(
        val add: AddVocabularyItemUseCase,
        val getByWord: GetVocabularyItemByWordUseCase,
        val override: OverrideVocabularyItemUseCase,
    )
