package com.procrastilearn.app.e2e.ai

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
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
class AiTranslationInvalidKeyE2eTest {
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
    fun previewWithInvalidKeyShowsErrorInsteadOfATranslation() {
        seedAiTranslationPrefs(targetContext, INVALID_API_KEY)
        navigateToAddWord()

        composeTestRule.onNode(hasSetTextAction()).performTextInput("harvest")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.add_word_button_preview)).performClick()

        composeTestRule.waitUntilNodeExists(hasTestTag(ERROR_CARD_TAG), AI_CALL_TIMEOUT_MS)
        composeTestRule.onNodeWithTag(ERROR_CARD_TAG).assertIsDisplayed()
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
        const val ERROR_CARD_TAG = "add_word_error_card"
        const val INVALID_API_KEY = "sk-invalid-test-key-0000000000000000000000000000"
    }
}
