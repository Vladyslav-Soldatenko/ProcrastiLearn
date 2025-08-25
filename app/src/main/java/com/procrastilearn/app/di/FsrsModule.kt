package com.procrastilearn.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.openspacedrepetition.Scheduler
import java.time.Duration
import javax.inject.Singleton

@Suppress("MagicNumber")
@Module
@InstallIn(SingletonComponent::class)
object FsrsModule {
    @Provides
    @Singleton
    fun provideFsrsScheduler(): Scheduler {
        // Defaults are sensible; tweak desiredRetention/steps if you want.
        // Library uses UTC internally. :contentReference[oaicite:1]{index=1}
        return Scheduler
            .builder()
            .desiredRetention(0.9)
            .learningSteps(arrayOf(Duration.ofMinutes(1), Duration.ofMinutes(10)))
            .relearningSteps(arrayOf(Duration.ofMinutes(10)))
            .maximumInterval(36500) // ~100 years cap; safe default.
            .enableFuzzing(true)
            .build()
    }
}
