package com.procrastilearn.app.ui.screens.settings.components

import android.content.Context
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.procrastilearn.app.R
import com.procrastilearn.app.data.local.prefs.OpenAiPromptDefaults
import com.procrastilearn.app.testing.ComponentActivityRegistrationRule
import com.procrastilearn.app.ui.theme.MyApplicationTheme
import io.mockk.called
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [33],
    manifest = Config.NONE,
    qualifiers = "xlarge",
)
class OpenAiPromptSettingsItemTest {
    private val composeTestRule = createComposeRule()

    @get:Rule
    val rules: TestRule =
        RuleChain
            .outerRule(ComponentActivityRegistrationRule())
            .around(composeTestRule)

    private lateinit var onClick: () -> Unit
    private lateinit var context: Context

    @Before
    fun setup() {
        onClick = mockk(relaxed = true)
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `shows default prompt state with given language codes`() {
        setContent(prompt = OpenAiPromptDefaults.translationPrompt)

        composeTestRule
            .onNodeWithText(context.getString(R.string.settings_openai_prompt_title, "RU", "EN"))
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText(context.getString(R.string.settings_openai_prompt_default, "EN", "RU"))
            .assertIsDisplayed()

        verify { onClick wasNot called }
    }

    @Test
    fun `shows custom prompt state`() {
        setContent(prompt = "Custom prompt")

        composeTestRule
            .onNodeWithText(context.getString(R.string.settings_openai_prompt_custom, "EN", "RU"))
            .assertIsDisplayed()
    }

    @Test
    fun `default prompt supporting text reflects a non-default language pair`() {
        setContent(prompt = OpenAiPromptDefaults.translationPrompt, nativeLanguageCode = "DE", targetLanguageCode = "FR")

        composeTestRule
            .onNodeWithText(context.getString(R.string.settings_openai_prompt_title, "FR", "DE"))
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText(context.getString(R.string.settings_openai_prompt_default, "DE", "FR"))
            .assertIsDisplayed()
    }

    @Test
    fun `title reflects the given language codes`() {
        setContent(prompt = OpenAiPromptDefaults.translationPrompt, nativeLanguageCode = "DE", targetLanguageCode = "FR")

        composeTestRule
            .onNodeWithText(context.getString(R.string.settings_openai_prompt_title, "FR", "DE"))
            .assertIsDisplayed()
    }

    @Test
    fun `row click invokes callback`() {
        setContent(prompt = "Custom prompt")

        composeTestRule
            .onNodeWithText(context.getString(R.string.settings_openai_prompt_title, "RU", "EN"))
            .assertHasClickAction()
            .performClick()

        verify(exactly = 1) { onClick.invoke() }
    }

    @Test
    fun `supporting text click delegates to callback`() {
        setContent(prompt = OpenAiPromptDefaults.translationPrompt)

        composeTestRule
            .onNodeWithText(
                context.getString(R.string.settings_openai_prompt_default, "EN", "RU"),
                useUnmergedTree = true,
            ).performClick()

        verify(exactly = 1) { onClick.invoke() }
    }

    private fun setContent(
        prompt: String,
        nativeLanguageCode: String = "EN",
        targetLanguageCode: String = "RU",
    ) {
        composeTestRule.setContent {
            MyApplicationTheme {
                OpenAiPromptSettingsItem(
                    prompt = prompt,
                    nativeLanguageCode = nativeLanguageCode,
                    targetLanguageCode = targetLanguageCode,
                    onClick = onClick,
                )
            }
        }
    }
}
