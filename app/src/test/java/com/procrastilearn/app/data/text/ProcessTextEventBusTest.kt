package com.procrastilearn.app.data.text

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProcessTextEventBusTest {
    @Test
    fun `starts with no pending event`() =
        runTest {
            val bus = ProcessTextEventBus()

            assertThat(bus.events.value).isNull()
        }

    @Test
    fun `submit publishes the text`() =
        runTest {
            val bus = ProcessTextEventBus()

            bus.submit("Haus")

            assertThat(bus.events.value).isEqualTo("Haus")
        }

    @Test
    fun `consume clears the pending event`() =
        runTest {
            val bus = ProcessTextEventBus()
            bus.submit("Haus")

            bus.consume()

            assertThat(bus.events.value).isNull()
        }

    @Test
    fun `submit overwrites a previously pending event`() =
        runTest {
            val bus = ProcessTextEventBus()
            bus.submit("Haus")

            bus.submit("Katze")

            assertThat(bus.events.value).isEqualTo("Katze")
        }

    @Test
    fun `consume without a pending event is a no-op`() =
        runTest {
            val bus = ProcessTextEventBus()

            bus.consume()

            assertThat(bus.events.value).isNull()
        }
}
