package com.procrastilearn.app.data.repository

import io.github.openspacedrepetition.Scheduler
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FsrsSchedulerFactory @Inject constructor() {
    private val schedulers = mutableMapOf<Int, Scheduler>()

    @Synchronized
    fun forMaximumInterval(days: Int): Scheduler =
        schedulers.getOrPut(days) {
            Scheduler
                .builder()
                .desiredRetention(0.93)
                .learningSteps(arrayOf(Duration.ofMinutes(1), Duration.ofMinutes(10)))
                .relearningSteps(arrayOf(Duration.ofMinutes(10)))
                .maximumInterval(days)
                .enableFuzzing(true)
                .build()
        }
}
