package com.procrastilearn.app.e2e.ai

import android.app.UiAutomation
import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
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
import com.procrastilearn.app.e2e.nodeVisibleWithin
import com.procrastilearn.app.e2e.waitUntilNodeExists
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiTranslationOfflineQueueE2eTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var targetContext: Context
    private lateinit var uiAutomation: UiAutomation

    @Before
    fun beforeEach() {
        targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        resetAiVocabulary(targetContext)
        clearAiTranslationPrefs(targetContext)
        composeTestRule.dismissOnboardingIfPresent(targetContext)
    }

    @After
    fun afterEach() {
        setNetworkEnabled(true)
        resetAiVocabulary(targetContext)
        clearAiTranslationPrefs(targetContext)
    }

    @Test
    fun addingAWordWhileOfflineQueuesItInsteadOfCallingAi() {
        seedAiTranslationPrefs(targetContext, "sk-not-used-while-offline")
        navigateToAddWord()

        setNetworkEnabled(false)
        composeTestRule.waitUntilNodeExists(hasText(string(R.string.add_word_button_add_later)), DEFAULT_TIMEOUT_MS)

        composeTestRule.onNode(hasSetTextAction()).performTextInput("glacier")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.add_word_button_add_later)).performClick()

        composeTestRule.waitUntilNodeExists(hasText(string(R.string.add_word_success_pending)), DEFAULT_TIMEOUT_MS)
        require(!composeTestRule.nodeVisibleWithin(hasTestTag(ERROR_CARD_TAG), SHORT_TIMEOUT_MS)) {
            "Offline add-later should never surface an AI error card"
        }
    }

    private fun string(resId: Int) = targetContext.getString(resId)

    private fun navigateToAddWord() {
        val label = string(R.string.nav_add_word)
        composeTestRule.waitUntilNodeExists(hasText(label), DEFAULT_TIMEOUT_MS)
        composeTestRule.onNodeWithContentDescription(label, useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()
    }

    private fun setNetworkEnabled(enabled: Boolean) {
        val toggle = if (enabled) "enable" else "disable"
        uiAutomation.shell("svc wifi $toggle")
        uiAutomation.shell("svc data $toggle")
    }

    private fun UiAutomation.shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(executeShellCommand(command)).bufferedReader().use { it.readText() }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 15_000L
        const val SHORT_TIMEOUT_MS = 3_000L
        const val ERROR_CARD_TAG = "add_word_error_card"
    }
}
