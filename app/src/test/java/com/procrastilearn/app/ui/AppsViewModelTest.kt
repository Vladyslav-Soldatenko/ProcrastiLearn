package com.procrastilearn.app.ui

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.data.AppRepository
import com.procrastilearn.app.domain.model.AppInfo
import com.procrastilearn.app.domain.repository.AppPreferencesRepository
import com.procrastilearn.app.utils.MainDispatcherRule
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var appRepository: AppRepository
    private lateinit var appPreferencesRepository: AppPreferencesRepository
    private lateinit var blockedAppsFlow: MutableStateFlow<Set<String>>
    private lateinit var enabledFlow: MutableStateFlow<Boolean>

    @Before
    fun setUp() {
        appRepository = mockk()
        appPreferencesRepository = mockk(relaxed = true)
        blockedAppsFlow = MutableStateFlow(emptySet())
        enabledFlow = MutableStateFlow(true)

        every { appPreferencesRepository.getBlockedApps() } returns blockedAppsFlow
        every { appPreferencesRepository.isProcrastilearnEnabled() } returns enabledFlow
        coEvery { appRepository.loadLaunchableApps() } returns emptyList()
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    private fun buildViewModel() = AppsViewModel(appRepository, appPreferencesRepository)

    @Test
    fun `refresh success populates apps and clears loading`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val apps =
                listOf(
                    AppInfo(label = "Alpha", packageName = "com.example.alpha"),
                    AppInfo(label = "Beta", packageName = "com.example.beta"),
                )
            coEvery { appRepository.loadLaunchableApps() } returns apps

            val viewModel = buildViewModel()

            viewModel.state.test {
                val initial = awaitItem()
                assertThat(initial.isLoading).isTrue()
                assertThat(initial.apps).isEmpty()

                advanceUntilIdle()

                val loaded = awaitItem()
                assertThat(loaded.isLoading).isFalse()
                assertThat(loaded.error).isNull()
                assertThat(loaded.apps).containsExactlyElementsIn(apps).inOrder()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `refresh failure exposes error message`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val failure = IllegalStateException("boom")
            coEvery { appRepository.loadLaunchableApps() } throws failure

            val viewModel = buildViewModel()

            viewModel.state.test {
                val initial = awaitItem()
                assertThat(initial.isLoading).isTrue()

                advanceUntilIdle()

                val errored = awaitItem()
                assertThat(errored.isLoading).isFalse()
                assertThat(errored.apps).isEmpty()
                assertThat(errored.error).isEqualTo("boom")
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `toggle delegates to preferences repository`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val app = AppInfo(label = "Gamma", packageName = "com.example.gamma")
            val viewModel = buildViewModel()

            advanceUntilIdle()

            viewModel.toggle(app)
            advanceUntilIdle()

            coVerify(exactly = 1) { appPreferencesRepository.toggleApp("com.example.gamma") }
        }

    @Test
    fun `setEnabled delegates to preferences repository`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()

            advanceUntilIdle()

            viewModel.setEnabled(false)
            advanceUntilIdle()

            coVerify(exactly = 1) { appPreferencesRepository.setProcrastilearnEnabled(false) }
        }

    @Test
    fun `blocked apps emission updates selected set`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()

            advanceUntilIdle()
            assertThat(viewModel.state.value.selected).isEmpty()

            blockedAppsFlow.value = setOf("com.example.alpha", "com.example.beta")
            advanceUntilIdle()

            assertThat(viewModel.state.value.selected)
                .containsExactly("com.example.alpha", "com.example.beta")
        }

    @Test
    fun `isProcrastilearnEnabled emission updates state`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()

            advanceUntilIdle()
            assertThat(viewModel.state.value.isEnabled).isTrue()

            enabledFlow.value = false
            advanceUntilIdle()

            assertThat(viewModel.state.value.isEnabled).isFalse()
        }
}
