package com.procrastilearn.app.service

import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.domain.model.GateTimingSettings
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.overlay.GateCompletion
import org.junit.Test

class GateSessionControllerTest {
    @Test
    fun cooldownAdmissionsKeepTheOriginalRepeatDeadlineAcrossSelectedAppSwitches() {
        val controller = GateSessionController()
        completeGate(controller, packageName = "A", epoch = 1, releaseAt = 180_000, timing = GateTimingSettings(2, 5))

        assertThat(controller.state.cooldownDeadlineElapsedMs).isEqualTo(300_000)
        assertThat(controller.state.nextRepeatDeadlineElapsedMs).isEqualTo(480_000)
        assertThat(loads(controller.handle(context("B", 2, GateTimingSettings(2, 5)), 210_000))).isEmpty()
        assertThat(loads(controller.handle(context("A", 3, GateTimingSettings(2, 5)), 240_000))).isEmpty()
        assertThat(loads(controller.handle(context("B", 4, GateTimingSettings(2, 5)), 270_000))).isEmpty()
        assertThat(controller.state.nextRepeatDeadlineElapsedMs).isEqualTo(480_000)
    }

    @Test
    fun departureCancelsWakeButReturnDuringCooldownInheritsTheRepeatDeadline() {
        val controller = GateSessionController()
        completeGate(controller, packageName = "A", epoch = 1, releaseAt = 180_000, timing = GateTimingSettings(2, 5))

        assertThat(controller.handle(context("Calculator", 2, GateTimingSettings(2, 5)), 240_000))
            .contains(GateEffect.CancelRepeat)
        assertThat(loads(controller.handle(context("A", 3, GateTimingSettings(2, 5)), 270_000))).isEmpty()
        assertThat(controller.state.nextRepeatDeadlineElapsedMs).isEqualTo(480_000)

        controller.handle(context("Calculator", 4, GateTimingSettings(2, 5)), 350_000)
        assertThat(loads(controller.handle(context("A", 5, GateTimingSettings(2, 5)), 360_000))).hasSize(1)
    }

    @Test
    fun cooldownExpiryDoesNotInterruptAnAlreadyAdmittedForegroundSession() {
        val controller = GateSessionController()
        completeGate(controller, packageName = "A", epoch = 1, releaseAt = 180_000, timing = GateTimingSettings(2, 5))

        assertThat(loads(controller.handle(context("A", 1, GateTimingSettings(2, 5)), 300_000))).isEmpty()
    }

    @Test
    fun simultaneousEntryAndRepeatWakeStartOnlyOneAttemptInEitherOrder() {
        fun controllerAtBoundary(): GateSessionController =
            GateSessionController().also {
                completeGate(it, packageName = "A", epoch = 1, releaseAt = 180_000, timing = GateTimingSettings(5, 5))
            }

        val entryFirst = controllerAtBoundary()
        val entryEffects = entryFirst.handle(context("A", 2, GateTimingSettings(5, 5)), 480_000)
        val wakeEffects = entryFirst.handle(GateEvent.RepeatWake(1), 480_000)
        assertThat(loads(entryEffects) + loads(wakeEffects)).hasSize(1)

        val wakeFirst = controllerAtBoundary()
        val wakeEffectsFirst = wakeFirst.handle(GateEvent.RepeatWake(1), 480_000)
        val entryEffectsSecond = wakeFirst.handle(context("A", 2, GateTimingSettings(5, 5)), 480_000)
        assertThat(loads(wakeEffectsFirst) + loads(entryEffectsSecond)).hasSize(1)
    }

    @Test
    fun zeroCooldownPreservesEntryGatesAndZeroRepeatDoesNotScheduleWake() {
        val controller = GateSessionController()
        completeGate(controller, packageName = "A", epoch = 1, releaseAt = 100, timing = GateTimingSettings())

        assertThat(controller.state.nextRepeatDeadlineElapsedMs).isNull()
        assertThat(loads(controller.handle(context("B", 2, GateTimingSettings()), 101))).hasSize(1)
    }

    @Test
    fun staleResultsAndDuplicateCompletionCannotAwardAnotherRelease() {
        val controller = GateSessionController()
        val initial = loads(controller.handle(context("A", 1, GateTimingSettings(1, 5)), 0)).single()
        controller.handle(context("Calculator", 2, GateTimingSettings(1, 5)), 1)

        assertThat(controller.handle(GateEvent.FirstCardLoaded(initial.attempt.id, item()), 2)).isEmpty()
        assertThat(controller.state.lastReviewedReleaseElapsedMs).isNull()

        val active = loads(controller.handle(context("A", 3, GateTimingSettings(1, 5)), 3)).single()
        controller.handle(GateEvent.FirstCardLoaded(active.attempt.id, item()), 4)
        controller.handle(
            GateEvent.OverlayReleased(active.attempt.id, GateCompletion(1)),
            5,
        )
        assertThat(controller.state.lastReviewedReleaseElapsedMs).isEqualTo(5)
        assertThat(
            controller.handle(
                GateEvent.OverlayReleased(active.attempt.id, GateCompletion(1)),
                6,
            ),
        ).isEmpty()
        assertThat(controller.state.lastReviewedReleaseElapsedMs).isEqualTo(5)
    }

    @Test
    fun cancellationAfterAPartialGateDoesNotAwardCooldown() {
        val controller = GateSessionController()
        val attempt = loads(controller.handle(context("A", 1, GateTimingSettings(1, 5)), 0)).single()
        controller.handle(GateEvent.FirstCardLoaded(attempt.attempt.id, item()), 1)

        assertThat(controller.handle(context("Calculator", 2, GateTimingSettings(1, 5)), 2))
            .contains(GateEffect.HideOverlay)
        assertThat(controller.state.lastReviewedReleaseElapsedMs).isNull()
        assertThat(controller.state.cooldownDeadlineElapsedMs).isNull()
    }

    @Test
    fun staleTimerWakeCannotStartAnAttemptAfterRescheduling() {
        val controller = GateSessionController()
        completeGate(controller, packageName = "A", epoch = 1, releaseAt = 0, timing = GateTimingSettings(0, 5))
        val rescheduled = controller.handle(context("A", 1, GateTimingSettings(0, 10)), 1)
        val currentRevision = rescheduled.filterIsInstance<GateEffect.ScheduleRepeat>().single().timerRevision

        assertThat(loads(controller.handle(GateEvent.RepeatWake(currentRevision - 1), 600_000))).isEmpty()
        assertThat(loads(controller.handle(GateEvent.RepeatWake(currentRevision), 600_000))).hasSize(1)
    }

    @Test
    fun zeroCompletedCardsDoesNotAwardCooldown() {
        val controller = GateSessionController()
        val attempt = loads(controller.handle(context("A", 1, GateTimingSettings(1, 5)), 0)).single()
        controller.handle(GateEvent.FirstCardLoaded(attempt.attempt.id, item()), 1)

        controller.handle(
            GateEvent.OverlayReleased(attempt.attempt.id, GateCompletion(0)),
            2,
        )

        assertThat(controller.state.lastReviewedReleaseElapsedMs).isNull()
        assertThat(controller.state.admission).isEqualTo(GateAdmission.NEEDS_GATE)
    }

    @Test
    fun attachFailureReturnsToAThrottledGateNeedWithoutCredit() {
        val controller = GateSessionController()
        val attempt = loads(controller.handle(context("A", 1, GateTimingSettings(1, 0)), 0)).single()
        controller.handle(GateEvent.FirstCardLoaded(attempt.attempt.id, item()), 1)

        assertThat(controller.handle(GateEvent.OverlayAttachFailed(attempt.attempt.id), 2)).isEmpty()
        assertThat(controller.state.lastReviewedReleaseElapsedMs).isNull()
        assertThat(controller.state.retryNotBeforeElapsedMs).isEqualTo(1_002)
    }

    @Test
    fun waitsForReadyEligibilityBeforeEvaluatingTheCachedForeground() {
        val controller = GateSessionController()
        val unavailable =
            GateEvent.ContextChanged(
                GateContextSnapshot(
                    preferencesReady = false,
                    enabled = true,
                    blockedPackages = setOf("A"),
                    timing = GateTimingSettings(),
                    cardsPerGate = 1,
                    foregroundPackage = "A",
                    foregroundEpoch = 1,
                    deviceEligible = true,
                    ownActivityResumed = false,
                ),
            )

        assertThat(loads(controller.handle(unavailable, 0))).isEmpty()
        assertThat(loads(controller.handle(context("A", 1, GateTimingSettings()), 1))).hasSize(1)
    }

    @Test
    fun timingEditsUseOriginalAnchorsWithoutInterruptingTheCurrentSession() {
        val controller = GateSessionController()
        completeGate(controller, packageName = "A", epoch = 1, releaseAt = 100, timing = GateTimingSettings(1, 5))

        assertThat(loads(controller.handle(context("A", 1, GateTimingSettings(0, 5)), 200))).isEmpty()
        assertThat(loads(controller.handle(context("A", 1, GateTimingSettings(3, 5)), 200))).isEmpty()
        assertThat(controller.state.cooldownDeadlineElapsedMs).isEqualTo(180_100)
        assertThat(controller.handle(context("A", 1, GateTimingSettings(3, 0)), 200)).contains(GateEffect.CancelRepeat)
        assertThat(loads(controller.handle(context("A", 1, GateTimingSettings(3, 1)), 70_100))).hasSize(1)
    }

    @Test
    fun timingEditDuringAnEmptyProbeThrottleCancelsAndReschedulesTheExistingRepeatWake() {
        val controller = GateSessionController()
        val attempt = loads(controller.handle(context("A", 1, GateTimingSettings(1, 5)), 0)).single()
        controller.handle(GateEvent.FirstCardUnavailable(attempt.attempt.id), 20)

        assertThat(controller.handle(context("A", 1, GateTimingSettings(1, 0)), 500))
            .contains(GateEffect.CancelRepeat)
        assertThat(controller.state.nextRepeatDeadlineElapsedMs).isNull()
        assertThat(loads(controller.handle(GateEvent.RepeatWake(1), 300_020))).isEmpty()
    }

    @Test
    fun emptyAndErrorFirstCardProbesDoNotAwardCooldownAndRecoverFromProbeTime() {
        val controller = GateSessionController()
        val first = loads(controller.handle(context("A", 1, GateTimingSettings(1, 5)), 0)).single()

        val emptyEffects =
            controller.handle(
                GateEvent.FirstCardUnavailable(first.attempt.id),
                20,
            )
        assertThat(controller.state.lastReviewedReleaseElapsedMs).isNull()
        assertThat(
            emptyEffects.filterIsInstance<GateEffect.ScheduleRepeat>().single().deadlineElapsedMs,
        ).isEqualTo(300_020)
        assertThat(loads(controller.handle(context("A", 1, GateTimingSettings(1, 5)), 500))).isEmpty()
        assertThat(loads(controller.handle(context("A", 1, GateTimingSettings(1, 5)), 1_020))).hasSize(1)

        controller.handle(context("Calculator", 2, GateTimingSettings(1, 5)), 1_021)
        assertThat(loads(controller.handle(context("A", 3, GateTimingSettings(1, 5)), 1_022))).hasSize(1)
    }

    @Test
    fun switchingSelectedAppsCancelsTheOldAttemptBeforeStartingTheNewOne() {
        val controller = GateSessionController()
        val first = loads(controller.handle(context("A", 1, GateTimingSettings()), 0)).single()

        val effects = controller.handle(context("B", 2, GateTimingSettings()), 1)

        assertThat(effects).contains(GateEffect.CancelFirstCardLoad)
        val second = loads(effects).single()
        assertThat(second.attempt.id).isNotEqualTo(first.attempt.id)
        assertThat(second.attempt.packageName).isEqualTo("B")
        assertThat(controller.handle(GateEvent.FirstCardLoaded(first.attempt.id, item()), 2)).isEmpty()
    }

    @Test
    fun successfulReleaseReplacesTheEmptyProbeAnchor() {
        val controller = GateSessionController()
        val initial = loads(controller.handle(context("A", 1, GateTimingSettings(1, 5)), 0)).single()
        controller.handle(GateEvent.FirstCardUnavailable(initial.attempt.id), 20)
        val retry = loads(controller.handle(context("A", 1, GateTimingSettings(1, 5)), 1_020)).single()
        controller.handle(GateEvent.FirstCardLoaded(retry.attempt.id, item()), 1_021)

        val effects =
            controller.handle(
                GateEvent.OverlayReleased(retry.attempt.id, GateCompletion(1)),
                2_000,
            )
        assertThat(controller.state.lastReviewedReleaseElapsedMs).isEqualTo(2_000)
        assertThat(effects.filterIsInstance<GateEffect.ScheduleRepeat>().single().deadlineElapsedMs).isEqualTo(302_000)
    }

    private fun completeGate(
        controller: GateSessionController,
        packageName: String,
        epoch: Long,
        releaseAt: Long,
        timing: GateTimingSettings,
    ) {
        val attempt = loads(controller.handle(context(packageName, epoch, timing), 0)).single()
        controller.handle(GateEvent.FirstCardLoaded(attempt.attempt.id, item()), 1)
        controller.handle(
            GateEvent.OverlayReleased(attempt.attempt.id, GateCompletion(1)),
            releaseAt,
        )
    }

    private fun context(
        packageName: String,
        epoch: Long,
        timing: GateTimingSettings,
    ) = GateEvent.ContextChanged(
        GateContextSnapshot(
            preferencesReady = true,
            enabled = true,
            blockedPackages = setOf("A", "B"),
            timing = timing,
            cardsPerGate = 1,
            foregroundPackage = packageName,
            foregroundEpoch = epoch,
            deviceEligible = true,
            ownActivityResumed = false,
        ),
    )

    private fun loads(effects: List<GateEffect>): List<GateEffect.LoadFirstCard> =
        effects.filterIsInstance<GateEffect.LoadFirstCard>()

    private fun item() = VocabularyItem(word = "word", translation = "translation", isNew = false)
}
