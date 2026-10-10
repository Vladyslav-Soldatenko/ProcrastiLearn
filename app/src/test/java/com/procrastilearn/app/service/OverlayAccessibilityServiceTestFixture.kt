package com.procrastilearn.app.service

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.lifecycle.ViewModelProvider
import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.data.local.prefs.DayCountersStore
import com.procrastilearn.app.domain.model.LearningPreferencesConfig
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.domain.repository.AppPreferencesRepository
import com.procrastilearn.app.domain.repository.VocabularyStudyRepository
import com.procrastilearn.app.domain.usecase.GetNextVocabularyItemUseCase
import com.procrastilearn.app.domain.usecase.SaveDifficultyRatingUseCase
import com.procrastilearn.app.overlay.GateCompletion
import com.procrastilearn.app.overlay.OverlayViewModel
import com.procrastilearn.app.utils.MainDispatcherRule
import com.procrastilearn.app.utils.ServiceLifecycleOwner
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSystemClock
import org.robolectric.util.ReflectionHelpers
import java.time.Duration

internal const val BLOCKED_PACKAGE = "com.example.blocked"
internal const val OTHER_BLOCKED_PACKAGE = "com.example.blocked.two"
internal const val LEGIT_PACKAGE = "com.example.legit"
internal const val DEBOUNCE_MILLIS = 100L

@OptIn(ExperimentalCoroutinesApi::class)
abstract class OverlayAccessibilityServiceTestFixture {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    protected val appPreferencesRepository = mockk<AppPreferencesRepository>()
    protected val vocabularyRepository = mockk<VocabularyStudyRepository>()
    protected val getNextVocabularyItemUseCase = mockk<GetNextVocabularyItemUseCase>()
    protected val getSaveDifficultyRatingUseCase = mockk<SaveDifficultyRatingUseCase>()
    protected val dayCountersStore = mockk<DayCountersStore>()
    protected val windowManager = mockk<WindowManager>(relaxed = true)

    protected val blockedAppsFlow = MutableStateFlow<Set<String>>(emptySet())
    protected val enabledFlow = MutableStateFlow(true)
    protected val pauseBackgroundAudioFlow = MutableStateFlow(true)
    protected val policyFlow = MutableStateFlow(LearningPreferencesConfig(overlayInterval = 0))

    protected val sampleItem = VocabularyItem(id = 1L, word = "Haus", translation = "House", isNew = false)

    protected lateinit var service: OverlayAccessibilityService
    protected val ownActivityForegroundStore = OwnActivityForegroundStore()

    @Before
    fun setUp() {
        every { appPreferencesRepository.getBlockedApps() } returns blockedAppsFlow
        every { appPreferencesRepository.isProcrastilearnEnabled() } returns enabledFlow
        every { appPreferencesRepository.pauseBackgroundAudio() } returns pauseBackgroundAudioFlow
        every { dayCountersStore.readPolicy() } returns policyFlow
        coEvery { getNextVocabularyItemUseCase() } returns Result.success(sampleItem)

        createService()
        connectService()
    }

    protected fun createService() {
        service = Robolectric.buildService(OverlayAccessibilityService::class.java).create().get()
        service.windowManager = windowManager
        service.appPreferencesRepository = appPreferencesRepository
        service.vocabularyRepository = vocabularyRepository
        service.getNextVocabularyItemUseCase = getNextVocabularyItemUseCase
        service.getSaveDifficultyRatingUseCase = getSaveDifficultyRatingUseCase
        service.dayCountersStore = dayCountersStore
        service.ownActivityForegroundStore = ownActivityForegroundStore
    }

    protected fun connectService() {
        ReflectionHelpers.callInstanceMethod<Unit>(service, "onServiceConnected")
        advanceUntilIdle()
    }

    protected fun advanceUntilIdle() = mainDispatcherRule.testDispatcher.scheduler.runCurrent()

    protected fun advanceTimeAndRun(millis: Long) {
        ShadowSystemClock.advanceBy(Duration.ofMillis(millis))
        mainDispatcherRule.testDispatcher.scheduler.advanceTimeBy(millis)
        mainDispatcherRule.testDispatcher.scheduler.runCurrent()
    }

    protected fun eventFor(
        pkg: String,
        cls: String = "com.example.MainActivity",
        eventType: Int = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
    ): AccessibilityEvent =
        AccessibilityEvent(eventType).apply {
            packageName = pkg
            className = cls
        }

    protected fun dispatch(
        pkg: String,
        cls: String = "com.example.MainActivity",
        eventType: Int = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
    ) {
        service.onAccessibilityEvent(eventFor(pkg, cls, eventType))
        advanceUntilIdle()
    }

    protected fun advanceRealClockPastDebounceWindow() {
        ShadowSystemClock.advanceBy(Duration.ofMillis(DEBOUNCE_MILLIS))
    }

    protected fun activeOverlayViewModel(): OverlayViewModel {
        val owner = ReflectionHelpers.getField<ServiceLifecycleOwner?>(service, "lifecycleOwner")!!
        return ViewModelProvider(owner).get(OverlayViewModel::class.java)
    }

    protected fun completionCallback(): (GateCompletion) -> Unit =
        ReflectionHelpers.getField(service, "gateCompletionCallback")

    protected fun completeOverlay() {
        completionCallback()(GateCompletion(1))
        advanceUntilIdle()
    }

    protected fun makePackageLaunchable(pkg: String) {
        val component = ComponentName(pkg, "$pkg.MainActivity")
        val packageManager = shadowOf(service.packageManager)
        packageManager.addActivityIfNotPresent(component)
        packageManager.addIntentFilterForActivity(
            component,
            IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
        )
        assertThat(service.packageManager.getLaunchIntentForPackage(pkg)).isNotNull()
    }
}
