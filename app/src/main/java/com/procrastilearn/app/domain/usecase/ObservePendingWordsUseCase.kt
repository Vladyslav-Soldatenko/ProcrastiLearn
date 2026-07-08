package com.procrastilearn.app.domain.usecase

import com.procrastilearn.app.domain.model.PendingWord
import com.procrastilearn.app.domain.repository.PendingWordRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObservePendingWordsUseCase
    @Inject
    constructor(
        private val repository: PendingWordRepository,
    ) {
        operator fun invoke(): Flow<List<PendingWord>> = repository.observePendingWords()
    }
