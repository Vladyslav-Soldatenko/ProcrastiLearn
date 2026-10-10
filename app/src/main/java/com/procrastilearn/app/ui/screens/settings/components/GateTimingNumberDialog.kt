package com.procrastilearn.app.ui.screens.settings.components

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.procrastilearn.app.R
import com.procrastilearn.app.domain.model.GateTimingField
import com.procrastilearn.app.domain.model.GateTimingSettings
import com.procrastilearn.app.domain.model.GateTimingValidationError
import com.procrastilearn.app.domain.model.MAX_GATE_TIMING_MINUTES
import com.procrastilearn.app.domain.model.validateGateTiming
import com.procrastilearn.app.ui.GateTimingSaveResult
import com.procrastilearn.app.ui.GateTimingSaveState
import com.procrastilearn.app.ui.screens.settings.DialogState
import com.procrastilearn.app.ui.screens.settings.StudySettings
import com.procrastilearn.app.ui.screens.settings.StudySettingsCallbacks

@Composable
@Suppress("CognitiveComplexMethod")
internal fun GateTimingNumberDialog(
    dialogState: DialogState,
    studySettings: StudySettings,
    studyCallbacks: StudySettingsCallbacks,
    onDismiss: () -> Unit,
) {
    val field =
        if (dialogState ==
            DialogState.GateCooldown
        ) {
            GateTimingField.COOLDOWN
        } else {
            GateTimingField.REPEAT_INTERVAL
        }
    var saveResult by remember(field) { mutableStateOf<GateTimingSaveResult?>(null) }
    var isSaving by remember(field) { mutableStateOf(false) }
    val legacySaveState = studySettings.gateTimingSaveState
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(legacySaveState) {
        if (studyCallbacks.onGateTimingSave == null && legacySaveState == GateTimingSaveState.Saved(field)) {
            currentOnDismiss()
        }
    }
    val resources = LocalResources.current
    val errorMessage: (GateTimingValidationError) -> String = { error ->
        gateTimingErrorMessage(resources, field, error)
    }
    val isCooldown = field == GateTimingField.COOLDOWN
    val description =
        if (isCooldown) {
            stringResource(R.string.settings_gate_cooldown_description)
        } else {
            null
        }
    NumberInputDialog(
        title =
            stringResource(
                if (isCooldown) {
                    R.string.settings_gate_cooldown_headline
                } else {
                    R.string.settings_overlay_interval_title
                },
            ),
        currentValue =
            if (isCooldown) {
                studySettings.gateCooldownMinutes
            } else {
                studySettings.overlayInterval
            },
        maxValue = MAX_GATE_TIMING_MINUTES,
        description = description,
        validateValue = { value ->
            validateGateTiming(gateTimingCandidate(field, studySettings, value))?.let(errorMessage)
        },
        saveError =
            if (studyCallbacks.onGateTimingSave == null) {
                gateTimingSaveError(resources, field, legacySaveState)
            } else {
                gateTimingSaveError(resources, field, saveResult)
            },
        isSaving =
            if (studyCallbacks.onGateTimingSave ==
                null
            ) {
                legacySaveState is GateTimingSaveState.Saving
            } else {
                isSaving
            },
        onValueConfirm = {
            if (studyCallbacks.onGateTimingSave == null) {
                if (field == GateTimingField.COOLDOWN) {
                    studyCallbacks.onGateCooldownChange(it)
                } else {
                    studyCallbacks.onOverlayIntervalChange(it)
                }
            } else if (!isSaving) {
                isSaving = true
                saveResult = null
                studyCallbacks.onGateTimingSave!!(field, it) { result ->
                    isSaving = false
                    saveResult = result
                    if (result == GateTimingSaveResult.Saved) onDismiss()
                }
            }
        },
        onDismiss = onDismiss,
    )
}

private fun gateTimingErrorMessage(
    resources: Resources,
    field: GateTimingField,
    error: GateTimingValidationError,
): String =
    when (error) {
        is GateTimingValidationError.OutOfRange ->
            resources.getString(R.string.settings_gate_timing_range_error, MAX_GATE_TIMING_MINUTES)
        is GateTimingValidationError.CooldownExceedsRepeat ->
            if (field == GateTimingField.COOLDOWN) {
                resources.getString(R.string.settings_gate_cooldown_exceeds_repeat, error.repeatIntervalMinutes)
            } else {
                resources.getString(R.string.settings_gate_repeat_below_cooldown, error.cooldownMinutes)
            }
    }

private fun gateTimingCandidate(
    field: GateTimingField,
    studySettings: StudySettings,
    value: Int,
): GateTimingSettings =
    when (field) {
        GateTimingField.COOLDOWN -> GateTimingSettings(value, studySettings.overlayInterval)
        GateTimingField.REPEAT_INTERVAL -> GateTimingSettings(studySettings.gateCooldownMinutes, value)
    }

private fun gateTimingSaveError(
    resources: Resources,
    field: GateTimingField,
    state: GateTimingSaveResult?,
): String? =
    when (state) {
        is GateTimingSaveResult.Rejected ->
            gateTimingErrorMessage(resources, field, state.error)
        GateTimingSaveResult.Failed -> resources.getString(R.string.settings_gate_timing_save_failed)
        else -> null
    }

private fun gateTimingSaveError(
    resources: Resources,
    field: GateTimingField,
    state: GateTimingSaveState,
): String? =
    when (state) {
        is GateTimingSaveState.Rejected ->
            if (state.field == field) gateTimingErrorMessage(resources, field, state.error) else null
        is GateTimingSaveState.Failed ->
            if (state.field == field) resources.getString(R.string.settings_gate_timing_save_failed) else null
        else -> null
    }
