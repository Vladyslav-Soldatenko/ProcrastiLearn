package com.procrastilearn.app.ui.screens.settings.components

import android.content.Context
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.procrastilearn.app.R
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
class OpenAiApiKeySettingsItemTest {
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
    fun `shows not set state when api key empty`() {
        setContent(apiKey = null)

        composeTestRule
            .onNodeWithText(string(R.string.settings_openai_api_key_title))
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText(string(R.string.settings_openai_api_key_not_set))
            .assertIsDisplayed()

        verify { onClick wasNot called }
    }

    @Test
    fun `shows set state when api key present`() {
        setContent(apiKey = "sk-123")

        composeTestRule
            .onNodeWithText(string(R.string.settings_openai_api_key_set))
            .assertIsDisplayed()
    }

    @Test
    fun `row click triggers callback`() {
        setContent(apiKey = "sk-123")

        composeTestRule
            .onNodeWithText(string(R.string.settings_openai_api_key_title))
            .assertHasClickAction()
            .performClick()

        verify(exactly = 1) { onClick.invoke() }
    }

    @Test
    fun `supporting text click also triggers callback`() {
        setContent(apiKey = "")

        composeTestRule
            .onNodeWithText(string(R.string.settings_openai_api_key_not_set), useUnmergedTree = true)
            .performClick()

        verify(exactly = 1) { onClick.invoke() }
    }

    private fun setContent(apiKey: String?) {
        composeTestRule.setContent {
            MyApplicationTheme {
                OpenAiApiKeySettingsItem(
                    apiKey = apiKey,
                    onClick = onClick,
                )
            }
        }
    }

    private fun string(resId: Int): String = context.getString(resId)
}
