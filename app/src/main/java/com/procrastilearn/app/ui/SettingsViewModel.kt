package com.procrastilearn.app.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.procrastilearn.app.data.local.dao.VocabularyDao
import com.procrastilearn.app.data.local.mapper.toEntity
import com.procrastilearn.app.data.local.prefs.DayCountersStore
import com.procrastilearn.app.data.local.prefs.OpenAiPromptDefaults
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject

data class SettingsUiState(
    val mixMode: MixMode = MixMode.MIX,
    val newPerDay: Int = 10,
    val reviewPerDay: Int = 100,
    val overlayInterval: Int = 6,
    val openAiApiKey: String? = null,
    val openAiPrompt: String = OpenAiPromptDefaults.translationPrompt,
    val openAiReversePrompt: String = OpenAiPromptDefaults.reverseTranslationPrompt,
)

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val store: DayCountersStore,
        private val vocabularyDao: VocabularyDao,
        private val vocabularyRepository: VocabularyRepository,
        private val parsers: Set<@JvmSuppressWildcards VocabularyParser>,
        private val ioDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        val uiState: StateFlow<SettingsUiState> =
            kotlinx.coroutines.flow
                .combine(
                    store.readPolicy(),
                    store.readOpenAiApiKey(),
                    store.readOpenAiPrompt(),
                    store.readOpenAiReversePrompt(),
                ) { policy, apiKey, prompt, reversePrompt ->
                    SettingsUiState(
                        mixMode = policy.mixMode,
                        newPerDay = policy.newPerDay,
                        reviewPerDay = policy.reviewPerDay,
                        overlayInterval = policy.overlayInterval,
                        openAiApiKey = apiKey,
                        openAiPrompt = prompt,
                        openAiReversePrompt = reversePrompt,
                    )
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

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
            viewModelScope.launch { store.setMixMode(mode) }
        }

        fun onNewPerDayChange(value: Int) {
            viewModelScope.launch { store.setNewPerDay(value) }
        }

        fun onReviewPerDayChange(value: Int) {
            viewModelScope.launch { store.setReviewPerDay(value) }
        }

        fun onOverlayIntervalChange(value: Int) {
            viewModelScope.launch { store.setOverlayInterval(value) }
        }

        fun onOpenAiApiKeyChange(value: String) {
            viewModelScope.launch { store.setOpenAiApiKey(value) }
        }

        fun onOpenAiPromptChange(value: String) {
            viewModelScope.launch { store.setOpenAiPrompt(value) }
        }

        fun onOpenAiReversePromptChange(value: String) {
            viewModelScope.launch { store.setOpenAiReversePrompt(value) }
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
                        val inputStream = context.contentResolver.openInputStream(uri)
                        if (inputStream == null) {
                            Log.w(
                                "SettingsViewModel",
                                "openInputStream returned null for uri=$uri with resolver=${context.contentResolver}",
                            )
                            VocabularyImportResult.Failure(VocabularyImportFailureReason.FILE_ERROR)
                        } else {
                            Log.d("SettingsViewModel", "openInputStream succeeded for uri=$uri")
                            inputStream.use { input ->
                                tempFile.outputStream().use { output -> input.copyTo(output) }
                            }
                            val result =
                                if (parser is VocabularyExportParser) {
                                    val items = parser.parseExport(tempFile)
                                    importExportItems(items)
                                    VocabularyImportResult.Success(items.size)
                                } else {
                                    val items = parser.parse(tempFile)
                                    importItems(items)
                                    VocabularyImportResult.Success(items.size)
                                }
                            result
                        }
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
