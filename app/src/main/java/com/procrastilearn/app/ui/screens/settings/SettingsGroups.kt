package com.procrastilearn.app.ui.screens.settings

import com.procrastilearn.app.domain.model.GateTimingField
import com.procrastilearn.app.domain.model.Language
import com.procrastilearn.app.domain.model.MixMode
import com.procrastilearn.app.domain.model.NewCardOrder
import com.procrastilearn.app.domain.model.StudyDirectionMode
import com.procrastilearn.app.ui.GateTimingSaveResult
import com.procrastilearn.app.ui.GateTimingSaveState

data class StudySettings(
    val mixMode: MixMode,
    val studyDirectionMode: StudyDirectionMode,
    val newPerDay: Int,
    val availableNewCount: Int,
    val availableToAddToday: Int,
    val reviewPerDay: Int,
    val maximumIntervalDays: Int,
    val overlayInterval: Int,
    val gateCooldownMinutes: Int,
    val cardsPerGate: Int,
    val pauseBackgroundAudio: Boolean = true,
    val ratingDelaySeconds: Int,
    val newCardOrder: NewCardOrder,
    val gateTimingSaveState: GateTimingSaveState = GateTimingSaveState.Idle,
)

data class StudySettingsCallbacks(
    val onMixModeChange: (MixMode) -> Unit,
    val onStudyDirectionModeChange: (StudyDirectionMode) -> Unit,
    val onNewPerDayDialogOpen: () -> Unit,
    val onNewPerDayChange: (Int) -> Unit,
    val onAddCardsForToday: (Int) -> Unit,
    val onReviewPerDayChange: (Int) -> Unit,
    val onMaximumIntervalDaysChange: (Int) -> Unit,
    val onGateTimingSave: ((GateTimingField, Int, (GateTimingSaveResult) -> Unit) -> Unit)? = null,
    val onOverlayIntervalChange: (Int) -> Unit = {},
    val onGateCooldownChange: (Int) -> Unit = {},
    val onCardsPerGateChange: (Int) -> Unit,
    val onPauseBackgroundAudioChange: (Boolean) -> Unit = {},
    val onRatingDelayChange: (Int) -> Unit,
    val onNewCardOrderChange: (NewCardOrder) -> Unit,
    val clearGateTimingSaveState: () -> Unit = {},
)

data class AiSettings(
    val openAiApiKey: String?,
    val openAiPrompt: String,
    val openAiReversePrompt: String,
    val nativeLanguage: Language,
    val targetLanguage: Language,
)

data class AiSettingsCallbacks(
    val onOpenAiApiKeyChange: (String) -> Unit,
    val onOpenAiPromptChange: (String) -> Unit,
    val onOpenAiReversePromptChange: (String) -> Unit,
    val onLanguagePairChange: (Language, Language) -> Unit,
)
