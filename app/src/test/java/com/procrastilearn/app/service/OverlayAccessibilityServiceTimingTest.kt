package com.procrastilearn.app.service

import android.content.Context
import android.media.AudioManager
import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.data.repository.NoAvailableItemsException
import com.procrastilearn.app.domain.model.LearningPreferencesConfig
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class OverlayAccessibilityServiceTimingTest : OverlayAccessibilityServiceTestFixture() {
    @Test
    fun `reviewed release with interval zero schedules no repeat`() {
        policyFlow.value = LearningPreferencesConfig(overlayInterval = 0)
        advanceUntilIdle()
        blockedAppsFlow.value = setOf(BLOCKED_PACKAGE)
        advanceUntilIdle()
        dispatch(BLOCKED_PACKAGE)
        completeOverlay()
        advanceTimeAndRun(Duration.ofDays(1).toMillis())

        coVerify(exactly = 1) { getNextVocabularyItemUseCase() }
    }

    @Test
    fun `visible overlay never schedules a repeat`() {
        policyFlow.value = LearningPreferencesConfig(overlayInterval = 5)
        advanceUntilIdle()
        blockedAppsFlow.value = setOf(BLOCKED_PACKAGE)
        advanceUntilIdle()
        dispatch(BLOCKED_PACKAGE)

        advanceTimeAndRun(Duration.ofMinutes(10).toMillis())

        coVerify(exactly = 1) { getNextVocabularyItemUseCase() }
    }

    @Test
    fun `disabling cancels a scheduled repeat`() {
        policyFlow.value = LearningPreferencesConfig(overlayInterval = 5)
        advanceUntilIdle()
        blockedAppsFlow.value = setOf(BLOCKED_PACKAGE)
        advanceUntilIdle()
        dispatch(BLOCKED_PACKAGE)
        completeOverlay()

        enabledFlow.value = false
        advanceUntilIdle()

        advanceTimeAndRun(Duration.ofMinutes(10).toMillis())

        coVerify(exactly = 1) { getNextVocabularyItemUseCase() }
    }

    @Test
    fun `reviewed release shows a fresh gate once the interval elapses`() {
        policyFlow.value = LearningPreferencesConfig(overlayInterval = 5)
        advanceUntilIdle()
        blockedAppsFlow.value = setOf(BLOCKED_PACKAGE)
        advanceUntilIdle()
        dispatch(BLOCKED_PACKAGE)
        verify(exactly = 1) { windowManager.addView(any(), any()) }

        completeOverlay()
        advanceTimeAndRun(Duration.ofMinutes(5).toMillis())

        coVerify(exactly = 2) { getNextVocabularyItemUseCase() }
        verify(exactly = 2) { windowManager.addView(any(), any()) }
    }

    @Test
    fun `ending a gate session cancels a pending interval timer`() {
        policyFlow.value = LearningPreferencesConfig(overlayInterval = 5)
        advanceUntilIdle()
        blockedAppsFlow.value = setOf(BLOCKED_PACKAGE)
        advanceUntilIdle()
        dispatch(BLOCKED_PACKAGE)
        completeOverlay()

        advanceRealClockPastDebounceWindow()
        dispatch(LEGIT_PACKAGE)

        advanceTimeAndRun(Duration.ofMinutes(5).toMillis())

        coVerify(exactly = 1) { getNextVocabularyItemUseCase() }
    }

    @Test
    fun `cooldown bypass neither loads attaches launches nor requests audio focus`() {
        policyFlow.value = LearningPreferencesConfig(gateCooldownMinutes = 2, overlayInterval = 5)
        blockedAppsFlow.value = setOf(BLOCKED_PACKAGE, OTHER_BLOCKED_PACKAGE)
        makePackageLaunchable(BLOCKED_PACKAGE)
        makePackageLaunchable(OTHER_BLOCKED_PACKAGE)
        advanceUntilIdle()
        dispatch(BLOCKED_PACKAGE)
        completeOverlay()
        assertThat(shadowOf(service).nextStartedActivity?.component?.packageName).isEqualTo(BLOCKED_PACKAGE)
        val audio = shadowOf(service.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
        val previousFocus = audio.lastAudioFocusRequest

        advanceTimeAndRun(30_000)
        dispatch(OTHER_BLOCKED_PACKAGE)
        advanceTimeAndRun(30_000)
        dispatch(BLOCKED_PACKAGE)

        coVerify(exactly = 1) { getNextVocabularyItemUseCase() }
        verify(exactly = 1) { windowManager.addView(any(), any()) }
        assertThat(audio.lastAudioFocusRequest).isSameInstanceAs(previousFocus)
        assertThat(shadowOf(service).nextStartedActivity).isNull()

        advanceTimeAndRun(239_999)
        coVerify(exactly = 1) { getNextVocabularyItemUseCase() }
        advanceTimeAndRun(1)
        coVerify(exactly = 2) { getNextVocabularyItemUseCase() }
    }

    @Test
    fun `cooldown expiry while staying in admitted app causes no entry gate`() {
        policyFlow.value = LearningPreferencesConfig(gateCooldownMinutes = 1)
        blockedAppsFlow.value = setOf(BLOCKED_PACKAGE)
        advanceUntilIdle()
        dispatch(BLOCKED_PACKAGE)
        completeOverlay()

        advanceTimeAndRun(60_000)
        dispatch(BLOCKED_PACKAGE)

        coVerify(exactly = 1) { getNextVocabularyItemUseCase() }
    }

    @Test
    fun `empty repeat recovers at the next interval without immediate loop`() {
        policyFlow.value = LearningPreferencesConfig(overlayInterval = 1)
        blockedAppsFlow.value = setOf(BLOCKED_PACKAGE)
        advanceUntilIdle()
        dispatch(BLOCKED_PACKAGE)
        completeOverlay()
        coEvery { getNextVocabularyItemUseCase() } returnsMany
            listOf(Result.failure(NoAvailableItemsException()), Result.success(sampleItem))

        advanceTimeAndRun(60_000)
        coVerify(exactly = 2) { getNextVocabularyItemUseCase() }
        advanceTimeAndRun(59_999)
        coVerify(exactly = 2) { getNextVocabularyItemUseCase() }
        advanceTimeAndRun(1)

        coVerify(exactly = 3) { getNextVocabularyItemUseCase() }
        verify(exactly = 2) { windowManager.addView(any(), any()) }
    }

    @Test
    fun `empty entry retries are bounded to one second`() {
        blockedAppsFlow.value = setOf(BLOCKED_PACKAGE)
        coEvery { getNextVocabularyItemUseCase() } returns Result.failure(NoAvailableItemsException())
        advanceUntilIdle()
        dispatch(BLOCKED_PACKAGE)
        advanceTimeAndRun(999)
        dispatch(BLOCKED_PACKAGE)
        coVerify(exactly = 1) { getNextVocabularyItemUseCase() }
        advanceTimeAndRun(1)
        dispatch(BLOCKED_PACKAGE)
        coVerify(exactly = 2) { getNextVocabularyItemUseCase() }
    }

    @Test
    fun `interval changes reschedule from release and zero cancels`() {
        policyFlow.value = LearningPreferencesConfig(overlayInterval = 5)
        blockedAppsFlow.value = setOf(BLOCKED_PACKAGE)
        advanceUntilIdle()
        dispatch(BLOCKED_PACKAGE)
        completeOverlay()
        advanceTimeAndRun(60_000)
        policyFlow.value = LearningPreferencesConfig(overlayInterval = 2)
        advanceUntilIdle()
        advanceTimeAndRun(59_999)
        coVerify(exactly = 1) { getNextVocabularyItemUseCase() }
        advanceTimeAndRun(1)
        coVerify(exactly = 2) { getNextVocabularyItemUseCase() }
        completeOverlay()
        policyFlow.value = LearningPreferencesConfig(overlayInterval = 0)
        advanceUntilIdle()
        advanceTimeAndRun(300_000)
        coVerify(exactly = 2) { getNextVocabularyItemUseCase() }
    }

    @Test
    fun `equal cooldown and repeat deadline produce one gate with an entry race`() {
        policyFlow.value = LearningPreferencesConfig(gateCooldownMinutes = 1, overlayInterval = 1)
        blockedAppsFlow.value = setOf(BLOCKED_PACKAGE, OTHER_BLOCKED_PACKAGE)
        advanceUntilIdle()
        dispatch(BLOCKED_PACKAGE)
        completeOverlay()
        ShadowSystemClock.advanceBy(Duration.ofMillis(60_000))
        mainDispatcherRule.testDispatcher.scheduler.advanceTimeBy(60_000)
        dispatch(OTHER_BLOCKED_PACKAGE)

        verify(exactly = 2) { windowManager.addView(any(), any()) }
    }
}
