package com.procrastilearn.app.ui.dojo

import com.procrastilearn.app.data.local.dao.VocabularyDao
import com.procrastilearn.app.data.local.prefs.DayCountersStore
import com.procrastilearn.app.data.time.TimeTicker
import com.procrastilearn.app.domain.model.includesBackward
import com.procrastilearn.app.domain.model.includesForward
import com.procrastilearn.app.domain.model.isBackwardOnly
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
class DojoCountersSource
    @Inject
    constructor(
        private val vocabularyDao: VocabularyDao,
        private val dayCountersStore: DayCountersStore,
        private val timeTicker: TimeTicker,
    ) {
        private val refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        private val nowSource = merge(timeTicker.nowTicks(), refreshRequests.map { timeTicker.now() })

        val reviewsDueAndSkippedCount: Flow<Pair<Int, Int>> =
            combine(nowSource, dayCountersStore.readPolicy()) { now, policy -> now to policy }
                .flatMapLatest { (now, policy) ->
                    val due =
                        vocabularyDao.observeReviewsDueCount(
                            now,
                            includeForward = policy.studyDirectionMode.includesForward,
                            includeBackward = policy.studyDirectionMode.includesBackward,
                        )
                    val skipped =
                        if (policy.studyDirectionMode.isBackwardOnly) {
                            vocabularyDao.observeBackwardOnlySkippedCount(now)
                        } else {
                            flowOf(0)
                        }
                    combine(due, skipped) { dueValue, skippedValue -> dueValue to skippedValue }
                }
                .distinctUntilChanged()

        val newTotalCount: Flow<Int> =
            dayCountersStore.readPolicy().flatMapLatest { policy ->
                vocabularyDao.observeNewTotalCount(requireBidirectional = policy.studyDirectionMode.isBackwardOnly)
            }

        suspend fun refresh() {
            refreshRequests.emit(Unit)
        }
    }
