package com.procrastilearn.app.e2e.ai

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.procrastilearn.app.R
import com.procrastilearn.app.domain.model.AiTranslationDirection
import com.procrastilearn.app.e2e.string
import com.procrastilearn.app.e2e.waitUntilNodeExists
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiTranslationDirectionE2eTest : AiTranslationE2eTest() {
    @Test
    fun togglingDirectionStillProducesASuccessfulAiPreview() {
        seedAiTranslationPrefs(
            targetContext,
            requireOpenAiApiKey(),
            direction = AiTranslationDirection.NATIVE_TO_TARGET,
        )
        composeTestRule.navigateToAddWord(targetContext)

        composeTestRule.typeAddWord("morning")
        composeTestRule
            .onNodeWithText(targetContext.string(R.string.add_word_button_preview))
            .performClick()
        composeTestRule.waitUntilNodeExists(
            hasText(targetContext.string(R.string.add_word_preview_title)),
            AI_CALL_TIMEOUT_MS,
        )
        composeTestRule
            .onNodeWithText(targetContext.string(R.string.add_word_preview_cancel))
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithContentDescription(targetContext.string(R.string.add_word_toggle_direction))
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.typeAddWord("утро")
        composeTestRule
            .onNodeWithText(targetContext.string(R.string.add_word_button_preview))
            .performClick()
        composeTestRule.waitUntilNodeExists(
            hasText(targetContext.string(R.string.add_word_preview_title)),
            AI_CALL_TIMEOUT_MS,
        )
    }
}
