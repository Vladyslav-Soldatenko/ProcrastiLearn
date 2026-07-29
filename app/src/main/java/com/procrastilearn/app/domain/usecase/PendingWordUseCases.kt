package com.procrastilearn.app.domain.usecase

import javax.inject.Inject

// Queue/observe/delete for words added while offline, awaiting AI translation once
// connectivity returns - only ever needed together, on the Add Word screen.
class PendingWordUseCases
    @Inject
    constructor(
        val queue: QueuePendingWordUseCase,
        val observe: ObservePendingWordsUseCase,
        val delete: DeletePendingWordUseCase,
    )
