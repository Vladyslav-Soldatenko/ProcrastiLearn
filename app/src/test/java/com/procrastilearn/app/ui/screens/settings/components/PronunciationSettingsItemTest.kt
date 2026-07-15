package com.procrastilearn.app.ui.screens.settings.components

import android.content.Context
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.procrastilearn.app.R
import com.procrastilearn.app.testing.ComponentActivityRegistrationRule
import com.procrastilearn.app.ui.theme.MyApplicationTheme
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
class PronunciationSettingsItemTest {
    private val composeTestRule = createComposeRule()

    @get:Rule
    val rules: TestRule =
        RuleChain
            .outerRule(ComponentActivityRegistrationRule())
            .around(composeTestRule)

    private lateinit var onToggle: (Boolean) -> Unit
    private lateinit var context: Context

    @Before
    fun setup() {
        onToggle = mockk(relaxed = true)
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `shows title and description`() {
        setContent(isEnabled = false)

        composeTestRule
            .onNodeWithText(string(R.string.settings_pronunciation_title))
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText(string(R.string.settings_pronunciation_desc), substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun `checkbox reflects disabled state`() {
        setContent(isEnabled = false)

        composeTestRule
            .onNodeWithTag("pronunciation_settings_checkbox", useUnmergedTree = true)
            .assertIsOff()
    }

    @Test
    fun `checkbox reflects enabled state`() {
        setContent(isEnabled = true)

        composeTestRule
            .onNodeWithTag("pronunciation_settings_checkbox", useUnmergedTree = true)
            .assertIsOn()
    }

    @Test
    fun `row click toggles from disabled to enabled`() {
        setContent(isEnabled = false)

        composeTestRule
            .onNodeWithText(string(R.string.settings_pronunciation_title))
            .assertHasClickAction()
            .performClick()

        verify(exactly = 1) { onToggle(true) }
    }

    @Test
    fun `row click toggles from enabled to disabled`() {
        setContent(isEnabled = true)

        composeTestRule
            .onNodeWithText(string(R.string.settings_pronunciation_title))
            .performClick()

        verify(exactly = 1) { onToggle(false) }
    }

    private fun setContent(isEnabled: Boolean) {
        composeTestRule.setContent {
            MyApplicationTheme {
                PronunciationSettingsItem(
                    isEnabled = isEnabled,
                    onToggle = onToggle,
                )
            }
        }
    }

    private fun string(resId: Int): String = context.getString(resId)
}
