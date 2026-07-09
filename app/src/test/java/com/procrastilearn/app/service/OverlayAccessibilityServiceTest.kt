package com.procrastilearn.app.service

import android.content.Context
import android.media.AudioManager
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.data.local.prefs.DayCountersStore
import com.procrastilearn.app.data.repository.NoAvailableItemsException
import com.procrastilearn.app.domain.model.LearningPreferencesConfig
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.domain.repository.AppPreferencesRepository
import com.procrastilearn.app.domain.repository.VocabularyRepository
import com.procrastilearn.app.domain.usecase.GetNextVocabularyItemUseCase
import com.procrastilearn.app.domain.usecase.SaveDifficultyRatingUseCase
import com.procrastilearn.app.utils.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSystemClock
import org.robolectric.util.ReflectionHelpers
import java.time.Duration

private const val BLOCKED_PACKAGE = "com.example.blocked"
private const val OTHER_BLOCKED_PACKAGE = "com.example.blocked.two"
private const val LEGIT_PACKAGE = "com.example.legit"
private const val DEBOUNCE_MILLIS = 100L

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class OverlayAccessibilityServiceTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val appPreferencesRepository = mockk<AppPreferencesRepository>()
    private val vocabularyRepository = mockk<VocabularyRepository>()
    private val getNextVocabularyItemUseCase = mockk<GetNextVocabularyItemUseCase>()
    private val getSaveDifficultyRatingUseCase = mockk<SaveDifficultyRatingUseCase>()
    private val dayCountersStore = mockk<DayCountersStore>()
    private val windowManager = mockk<WindowManager>(relaxed = true)

    private val blockedAppsFlow = MutableStateFlow<Set<String>>(emptySet())
    private val enabledFlow = MutableStateFlow(true)
    private val policyFlow = MutableStateFlow(LearningPreferencesConfig(overlayInterval = 0))

    private val sampleItem = VocabularyItem(id = 1L, word = "Haus", translation = "House", isNew = false)

    private lateinit var service: OverlayAccessibilityService

    @Before
    fun setUp() {
        every { appPreferencesRepository.getBlockedApps() } returns blockedAppsFlow
        every { appPreferencesRepository.isProcrastilearnEnabled() } returns enabledFlow
        every { dayCountersStore.readPolicy() } returns policyFlow
        coEvery { getNextVocabularyItemUseCase() } returns Result.success(sampleItem)

        service = Robolectric.buildService(OverlayAccessibilityService::class.java).create().get()
        service.windowManager = windowManager
        service.appPreferencesRepository = appPreferencesRepository
        service.vocabularyRepository = vocabularyRepository
        service.getNextVocabularyItemUseCase = getNextVocabularyItemUseCase
        service.getSaveDifficultyRatingUseCase = getSaveDifficultyRatingUseCase
        service.dayCountersStore = dayCountersStore

        connectService()
    }

    private fun connectService() {
        ReflectionHelpers.callInstanceMethod<Unit>(service, "onServiceConnected")
        advanceUntilIdle()
    }

    private fun advanceUntilIdle() = mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

    private fun advanceTimeAndRun(millis: Long) {
        mainDispatcherRule.testDispatcher.scheduler.advanceTimeBy(millis)
        mainDispatcherRule.testDispatcher.scheduler.runCurrent()
    }

    private fun eventFor(
        pkg: String,
        cls: String = "com.example.MainActivity",
        eventType: Int = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
    ): AccessibilityEvent =
        AccessibilityEvent(eventType).apply {
            packageName = pkg
            className = cls
        }

    private fun dispatch(
        pkg: String,
        cls: String = "com.example.MainActivity",
        eventType: Int = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
    ) {
        service.onAccessibilityEvent(eventFor(pkg, cls, eventType))
        advanceUntilIdle()
    }

    private fun advanceRealClockPastDebounceWindow() {
        ShadowSystemClock.advanceBy(Duration.ofMillis(DEBOUNCE_MILLIS))
    }

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
    fun `rapid duplicate events within debounce window are dropped`() {
        blockedAppsFlow.value = setOf(BLOCKED_PACKAGE)
        advanceUntilIdle()
        dispatch(BLOCKED_PACKAGE)
        verify(exactly = 1) { windowManager.addView(any(), any()) }

        service.onAccessibilityEvent(eventFor(LEGIT_PACKAGE))
        advanceUntilIdle()

        verify(exactly = 0) { windowManager.removeView(any()) }

        advanceRealClockPastDebounceWindow()
        dispatch(LEGIT_PACKAGE)

        verify(exactly = 1) { windowManager.removeView(any()) }
    }

    @Test
    fun `blocked app starts a gate session and shows the overlay`() {
        blockedAppsFlow.value = setOf(BLOCKED_PACKAGE)
        advanceUntilIdle()

        dispatch(BLOCKED_PACKAGE)

        coVerify(exactly = 1) { getNextVocabularyItemUseCase() }
        verify(exactly = 1) { windowManager.addView(any(), any()) }

        val audioManager =
            ApplicationProvider.getApplicationContext<Context>()
                .getSystemService(Context.AUDIO_SERVICE) as AudioManager
        assertThat(shadowOf(audioManager).lastAudioFocusRequest).isNotNull()
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
    fun `leaving a blocked app for a legit app ends the gate session and releases audio focus`() {
        blockedAppsFlow.value = setOf(BLOCKED_PACKAGE)
        advanceUntilIdle()
        dispatch(BLOCKED_PACKAGE)

        advanceRealClockPastDebounceWindow()
        dispatch(LEGIT_PACKAGE)

        verify(exactly = 1) { windowManager.removeView(any()) }

        val audioManager =
            ApplicationProvider.getApplicationContext<Context>()
                .getSystemService(Context.AUDIO_SERVICE) as AudioManager
        assertThat(shadowOf(audioManager).lastAbandonedAudioFocusRequest).isNotNull()
    }

    @Test
    fun `no available items keeps the gate inactive so it retries on the next event`() {
        blockedAppsFlow.value = setOf(BLOCKED_PACKAGE)
        advanceUntilIdle()
        coEvery { getNextVocabularyItemUseCase() } returns Result.failure(NoAvailableItemsException())

        dispatch(BLOCKED_PACKAGE)

        verify(exactly = 0) { windowManager.addView(any(), any()) }

        coEvery { getNextVocabularyItemUseCase() } returns Result.success(sampleItem)
        advanceRealClockPastDebounceWindow()
        dispatch(BLOCKED_PACKAGE)

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
    fun `startIntervalTimer does nothing when interval is zero`() {
        policyFlow.value = LearningPreferencesConfig(overlayInterval = 0)
        advanceUntilIdle()
        blockedAppsFlow.value = setOf(BLOCKED_PACKAGE)
        advanceUntilIdle()
        dispatch(BLOCKED_PACKAGE)
        ReflectionHelpers.callInstanceMethod<Unit>(service, "hideOverlay")

        service.startIntervalTimer()
        advanceTimeAndRun(Duration.ofDays(1).toMillis())

        coVerify(exactly = 1) { getNextVocabularyItemUseCase() }
    }

    @Test
    fun `startIntervalTimer does nothing while the overlay is still showing`() {
        policyFlow.value = LearningPreferencesConfig(overlayInterval = 5)
        advanceUntilIdle()
        blockedAppsFlow.value = setOf(BLOCKED_PACKAGE)
        advanceUntilIdle()
        dispatch(BLOCKED_PACKAGE)

        // Overlay is still showing (no hideOverlay call), so the guard should block scheduling.
        service.startIntervalTimer()
        advanceTimeAndRun(Duration.ofMinutes(10).toMillis())

        coVerify(exactly = 1) { getNextVocabularyItemUseCase() }
    }

    @Test
    fun `startIntervalTimer does nothing when ProcrastiLearn is disabled`() {
        policyFlow.value = LearningPreferencesConfig(overlayInterval = 5)
        advanceUntilIdle()
        blockedAppsFlow.value = setOf(BLOCKED_PACKAGE)
        advanceUntilIdle()
        dispatch(BLOCKED_PACKAGE)
        ReflectionHelpers.callInstanceMethod<Unit>(service, "hideOverlay")

        enabledFlow.value = false
        advanceUntilIdle()

        service.startIntervalTimer()
        advanceTimeAndRun(Duration.ofMinutes(10).toMillis())

        coVerify(exactly = 1) { getNextVocabularyItemUseCase() }
    }

    @Test
    fun `startIntervalTimer re-shows the overlay with a fresh word once the interval elapses`() {
        policyFlow.value = LearningPreferencesConfig(overlayInterval = 5)
        advanceUntilIdle()
        blockedAppsFlow.value = setOf(BLOCKED_PACKAGE)
        advanceUntilIdle()
        dispatch(BLOCKED_PACKAGE)
        verify(exactly = 1) { windowManager.addView(any(), any()) }

        // Simulate the user having answered: the overlay is hidden but the gate session
        // (gateActive/gatedPackage) is still logically open, exactly as production leaves it
        // between OverlayScreen's onUnlock callback and the timer firing again.
        ReflectionHelpers.callInstanceMethod<Unit>(service, "hideOverlay")

        service.startIntervalTimer()
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
        ReflectionHelpers.callInstanceMethod<Unit>(service, "hideOverlay")
        service.startIntervalTimer()

        advanceRealClockPastDebounceWindow()
        dispatch(LEGIT_PACKAGE)

        advanceTimeAndRun(Duration.ofMinutes(5).toMillis())

        coVerify(exactly = 1) { getNextVocabularyItemUseCase() }
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
}
