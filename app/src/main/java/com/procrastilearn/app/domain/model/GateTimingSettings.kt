package com.procrastilearn.app.domain.model

const val MAX_GATE_TIMING_MINUTES = 2000

data class GateTimingSettings(
    val cooldownMinutes: Int = 0,
    val repeatIntervalMinutes: Int = 0,
)

enum class GateTimingField {
    COOLDOWN,
    REPEAT_INTERVAL,
}

sealed interface GateTimingValidationError {
    data class OutOfRange(
        val field: GateTimingField,
    ) : GateTimingValidationError

    data class CooldownExceedsRepeat(
        val cooldownMinutes: Int,
        val repeatIntervalMinutes: Int,
    ) : GateTimingValidationError
}

sealed interface GateTimingChangeResult {
    data object Applied : GateTimingChangeResult

    data class Rejected(
        val error: GateTimingValidationError,
    ) : GateTimingChangeResult
}

fun validateGateTiming(settings: GateTimingSettings): GateTimingValidationError? =
    when {
        settings.cooldownMinutes !in 0..MAX_GATE_TIMING_MINUTES ->
            GateTimingValidationError.OutOfRange(GateTimingField.COOLDOWN)
        settings.repeatIntervalMinutes !in 0..MAX_GATE_TIMING_MINUTES ->
            GateTimingValidationError.OutOfRange(GateTimingField.REPEAT_INTERVAL)
        settings.repeatIntervalMinutes > 0 && settings.cooldownMinutes > settings.repeatIntervalMinutes ->
            GateTimingValidationError.CooldownExceedsRepeat(
                settings.cooldownMinutes,
                settings.repeatIntervalMinutes,
            )
        else -> null
    }
