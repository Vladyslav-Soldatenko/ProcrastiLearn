package com.procrastilearn.app.e2e

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityManager
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.procrastilearn.app.MainActivity
import com.procrastilearn.app.R
import com.procrastilearn.app.data.local.prefs.DayCountersStore
import com.procrastilearn.app.domain.model.GateTimingChangeResult
import com.procrastilearn.app.domain.model.MixMode
import com.procrastilearn.app.domain.model.StudyDirection
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.domain.repository.AppPreferencesRepository
import com.procrastilearn.app.domain.repository.VocabularyStudyRepository
import com.procrastilearn.app.domain.usecase.GetNextVocabularyItemUseCase
import com.procrastilearn.app.domain.usecase.SaveDifficultyRatingUseCase
import com.procrastilearn.app.service.GateRuntimeSnapshot
import com.procrastilearn.app.service.OverlayAccessibilityService
import com.procrastilearn.app.service.ServiceEntryPoint
import dagger.hilt.android.EntryPointAccessors
import io.github.openspacedrepetition.Rating
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

@RunWith(AndroidJUnit4::class)
@Suppress("LargeClass")
open class OverlayE2eTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    protected lateinit var targetContext: Context
    protected lateinit var uiAutomation: UiAutomation
    protected var previousEnabledServices: String? = null
    protected var previousBlockedApps: Set<String> = emptySet()
    protected var previousMasterEnabled = true
    protected val elapsedClock = AtomicLong()

    @Before
    fun beforeEach() {
        targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking(Dispatchers.IO) {
            previousBlockedApps = appPreferencesRepository().getBlockedApps().first()
            previousMasterEnabled = appPreferencesRepository().isProcrastilearnEnabled().first()
        }
        // UiAutomation suppresses all other accessibility services while connected, so without
        // this flag the shell commands below would write enabled_accessibility_services
        // correctly but AccessibilityManagerService would never actually bind our service.
        uiAutomation =
            InstrumentationRegistry
                .getInstrumentation()
                .getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)

        val serviceComponent = "${targetContext.packageName}/${OverlayAccessibilityService::class.java.name}"
        previousEnabledServices = uiAutomation.shell("settings get secure enabled_accessibility_services").trim()
        uiAutomation.shell("settings put secure enabled_accessibility_services $serviceComponent")
        uiAutomation.shell("settings put secure accessibility_enabled 1")
        uiAutomation.shell("appops set ${targetContext.packageName} SYSTEM_ALERT_WINDOW allow")
        waitUntilAccessibilityServiceBound()

        resetOverlayState()
        runBlocking(Dispatchers.IO) { resetGatePreferences() }
        targetContext.seedWord(SEEDED_WORD, "seeded-translation", position = 0L)
        targetContext.seedWord(SECOND_WORD, "seeded-translation-2", position = 1L)
        runBlocking(Dispatchers.IO) {
            appPreferencesRepository().setBlockedApps(emptySet())
            appPreferencesRepository().setProcrastilearnEnabled(true)
        }

        resetServiceRuntime()

        composeTestRule.dismissOnboardingIfPresent(targetContext)
    }

    @After
    fun afterEach() {
        restoreServiceUseCases()
        resetServiceRuntime()
        runBlocking(Dispatchers.IO) {
            appPreferencesRepository().setProcrastilearnEnabled(false)
            appPreferencesRepository().setBlockedApps(emptySet())
            resetGatePreferences()
            appPreferencesRepository().setBlockedApps(previousBlockedApps)
            appPreferencesRepository().setProcrastilearnEnabled(previousMasterEnabled)
        }
        resetOverlayState()

        val restored = previousEnabledServices
        if (restored.isNullOrBlank() || restored == "null") {
            uiAutomation.shell("settings put secure enabled_accessibility_services \"\"")
            uiAutomation.shell("settings put secure accessibility_enabled 0")
        } else {
            uiAutomation.shell("settings put secure enabled_accessibility_services $restored")
        }
        uiAutomation.shell("appops set ${targetContext.packageName} SYSTEM_ALERT_WINDOW default")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun selectingAndOpeningBlockedAppShowsOverlayThenHidesAfterRating() {
        selectTargetAppAsBlocked()

        launchTargetAppUntilOverlayAppears()

        composeTestRule.assertEventuallyDisplayed(hasText(SEEDED_WORD, substring = true), E2E_TIMEOUT_MS)
        assertGateProgressAbsent()
        revealTranslation()

        listOf(
            R.string.rating_again,
            R.string.rating_hard,
            R.string.rating_good,
            R.string.rating_easy,
        ).forEach { resId ->
            composeTestRule.assertEventuallyDisplayed(hasText(targetContext.string(resId)), E2E_TIMEOUT_MS)
        }

        composeTestRule.onNodeWithText(targetContext.string(R.string.rating_good)).performClick()

        composeTestRule.waitUntilNodeGone(hasText(SEEDED_WORD, substring = true), E2E_TIMEOUT_MS)
        composeTestRule.waitUntilNodeGone(hasText(targetContext.string(R.string.rating_good)), E2E_TIMEOUT_MS)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun revealingTranslationWithRatingDelayShowsCountdownAndDisablesRatingButtons() {
        setRatingDelayViaSettings(RATING_DELAY_SECONDS)
        selectTargetAppAsBlocked()

        launchTargetAppUntilOverlayAppears()
        revealTranslation()

        composeTestRule.assertEventuallyDisplayed(hasTestTag("rating_lock_countdown"), E2E_TIMEOUT_MS)
        listOf(
            R.string.rating_again,
            R.string.rating_hard,
            R.string.rating_good,
            R.string.rating_easy,
        ).forEach { resId ->
            composeTestRule.onNodeWithText(targetContext.string(resId)).assertIsNotEnabled()
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun attemptingToRateWhileLockedIsANoOp() {
        setRatingDelayViaSettings(RATING_DELAY_SECONDS)
        selectTargetAppAsBlocked()

        launchTargetAppUntilOverlayAppears()
        revealTranslation()

        composeTestRule.assertEventuallyDisplayed(hasTestTag("rating_lock_countdown"), E2E_TIMEOUT_MS)
        listOf(
            R.string.rating_again,
            R.string.rating_hard,
            R.string.rating_good,
            R.string.rating_easy,
        ).forEach { resId ->
            composeTestRule.onNodeWithText(targetContext.string(resId)).assertIsNotEnabled()
        }

        composeTestRule.onNodeWithText(targetContext.string(R.string.rating_good)).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.assertEventuallyDisplayed(hasText(targetContext.string(R.string.rating_good)), E2E_TIMEOUT_MS)
        composeTestRule.onNodeWithText(targetContext.string(R.string.rating_good)).assertIsNotEnabled()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun ratingButtonsUnlockAndRatingSucceedsAfterCountdownExpires() {
        setRatingDelayViaSettings(RATING_DELAY_SECONDS)
        selectTargetAppAsBlocked()

        launchTargetAppUntilOverlayAppears()
        revealTranslation()

        composeTestRule.assertEventuallyDisplayed(hasTestTag("rating_lock_countdown"), E2E_TIMEOUT_MS)
        waitForRatingUnlock()

        composeTestRule.onNodeWithText(targetContext.string(R.string.rating_good)).assertIsEnabled()
        composeTestRule.onNodeWithText(targetContext.string(R.string.rating_good)).performClick()

        composeTestRule.waitUntilNodeGone(hasText(targetContext.string(R.string.rating_good)), E2E_TIMEOUT_MS)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun newOverlaySessionRequiresFullDelayAgain() {
        setRatingDelayViaSettings(RATING_DELAY_SECONDS)
        selectTargetAppAsBlocked()

        launchTargetAppUntilOverlayAppears()
        revealTranslation()
        composeTestRule.assertEventuallyDisplayed(hasTestTag("rating_lock_countdown"), E2E_TIMEOUT_MS)
        waitForRatingUnlock()
        composeTestRule.onNodeWithText(targetContext.string(R.string.rating_good)).performClick()
        composeTestRule.waitUntilNodeGone(hasText(targetContext.string(R.string.rating_good)), E2E_TIMEOUT_MS)

        launchTargetAppUntilOverlayAppears()
        revealTranslation()

        composeTestRule.assertEventuallyDisplayed(hasTestTag("rating_lock_countdown"), E2E_TIMEOUT_MS)
        composeTestRule
            .onNodeWithTag("rating_lock_countdown", useUnmergedTree = true)
            .assertTextEquals(RATING_DELAY_SECONDS.toString())
        listOf(
            R.string.rating_again,
            R.string.rating_hard,
            R.string.rating_good,
            R.string.rating_easy,
        ).forEach { resId ->
            composeTestRule.onNodeWithText(targetContext.string(resId)).assertIsNotEnabled()
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun multiCardInitialGateShowsProgressAndReleasesAfterTwoSavedRatings() {
        setCardsPerGate(2)
        selectTargetAppAsBlocked()

        launchTargetAppUntilOverlayAppears()
        assertGateProgress(0)
        rateCurrentCard()
        assertGateProgress(1)
        composeTestRule.assertEventuallyDisplayed(hasText(SECOND_WORD, substring = true), E2E_TIMEOUT_MS)

        rateCurrentCard()
        composeTestRule.waitUntilNodeGone(hasTestTag("gate_card_progress"), E2E_TIMEOUT_MS)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun multiCardIntervalGateStartsWithZeroAndRequiresTwoRatingsAgain() {
        targetContext.seedWord(THIRD_WORD, "seeded-translation-3", position = 2L)
        targetContext.seedWord(FOURTH_WORD, "seeded-translation-4", position = 3L)
        setCardsPerGate(cards = 2, intervalMinutes = INTERVAL_MINUTES)
        selectTargetAppAsBlocked()

        launchTargetAppUntilOverlayAppears()
        assertGateProgress(0)
        rateCurrentCard()
        assertGateProgress(1)
        rateCurrentCard()
        composeTestRule.waitUntilNodeGone(hasTestTag("gate_card_progress"), E2E_TIMEOUT_MS)

        composeTestRule.waitUntilNodeExists(hasTestTag("gate_card_progress"), INTERVAL_TIMEOUT_MS)
        assertGateProgress(0)
        rateCurrentCard()
        assertGateProgress(1)
        rateCurrentCard()
        composeTestRule.waitUntilNodeGone(hasTestTag("gate_card_progress"), E2E_TIMEOUT_MS)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun leavingAndReopeningBlockedAppResetsMultiCardProgress() {
        setCardsPerGate(2)
        selectTargetAppAsBlocked()

        launchTargetAppUntilOverlayAppears()
        assertGateProgress(0)
        rateCurrentCard()
        assertGateProgress(1)

        goHome()
        composeTestRule.waitUntilNodeGone(hasTestTag("gate_card_progress"), E2E_TIMEOUT_MS)
        launchTargetAppUntilOverlayAppears()

        assertGateProgress(0)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun dojoDoesNotShowBlockedAppGateProgress() {
        setCardsPerGate(2)
        composeTestRule.navigateTo(targetContext, R.string.nav_dojo)

        assertGateProgressAbsent()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun exhaustedReviewQuotaAndNoEligibleNewCardsShowNoInitialOverlay() {
        val repository = FaultInjectingVocabularyRepository(vocabularyRepository())
        targetContext.seedWord(
            word = "due-only-card",
            translation = "repaso pendiente",
            correctCount = 1,
            fsrsDueAt = System.currentTimeMillis() - 60_000L,
        )
        setDailyLimits(newLimit = 1, reviewLimit = 1, newShown = 1, reviewShown = 1)
        installStudyRepository(repository)
        selectTargetAppAsBlocked()

        launchTargetAppWithoutWaitingForOverlay()
        composeTestRule.waitUntil(INITIAL_QUERY_TIMEOUT_MS) { repository.nextItemCalls >= 1 }
        assertOverlayAbsent()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun exhaustedReviewQuotaAndNoEligibleNewCardsShowNoIntervalOverlay() {
        val repository = FaultInjectingVocabularyRepository(vocabularyRepository())
        useVirtualElapsedClock()
        resetOverlayState()
        targetContext.seedWord("only-new-card", "solo nueva")
        targetContext.seedWord(
            word = "due-only-card",
            translation = "repaso pendiente",
            correctCount = 1,
            fsrsDueAt = System.currentTimeMillis() - 60_000L,
        )
        runBlocking(Dispatchers.IO) {
            dayCountersStore().setMixMode(MixMode.NEW_FIRST)
        }
        setCardsPerGate(cards = 1, intervalMinutes = INTERVAL_MINUTES)
        setDailyLimits(newLimit = 1, reviewLimit = 1)
        installStudyRepository(repository)
        selectTargetAppAsBlocked()

        launchTargetAppUntilOverlayAppears()
        rateCurrentCard()
        composeTestRule.waitUntilNodeGone(
            hasText(targetContext.string(R.string.learning_show_translation)),
            E2E_TIMEOUT_MS,
        )
        runBlocking(Dispatchers.IO) { dayCountersStore().markReviewShown() }
        check(targetContext.vocabularyByWord("only-new-card")?.correctCount == 1) {
            "The initial card rating did not save before the interval quota was exhausted"
        }

        advanceElapsedTo(reviewedRelease() + 60_000L)
        composeTestRule.waitUntil(E2E_TIMEOUT_MS) { repository.nextItemCalls >= 2 }
        assertOverlayAbsent()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun runningOutAfterOneOfTwoRatingsReleasesTheBlockedApp() {
        resetOverlayState()
        targetContext.seedWord("only-new-card", "solo nueva")
        setCardsPerGate(2)
        setDailyLimits(newLimit = 1, reviewLimit = 0)
        selectTargetAppAsBlocked()

        launchTargetAppUntilOverlayAppears()
        assertGateProgress(0)
        rateCurrentCard()

        composeTestRule.waitUntilNodeGone(hasTestTag("gate_card_progress"), E2E_TIMEOUT_MS)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun failedRatingSaveKeepsProgressAndRatingCanBeRetried() {
        setCardsPerGate(2)
        injectStudyFailure(InjectedStudyFailure.FIRST_RATING_SAVE)
        selectTargetAppAsBlocked()

        launchTargetAppUntilOverlayAppears()
        assertGateProgress(0)
        revealTranslation()
        composeTestRule.onNodeWithText(targetContext.string(R.string.rating_good)).performClick()

        composeTestRule.assertEventuallyDisplayed(hasTestTag("rating_save_error"), E2E_TIMEOUT_MS)
        assertGateProgress(0)
        composeTestRule.onNodeWithText(targetContext.string(R.string.rating_good)).performClick()
        assertGateProgress(1)
        composeTestRule.waitUntilNodeGone(hasTestTag("rating_save_error"), E2E_TIMEOUT_MS)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun failedNextCardLoadShowsRetryAndKeepsGateOpen() {
        setCardsPerGate(2)
        injectStudyFailure(InjectedStudyFailure.SECOND_CARD_LOAD)
        selectTargetAppAsBlocked()

        launchTargetAppUntilOverlayAppears()
        assertGateProgress(0)
        rateCurrentCard()

        composeTestRule.assertEventuallyDisplayed(hasTestTag("next_card_load_error"), E2E_TIMEOUT_MS)
        assertGateProgress(1)
        composeTestRule.onNodeWithTag("next_card_retry").performClick()
        composeTestRule.assertEventuallyDisplayed(hasText(SECOND_WORD, substring = true), E2E_TIMEOUT_MS)
        assertGateProgress(1)

        rateCurrentCard()
        composeTestRule.waitUntilNodeGone(hasTestTag("gate_card_progress"), E2E_TIMEOUT_MS)
    }

    protected fun assertGateProgress(completed: Int) {
        composeTestRule.waitUntil(E2E_TIMEOUT_MS) {
            try {
                composeTestRule.onNodeWithTag("gate_card_progress").assertTextEquals("$completed/2")
                true
            } catch (_: AssertionError) {
                false
            } catch (_: IllegalStateException) {
                false
            }
        }
    }

    private fun assertGateProgressAbsent() {
        check(!composeTestRule.nodeVisibleWithin(hasTestTag("gate_card_progress"), NODE_ABSENCE_CHECK_MS)) {
            "Gate progress should not be visible in this screen"
        }
    }

    private fun assertOverlayAbsent() {
        check(
            !composeTestRule.nodeVisibleWithin(
                hasText(targetContext.string(R.string.learning_show_translation)),
                NO_OVERLAY_CHECK_MS,
            ),
        ) { "A gate overlay appeared despite no eligible cards" }
    }

    protected fun rateCurrentCard() {
        composeTestRule.waitUntilNodeExists(
            hasText(targetContext.string(R.string.learning_show_translation)),
            E2E_TIMEOUT_MS,
        )
        revealTranslation()
        composeTestRule.onNodeWithText(targetContext.string(R.string.rating_good)).performClick()
        composeTestRule.waitForIdle()
    }

    protected fun launchTargetAppWithoutWaitingForOverlay() {
        goHome()
        val intent =
            targetContext.packageManager.getLaunchIntentForPackage(TARGET_PACKAGE)
                ?: error("Target app $TARGET_PACKAGE has no launch intent")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        targetContext.startActivity(intent)
        composeTestRule.waitForIdle()
    }

    protected fun setCardsPerGate(
        cards: Int,
        intervalMinutes: Int = 0,
    ) {
        runBlocking(Dispatchers.IO) {
            dayCountersStore().setCardsPerGate(cards)
            dayCountersStore().setOverlayInterval(intervalMinutes)
        }
    }

    protected fun setDailyLimits(
        newLimit: Int,
        reviewLimit: Int,
        newShown: Int = 0,
        reviewShown: Int = 0,
    ) {
        runBlocking(Dispatchers.IO) {
            dayCountersStore().resetFor(todayStamp())
            dayCountersStore().setNewPerDay(newLimit)
            dayCountersStore().setReviewPerDay(reviewLimit)
            repeat(newShown) { dayCountersStore().markNewShown() }
            repeat(reviewShown) { dayCountersStore().markReviewShown() }
        }
    }

    private suspend fun resetGatePreferences() {
        dayCountersStore().setCardsPerGate(1)
        dayCountersStore().setNewPerDay(E2E_DEFAULT_NEW_CARDS_PER_DAY)
        dayCountersStore().setReviewPerDay(99)
        dayCountersStore().setMixMode(MixMode.MIX)
        dayCountersStore().setOverlayInterval(0)
        dayCountersStore().setGateCooldownMinutes(0)
        dayCountersStore().setRatingDelaySeconds(0)
        dayCountersStore().resetFor(todayStamp())
    }

    protected fun blockBothTargetApps() {
        listOf(TARGET_PACKAGE, SECOND_TARGET_PACKAGE).forEach { packageName ->
            val intent =
                checkNotNull(targetContext.packageManager.getLaunchIntentForPackage(packageName)) {
                    "Target app $packageName has no launch intent"
                }
            assertEquals(packageName, intent.resolveActivity(targetContext.packageManager)?.packageName)
        }
        runBlocking(Dispatchers.IO) {
            appPreferencesRepository().setBlockedApps(setOf(TARGET_PACKAGE, SECOND_TARGET_PACKAGE))
        }
    }

    protected fun launchPackage(packageName: String) {
        val intent = checkNotNull(targetContext.packageManager.getLaunchIntentForPackage(packageName))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        targetContext.startActivity(intent)
        composeTestRule.waitForIdle()
    }

    protected fun setCooldown(minutes: Int) {
        runBlocking(Dispatchers.IO) {
            assertEquals(GateTimingChangeResult.Applied, dayCountersStore().setGateCooldownMinutes(minutes))
        }
        composeTestRule.waitUntil(E2E_TIMEOUT_MS) {
            runBlocking(Dispatchers.IO) { dayCountersStore().readGateTiming().first().cooldownMinutes == minutes }
        }
    }

    protected fun useVirtualElapsedClock() {
        elapsedClock.set(SystemClock.elapsedRealtime())
        withServiceOnMain { setGateClockForE2eTests(elapsedClock::get) }
    }

    protected fun advanceElapsedTo(elapsedMs: Long) {
        elapsedClock.set(elapsedMs)
        withServiceOnMain { reevaluateGateTimingForE2eTests() }
        composeTestRule.waitForIdle()
    }

    private fun resetServiceRuntime() {
        if (OverlayAccessibilityService.debugInstanceForE2eTests == null) return
        withServiceOnMain {
            resetGateStateForE2eTests()
            setGateClockForE2eTests(SystemClock::elapsedRealtime)
        }
    }

    protected fun gateState(): GateRuntimeSnapshot {
        var snapshot: GateRuntimeSnapshot? = null
        withServiceOnMain { snapshot = gateSnapshotForE2eTests() }
        return checkNotNull(snapshot)
    }

    protected fun reviewedRelease(): Long {
        composeTestRule.waitUntil(E2E_TIMEOUT_MS) { gateState().lastReviewedReleaseElapsedMs != null }
        return checkNotNull(gateState().lastReviewedReleaseElapsedMs)
    }

    protected fun waitForForeground(packageName: String) {
        composeTestRule.waitUntil(E2E_TIMEOUT_MS) { gateState().foregroundPackage == packageName }
    }

    protected fun assertOverlayAndFocusAbsent() {
        gateState().also {
            assertFalse("overlay must be absent while access is admitted", it.overlayAttached)
            assertFalse("audio focus must be absent while access is admitted", it.audioFocusAttached)
        }
    }

    private fun withServiceOnMain(action: OverlayAccessibilityService.() -> Unit) {
        val service = checkNotNull(OverlayAccessibilityService.debugInstanceForE2eTests)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { service.action() }
    }

    private fun injectStudyFailure(failure: InjectedStudyFailure) {
        val repository = FaultInjectingVocabularyRepository(vocabularyRepository(), failure)
        installStudyRepository(repository)
    }

    protected fun installStudyRepository(repository: VocabularyStudyRepository) {
        val service =
            OverlayAccessibilityService.debugInstanceForE2eTests
                ?: error("OverlayAccessibilityService test accessor was not initialized")
        val getNextVocabularyItemUseCase = GetNextVocabularyItemUseCase(repository)
        val getSaveDifficultyRatingUseCase = SaveDifficultyRatingUseCase(repository)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            service.setStudyUseCasesForE2eTests(
                getNextVocabularyItemUseCase,
                getSaveDifficultyRatingUseCase,
            )
        }
    }

    private fun restoreServiceUseCases() {
        val service = OverlayAccessibilityService.debugInstanceForE2eTests ?: return
        val entryPoint =
            EntryPointAccessors.fromApplication(
                targetContext.applicationContext,
                ServiceEntryPoint::class.java,
            )
        val getNextVocabularyItemUseCase = entryPoint.getNextVocabularyItemUseCase()
        val getSaveDifficultyRatingUseCase = entryPoint.getSaveDifficultyRatingUseCase()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            service.setStudyUseCasesForE2eTests(
                getNextVocabularyItemUseCase,
                getSaveDifficultyRatingUseCase,
            )
        }
    }

    protected fun vocabularyRepository(): VocabularyStudyRepository =
        EntryPointAccessors
            .fromApplication(targetContext.applicationContext, ServiceEntryPoint::class.java)
            .vocabularyRepository()

    protected enum class InjectedStudyFailure {
        FIRST_RATING_SAVE,
        SECOND_CARD_LOAD,
    }

    protected class FaultInjectingVocabularyRepository(
        private val delegate: VocabularyStudyRepository,
        private val failure: InjectedStudyFailure? = null,
    ) : VocabularyStudyRepository by delegate {
        private val nextItemCallCount = AtomicInteger()

        @Volatile
        private var failNextCardLoad = false

        val nextItemCalls: Int
            get() = nextItemCallCount.get()

        private var ratingSaveCalls = 0

        override suspend fun getNextVocabularyItem(): VocabularyItem =
            try {
                if (failure == InjectedStudyFailure.SECOND_CARD_LOAD && failNextCardLoad) {
                    failNextCardLoad = false
                    error("Injected next-card load failure")
                }
                delegate.getNextVocabularyItem()
            } finally {
                nextItemCallCount.incrementAndGet()
            }

        override suspend fun reviewVocabularyItem(
            id: Long,
            rating: Rating,
            direction: StudyDirection,
        ) {
            ratingSaveCalls++
            if (failure == InjectedStudyFailure.FIRST_RATING_SAVE && ratingSaveCalls == 1) {
                error("Injected rating save failure")
            }
            delegate.reviewVocabularyItem(id, rating, direction)
            if (failure == InjectedStudyFailure.SECOND_CARD_LOAD) failNextCardLoad = true
        }
    }

    protected fun selectTargetAppAsBlocked() {
        check(composeTestRule.nodeVisibleWithin(hasText(targetContext.string(R.string.nav_apps)), E2E_TIMEOUT_MS)) {
            "Apps navigation unavailable"
        }
        composeTestRule
            .onNodeWithContentDescription(targetContext.string(R.string.nav_apps), useUnmergedTree = true)
            .performClick()
        composeTestRule.waitForIdle()

        val rowTag = "app_row_$TARGET_PACKAGE"
        val checkboxTag = "app_checkbox_$TARGET_PACKAGE"

        composeTestRule.waitUntilNodeExists(hasScrollAction(), E2E_TIMEOUT_MS)
        composeTestRule.waitUntilNodeGone(hasTestTag("apps_list_loading_indicator"), APPS_LIST_TIMEOUT_MS)
        composeTestRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasTestTag(rowTag))
        composeTestRule.onNodeWithTag(checkboxTag, useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()

        waitUntilBlockedAppsContains(TARGET_PACKAGE)
    }

    private fun revealTranslation() {
        composeTestRule.onNodeWithText(targetContext.string(R.string.learning_show_translation)).performClick()
        composeTestRule.waitForIdle()
    }

    private fun setRatingDelayViaSettings(seconds: Int) {
        composeTestRule.waitUntilNodeExists(hasText(targetContext.string(R.string.nav_settings)), E2E_TIMEOUT_MS)
        composeTestRule
            .onNodeWithContentDescription(targetContext.string(R.string.nav_settings), useUnmergedTree = true)
            .performClick()
        composeTestRule.waitForIdle()

        openRatingDelayDialog()

        val ratingDelayField = composeTestRule.onNode(hasSetTextAction())
        ratingDelayField.performClick()
        composeTestRule.waitForIdle()
        ratingDelayField.performTextReplacement(seconds.toString())
        composeTestRule.onNodeWithText(targetContext.string(R.string.action_ok)).performClick()
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithContentDescription(targetContext.string(R.string.nav_apps), useUnmergedTree = true)
            .performClick()
        composeTestRule.waitForIdle()
    }

    private fun openRatingDelayDialog() {
        composeTestRule
            .onNodeWithText(targetContext.string(R.string.settings_rating_delay_headline))
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.waitUntilNodeExists(
            hasText(targetContext.string(R.string.settings_rating_delay_title)),
            E2E_TIMEOUT_MS,
        )
    }

    private fun waitForRatingUnlock() {
        composeTestRule.waitUntilNodeGone(hasTestTag("rating_lock_countdown"), RATING_LOCK_TIMEOUT_MS)
    }

    protected fun launchTargetAppUntilOverlayAppears() {
        repeat(LAUNCH_RETRY_COUNT) {
            goHome()

            targetContext.packageManager.getLaunchIntentForPackage(TARGET_PACKAGE)?.let { intent ->
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                targetContext.startActivity(intent)
            }

            if (composeTestRule.nodeVisibleWithin(
                    hasText(targetContext.string(R.string.learning_show_translation)),
                    LAUNCH_POLL_TIMEOUT_MS,
                )
            ) {
                return
            }
        }
        val service = OverlayAccessibilityService.debugInstanceForE2eTests
        val state =
            if (service == null) {
                "service=null"
            } else {
                listOf(
                    "controller",
                    "blockedPackages",
                    "isProcrastilearnEnabled",
                    "overlayView",
                ).joinToString { name ->
                    val field = service.javaClass.getDeclaredField(name).apply { isAccessible = true }
                    "$name=${field.get(service)}"
                }
            }
        error("Overlay did not appear after $LAUNCH_RETRY_COUNT launches: $state")
    }

    protected fun goHome() {
        targetContext.startActivity(
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
        composeTestRule.waitForIdle()
        Thread.sleep(GO_HOME_SETTLE_MS)
    }

    protected fun waitUntilAccessibilityServiceBound() {
        val manager = targetContext.getSystemService(AccessibilityManager::class.java)
        val deadline = System.currentTimeMillis() + E2E_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val bound =
                manager
                    .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                    .any { it.resolveInfo.serviceInfo.packageName == targetContext.packageName }
            if (bound) return
            Thread.sleep(SERVICE_BIND_POLL_MS)
        }
        error("OverlayAccessibilityService never appeared in the enabled accessibility service list")
    }

    private fun waitUntilBlockedAppsContains(packageName: String) {
        runBlocking(Dispatchers.IO) {
            val deadline = System.currentTimeMillis() + E2E_TIMEOUT_MS
            while (System.currentTimeMillis() < deadline) {
                if (appPreferencesRepository().getBlockedApps().first().contains(packageName)) return@runBlocking
                delay(SERVICE_BIND_POLL_MS)
            }
        }
    }

    protected fun resetOverlayState() {
        runBlocking(Dispatchers.IO) {
            val db = targetContext.databaseEntryPoint().appDatabase()
            db.vocabularyDao().deleteAllVocabulary()
            db.undoSnapshotDao().deleteAll()
            // DayCountersStore persists across app runs (unlike the DB tables above), so a
            // daily new-word cap exhausted by an earlier run would otherwise make
            // getNextVocabularyItemUseCase() throw NoAvailableItemsException here.
            dayCountersStore().resetFor(todayStamp())
        }
    }

    protected fun dayCountersStore(): DayCountersStore =
        EntryPointAccessors
            .fromApplication(
                targetContext.applicationContext,
                ServiceEntryPoint::class.java,
            ).dayCountersStore()

    protected fun appPreferencesRepository(): AppPreferencesRepository =
        EntryPointAccessors
            .fromApplication(
                targetContext.applicationContext,
                ServiceEntryPoint::class.java,
            ).appPreferencesRepository()

    companion object {
        // The installed-apps list is loaded via PackageManager.queryIntentActivities() plus a
        // per-app icon-load loop (AppRepositoryImpl.loadLaunchableApps()). On a cold emulator this
        // can be slow the first time it runs (odex/vdex verification of large system packages like
        // Google Play services), well past E2E_TIMEOUT_MS, so give it its own longer budget.
        const val APPS_LIST_TIMEOUT_MS = 30_000L
        const val LAUNCH_POLL_TIMEOUT_MS = 3_000L
        const val LAUNCH_RETRY_COUNT = 3
        const val SERVICE_BIND_POLL_MS = 100L
        const val TARGET_PACKAGE = "com.google.android.deskclock"
        const val SECOND_TARGET_PACKAGE = "com.android.settings"
        const val SEEDED_WORD = "overlayflashword"
        const val SECOND_WORD = "overlayflashwordtwo"
        const val THIRD_WORD = "overlayflashwordthree"
        const val FOURTH_WORD = "overlayflashwordfour"
        const val RATING_DELAY_SECONDS = 3
        const val RATING_LOCK_TIMEOUT_MS = 10_000L
        const val GO_HOME_SETTLE_MS = 500L
        const val INTERVAL_MINUTES = 1
        const val INTERVAL_TIMEOUT_MS = 90_000L
        const val INITIAL_QUERY_TIMEOUT_MS = 15_000L
        const val NO_OVERLAY_CHECK_MS = 500L
        const val NODE_ABSENCE_CHECK_MS = 250L
    }
}
