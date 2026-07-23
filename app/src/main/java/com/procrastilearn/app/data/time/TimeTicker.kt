package com.procrastilearn.app.data.time

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

interface TimeTicker {
    fun nowTicks(): Flow<Long>
}

@Singleton
class RealTimeTicker
    @Inject
    constructor() : TimeTicker {
        override fun nowTicks(): Flow<Long> =
            flow {
                while (true) {
                    emit(System.currentTimeMillis())
                    delay(TICK_INTERVAL_MS)
                }
            }

        private companion object {
            const val TICK_INTERVAL_MS = 30_000L
        }
    }
