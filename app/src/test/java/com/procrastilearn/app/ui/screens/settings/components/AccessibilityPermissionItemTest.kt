package com.procrastilearn.app.ui.screens.settings.components

import android.content.Context
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
class AccessibilityPermissionItemTest {
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
    fun `renders strings without triggering callbacks`() {
        setContent(isEnabled = true)

        composeTestRule
            .onNodeWithText(string(R.string.settings_a11y_headline))
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText(string(R.string.settings_a11y_support), substring = true)
            .assertIsDisplayed()

        verify { onClick wasNot called }
    }

    @Test
    fun `invokes callback when item clicked`() {
        setContent(isEnabled = false)

        composeTestRule
            .onNodeWithText(string(R.string.settings_a11y_headline))
            .assertHasClickAction()
            .performClick()

        verify(exactly = 1) { onClick.invoke() }
    }

    @Test
    fun `checkbox matches enabled state when true`() {
        setContent(isEnabled = true)

        composeTestRule
            .onNodeWithTag("permission_settings_checkbox", useUnmergedTree = true)
            .assertIsOn()

        verify { onClick wasNot called }
    }

    @Test
    fun `checkbox matches enabled state when false`() {
        setContent(isEnabled = false)

        composeTestRule
            .onNodeWithTag("permission_settings_checkbox", useUnmergedTree = true)
            .assertIsOff()

        verify { onClick wasNot called }
    }

    @Test
    fun `supporting text click also triggers callback`() {
        setContent(isEnabled = false)

        composeTestRule
            .onNode(hasText(string(R.string.settings_a11y_support), substring = true))
            .performClick()

        verify(exactly = 1) { onClick.invoke() }
    }

    private fun setContent(isEnabled: Boolean) {
        composeTestRule.setContent {
            MyApplicationTheme {
                AccessibilityPermissionItem(
                    isEnabled = isEnabled,
                    onClick = onClick,
                )
            }
        }
    }

    private fun string(resId: Int): String = context.getString(resId)
}
