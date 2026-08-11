package com.procrastilearn.app.domain.usecase

import javax.inject.Inject

data class PendingWordUseCases
    @Inject
    constructor(
        val queue: QueuePendingWordUseCase,
        val observe: ObservePendingWordsUseCase,
        val delete: DeletePendingWordUseCase,
    )
