package com.procrastilearn.app.service

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.PowerManager
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.data.repository.NoAvailableItemsException
import com.procrastilearn.app.domain.model.LearningPreferencesConfig
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.overlay.GateCompletion
import io.github.openspacedrepetition.Rating
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSystemClock
import org.robolectric.util.ReflectionHelpers
import java.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class OverlayAccessibilityServiceTest : OverlayAccessibilityServiceTestFixture() {
    @Test
    fun `ignores events that are not window state changes`() {
        blockedAppsFlow.value = setOf(BLOCKED_PACKAGE)
        advanceUntilIdle()

        dispatch(BLOCKED_PACKAGE, eventType = AccessibilityEvent.TYPE_VIEW_CLICKED)

        coVerify(exactly = 0) { getNextVocabularyItemUseCase() }
    }

    @Test
    fun `ignores events with no package name`() {
        service.onAccessibilityEvent(AccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED))
        advanceUntilIdle()

        coVerify(exactly = 0) { getNextVocabularyItemUseCase() }
    }

    @Test
    fun `ignores events from its own package`() {
        blockedAppsFlow.value = setOf(service.packageName)
        advanceUntilIdle()

        dispatch(service.packageName)

        coVerify(exactly = 0) { getNextVocabularyItemUseCase() }
    }

    @Test
    fun `ignores input method packages even when blocked`() {
        blockedAppsFlow.value = setOf("com.google.android.inputmethod.latin")
        advanceUntilIdle()

        dispatch("com.google.android.inputmethod.latin")

        coVerify(exactly = 0) { getNextVocabularyItemUseCase() }
    }

    @Test
    fun `ignores packages whose class name indicates an input method even when blocked`() {
        blockedAppsFlow.value = setOf(BLOCKED_PACKAGE)
        advanceUntilIdle()

        dispatch(BLOCKED_PACKAGE, cls = "com.example.SomeInputMethodService")

        coVerify(exactly = 0) { getNextVocabularyItemUseCase() }
    }

    @Test
    fun `ignores known system packages even when blocked`() {
        blockedAppsFlow.value = setOf("com.android.systemui")
        advanceUntilIdle()

        dispatch("com.android.systemui")

        coVerify(exactly = 0) { getNextVocabularyItemUseCase() }
    }

    @Test
    fun `rapid departure cancels the overlay without global debounce`() {
        blockedAppsFlow.value = setOf(BLOCKED_PACKAGE)
        advanceUntilIdle()
        dispatch(BLOCKED_PACKAGE)
        verify(exactly = 1) { windowManager.addView(any(), any()) }

        service.onAccessibilityEvent(eventFor(LEGIT_PACKAGE))
        advanceUntilIdle()

        verify(exactly = 1) { windowManager.removeView(any()) }

        advanceRealClockPastDebounceWindow()
        dispatch(LEGIT_PACKAGE)

        verify(exactly = 1) { windowManager.removeView(any()) }
    }

    @Test
    fun `removing the foreground package cancels its overlay immediately`() {
        blockedAppsFlow.value = setOf(BLOCKED_PACKAGE)
        advanceUntilIdle()
        dispatch(BLOCKED_PACKAGE)

        blockedAppsFlow.value = emptySet()
        advanceUntilIdle()

        verify(exactly = 1) { windowManager.removeView(any()) }
    }

    @Test
    fun `rapid app switch rejects a cancelled first card that returns late`() {
        blockedAppsFlow.value = setOf(BLOCKED_PACKAGE, OTHER_BLOCKED_PACKAGE)
        advanceUntilIdle()
        val lateItem = CompletableDeferred<Result<VocabularyItem>>()
        coEvery { getNextVocabularyItemUseCase() } coAnswers {
            withContext(NonCancellable) { lateItem.await() }
        } andThen Result.success(sampleItem.copy(id = 2L))

        dispatch(BLOCKED_PACKAGE)
        dispatch(OTHER_BLOCKED_PACKAGE)
        lateItem.complete(Result.success(sampleItem))
        advanceUntilIdle()

        verify(exactly = 1) { windowManager.addView(any(), any()) }
        assertThat(
            activeOverlayViewModel()
                .uiState.value.vocabularyItem
                ?.id,
        ).isEqualTo(2L)
    }

    @Test
    fun `departure rejects a first card that ignores cancellation`() {
        assertLateLoadCannotAttach { dispatch(LEGIT_PACKAGE) }
    }

    @Test
    fun `disable rejects a first card that ignores cancellation`() {
        assertLateLoadCannotAttach { enabledFlow.value = false }
    }

    @Test
    fun `membership removal rejects a first card that ignores cancellation`() {
        assertLateLoadCannotAttach { blockedAppsFlow.value = emptySet() }
    }

    @Test
    fun `screen off rejects a first card that ignores cancellation`() {
        assertLateLoadCannotAttach {
            service.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
            shadowOf(android.os.Looper.getMainLooper()).idle()
        }
    }

    @Test
    fun `destruction rejects a first card that ignores cancellation`() {
        assertLateLoadCannotAttach { service.onDestroy() }
    }

    private fun assertLateLoadCannotAttach(invalidate: () -> Unit) {
        blockedAppsFlow.value = setOf(BLOCKED_PACKAGE)
        advanceUntilIdle()
        val result = CompletableDeferred<Result<VocabularyItem>>()
        coEvery { getNextVocabularyItemUseCase() } coAnswers { withContext(NonCancellable) { result.await() } }
        dispatch(BLOCKED_PACKAGE)
        invalidate()
        advanceUntilIdle()

        result.complete(Result.success(sampleItem))
        advanceUntilIdle()

        verify(exactly = 0) { windowManager.addView(any(), any()) }
    }

    @Test
    fun `first app event waits for every initial preference value`() {
        service.onDestroy()
        val blocked = MutableSharedFlow<Set<String>>(replay = 1)
        val enabled = MutableSharedFlow<Boolean>(replay = 1)
        val policy = MutableSharedFlow<LearningPreferencesConfig>(replay = 1)
        every { appPreferencesRepository.getBlockedApps() } returns blocked
        every { appPreferencesRepository.isProcrastilearnEnabled() } returns enabled
        every { dayCountersStore.readPolicy() } returns policy
        createService()
        connectService()
        dispatch(BLOCKED_PACKAGE)
        blocked.tryEmit(setOf(BLOCKED_PACKAGE))
        enabled.tryEmit(true)
        advanceUntilIdle()
        coVerify(exactly = 0) { getNextVocabularyItemUseCase() }

        policy.tryEmit(LearningPreferencesConfig())
        advanceUntilIdle()

        coVerify(exactly = 1) { getNextVocabularyItemUseCase() }
        verify(exactly = 1) { windowManager.addView(any(), any()) }
    }

    @Test
    fun `first app event does not wait for background audio preference`() {
        service.onDestroy()
        val blocked = MutableSharedFlow<Set<String>>(replay = 1)
        val enabled = MutableSharedFlow<Boolean>(replay = 1)
        val policy = MutableSharedFlow<LearningPreferencesConfig>(replay = 1)
        val pauseBackgroundAudio = MutableSharedFlow<Boolean>()
        every { appPreferencesRepository.getBlockedApps() } returns blocked
        every { appPreferencesRepository.isProcrastilearnEnabled() } returns enabled
        every { appPreferencesRepository.pauseBackgroundAudio() } returns pauseBackgroundAudio
        every { dayCountersStore.readPolicy() } returns policy
        createService()
        connectService()
        dispatch(BLOCKED_PACKAGE)
        blocked.tryEmit(setOf(BLOCKED_PACKAGE))
        enabled.tryEmit(true)
        policy.tryEmit(LearningPreferencesConfig())
        advanceUntilIdle()

        coVerify(exactly = 1) { getNextVocabularyItemUseCase() }
        verify(exactly = 1) { windowManager.addView(any(), any()) }
    }

    @Test
    fun `locked device prevents initial card loading`() {
        blockedAppsFlow.value = setOf(BLOCKED_PACKAGE)
        advanceUntilIdle()
        shadowOf(service.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).setKeyguardLocked(true)

        dispatch(BLOCKED_PACKAGE)

        coVerify(exactly = 0) { getNextVocabularyItemUseCase() }
    }

    @Test
    fun `noninteractive device prevents initial card loading`() {
        blockedAppsFlow.value = setOf(BLOCKED_PACKAGE)
        advanceUntilIdle()
        shadowOf(service.getSystemService(Context.POWER_SERVICE) as PowerManager).turnScreenOn(false)

        dispatch(BLOCKED_PACKAGE)

        coVerify(exactly = 0) { getNextVocabularyItemUseCase() }
    }

    @Test
    fun `device locking during loading prevents final attachment`() {
        assertLateLoadCannotAttach {
            shadowOf(service.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).setKeyguardLocked(true)
        }
    }

    @Test
    fun `device becoming noninteractive during loading prevents final attachment`() {
        assertLateLoadCannotAttach {
            shadowOf(service.getSystemService(Context.POWER_SERVICE) as PowerManager).turnScreenOn(false)
        }
    }

    @Test
    fun `blocked app starts a gate session and shows the overlay`() {
        blockedAppsFlow.value = setOf(BLOCKED_PACKAGE)
        advanceUntilIdle()

        dispatch(BLOCKED_PACKAGE)

        coVerify(exactly = 1) { getNextVocabularyItemUseCase() }
        verify(exactly = 1) { windowManager.addView(any(), any()) }

        val audioManager =
            ApplicationProvider
                .getApplicationContext<Context>()
                .getSystemService(Context.AUDIO_SERVICE) as AudioManager
        assertThat(shadowOf(audioManager).lastAudioFocusRequest).isNotNull()
    }

    @Test
    fun `initial gate snapshots the configured cards per gate`() {
        policyFlow.value = LearningPreferencesConfig(cardsPerGate = 3)
        blockedAppsFlow.value = setOf(BLOCKED_PACKAGE)
        advanceUntilIdle()

        dispatch(BLOCKED_PACKAGE)

        assertThat(activeOverlayViewModel().uiState.value.requiredCards).isEqualTo(3)
        assertThat(activeOverlayViewModel().uiState.value.completedCards).isEqualTo(0)
    }

    @Test
    fun `interval gate snapshots the latest target into a fresh gate`() {
        policyFlow.value = LearningPreferencesConfig(overlayInterval = 5, cardsPerGate = 2)
        blockedAppsFlow.value = setOf(BLOCKED_PACKAGE)
        advanceUntilIdle()
        dispatch(BLOCKED_PACKAGE)
        val initialViewModel = activeOverlayViewModel()

        policyFlow.value = LearningPreferencesConfig(overlayInterval = 5, cardsPerGate = 4)
        advanceUntilIdle()
        assertThat(initialViewModel.uiState.value.requiredCards).isEqualTo(2)

        completeOverlay()
        advanceTimeAndRun(Duration.ofMinutes(5).toMillis())

        val intervalViewModel = activeOverlayViewModel()
        assertThat(intervalViewModel.uiState.value.requiredCards).isEqualTo(4)
        assertThat(intervalViewModel.uiState.value.completedCards).isEqualTo(0)
        verify(exactly = 2) { windowManager.addView(any(), any()) }
    }

    @Test
    fun `leaving and reopening a blocked app resets gate progress`() {
        policyFlow.value = LearningPreferencesConfig(cardsPerGate = 3)
        coEvery { getSaveDifficultyRatingUseCase(any(), any(), any()) } returns Result.success(Unit)
        blockedAppsFlow.value = setOf(BLOCKED_PACKAGE)
        advanceUntilIdle()
        dispatch(BLOCKED_PACKAGE)
        val firstViewModel = activeOverlayViewModel()

        firstViewModel.onDifficultySelected(Rating.GOOD)
        advanceUntilIdle()
        assertThat(firstViewModel.uiState.value.completedCards).isEqualTo(1)

        advanceRealClockPastDebounceWindow()
        dispatch(LEGIT_PACKAGE)
        advanceRealClockPastDebounceWindow()
        dispatch(BLOCKED_PACKAGE)

        val reopenedViewModel = activeOverlayViewModel()
        assertThat(reopenedViewModel.uiState.value.requiredCards).isEqualTo(3)
        assertThat(reopenedViewModel.uiState.value.completedCards).isEqualTo(0)
    }

    @Test
    fun `repeated events for the same active blocked app do not restart the session`() {
        blockedAppsFlow.value = setOf(BLOCKED_PACKAGE)
        advanceUntilIdle()
        dispatch(BLOCKED_PACKAGE)

        advanceRealClockPastDebounceWindow()
        dispatch(BLOCKED_PACKAGE)
        advanceRealClockPastDebounceWindow()
        dispatch(BLOCKED_PACKAGE)

        coVerify(exactly = 1) { getNextVocabularyItemUseCase() }
        verify(exactly = 1) { windowManager.addView(any(), any()) }
    }

    @Test
    fun `switching to a different blocked app ends the old session and starts a new one`() {
        blockedAppsFlow.value = setOf(BLOCKED_PACKAGE, OTHER_BLOCKED_PACKAGE)
        advanceUntilIdle()
        dispatch(BLOCKED_PACKAGE)

        advanceRealClockPastDebounceWindow()
        dispatch(OTHER_BLOCKED_PACKAGE)

        coVerify(exactly = 2) { getNextVocabularyItemUseCase() }
        verify(exactly = 1) { windowManager.removeView(any()) }
        verify(exactly = 2) { windowManager.addView(any(), any()) }
    }

    @Test
    fun `no available items keeps the gate inactive so it retries on the next event`() {
        blockedAppsFlow.value = setOf(BLOCKED_PACKAGE)
        advanceUntilIdle()
        coEvery { getNextVocabularyItemUseCase() } returns Result.failure(NoAvailableItemsException())

        dispatch(BLOCKED_PACKAGE)

        verify(exactly = 0) { windowManager.addView(any(), any()) }

        coEvery { getNextVocabularyItemUseCase() } returns Result.success(sampleItem)
        advanceTimeAndRun(1_000)
        dispatch(BLOCKED_PACKAGE)

        coVerify(exactly = 2) { getNextVocabularyItemUseCase() }
        verify(exactly = 1) { windowManager.addView(any(), any()) }
    }

    @Test
    fun `no available items on an interval gate leaves the overlay hidden`() {
        policyFlow.value = LearningPreferencesConfig(overlayInterval = 5, cardsPerGate = 2)
        coEvery { getNextVocabularyItemUseCase() } returnsMany
            listOf(Result.success(sampleItem), Result.failure(NoAvailableItemsException()))
        blockedAppsFlow.value = setOf(BLOCKED_PACKAGE)
        advanceUntilIdle()
        dispatch(BLOCKED_PACKAGE)
        completeOverlay()
        advanceTimeAndRun(Duration.ofMinutes(5).toMillis())

        coVerify(exactly = 2) { getNextVocabularyItemUseCase() }
        verify(exactly = 1) { windowManager.addView(any(), any()) }
    }

    @Test
    fun `generic failure loading a word keeps the overlay hidden`() {
        blockedAppsFlow.value = setOf(BLOCKED_PACKAGE)
        advanceUntilIdle()
        coEvery { getNextVocabularyItemUseCase() } returns Result.failure(RuntimeException("boom"))

        dispatch(BLOCKED_PACKAGE)

        verify(exactly = 0) { windowManager.addView(any(), any()) }
    }

    @Test
    fun `generic first card load failure retries on the next blocked app event`() {
        blockedAppsFlow.value = setOf(BLOCKED_PACKAGE)
        advanceUntilIdle()
        coEvery { getNextVocabularyItemUseCase() } returnsMany
            listOf(Result.failure(RuntimeException("temporary")), Result.success(sampleItem))

        dispatch(BLOCKED_PACKAGE)

        verify(exactly = 0) { windowManager.addView(any(), any()) }
        coVerify(exactly = 1) { getNextVocabularyItemUseCase() }

        advanceTimeAndRun(1_000)
        dispatch(BLOCKED_PACKAGE)

        coVerify(exactly = 2) { getNextVocabularyItemUseCase() }
        verify(exactly = 1) { windowManager.addView(any(), any()) }
        assertThat(activeOverlayViewModel().uiState.value.vocabularyItem).isEqualTo(sampleItem)
    }

    @Test
    fun `disabling ProcrastiLearn while a gate is active ends the session`() {
        blockedAppsFlow.value = setOf(BLOCKED_PACKAGE)
        advanceUntilIdle()
        dispatch(BLOCKED_PACKAGE)
        verify(exactly = 1) { windowManager.addView(any(), any()) }

        enabledFlow.value = false
        advanceUntilIdle()

        verify(exactly = 1) { windowManager.removeView(any()) }
    }

    @Test
    fun `disabling ProcrastiLearn with no active gate is a safe no-op`() {
        enabledFlow.value = false
        advanceUntilIdle()

        verify(exactly = 0) { windowManager.removeView(any()) }
        verify(exactly = 0) { windowManager.addView(any(), any()) }
    }

    @Test
    fun `blocked app events are ignored while ProcrastiLearn is disabled`() {
        enabledFlow.value = false
        advanceUntilIdle()
        blockedAppsFlow.value = setOf(BLOCKED_PACKAGE)
        advanceUntilIdle()

        dispatch(BLOCKED_PACKAGE)

        coVerify(exactly = 0) { getNextVocabularyItemUseCase() }
    }

    @Test
    fun `re-enabling ProcrastiLearn allows a new gate session to start`() {
        enabledFlow.value = false
        advanceUntilIdle()
        blockedAppsFlow.value = setOf(BLOCKED_PACKAGE)
        advanceUntilIdle()
        dispatch(BLOCKED_PACKAGE)
        coVerify(exactly = 0) { getNextVocabularyItemUseCase() }

        enabledFlow.value = true
        advanceUntilIdle()
        advanceRealClockPastDebounceWindow()
        dispatch(BLOCKED_PACKAGE)

        coVerify(exactly = 1) { getNextVocabularyItemUseCase() }
        verify(exactly = 1) { windowManager.addView(any(), any()) }
    }

    @Test
    fun `onDestroy hides an active overlay without throwing`() {
        blockedAppsFlow.value = setOf(BLOCKED_PACKAGE)
        advanceUntilIdle()
        dispatch(BLOCKED_PACKAGE)

        service.onDestroy()

        verify(exactly = 1) { windowManager.removeView(any()) }
    }

    @Test
    fun `onDestroy with no active overlay is a safe no-op`() {
        service.onDestroy()

        verify(exactly = 0) { windowManager.removeView(any()) }
    }

    @Test
    fun `cancelled old completion cannot release a new app gate`() {
        makePackageLaunchable(BLOCKED_PACKAGE)
        makePackageLaunchable(OTHER_BLOCKED_PACKAGE)
        blockedAppsFlow.value = setOf(BLOCKED_PACKAGE, OTHER_BLOCKED_PACKAGE)
        advanceUntilIdle()
        dispatch(BLOCKED_PACKAGE)
        val staleCallback = completionCallback()
        dispatch(OTHER_BLOCKED_PACKAGE)

        staleCallback(GateCompletion(1))
        advanceUntilIdle()

        verify(exactly = 1) { windowManager.removeView(any()) }
        assertThat(activeOverlayViewModel().uiState.value.vocabularyItem).isEqualTo(sampleItem)
        assertThat(shadowOf(service).nextStartedActivity).isNull()
    }

    @Test
    fun `failed attachment leaves no focus overlay or cooldown`() {
        blockedAppsFlow.value = setOf(BLOCKED_PACKAGE)
        every { windowManager.addView(any(), any()) } throws WindowManager.BadTokenException("bad token")
        advanceUntilIdle()
        dispatch(BLOCKED_PACKAGE)

        assertThat(ReflectionHelpers.getField<android.view.View?>(service, "overlayView")).isNull()
        assertThat(ReflectionHelpers.getField<Any?>(service, "focusRequest")).isNull()
        assertThat(
            ReflectionHelpers.getField<GateSessionController>(service, "controller").state.lastReviewedReleaseElapsedMs,
        ).isNull()
    }

    @Test
    fun `screen unlock requires a fresh actual app event`() {
        blockedAppsFlow.value = setOf(BLOCKED_PACKAGE)
        advanceUntilIdle()
        dispatch(BLOCKED_PACKAGE)
        service.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
        shadowOf(android.os.Looper.getMainLooper()).idle()
        advanceUntilIdle()
        service.sendBroadcast(Intent(Intent.ACTION_USER_PRESENT))
        shadowOf(android.os.Looper.getMainLooper()).idle()
        advanceUntilIdle()
        dispatch("com.android.systemui")
        coVerify(exactly = 1) { getNextVocabularyItemUseCase() }

        dispatch(BLOCKED_PACKAGE)

        coVerify(exactly = 2) { getNextVocabularyItemUseCase() }
        verify(exactly = 1) { windowManager.removeView(any()) }
    }

    @Test
    fun `own compose overlay events preserve an active gate`() {
        blockedAppsFlow.value = setOf(BLOCKED_PACKAGE)
        advanceUntilIdle()
        dispatch(BLOCKED_PACKAGE)

        dispatch(service.packageName, "androidx.compose.ui.platform.ComposeView")
        dispatch("com.android.systemui")
        dispatch("com.google.android.inputmethod.latin")

        verify(exactly = 0) { windowManager.removeView(any()) }
        coVerify(exactly = 1) { getNextVocabularyItemUseCase() }
    }

    @Test
    fun `own activity resume cancels gate and forbids attachment until pause`() {
        blockedAppsFlow.value = setOf(BLOCKED_PACKAGE)
        advanceUntilIdle()
        dispatch(BLOCKED_PACKAGE)
        val activity = mockk<Activity>()

        ownActivityForegroundStore.onActivityResumed(activity)
        advanceUntilIdle()
        dispatch(BLOCKED_PACKAGE)
        verify(exactly = 1) { windowManager.removeView(any()) }
        coVerify(exactly = 1) { getNextVocabularyItemUseCase() }
        ownActivityForegroundStore.onActivityPaused(activity)
        advanceUntilIdle()
        coVerify(exactly = 1) { getNextVocabularyItemUseCase() }
        dispatch(BLOCKED_PACKAGE)
        coVerify(exactly = 2) { getNextVocabularyItemUseCase() }
    }

    @Test
    fun `own activity package event corroborates real departure`() {
        blockedAppsFlow.value = setOf(BLOCKED_PACKAGE)
        advanceUntilIdle()
        dispatch(BLOCKED_PACKAGE)

        dispatch(service.packageName, "com.procrastilearn.app.MainActivity")

        verify(exactly = 1) { windowManager.removeView(any()) }
    }

    @Test
    fun `own activity resume rejects a first card that ignores cancellation`() {
        assertLateLoadCannotAttach { ownActivityForegroundStore.onActivityResumed(mockk<Activity>()) }
    }

    @Test
    fun `attempt snapshots target before first card loading`() {
        policyFlow.value = LearningPreferencesConfig(cardsPerGate = 3)
        blockedAppsFlow.value = setOf(BLOCKED_PACKAGE)
        advanceUntilIdle()
        val result = CompletableDeferred<Result<VocabularyItem>>()
        coEvery { getNextVocabularyItemUseCase() } coAnswers { result.await() }
        dispatch(BLOCKED_PACKAGE)
        policyFlow.value = LearningPreferencesConfig(cardsPerGate = 4)
        advanceUntilIdle()

        result.complete(Result.success(sampleItem))
        advanceUntilIdle()

        assertThat(activeOverlayViewModel().uiState.value.requiredCards).isEqualTo(3)
    }

    @Test
    fun `departure during repeat loading rejects a late first card`() {
        policyFlow.value = LearningPreferencesConfig(overlayInterval = 1)
        blockedAppsFlow.value = setOf(BLOCKED_PACKAGE)
        advanceUntilIdle()
        dispatch(BLOCKED_PACKAGE)
        completeOverlay()
        val result = CompletableDeferred<Result<VocabularyItem>>()
        coEvery { getNextVocabularyItemUseCase() } coAnswers { withContext(NonCancellable) { result.await() } }
        advanceTimeAndRun(60_000)
        dispatch(LEGIT_PACKAGE)

        result.complete(Result.success(sampleItem))
        advanceUntilIdle()

        verify(exactly = 1) { windowManager.addView(any(), any()) }
    }

    @Test
    fun `unrelated policy emissions leave the repeat timer unchanged`() {
        policyFlow.value = LearningPreferencesConfig(overlayInterval = 5)
        blockedAppsFlow.value = setOf(BLOCKED_PACKAGE)
        advanceUntilIdle()
        dispatch(BLOCKED_PACKAGE)
        completeOverlay()
        val controller = ReflectionHelpers.getField<GateSessionController>(service, "controller")
        val revision = controller.state.timerRevision
        val deadline = controller.state.nextRepeatDeadlineElapsedMs

        policyFlow.value = policyFlow.value.copy(newPerDay = 40, ratingDelaySeconds = 2)
        advanceUntilIdle()

        assertThat(controller.state.timerRevision).isEqualTo(revision)
        assertThat(controller.state.nextRepeatDeadlineElapsedMs).isEqualTo(deadline)
    }

    @Test
    fun `reviewed release timestamp is captured after view removal`() {
        blockedAppsFlow.value = setOf(BLOCKED_PACKAGE)
        advanceUntilIdle()
        dispatch(BLOCKED_PACKAGE)
        val beforeRemoval = android.os.SystemClock.elapsedRealtime()
        every { windowManager.removeView(any()) } answers {
            ShadowSystemClock.advanceBy(Duration.ofMillis(500))
        }

        completeOverlay()

        val controller = ReflectionHelpers.getField<GateSessionController>(service, "controller")
        assertThat(controller.state.lastReviewedReleaseElapsedMs).isEqualTo(beforeRemoval + 500)
    }
}
