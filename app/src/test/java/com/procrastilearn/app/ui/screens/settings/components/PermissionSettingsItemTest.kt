package com.procrastilearn.app.ui.screens.settings.components

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
class PermissionSettingsItemTest {
    private val composeTestRule = createComposeRule()

    @get:Rule
    val rules: TestRule =
        RuleChain
            .outerRule(ComponentActivityRegistrationRule())
            .around(composeTestRule)

    private lateinit var onClick: () -> Unit

    @Before
    fun setup() {
        onClick = mockk(relaxed = true)
    }

    @Test
    fun `renders headline and supporting text`() {
        setContent(
            headline = "Headline",
            supporting = "Supporting info",
            isChecked = true,
        )

        composeTestRule
            .onNodeWithText("Headline")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Supporting info")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("permission_settings_checkbox", useUnmergedTree = true)
            .assertIsOn()

        verify { onClick wasNot called }
    }

    @Test
    fun `reflects unchecked state`() {
        setContent(
            headline = "Headline",
            supporting = "Supporting info",
            isChecked = false,
        )

        composeTestRule
            .onNodeWithTag("permission_settings_checkbox", useUnmergedTree = true)
            .assertIsOff()
    }

    @Test
    fun `clicking row triggers callback`() {
        setContent("Headline", "Supporting", isChecked = true)

        composeTestRule
            .onNodeWithText("Headline")
            .assertHasClickAction()
            .performClick()

        verify(exactly = 1) { onClick.invoke() }
    }

    @Test
    fun `clicking supporting text triggers callback`() {
        setContent("Headline", "Supporting", isChecked = true)

        composeTestRule
            .onNodeWithText("Supporting", useUnmergedTree = true)
            .performClick()

        verify(exactly = 1) { onClick.invoke() }
    }

    private fun setContent(
        headline: String,
        supporting: String,
        isChecked: Boolean,
    ) {
        composeTestRule.setContent {
            MyApplicationTheme {
                PermissionSettingsItem(
                    headline = headline,
                    supportingText = supporting,
                    isChecked = isChecked,
                    onClick = onClick,
                )
            }
        }
    }
}
