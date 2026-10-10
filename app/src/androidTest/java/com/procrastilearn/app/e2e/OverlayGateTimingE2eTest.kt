package com.procrastilearn.app.e2e

import android.content.Intent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.procrastilearn.app.R
import com.procrastilearn.app.service.OverlayAccessibilityService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OverlayGateTimingE2eTest : OverlayE2eTest() {
    @Test
    fun selectedAppSwitchingDuringCooldownInheritsTheOriginalRepeatDeadline() {
        targetContext.seedWord(THIRD_WORD, "seeded-translation-3", position = 2L)
        targetContext.seedWord(FOURTH_WORD, "seeded-translation-4", position = 3L)
        val repository = FaultInjectingVocabularyRepository(vocabularyRepository())
        installStudyRepository(repository)
        useVirtualElapsedClock()
        setCardsPerGate(2, intervalMinutes = 2)
        setCooldown(1)
        blockBothTargetApps()
        launchTargetAppUntilOverlayAppears()
        rateCurrentCard()
        rateCurrentCard()
        composeTestRule.waitUntilNodeGone(hasTestTag("gate_card_progress"), E2E_TIMEOUT_MS)
        val release = reviewedRelease()
        val callsAfterRelease = repository.nextItemCalls

        advanceElapsedTo(release + 30_000L)
        launchPackage(SECOND_TARGET_PACKAGE)
        waitForForeground(SECOND_TARGET_PACKAGE)
        assertOverlayAndFocusAbsent()
        goHome()
        launchPackage(TARGET_PACKAGE)
        waitForForeground(TARGET_PACKAGE)
        assertOverlayAndFocusAbsent()
        assertEquals(callsAfterRelease, repository.nextItemCalls)
        assertEquals(release + 120_000L, gateState().nextRepeatDeadlineElapsedMs)

        advanceElapsedTo(release + 120_000L)
        composeTestRule.waitUntilNodeExists(hasTestTag("gate_card_progress"), E2E_TIMEOUT_MS)
        assertGateProgress(0)
        assertEquals(callsAfterRelease + 1, repository.nextItemCalls)
    }

    @Test
    fun cooldownExpiryDoesNotGateTheAdmittedAppButReturningAtTheBoundaryDoes() {
        useVirtualElapsedClock()
        setCooldown(1)
        blockBothTargetApps()
        launchTargetAppUntilOverlayAppears()
        rateCurrentCard()
        composeTestRule.waitUntilNodeGone(hasText(targetContext.string(R.string.rating_good)), E2E_TIMEOUT_MS)
        val release = reviewedRelease()

        advanceElapsedTo(release + 60_000L)
        assertOverlayAndFocusAbsent()
        goHome()
        launchPackage(SECOND_TARGET_PACKAGE)

        composeTestRule.waitUntilNodeExists(
            hasText(targetContext.string(R.string.learning_show_translation)),
            E2E_TIMEOUT_MS,
        )
    }

    @Test
    fun exhaustionAfterOneSavedRatingEarnsCooldown() {
        resetOverlayState()
        targetContext.seedWord("only-new-card", "solo nueva")
        useVirtualElapsedClock()
        setCardsPerGate(2)
        setDailyLimits(newLimit = 1, reviewLimit = 0)
        setCooldown(1)
        val repository = FaultInjectingVocabularyRepository(vocabularyRepository())
        installStudyRepository(repository)
        blockBothTargetApps()
        launchTargetAppUntilOverlayAppears()
        rateCurrentCard()
        composeTestRule.waitUntilNodeGone(hasTestTag("gate_card_progress"), E2E_TIMEOUT_MS)
        assertEquals(reviewedRelease() + 60_000L, gateState().cooldownDeadlineElapsedMs)
        val callsAfterRelease = repository.nextItemCalls

        launchPackage(SECOND_TARGET_PACKAGE)
        waitForForeground(SECOND_TARGET_PACKAGE)

        assertOverlayAndFocusAbsent()
        assertEquals(callsAfterRelease, repository.nextItemCalls)
    }

    @Test
    fun emptyFirstProbeDoesNotEarnCooldownAndAnotherSelectedAppProbesAgain() {
        useVirtualElapsedClock()
        setCooldown(1)
        setDailyLimits(newLimit = 0, reviewLimit = 0)
        val repository = FaultInjectingVocabularyRepository(vocabularyRepository())
        installStudyRepository(repository)
        blockBothTargetApps()
        launchTargetAppWithoutWaitingForOverlay()
        composeTestRule.waitUntil(E2E_TIMEOUT_MS) { repository.nextItemCalls >= 1 }
        assertNull(gateState().lastReviewedReleaseElapsedMs)
        assertOverlayAndFocusAbsent()
        val callsAfterEmpty = repository.nextItemCalls

        launchPackage(SECOND_TARGET_PACKAGE)
        composeTestRule.waitUntil(E2E_TIMEOUT_MS) { repository.nextItemCalls > callsAfterEmpty }

        assertNull(gateState().lastReviewedReleaseElapsedMs)
        assertOverlayAndFocusAbsent()
    }

    @Test
    fun emptyRepeatProbeRecoversAtTheNextDeadlineWhenCardsBecomeAvailable() {
        useVirtualElapsedClock()
        setCardsPerGate(1, intervalMinutes = 1)
        val repository = FaultInjectingVocabularyRepository(vocabularyRepository())
        installStudyRepository(repository)
        selectTargetAppAsBlocked()
        launchTargetAppUntilOverlayAppears()
        rateCurrentCard()
        composeTestRule.waitUntilNodeGone(hasText(targetContext.string(R.string.rating_good)), E2E_TIMEOUT_MS)
        val release = reviewedRelease()
        setDailyLimits(newLimit = 0, reviewLimit = 0)
        val callsBeforeRepeat = repository.nextItemCalls

        advanceElapsedTo(release + 60_000L)
        composeTestRule.waitUntil(E2E_TIMEOUT_MS) { repository.nextItemCalls > callsBeforeRepeat }
        composeTestRule.waitUntil(E2E_TIMEOUT_MS) {
            gateState().nextRepeatDeadlineElapsedMs == release + 120_000L
        }
        assertOverlayAndFocusAbsent()
        assertEquals(release, gateState().lastReviewedReleaseElapsedMs)
        assertEquals(release + 120_000L, gateState().nextRepeatDeadlineElapsedMs)
        setDailyLimits(newLimit = E2E_DEFAULT_NEW_CARDS_PER_DAY, reviewLimit = 99)

        advanceElapsedTo(release + 120_000L)

        composeTestRule.waitUntilNodeExists(
            hasText(targetContext.string(R.string.learning_show_translation)),
            E2E_TIMEOUT_MS,
        )
    }

    @Test
    fun serviceReconnectEndsRuntimeCooldownAndNextSelectedEntryGates() {
        setCooldown(1)
        selectTargetAppAsBlocked()
        launchTargetAppUntilOverlayAppears()
        rateCurrentCard()
        composeTestRule.waitUntilNodeGone(hasText(targetContext.string(R.string.rating_good)), E2E_TIMEOUT_MS)
        assertNotNull(gateState().lastReviewedReleaseElapsedMs)

        uiAutomation.shell("settings put secure enabled_accessibility_services \"\"")
        uiAutomation.shell("settings put secure accessibility_enabled 0")
        composeTestRule.waitUntil(E2E_TIMEOUT_MS) {
            OverlayAccessibilityService.debugInstanceForE2eTests == null
        }
        val component = "${targetContext.packageName}/${OverlayAccessibilityService::class.java.name}"
        uiAutomation.shell("settings put secure enabled_accessibility_services $component")
        uiAutomation.shell("settings put secure accessibility_enabled 1")
        waitUntilAccessibilityServiceBound()
        composeTestRule.waitUntil(E2E_TIMEOUT_MS) {
            OverlayAccessibilityService.debugInstanceForE2eTests != null
        }
        assertNull(gateState().lastReviewedReleaseElapsedMs)

        launchTargetAppUntilOverlayAppears()

        composeTestRule.assertEventuallyDisplayed(hasText(SECOND_WORD, substring = true), E2E_TIMEOUT_MS)
    }

    @Test
    fun rapidSelectedAppSwitchKeepsTheLatestForegroundAndGate() {
        blockBothTargetApps()
        goHome()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            listOf(TARGET_PACKAGE, SECOND_TARGET_PACKAGE).forEach { packageName ->
                val intent = checkNotNull(targetContext.packageManager.getLaunchIntentForPackage(packageName))
                targetContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }

        waitForForeground(SECOND_TARGET_PACKAGE)
        composeTestRule.waitUntilNodeExists(
            hasText(targetContext.string(R.string.learning_show_translation)),
            E2E_TIMEOUT_MS,
        )

        assertEquals(SECOND_TARGET_PACKAGE, gateState().foregroundPackage)
        assertEquals(SECOND_TARGET_PACKAGE, gateState().activeAttemptPackage)
    }
}
