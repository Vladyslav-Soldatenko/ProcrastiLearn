package com.procrastilearn.app.e2e.ai

import android.app.UiAutomation
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.procrastilearn.app.R
import com.procrastilearn.app.e2e.E2E_TIMEOUT_MS
import com.procrastilearn.app.e2e.nodeVisibleWithin
import com.procrastilearn.app.e2e.shell
import com.procrastilearn.app.e2e.string
import com.procrastilearn.app.e2e.waitUntilNodeExists
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiTranslationOfflineQueueE2eTest : AiTranslationE2eTest() {
    private val uiAutomation: UiAutomation
        get() = InstrumentationRegistry.getInstrumentation().uiAutomation

    override fun restoreAiTranslationE2eEnvironment() {
        setNetworkEnabled(true)
    }

    @Test
    fun addingAWordWhileOfflineQueuesItInsteadOfCallingAi() {
        seedAiTranslationPrefs(targetContext, "sk-not-used-while-offline")
        composeTestRule.navigateToAddWord(targetContext)

        setNetworkEnabled(false)
        composeTestRule.waitUntilNodeExists(
            hasText(targetContext.string(R.string.add_word_button_add_later)),
            E2E_TIMEOUT_MS,
        )

        composeTestRule.typeAddWord("glacier")
        composeTestRule
            .onNodeWithText(targetContext.string(R.string.add_word_button_add_later))
            .performClick()

        composeTestRule.waitUntilNodeExists(
            hasText(targetContext.string(R.string.add_word_success_pending)),
            E2E_TIMEOUT_MS,
        )
        require(
            !composeTestRule.nodeVisibleWithin(hasTestTag(AI_ERROR_CARD_TAG), AI_NO_RESPONSE_TIMEOUT_MS),
        ) {
            "Offline add-later should never surface an AI error card"
        }
    }

    private fun setNetworkEnabled(enabled: Boolean) {
        val toggle = if (enabled) "enable" else "disable"
        uiAutomation.shell("svc wifi $toggle")
        uiAutomation.shell("svc data $toggle")
    }
}
