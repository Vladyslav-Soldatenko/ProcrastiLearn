package com.procrastilearn.app.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.procrastilearn.app.data.export.VocabularyImportResult
import com.procrastilearn.app.data.export.VocabularyTransferManager
import com.procrastilearn.app.data.local.dao.VocabularyDao
import com.procrastilearn.app.data.local.prefs.DayCountersStore
import com.procrastilearn.app.data.local.prefs.OpenAiPromptDefaults
import com.procrastilearn.app.data.local.prefs.TranslationPreferences
import com.procrastilearn.app.domain.model.Language
import com.procrastilearn.app.domain.model.MixMode
import com.procrastilearn.app.domain.model.StudyDirectionMode
import com.procrastilearn.app.domain.parser.VocabularyImportOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SettingsUiState(
    val mixMode: MixMode = MixMode.MIX,
    val studyDirectionMode: StudyDirectionMode = StudyDirectionMode.FORWARD,
    val newPerDay: Int = 10,
    val reviewPerDay: Int = 100,
    val overlayInterval: Int = 6,
    val openAiApiKey: String? = null,
    val openAiPrompt: String = OpenAiPromptDefaults.translationPrompt,
    val openAiReversePrompt: String = OpenAiPromptDefaults.reverseTranslationPrompt,
    val nativeLanguage: Language = Language.ENGLISH,
    val targetLanguage: Language = Language.RUSSIAN,
)

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val dayCountersStore: DayCountersStore,
        private val translationPreferences: TranslationPreferences,
        private val vocabularyDao: VocabularyDao,
        private val transferManager: VocabularyTransferManager,
    ) : ViewModel() {
        val uiState: StateFlow<SettingsUiState> =
            kotlinx.coroutines.flow
                .combine(
                    dayCountersStore.readPolicy(),
                    translationPreferences.openAiStore.readOpenAiApiKey(),
                    translationPreferences.openAiStore.readOpenAiPrompt(),
                    translationPreferences.openAiStore.readOpenAiReversePrompt(),
                    translationPreferences.languagePreferencesStore.readLanguagePair(),
                ) { policy, apiKey, prompt, reversePrompt, languagePair ->
                    SettingsUiState(
                        mixMode = policy.mixMode,
                        studyDirectionMode = policy.studyDirectionMode,
                        newPerDay = policy.newPerDay,
                        reviewPerDay = policy.reviewPerDay,
                        overlayInterval = policy.overlayInterval,
                        openAiApiKey = apiKey,
                        openAiPrompt = prompt,
                        openAiReversePrompt = reversePrompt,
                        nativeLanguage = languagePair?.native ?: Language.ENGLISH,
                        targetLanguage = languagePair?.target ?: Language.RUSSIAN,
                    )
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

        private val _availableNewCount = MutableStateFlow(0)
        val availableNewCount: StateFlow<Int> = _availableNewCount

        // How many more new cards can still be added to today's quota before the
        // total new-card quota would exceed the actual number of unseen cards.
        private val _availableToAddToday = MutableStateFlow(0)
        val availableToAddToday: StateFlow<Int> = _availableToAddToday

        fun loadAvailableNewCount() {
            viewModelScope.launch {
                val totalNew = vocabularyDao.countNewTotal()
                _availableNewCount.value = totalNew

                val policy = dayCountersStore.readPolicy().first()
                val counters = dayCountersStore.read().first()
                val remaining =
                    (policy.newPerDay + counters.extraNewToday - counters.newShown).coerceAtLeast(0)
                _availableToAddToday.value = (totalNew - remaining).coerceAtLeast(0)
            }
        }

        val importOptions: List<VocabularyImportOption> = transferManager.importOptions

        fun onMixModeChange(mode: MixMode) {
            viewModelScope.launch { dayCountersStore.setMixMode(mode) }
        }

        fun onStudyDirectionModeChange(mode: StudyDirectionMode) {
            viewModelScope.launch { dayCountersStore.setStudyDirectionMode(mode) }
        }

        fun onNewPerDayChange(value: Int) {
            viewModelScope.launch { dayCountersStore.setNewPerDay(value) }
        }

        fun onAddCardsForToday(amount: Int) {
            viewModelScope.launch {
                dayCountersStore.addExtraNewToday(amount, vocabularyDao.countNewTotal())
            }
        }

        fun onReviewPerDayChange(value: Int) {
            viewModelScope.launch { dayCountersStore.setReviewPerDay(value) }
        }

        fun onOverlayIntervalChange(value: Int) {
            viewModelScope.launch { dayCountersStore.setOverlayInterval(value) }
        }

        fun onOpenAiApiKeyChange(value: String) {
            viewModelScope.launch { translationPreferences.openAiStore.setOpenAiApiKey(value) }
        }

        fun onOpenAiPromptChange(value: String) {
            viewModelScope.launch { translationPreferences.openAiStore.setOpenAiPrompt(value) }
        }

        fun onOpenAiReversePromptChange(value: String) {
            viewModelScope.launch { translationPreferences.openAiStore.setOpenAiReversePrompt(value) }
        }

        fun onLanguagePairChange(
            native: Language,
            target: Language,
        ) {
            viewModelScope.launch {
                translationPreferences.languagePreferencesStore.setLanguagePair(native, target)
            }
        }

        /**
         * Export all vocabulary rows (full DB fields) as a JSON array to the given [uri].
         * Calls [onComplete] on the main thread with success/failure.
         */
        fun exportVocabularyToUri(
            context: Context,
            uri: Uri,
            onComplete: (Boolean) -> Unit,
        ) {
            viewModelScope.launch {
                val ok = transferManager.exportToUri(context, uri)
                withContext(Dispatchers.Main) { onComplete(ok) }
            }
        }

        fun importVocabularyFromUri(
            context: Context,
            optionId: String,
            uri: Uri,
            onComplete: (VocabularyImportResult) -> Unit,
        ) {
            viewModelScope.launch {
                val result = transferManager.importFromUri(context, optionId, uri)
                withContext(Dispatchers.Main) { onComplete(result) }
            }
        }
    }
