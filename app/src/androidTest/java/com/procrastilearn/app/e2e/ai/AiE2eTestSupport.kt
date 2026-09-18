package com.procrastilearn.app.e2e.ai

import android.content.Context
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.procrastilearn.app.MainActivity
import com.procrastilearn.app.R
import com.procrastilearn.app.data.local.entity.VocabularyEntity
import com.procrastilearn.app.data.local.prefs.TranslationPreferences
import com.procrastilearn.app.domain.model.AiTranslationDirection
import com.procrastilearn.app.domain.model.Language
import com.procrastilearn.app.e2e.dismissOnboardingIfPresent
import com.procrastilearn.app.e2e.insertVocabulary
import com.procrastilearn.app.e2e.navigateTo
import com.procrastilearn.app.e2e.preferencesEntryPoint
import com.procrastilearn.app.e2e.resetE2eDatabase
import com.procrastilearn.app.e2e.vocabularyByWord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Rule

const val AI_CALL_TIMEOUT_MS = 30_000L
const val AI_ERROR_CARD_TAG = "add_word_error_card"
const val AI_NO_RESPONSE_TIMEOUT_MS = 3_000L

private const val OPENAI_API_KEY_ARG = "OPENAI_API_KEY"

abstract class AiTranslationE2eTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    protected lateinit var targetContext: Context

    @Before
    fun setUpAiTranslationE2eTest() {
        targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        targetContext.resetE2eDatabase()
        clearAiTranslationPrefs(targetContext)
        composeTestRule.dismissOnboardingIfPresent(targetContext)
    }

    @After
    fun tearDownAiTranslationE2eTest() {
        restoreAiTranslationE2eEnvironment()
        targetContext.resetE2eDatabase()
        clearAiTranslationPrefs(targetContext)
    }

    protected open fun restoreAiTranslationE2eEnvironment() = Unit
}

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
    context.updateAiTranslationPrefs {
        openAiStore.setOpenAiApiKey(apiKey)
        openAiStore.setUseAiForTranslation(useAi)
        openAiStore.setAiTranslationDirection(direction)
        languagePreferencesStore.setLanguagePair(native, target)
    }
}

fun clearAiTranslationPrefs(context: Context) {
    context.updateAiTranslationPrefs {
        openAiStore.setUseAiForTranslation(false)
        openAiStore.setOpenAiApiKey("")
    }
}

fun seedExistingWord(
    context: Context,
    word: String,
    translation: String,
) {
    context.insertVocabulary(
        VocabularyEntity(
            word = word,
            translation = translation,
            correctCount = 0,
            incorrectCount = 0,
            fsrsCardJson = "",
            fsrsDueAt = 0L,
        ),
    )
}

fun vocabularyExists(context: Context, word: String): Boolean = context.vocabularyByWord(word) != null

fun ComposeTestRule.navigateToAddWord(context: Context) = navigateTo(context, R.string.nav_add_word)

fun ComposeTestRule.typeAddWord(word: String) {
    onNode(hasSetTextAction()).performTextInput(word)
    waitForIdle()
}

fun ComposeTestRule.clickUseAiToggle(useAiToggleLabel: String) {
    onNode(
        isToggleable().and(hasAnySibling(hasText(useAiToggleLabel))),
        useUnmergedTree = true,
    ).performClick()
}

private fun Context.updateAiTranslationPrefs(update: suspend TranslationPreferences.() -> Unit) {
    runBlocking {
        withContext(Dispatchers.IO) {
            preferencesEntryPoint().translationPreferences().update()
        }
    }
}
