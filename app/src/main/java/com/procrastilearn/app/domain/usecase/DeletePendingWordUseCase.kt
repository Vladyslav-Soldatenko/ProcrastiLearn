package com.procrastilearn.app.domain.usecase

import com.procrastilearn.app.domain.repository.PendingWordRepository
import javax.inject.Inject

class DeletePendingWordUseCase
    @Inject
    constructor(
        private val repository: PendingWordRepository,
    ) {
        suspend operator fun invoke(id: Long) {
            repository.deletePendingWordById(id)
        }
    }
