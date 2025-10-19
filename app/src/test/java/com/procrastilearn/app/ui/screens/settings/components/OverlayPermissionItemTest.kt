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
class OverlayPermissionItemTest {
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
    fun `shows overlay details when not granted`() {
        setContent(isGranted = false)

        composeTestRule
            .onNodeWithText(string(R.string.settings_overlay_headline))
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText(string(R.string.settings_overlay_support), substring = true)
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("permission_settings_checkbox", useUnmergedTree = true)
            .assertIsOff()

        verify { onClick wasNot called }
    }

    @Test
    fun `checkbox reflects granted state`() {
        setContent(isGranted = true)

        composeTestRule
            .onNodeWithTag("permission_settings_checkbox", useUnmergedTree = true)
            .assertIsOn()
    }

    @Test
    fun `row click triggers callback`() {
        setContent(isGranted = false)

        composeTestRule
            .onNodeWithText(string(R.string.settings_overlay_headline))
            .assertHasClickAction()
            .performClick()

        verify(exactly = 1) { onClick.invoke() }
    }

    @Test
    fun `supporting text click triggers callback`() {
        setContent(isGranted = false)

        composeTestRule
            .onNodeWithText(string(R.string.settings_overlay_support), substring = true, useUnmergedTree = true)
            .performClick()

        verify(exactly = 1) { onClick.invoke() }
    }

    private fun setContent(isGranted: Boolean) {
        composeTestRule.setContent {
            MyApplicationTheme {
                OverlayPermissionItem(
                    isGranted = isGranted,
                    onClick = onClick,
                )
            }
        }
    }

    private fun string(resId: Int): String = context.getString(resId)
}
