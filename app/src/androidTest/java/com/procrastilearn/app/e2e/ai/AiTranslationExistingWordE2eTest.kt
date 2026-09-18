package com.procrastilearn.app.e2e.ai

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.procrastilearn.app.R
import com.procrastilearn.app.e2e.E2E_TIMEOUT_MS
import com.procrastilearn.app.e2e.string
import com.procrastilearn.app.e2e.waitUntilNodeExists
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiTranslationExistingWordE2eTest : AiTranslationE2eTest() {
    @Test
    fun proceedingOnExistingWordConflictOverridesWithAFreshAiTranslation() {
        val word = "harbor"
        seedExistingWord(targetContext, word, "old-translation")
        seedAiTranslationPrefs(targetContext, requireOpenAiApiKey())
        composeTestRule.navigateToAddWord(targetContext)

        composeTestRule.typeAddWord(word)
        composeTestRule.onNodeWithText(targetContext.string(R.string.action_add)).performClick()

        composeTestRule.waitUntilNodeExists(
            hasText(targetContext.string(R.string.add_word_existing_title)),
            E2E_TIMEOUT_MS,
        )
        composeTestRule.onNodeWithText(targetContext.string(R.string.add_word_existing_proceed)).performClick()

        composeTestRule.waitUntilNodeExists(
            hasText(targetContext.string(R.string.add_word_success_updated)),
            AI_CALL_TIMEOUT_MS,
        )
    }
}
