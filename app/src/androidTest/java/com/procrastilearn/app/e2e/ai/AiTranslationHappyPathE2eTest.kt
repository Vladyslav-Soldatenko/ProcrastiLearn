package com.procrastilearn.app.e2e.ai

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.procrastilearn.app.MainActivity
import com.procrastilearn.app.R
import com.procrastilearn.app.e2e.dismissOnboardingIfPresent
import com.procrastilearn.app.e2e.waitUntilNodeExists
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real, end-to-end AI translation coverage: real Hilt DI, real Room database and a real call to
 * the OpenAI API through the running app. Requires a valid OPENAI_API_KEY (see
 * [requireOpenAiApiKey]) - JVM unit tests already cover the mocked/error-branch behavior of
 * [com.procrastilearn.app.domain.usecase.GenerateAiTranslationUseCase].
 */
@RunWith(AndroidJUnit4::class)
class AiTranslationHappyPathE2eTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var targetContext: Context

    @Before
    fun beforeEach() {
        targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        resetAiVocabulary(targetContext)
        clearAiTranslationPrefs(targetContext)
        composeTestRule.dismissOnboardingIfPresent(targetContext)
    }

    @After
    fun afterEach() {
        resetAiVocabulary(targetContext)
        clearAiTranslationPrefs(targetContext)
    }

    @Test
    fun settingsApiKeyEntry_savesKeyAndEnablesAiPreview() {
        navigateToSettings()

        composeTestRule.onNodeWithText(string(R.string.settings_openai_api_key_title)).performClick()
        composeTestRule.waitUntilNodeExists(
            hasText(string(R.string.settings_openai_api_key_dialog_title)),
            DEFAULT_TIMEOUT_MS,
        )
        composeTestRule.onNode(hasSetTextAction()).performTextInput(requireOpenAiApiKey())
        composeTestRule.onNodeWithText(string(R.string.action_ok)).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(string(R.string.settings_openai_api_key_set)).assertIsDisplayed()

        navigateToAddWord()
        composeTestRule.waitUntilNodeExists(hasText(string(R.string.add_word_use_ai_toggle)), DEFAULT_TIMEOUT_MS)
        composeTestRule.clickUseAiToggle(string(R.string.add_word_use_ai_toggle))

        composeTestRule.waitUntilNodeExists(hasText(string(R.string.add_word_button_preview)), DEFAULT_TIMEOUT_MS)
    }

    @Test
    fun addWord_aiPreviewAndConfirmAdd_producesRealTranslation() {
        seedAiTranslationPrefs(targetContext, requireOpenAiApiKey())
        navigateToAddWord()

        val word = "lighthouse"
        typeWord(word)
        composeTestRule.onNodeWithText(string(R.string.add_word_button_preview)).performClick()

        composeTestRule.waitUntilNodeExists(hasText(string(R.string.add_word_preview_title)), AI_CALL_TIMEOUT_MS)
        composeTestRule.onNodeWithText(word).assertIsDisplayed()
        composeTestRule.clickDialogConfirmButton(string(R.string.action_add), string(R.string.add_word_preview_cancel))

        composeTestRule.waitUntilNodeExists(hasText(string(R.string.add_word_success_added)), AI_CALL_TIMEOUT_MS)

        navigateToWordList()
        composeTestRule.waitUntilNodeExists(hasText(word, substring = true), DEFAULT_TIMEOUT_MS)
    }

    @Test
    fun addWord_previewRegenerate_getsFreshAiTranslation() {
        val word = "runway"
        seedExistingWord(targetContext, word, "stale-stored-translation")
        seedAiTranslationPrefs(targetContext, requireOpenAiApiKey())
        navigateToAddWord()

        typeWord(word)
        composeTestRule.onNodeWithText(string(R.string.add_word_button_preview)).performClick()

        // The word already has a stored translation, so Preview shows it immediately without an
        // AI call, offering "Regenerate" instead of "Add" to force a fresh one.
        composeTestRule.waitUntilNodeExists(hasText(string(R.string.add_word_preview_stored_title)), DEFAULT_TIMEOUT_MS)
        composeTestRule.onNodeWithText(string(R.string.add_word_preview_regenerate)).performClick()

        composeTestRule.waitUntilNodeExists(hasText(string(R.string.add_word_preview_title)), AI_CALL_TIMEOUT_MS)
        composeTestRule.clickDialogConfirmButton(string(R.string.action_add), string(R.string.add_word_preview_cancel))

        composeTestRule.waitUntilNodeExists(hasText(string(R.string.add_word_success_updated)), AI_CALL_TIMEOUT_MS)
    }

    private fun string(resId: Int) = targetContext.getString(resId)

    private fun typeWord(word: String) {
        composeTestRule.onNode(hasSetTextAction()).performTextInput(word)
        composeTestRule.waitForIdle()
    }

    private fun navigateTo(labelResId: Int) {
        val label = string(labelResId)
        composeTestRule.waitUntilNodeExists(hasText(label), DEFAULT_TIMEOUT_MS)
        composeTestRule.onNodeWithContentDescription(label, useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()
    }

    private fun navigateToSettings() = navigateTo(R.string.nav_settings)

    private fun navigateToAddWord() = navigateTo(R.string.nav_add_word)

    private fun navigateToWordList() {
        navigateToAddWord()
        val viewListLabel = string(R.string.action_view_list)
        composeTestRule.waitUntilNodeExists(hasContentDescription(viewListLabel), DEFAULT_TIMEOUT_MS)
        composeTestRule.onNodeWithContentDescription(viewListLabel).performClick()
        composeTestRule.waitForIdle()
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 15_000L

        // Real network call to OpenAI - generous timeout to avoid flaking on slow responses.
        const val AI_CALL_TIMEOUT_MS = 30_000L
    }
}
