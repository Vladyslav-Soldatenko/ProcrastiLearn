package com.procrastilearn.app.service

class GateSessionController {
    var state: GateSessionState = GateSessionState()
        private set

    fun handle(
        event: GateEvent,
        nowElapsedMs: Long,
    ): List<GateEffect> =
        when (event) {
            is GateEvent.ContextChanged -> handleContextChanged(event.snapshot, nowElapsedMs)
            is GateEvent.FirstCardLoaded -> handleFirstCardLoaded(event)
            is GateEvent.FirstCardUnavailable -> handleFirstCardUnavailable(event, nowElapsedMs)
            is GateEvent.OverlayReleased -> handleOverlayReleased(event, nowElapsedMs)
            is GateEvent.RepeatWake -> handleRepeatWake(event, nowElapsedMs)
            is GateEvent.OverlayAttachFailed -> handleOverlayAttachFailed(event, nowElapsedMs)
            GateEvent.Stop -> stop()
        }

    private fun handleContextChanged(
        snapshot: GateContextSnapshot,
        nowElapsedMs: Long,
    ): List<GateEffect> {
        val previous = state.context
        val wasEligible = previous?.isEligible == true
        val wasTiming = previous?.timing
        val enteredForeground = previous == null || snapshot.foregroundEpoch != previous.foregroundEpoch
        val changedPackage = previous?.foregroundPackage != snapshot.foregroundPackage
        state = state.copy(context = snapshot, cooldownDeadlineElapsedMs = cooldownDeadline(snapshot))

        return when {
            !snapshot.isEligible -> {
                cancelActiveGate()
            }
            state.phase != GatePhase.Idle -> {
                if (changedPackage) {
                    cancelActiveGate() + admitOrBeginEntry(nowElapsedMs)
                } else {
                    emptyList()
                }
            }
            !wasEligible || enteredForeground -> {
                admitOrBeginEntry(nowElapsedMs)
            }
            wasTiming != snapshot.timing -> {
                handleTimingChanged(snapshot, nowElapsedMs)
            }
            state.admission == GateAdmission.NEEDS_GATE -> {
                val retryNotBefore = state.retryNotBeforeElapsedMs
                if (retryNotBefore == null || nowElapsedMs >= retryNotBefore) {
                    beginAttempt()
                } else {
                    emptyList()
                }
            }
            else -> {
                emptyList()
            }
        }
    }

    private fun admitOrBeginEntry(nowElapsedMs: Long): List<GateEffect> =
        if (isCooldownActive(nowElapsedMs)) {
            state = state.copy(admission = GateAdmission.GRANTED)
            rescheduleRepeat()
        } else {
            beginAttempt()
        }

    private fun handleTimingChanged(
        snapshot: GateContextSnapshot,
        nowElapsedMs: Long,
    ): List<GateEffect> {
        val deadline = repeatDeadline(snapshot)
        return if (deadline != null && deadline <= nowElapsedMs) {
            beginAttempt()
        } else {
            rescheduleRepeat()
        }
    }

    private fun handleFirstCardLoaded(event: GateEvent.FirstCardLoaded): List<GateEffect> {
        val loading = state.phase as? GatePhase.Loading ?: return emptyList()
        if (loading.attempt.id != event.attemptId || !isCurrentlyEligible()) return emptyList()
        state = state.copy(phase = GatePhase.Studying(loading.attempt))
        return listOf(GateEffect.ShowOverlay(loading.attempt, event.item))
    }

    private fun handleFirstCardUnavailable(
        event: GateEvent.FirstCardUnavailable,
        nowElapsedMs: Long,
    ): List<GateEffect> {
        val loading = state.phase as? GatePhase.Loading ?: return emptyList()
        if (loading.attempt.id != event.attemptId || !isCurrentlyEligible()) return emptyList()
        return recoverUnreviewedAttempt(nowElapsedMs)
    }

    private fun handleOverlayReleased(
        event: GateEvent.OverlayReleased,
        nowElapsedMs: Long,
    ): List<GateEffect> {
        val studying = state.phase as? GatePhase.Studying ?: return emptyList()
        if (studying.attempt.id != event.attemptId || !isCurrentlyEligible()) return emptyList()
        return if (event.completion.completedCards <= 0) {
            recoverUnreviewedAttempt(nowElapsedMs)
        } else {
            state =
                state.copy(
                    phase = GatePhase.Idle,
                    admission = GateAdmission.GRANTED,
                    lastReviewedReleaseElapsedMs = nowElapsedMs,
                    repeatAnchorElapsedMs = nowElapsedMs,
                    cooldownDeadlineElapsedMs = cooldownDeadline(state.context!!, nowElapsedMs),
                    retryNotBeforeElapsedMs = null,
                )
            rescheduleRepeat() + GateEffect.ReturnToApp(studying.attempt.packageName)
        }
    }

    private fun handleRepeatWake(
        event: GateEvent.RepeatWake,
        nowElapsedMs: Long,
    ): List<GateEffect> {
        val deadline = state.nextRepeatDeadlineElapsedMs ?: return emptyList()
        val validWake = event.timerRevision == state.timerRevision && nowElapsedMs >= deadline
        if (
            !validWake ||
            state.phase != GatePhase.Idle ||
            !isCurrentlyEligible()
        ) {
            return emptyList()
        }
        state = state.copy(nextRepeatDeadlineElapsedMs = null)
        return beginAttempt()
    }

    private fun handleOverlayAttachFailed(
        event: GateEvent.OverlayAttachFailed,
        nowElapsedMs: Long,
    ): List<GateEffect> {
        val studying = state.phase as? GatePhase.Studying ?: return emptyList()
        if (studying.attempt.id != event.attemptId || !isCurrentlyEligible()) return emptyList()
        return recoverUnreviewedAttempt(nowElapsedMs)
    }

    private fun recoverUnreviewedAttempt(nowElapsedMs: Long): List<GateEffect> {
        state =
            state.copy(
                phase = GatePhase.Idle,
                admission = GateAdmission.NEEDS_GATE,
                repeatAnchorElapsedMs = nowElapsedMs,
                retryNotBeforeElapsedMs = nowElapsedMs + RETRY_DELAY_MS,
            )
        return rescheduleRepeat()
    }

    private fun beginAttempt(): List<GateEffect> {
        val context = state.context ?: return emptyList()
        val packageName = context.foregroundPackage ?: return emptyList()
        val attempt =
            GateAttempt(
                id = state.nextAttemptId,
                packageName = packageName,
                requiredCards = context.cardsPerGate,
            )
        val hadWake = state.nextRepeatDeadlineElapsedMs != null
        state =
            state.copy(
                phase = GatePhase.Loading(attempt),
                admission = GateAdmission.NEEDS_GATE,
                nextAttemptId = attempt.id + 1,
                nextRepeatDeadlineElapsedMs = null,
                timerRevision = state.timerRevision + if (hadWake) 1 else 0,
            )
        return buildList {
            if (hadWake) add(GateEffect.CancelRepeat)
            add(GateEffect.LoadFirstCard(attempt))
        }
    }

    private fun cancelActiveGate(): List<GateEffect> {
        val effects = mutableListOf<GateEffect>()
        when (state.phase) {
            is GatePhase.Loading -> effects += GateEffect.CancelFirstCardLoad
            is GatePhase.Studying -> effects += GateEffect.HideOverlay
            GatePhase.Idle -> Unit
        }
        if (state.nextRepeatDeadlineElapsedMs != null) effects += GateEffect.CancelRepeat
        state =
            state.copy(
                phase = GatePhase.Idle,
                admission = null,
                nextRepeatDeadlineElapsedMs = null,
                timerRevision = state.timerRevision + 1,
            )
        return effects
    }

    private fun stop(): List<GateEffect> = cancelActiveGate().also { state = state.copy(context = null) }

    private fun rescheduleRepeat(): List<GateEffect> {
        val deadline = repeatDeadline(state.context ?: return emptyList())
        val hadWake = state.nextRepeatDeadlineElapsedMs != null
        if (deadline == null || !isCurrentlyEligible()) {
            state =
                state.copy(
                    nextRepeatDeadlineElapsedMs = null,
                    timerRevision =
                        state.timerRevision + if (hadWake) 1 else 0,
                )
            return if (hadWake) listOf(GateEffect.CancelRepeat) else emptyList()
        }
        val revision = state.timerRevision + 1
        state = state.copy(nextRepeatDeadlineElapsedMs = deadline, timerRevision = revision)
        return buildList {
            if (hadWake) add(GateEffect.CancelRepeat)
            add(GateEffect.ScheduleRepeat(deadline, revision))
        }
    }

    private fun cooldownDeadline(
        snapshot: GateContextSnapshot,
        releaseAt: Long? = state.lastReviewedReleaseElapsedMs,
    ): Long? = releaseAt?.plus(snapshot.timing.cooldownMinutes * MINUTE_MS)

    private fun repeatDeadline(snapshot: GateContextSnapshot): Long? =
        state.repeatAnchorElapsedMs
            ?.takeIf { snapshot.timing.repeatIntervalMinutes > 0 }
            ?.plus(snapshot.timing.repeatIntervalMinutes * MINUTE_MS)

    private fun isCooldownActive(nowElapsedMs: Long): Boolean =
        state.cooldownDeadlineElapsedMs?.let { nowElapsedMs < it } == true

    private fun isCurrentlyEligible(): Boolean = state.context?.isEligible == true

    private companion object {
        const val MINUTE_MS = 60_000L
        const val RETRY_DELAY_MS = 1_000L
    }
}
