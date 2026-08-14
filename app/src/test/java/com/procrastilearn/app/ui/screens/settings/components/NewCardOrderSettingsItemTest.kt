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
import com.procrastilearn.app.domain.model.NewCardOrder
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
class NewCardOrderSettingsItemTest {
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
    fun `displays sequential order label`() {
        assertOrderDisplayed(
            newCardOrder = NewCardOrder.SEQUENTIAL,
            expectedTextRes = R.string.settings_new_card_order_sequential,
        )
    }

    @Test
    fun `displays random order label`() {
        assertOrderDisplayed(
            newCardOrder = NewCardOrder.RANDOM,
            expectedTextRes = R.string.settings_new_card_order_random,
        )
    }

    @Test
    fun `invokes callback when row clicked`() {
        setContent(NewCardOrder.SEQUENTIAL)

        composeTestRule
            .onNodeWithText(string(R.string.settings_new_card_order_title))
            .assertHasClickAction()
            .performClick()

        verify(exactly = 1) { onClick.invoke() }
    }

    @Test
    fun `supporting text delegates click to callback`() {
        setContent(NewCardOrder.RANDOM)

        composeTestRule
            .onNode(
                hasText(string(R.string.settings_new_card_order_random)),
                useUnmergedTree = true,
            ).performClick()

        verify(exactly = 1) { onClick.invoke() }
    }

    private fun setContent(newCardOrder: NewCardOrder) {
        composeTestRule.setContent {
            MyApplicationTheme {
                NewCardOrderSettingsItem(
                    newCardOrder = newCardOrder,
                    onClick = onClick,
                )
            }
        }
    }

    private fun assertOrderDisplayed(
        newCardOrder: NewCardOrder,
        expectedTextRes: Int,
    ) {
        setContent(newCardOrder)

        composeTestRule
            .onNodeWithText(string(R.string.settings_new_card_order_title))
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText(string(expectedTextRes))
            .assertIsDisplayed()

        verify { onClick wasNot called }
    }

    private fun string(resId: Int): String = context.getString(resId)
}
