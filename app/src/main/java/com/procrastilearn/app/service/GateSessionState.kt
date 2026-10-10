package com.procrastilearn.app.service

import com.procrastilearn.app.domain.model.GateTimingSettings
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.overlay.GateCompletion

data class GateContextSnapshot(
    val preferencesReady: Boolean,
    val enabled: Boolean,
    val blockedPackages: Set<String>,
    val timing: GateTimingSettings,
    val cardsPerGate: Int,
    val foregroundPackage: String?,
    val foregroundEpoch: Long,
    val deviceEligible: Boolean,
    val ownActivityResumed: Boolean,
) {
    val isEligible: Boolean
        get() =
            preferencesReady &&
                enabled &&
                foregroundPackage in blockedPackages &&
                deviceEligible &&
                !ownActivityResumed
}

data class GateAttempt(
    val id: Long,
    val packageName: String,
    val requiredCards: Int,
)

sealed interface GatePhase {
    data object Idle : GatePhase

    data class Loading(
        val attempt: GateAttempt,
    ) : GatePhase

    data class Studying(
        val attempt: GateAttempt,
    ) : GatePhase
}

enum class GateAdmission {
    GRANTED,
    NEEDS_GATE,
}

data class GateSessionState(
    val context: GateContextSnapshot? = null,
    val phase: GatePhase = GatePhase.Idle,
    val admission: GateAdmission? = null,
    val lastReviewedReleaseElapsedMs: Long? = null,
    val repeatAnchorElapsedMs: Long? = null,
    val cooldownDeadlineElapsedMs: Long? = null,
    val retryNotBeforeElapsedMs: Long? = null,
    val nextRepeatDeadlineElapsedMs: Long? = null,
    val nextAttemptId: Long = 1,
    val timerRevision: Long = 0,
)

sealed interface GateEvent {
    data class ContextChanged(
        val snapshot: GateContextSnapshot,
    ) : GateEvent

    data class FirstCardLoaded(
        val attemptId: Long,
        val item: VocabularyItem,
    ) : GateEvent

    data class FirstCardUnavailable(
        val attemptId: Long,
    ) : GateEvent

    data class OverlayReleased(
        val attemptId: Long,
        val completion: GateCompletion,
    ) : GateEvent

    data class RepeatWake(
        val timerRevision: Long,
    ) : GateEvent

    data class OverlayAttachFailed(
        val attemptId: Long,
    ) : GateEvent

    data object Stop : GateEvent
}

sealed interface GateEffect {
    data class LoadFirstCard(
        val attempt: GateAttempt,
    ) : GateEffect

    data object CancelFirstCardLoad : GateEffect

    data class ShowOverlay(
        val attempt: GateAttempt,
        val item: VocabularyItem,
    ) : GateEffect

    data object HideOverlay : GateEffect

    data class ScheduleRepeat(
        val deadlineElapsedMs: Long,
        val timerRevision: Long,
    ) : GateEffect

    data object CancelRepeat : GateEffect

    data class ReturnToApp(
        val packageName: String,
    ) : GateEffect
}
