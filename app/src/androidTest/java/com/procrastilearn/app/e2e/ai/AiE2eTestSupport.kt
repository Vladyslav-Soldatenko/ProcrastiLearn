package com.procrastilearn.app.e2e.ai

import android.content.Context
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.procrastilearn.app.data.local.entity.VocabularyEntity
import com.procrastilearn.app.di.DatabaseEntryPoint
import com.procrastilearn.app.di.PreferencesEntryPoint
import com.procrastilearn.app.domain.model.AiTranslationDirection
import com.procrastilearn.app.domain.model.Language
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

private const val OPENAI_API_KEY_ARG = "OPENAI_API_KEY"

fun requireOpenAiApiKey(): String {
    val key = InstrumentationRegistry.getArguments().getString(OPENAI_API_KEY_ARG)
    check(!key.isNullOrBlank()) {
        "OPENAI_API_KEY not provided. Add it to local.properties for local runs, or set the " +
            "OPENAI_API_KEY repository secret in GitHub (Settings > Secrets and variables > Actions) for CI."
    }
    return key
}

fun seedAiTranslationPrefs(
    context: Context,
    apiKey: String,
    useAi: Boolean = true,
    direction: AiTranslationDirection = AiTranslationDirection.TARGET_TO_NATIVE,
    native: Language = Language.ENGLISH,
    target: Language = Language.RUSSIAN,
) {
    runBlocking {
        withContext(Dispatchers.IO) {
            val prefs = translationPreferences(context)
            prefs.openAiStore.setOpenAiApiKey(apiKey)
            prefs.openAiStore.setUseAiForTranslation(useAi)
            prefs.openAiStore.setAiTranslationDirection(direction)
            prefs.languagePreferencesStore.setLanguagePair(native, target)
        }
    }
}

fun clearAiTranslationPrefs(context: Context) {
    runBlocking {
        withContext(Dispatchers.IO) {
            val prefs = translationPreferences(context)
            prefs.openAiStore.setUseAiForTranslation(false)
            prefs.openAiStore.setOpenAiApiKey("")
        }
    }
}

fun seedExistingWord(
    context: Context,
    word: String,
    translation: String,
) {
    runBlocking {
        withContext(Dispatchers.IO) {
            val dao = databaseEntryPoint(context).appDatabase().vocabularyDao()
            dao.insertVocabulary(
                VocabularyEntity(
                    word = word,
                    translation = translation,
                    correctCount = 0,
                    incorrectCount = 0,
                    fsrsCardJson = "",
                    fsrsDueAt = 0L,
                    position = dao.getMaxPosition() + 1,
                ),
            )
        }
    }
}

fun resetAiVocabulary(context: Context) {
    runBlocking {
        withContext(Dispatchers.IO) {
            val db = databaseEntryPoint(context).appDatabase()
            db.vocabularyDao().deleteAllVocabulary()
            db.undoSnapshotDao().deleteAll()
        }
    }
}

// The "use AI" Checkbox has no testTag/contentDescription and, whenever the bidirectional
// checkbox is also visible (AI mode not active yet), isToggleable() alone isn't unique.
fun ComposeTestRule.clickUseAiToggle(useAiToggleLabel: String) {
    onNode(
        isToggleable().and(hasAnySibling(hasText(useAiToggleLabel))),
        useUnmergedTree = true,
    ).performClick()
}

private fun translationPreferences(context: Context) =
    EntryPointAccessors
        .fromApplication(
            context.applicationContext,
            PreferencesEntryPoint::class.java,
        ).translationPreferences()

private fun databaseEntryPoint(context: Context): DatabaseEntryPoint =
    EntryPointAccessors.fromApplication(
        context.applicationContext,
        DatabaseEntryPoint::class.java,
    )
