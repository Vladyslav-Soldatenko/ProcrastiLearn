package com.procrastilearn.app.data.time

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RealTimeTickerTest {
    @Test
    fun `now returns the current wall-clock time`() {
        val ticker = RealTimeTicker()

        val before = System.currentTimeMillis()
        val result = ticker.now()
        val after = System.currentTimeMillis()

        assertThat(result).isAtLeast(before)
        assertThat(result).isAtMost(after)
    }

    @Test
    fun `nowTicks emits immediately without waiting for the first interval`() =
        runTest {
            val ticker = RealTimeTicker()
            val emissions = mutableListOf<Long>()

            val before = System.currentTimeMillis()
            val job = launch { ticker.nowTicks().collect { emissions.add(it) } }
            runCurrent()
            val after = System.currentTimeMillis()

            assertThat(emissions).hasSize(1)
            assertThat(emissions[0]).isAtLeast(before)
            assertThat(emissions[0]).isAtMost(after)

            job.cancel()
        }

    @Test
    fun `nowTicks does not emit again before the 30 second interval elapses`() =
        runTest {
            val ticker = RealTimeTicker()
            val emissions = mutableListOf<Long>()

            val job = launch { ticker.nowTicks().collect { emissions.add(it) } }
            runCurrent()
            assertThat(emissions).hasSize(1)

            advanceTimeBy(29_999)
            runCurrent()

            assertThat(emissions).hasSize(1)

            job.cancel()
        }

    @Test
    fun `nowTicks emits again exactly every 30 seconds and never completes`() =
        runTest {
            val ticker = RealTimeTicker()
            val emissions = mutableListOf<Long>()

            val job = launch { ticker.nowTicks().collect { emissions.add(it) } }
            runCurrent()
            assertThat(emissions).hasSize(1)

            advanceTimeBy(30_000)
            runCurrent()
            assertThat(emissions).hasSize(2)

            advanceTimeBy(30_000)
            runCurrent()
            assertThat(emissions).hasSize(3)

            assertThat(job.isActive).isTrue()

            job.cancel()
        }
}
