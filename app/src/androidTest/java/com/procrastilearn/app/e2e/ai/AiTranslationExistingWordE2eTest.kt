package com.procrastilearn.app.e2e.ai

import android.content.Context
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

@RunWith(AndroidJUnit4::class)
class AiTranslationExistingWordE2eTest {
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
    fun proceedingOnExistingWordConflictOverridesWithAFreshAiTranslation() {
        val word = "harbor"
        seedExistingWord(targetContext, word, "old-translation")
        seedAiTranslationPrefs(targetContext, requireOpenAiApiKey())
        navigateToAddWord()

        composeTestRule.onNode(hasSetTextAction()).performTextInput(word)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.action_add)).performClick()

        composeTestRule.waitUntilNodeExists(hasText(string(R.string.add_word_existing_title)), DEFAULT_TIMEOUT_MS)
        composeTestRule.onNodeWithText(string(R.string.add_word_existing_proceed)).performClick()

        composeTestRule.waitUntilNodeExists(hasText(string(R.string.add_word_success_updated)), AI_CALL_TIMEOUT_MS)
    }

    private fun string(resId: Int) = targetContext.getString(resId)

    private fun navigateToAddWord() {
        val label = string(R.string.nav_add_word)
        composeTestRule.waitUntilNodeExists(hasText(label), DEFAULT_TIMEOUT_MS)
        composeTestRule.onNodeWithContentDescription(label, useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 15_000L
        const val AI_CALL_TIMEOUT_MS = 30_000L
    }
}
