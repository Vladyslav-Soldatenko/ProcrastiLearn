package com.procrastilearn.app.ui.screens.settings.components

import android.content.Context
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
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
class ExportSettingsItemTest {
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
    fun `renders title and description`() {
        setContent()

        composeTestRule
            .onNodeWithText(string(R.string.settings_export_row))
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText(string(R.string.settings_export_row_desc), substring = true)
            .assertIsDisplayed()

        verify { onClick wasNot called }
    }

    @Test
    fun `invokes callback when row clicked`() {
        setContent()

        composeTestRule
            .onNodeWithText(string(R.string.settings_export_row))
            .assertHasClickAction()
            .performClick()

        verify(exactly = 1) { onClick.invoke() }
    }

    @Test
    fun `supporting text click triggers callback`() {
        setContent()

        composeTestRule
            .onNode(
                hasText(string(R.string.settings_export_row_desc), substring = true),
                useUnmergedTree = true,
            ).performClick()

        verify(exactly = 1) { onClick.invoke() }
    }

    private fun setContent() {
        composeTestRule.setContent {
            MyApplicationTheme {
                ExportSettingsItem(onClick = onClick)
            }
        }
    }

    private fun string(resId: Int): String = context.getString(resId)
}
