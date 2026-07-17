package com.procrastilearn.app.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.procrastilearn.app.data.local.dao.VocabularyDao
import com.procrastilearn.app.data.local.mapper.toEntity
import com.procrastilearn.app.data.local.prefs.DayCountersStore
import com.procrastilearn.app.data.local.prefs.LanguagePreferencesStore
import com.procrastilearn.app.data.local.prefs.OpenAiPreferencesStore
import com.procrastilearn.app.data.local.prefs.OpenAiPromptDefaults
import com.procrastilearn.app.domain.model.Language
import com.procrastilearn.app.domain.model.MixMode
import com.procrastilearn.app.domain.model.VocabularyExportItem
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.domain.parser.VocabularyExportParser
import com.procrastilearn.app.domain.parser.VocabularyImportOption
import com.procrastilearn.app.domain.parser.VocabularyParser
import com.procrastilearn.app.domain.repository.VocabularyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import javax.inject.Inject

data class SettingsUiState(
    val mixMode: MixMode = MixMode.MIX,
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
@Suppress("LongParameterList")
class SettingsViewModel
    @Inject
    constructor(
        private val dayCountersStore: DayCountersStore,
        private val openAiStore: OpenAiPreferencesStore,
        private val languagePreferencesStore: LanguagePreferencesStore,
        private val vocabularyDao: VocabularyDao,
        private val vocabularyRepository: VocabularyRepository,
        private val parsers: Set<@JvmSuppressWildcards VocabularyParser>,
        private val ioDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        val uiState: StateFlow<SettingsUiState> =
            kotlinx.coroutines.flow
                .combine(
                    dayCountersStore.readPolicy(),
                    openAiStore.readOpenAiApiKey(),
                    openAiStore.readOpenAiPrompt(),
                    openAiStore.readOpenAiReversePrompt(),
                    languagePreferencesStore.readLanguagePair(),
                ) { policy, apiKey, prompt, reversePrompt, languagePair ->
                    SettingsUiState(
                        mixMode = policy.mixMode,
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

        val importOptions: List<VocabularyImportOption> =
            parsers
                .map { parser ->
                    VocabularyImportOption(
                        id = parser.id,
                        titleResId = parser.titleResId,
                        descriptionResId = parser.descriptionResId,
                        mimeTypes = parser.mimeTypes,
                        extensions = parser.supportedExtensions,
                    )
                }.sortedBy { it.titleResId }

        fun onMixModeChange(mode: MixMode) {
            viewModelScope.launch { dayCountersStore.setMixMode(mode) }
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
            viewModelScope.launch { openAiStore.setOpenAiApiKey(value) }
        }

        fun onOpenAiPromptChange(value: String) {
            viewModelScope.launch { openAiStore.setOpenAiPrompt(value) }
        }

        fun onOpenAiReversePromptChange(value: String) {
            viewModelScope.launch { openAiStore.setOpenAiReversePrompt(value) }
        }

        fun onLanguagePairChange(
            native: Language,
            target: Language,
        ) {
            viewModelScope.launch { languagePreferencesStore.setLanguagePair(native, target) }
        }

        /**
         * Export all vocabulary rows (full DB fields) as a JSON array to the given [uri].
         * Calls [onComplete] on the main thread with success/failure.
         */
        @Suppress("TooGenericExceptionCaught")
        fun exportVocabularyToUri(
            context: Context,
            uri: Uri,
            onComplete: (Boolean) -> Unit,
        ) {
            viewModelScope.launch(ioDispatcher) {
                val ok =
                    try {
                        val list = vocabularyDao.getAllVocabulary().first()
                        val json =
                            JSONArray().apply {
                                list.forEach { e ->
                                    put(
                                        JSONObject().apply {
                                            put("id", e.id)
                                            put("word", e.word)
                                            put("translation", e.translation)
                                            put("createdAt", e.createdAt)
                                            if (e.lastShownAt ==
                                                null
                                            ) {
                                                put("lastShownAt", JSONObject.NULL)
                                            } else {
                                                put("lastShownAt", e.lastShownAt)
                                            }
                                            put("correctCount", e.correctCount)
                                            put("incorrectCount", e.incorrectCount)
                                            put("fsrsCardJson", e.fsrsCardJson)
                                            put("fsrsDueAt", e.fsrsDueAt)
                                        },
                                    )
                                }
                            }

                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.writer(Charsets.UTF_8).use { writer ->
                                writer.write(json.toString())
                                writer.flush()
                            }
                        } ?: false
                        true
                    } catch (t: Throwable) {
                        Log.e("SettingsViewModel", "Failed to export vocabulary to uri=$uri", t)
                        false
                    }

                withContext(Dispatchers.Main) { onComplete(ok) }
            }
        }

        fun importVocabularyFromUri(
            context: Context,
            optionId: String,
            uri: Uri,
            onComplete: (VocabularyImportResult) -> Unit,
        ) {
            viewModelScope.launch(ioDispatcher) {
                val parser = findParser(optionId)
                if (parser == null) {
                    withContext(Dispatchers.Main) {
                        onComplete(VocabularyImportResult.Failure(VocabularyImportFailureReason.UNSUPPORTED_FORMAT))
                    }
                    return@launch
                }

                val suffix =
                    parser.supportedExtensions.firstOrNull()?.let { ".$it" }
                        ?: ".tmp"
                val tempFile = File.createTempFile("pl-import-", suffix, context.cacheDir)

                val result =
                    try {
                        performImport(context, parser, uri, tempFile)
                    } finally {
                        tempFile.delete()
                    }

                Log.d(
                    "SettingsViewModel",
                    "importVocabularyFromUri optionId=$optionId uri=$uri result=$result",
                )
                withContext(Dispatchers.Main) {
                    onComplete(result)
                }
            }
        }

        @Suppress("TooGenericExceptionCaught")
        private suspend fun performImport(
            context: Context,
            parser: VocabularyParser,
            uri: Uri,
            tempFile: File,
        ): VocabularyImportResult =
            try {
                importFromStream(context, parser, uri, tempFile)
            } catch (exception: IllegalArgumentException) {
                Log.e(
                    "SettingsViewModel",
                    "Failed to parse imported vocabulary from uri=$uri",
                    exception,
                )
                VocabularyImportResult.Failure(VocabularyImportFailureReason.PARSE_ERROR)
            } catch (throwable: Throwable) {
                Log.e(
                    "SettingsViewModel",
                    "Failed to import vocabulary from uri=$uri",
                    throwable,
                )
                VocabularyImportResult.Failure(VocabularyImportFailureReason.FILE_ERROR)
            }

        private suspend fun importFromStream(
            context: Context,
            parser: VocabularyParser,
            uri: Uri,
            tempFile: File,
        ): VocabularyImportResult {
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Log.w(
                    "SettingsViewModel",
                    "openInputStream returned null for uri=$uri with resolver=${context.contentResolver}",
                )
                return VocabularyImportResult.Failure(VocabularyImportFailureReason.FILE_ERROR)
            }
            Log.d("SettingsViewModel", "openInputStream succeeded for uri=$uri")
            copyToTempFile(inputStream, tempFile)
            return parseAndImport(parser, tempFile)
        }

        private fun copyToTempFile(
            inputStream: InputStream,
            tempFile: File,
        ) {
            inputStream.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
        }

        private suspend fun parseAndImport(
            parser: VocabularyParser,
            tempFile: File,
        ): VocabularyImportResult =
            if (parser is VocabularyExportParser) {
                val items = parser.parseExport(tempFile)
                importExportItems(items)
                VocabularyImportResult.Success(items.size)
            } else {
                val items = parser.parse(tempFile)
                importItems(items)
                VocabularyImportResult.Success(items.size)
            }

        private suspend fun importItems(items: List<VocabularyItem>) {
            items.forEach { item ->
                vocabularyRepository.addVocabularyItem(item)
            }
        }

        private suspend fun importExportItems(items: List<VocabularyExportItem>) {
            if (items.isEmpty()) return
            vocabularyDao.insertAllVocabulary(items.map { it.toEntity() })
        }

        private fun findParser(optionId: String): VocabularyParser? =
            parsers.firstOrNull { parser ->
                parser.id.equals(optionId, ignoreCase = true) ||
                    parser.supportedExtensions.any { ext -> ext.equals(optionId, ignoreCase = true) }
            }
    }

sealed interface VocabularyImportResult {
    data class Success(
        val importedCount: Int,
    ) : VocabularyImportResult

    data class Failure(
        val reason: VocabularyImportFailureReason,
    ) : VocabularyImportResult
}

enum class VocabularyImportFailureReason {
    UNSUPPORTED_FORMAT,
    FILE_ERROR,
    PARSE_ERROR,
}
