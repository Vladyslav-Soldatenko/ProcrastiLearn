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
class NewPerDaySettingsItemTest {
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
    fun `displays singular count correctly`() {
        setContent(value = 1)

        composeTestRule
            .onNodeWithText(string(R.string.settings_new_cards_per_day_title))
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText(plural(R.plurals.cards_count, 1), substring = true)
            .assertIsDisplayed()

        verify { onClick wasNot called }
    }

    @Test
    fun `displays plural count correctly`() {
        val value = 5
        setContent(value)

        composeTestRule
            .onNodeWithText(plural(R.plurals.cards_count, value), substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun `row click triggers callback`() {
        setContent(value = 3)

        composeTestRule
            .onNodeWithText(string(R.string.settings_new_cards_per_day_title))
            .assertHasClickAction()
            .performClick()

        verify(exactly = 1) { onClick.invoke() }
    }

    @Test
    fun `supporting text click delegates to callback`() {
        val value = 2
        setContent(value)

        composeTestRule
            .onNode(
                hasText(plural(R.plurals.cards_count, value), substring = true),
                useUnmergedTree = true,
            ).performClick()

        verify(exactly = 1) { onClick.invoke() }
    }

    private fun setContent(value: Int) {
        composeTestRule.setContent {
            MyApplicationTheme {
                NewPerDaySettingsItem(
                    value = value,
                    onClick = onClick,
                )
            }
        }
    }

    private fun string(resId: Int): String = context.getString(resId)

    private fun plural(
        resId: Int,
        quantity: Int,
    ): String = context.resources.getQuantityString(resId, quantity, quantity)
}
