package com.procrastilearn.app.ui.views

import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.data.text.ProcessTextEventBus
import com.procrastilearn.app.utils.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `exposes the event bus text as process text events`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val bus = ProcessTextEventBus()
            val viewModel = MainViewModel(bus)

            assertThat(viewModel.processTextEvents.value).isNull()

            bus.submit("Haus")

            assertThat(viewModel.processTextEvents.value).isEqualTo("Haus")
        }
}
