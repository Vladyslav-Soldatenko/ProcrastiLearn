package com.procrastilearn.app.e2e.ai

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.procrastilearn.app.R
import com.procrastilearn.app.e2e.waitUntilNodeExists
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiTranslationInvalidKeyE2eTest : AiTranslationE2eTest() {
    @Test
    fun previewWithInvalidKeyShowsErrorInsteadOfATranslation() {
        seedAiTranslationPrefs(targetContext, INVALID_API_KEY)
        composeTestRule.navigateToAddWord(targetContext)

        composeTestRule.typeAddWord("harvest")
        composeTestRule
            .onNodeWithText(targetContext.getString(R.string.add_word_button_preview))
            .performClick()

        composeTestRule.waitUntilNodeExists(hasTestTag(AI_ERROR_CARD_TAG), AI_CALL_TIMEOUT_MS)
        composeTestRule.onNodeWithTag(AI_ERROR_CARD_TAG).assertIsDisplayed()
    }

    private companion object {
        const val INVALID_API_KEY = "sk-invalid-test-key-0000000000000000000000000000"
    }
}
