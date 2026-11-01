package com.procrastilearn.app.appfunctions

import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunctionAppUnknownException
import androidx.appfunctions.AppFunctionDeclaration
import androidx.appfunctions.AppFunctionException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import com.procrastilearn.app.data.local.prefs.LanguagePreferencesStore
import com.procrastilearn.app.data.local.prefs.OpenAiPreferencesStore
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.domain.repository.VocabularyCatalogRepository
import com.procrastilearn.app.domain.usecase.GenerateAiTranslationUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val DUPLICATE =
    "Word already exists in your learning list; its translation and learning progress were preserved."
private const val CONFIGURATION =
    "Provide a translation or enable AI translation with an API key and language pair in app settings."
private const val TRANSLATION_FAILED = "AI translation failed. Provide a manual translation or retry later."

@RequiresApi(36)
@AndroidEntryPoint
@AppFunctionServiceEntryPoint(
    serviceName = "VocabularyAppFunctionService",
    appFunctionXmlFileName = "vocabulary_app_function_service",
)
@Suppress("LateinitUsage")
abstract class VocabularyFunctions : AppFunctionService() {
    @Inject lateinit var repository: VocabularyCatalogRepository

    @Inject lateinit var openAiStore: OpenAiPreferencesStore

    @Inject lateinit var languageStore: LanguagePreferencesStore

    @Inject lateinit var generator: GenerateAiTranslationUseCase

    @Inject lateinit var ioDispatcher: CoroutineDispatcher

    /**
     * Adds a new word or phrase to the vocabulary learning list. Existing words keep their
     * translation and learning progress. A supplied translation is used directly; otherwise
     * the app uses its configured AI translation.
     *
     * @param word The word or phrase to add. Whitespace is trimmed; blank input is rejected.
     * @param translation Manual translation. Whitespace is trimmed. If omitted or blank,
     * AI translation must be enabled with an API key and language pair configured in the app.
     * @return A message confirming that the word was added or was already present unchanged.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun addWord(
        word: String,
        translation: String? = null,
    ): String =
        runCatching { addWordInternal(word, translation) }.getOrElse { error ->
            when (error) {
                is CancellationException, is AppFunctionException, is Error -> throw error
                else -> throw AppFunctionAppUnknownException("Could not add the word. Please try again.")
            }
        }

    private suspend fun addWordInternal(
        word: String,
        translation: String?,
    ): String =
        withContext(ioDispatcher) {
            if (word.isBlank()) throw AppFunctionInvalidArgumentException("Word must not be blank.")
            val trimmedWord = word.trim()
            if (repository.getVocabularyItemByWord(trimmedWord) != null) {
                return@withContext DUPLICATE
            }
            val manual = translation?.trim().orEmpty()
            val finalTranslation = manual.ifBlank { generateTranslation(trimmedWord) }
            currentCoroutineContext().ensureActive()
            val inserted =
                repository.addVocabularyItemIfAbsent(
                    VocabularyItem(word = trimmedWord, translation = finalTranslation, isNew = true),
                )
            if (inserted) "Added \"$trimmedWord\" to your learning list." else DUPLICATE
        }

    private suspend fun generateTranslation(word: String): String {
        val enabled = openAiStore.readUseAiForTranslation().first()
        val key = openAiStore.readOpenAiApiKey().first()
        if (!enabled || key.isNullOrBlank() || languageStore.readLanguagePair().first() == null) {
            throw AppFunctionInvalidArgumentException(CONFIGURATION)
        }
        val direction = openAiStore.readAiTranslationDirection().first()
        val result = generator(word, direction).trim()
        if (result.isBlank()) throw AppFunctionAppUnknownException(TRANSLATION_FAILED)
        return result
    }
}
