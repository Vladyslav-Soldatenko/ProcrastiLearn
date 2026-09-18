package com.procrastilearn.app.e2e.ai

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.procrastilearn.app.R
import com.procrastilearn.app.e2e.E2E_TIMEOUT_MS
import com.procrastilearn.app.e2e.navigateTo
import com.procrastilearn.app.e2e.string
import com.procrastilearn.app.e2e.waitUntilNodeExists
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiTranslationHappyPathE2eTest : AiTranslationE2eTest() {
    @Test
    fun settingsApiKeyEntry_savesKeyAndEnablesAiPreview() {
        composeTestRule.navigateTo(targetContext, R.string.nav_settings)

        composeTestRule
            .onNodeWithText(targetContext.string(R.string.settings_openai_api_key_title))
            .performScrollTo()
            .performClick()
        composeTestRule.waitUntilNodeExists(
            hasText(targetContext.string(R.string.settings_openai_api_key_dialog_title)),
            E2E_TIMEOUT_MS,
        )
        composeTestRule.onNode(hasSetTextAction()).performTextInput(requireOpenAiApiKey())
        composeTestRule.onNodeWithText(targetContext.string(R.string.action_ok)).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(targetContext.string(R.string.settings_openai_api_key_set)).assertIsDisplayed()

        composeTestRule.navigateToAddWord(targetContext)
        composeTestRule.waitUntilNodeExists(
            hasText(targetContext.string(R.string.add_word_use_ai_toggle)),
            E2E_TIMEOUT_MS,
        )
        composeTestRule.clickUseAiToggle(targetContext.string(R.string.add_word_use_ai_toggle))

        composeTestRule.waitUntilNodeExists(
            hasText(targetContext.string(R.string.add_word_button_preview)),
            E2E_TIMEOUT_MS,
        )
    }

    @Test
    fun addWord_aiPreviewAndConfirmAdd_producesRealTranslation() {
        seedAiTranslationPrefs(targetContext, requireOpenAiApiKey())
        composeTestRule.navigateToAddWord(targetContext)

        val word = "lighthouse"
        composeTestRule.typeAddWord(word)
        composeTestRule.onNodeWithText(targetContext.string(R.string.add_word_button_preview)).performClick()

        composeTestRule.waitUntilNodeExists(
            hasText(targetContext.string(R.string.add_word_preview_title)),
            AI_CALL_TIMEOUT_MS,
        )
        composeTestRule.onNodeWithTag(PREVIEW_CONFIRM_BUTTON_TAG).performClick()

        composeTestRule.waitUntilNodeExists(
            hasText(targetContext.string(R.string.add_word_success_added)),
            AI_CALL_TIMEOUT_MS,
        )
        assertTrue(vocabularyExists(targetContext, word))
    }

    @Test
    fun addWord_previewRegenerate_getsFreshAiTranslation() {
        val word = "runway"
        seedExistingWord(targetContext, word, "stale-stored-translation")
        seedAiTranslationPrefs(targetContext, requireOpenAiApiKey())
        composeTestRule.navigateToAddWord(targetContext)

        composeTestRule.typeAddWord(word)
        composeTestRule.onNodeWithText(targetContext.string(R.string.add_word_button_preview)).performClick()

        composeTestRule.waitUntilNodeExists(
            hasText(targetContext.string(R.string.add_word_preview_stored_title)),
            E2E_TIMEOUT_MS,
        )
        composeTestRule.onNodeWithTag(PREVIEW_CONFIRM_BUTTON_TAG).performClick()

        composeTestRule.waitUntilNodeExists(
            hasText(targetContext.string(R.string.add_word_preview_title)),
            AI_CALL_TIMEOUT_MS,
        )
        composeTestRule.onNodeWithTag(PREVIEW_CONFIRM_BUTTON_TAG).performClick()

        composeTestRule.waitUntilNodeExists(
            hasText(targetContext.string(R.string.add_word_success_updated)),
            AI_CALL_TIMEOUT_MS,
        )
    }

    private companion object {
        const val PREVIEW_CONFIRM_BUTTON_TAG = "add_word_preview_confirm_button"
    }
}
