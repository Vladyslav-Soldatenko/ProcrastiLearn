package com.procrastilearn.app.ui.screens.settings.components

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
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
class NumberInputDialogTest {
    private val composeTestRule = createComposeRule()

    @get:Rule
    val rules: TestRule =
        RuleChain
            .outerRule(ComponentActivityRegistrationRule())
            .around(composeTestRule)

    private lateinit var onValueConfirm: (Int) -> Unit
    private lateinit var onDismiss: () -> Unit
    private lateinit var context: Context

    @Before
    fun setup() {
        onValueConfirm = mockk(relaxed = true)
        onDismiss = mockk(relaxed = true)
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `shows title and initial value`() {
        val initialValue = 7

        setContent(
            title = "Some title",
            currentValue = initialValue,
        )

        composeTestRule
            .onNodeWithText("Some title")
            .assertIsDisplayed()

        composeTestRule
            .onNode(hasSetTextAction())
            .assertTextEquals(initialValue.toString())

        verify { onValueConfirm wasNot called }
        verify { onDismiss wasNot called }
    }

    @Test
    fun `filters non digit characters`() {
        setContent(
            title = "Digits only",
            currentValue = 10,
        )

        val field = composeTestRule.onNode(hasSetTextAction())

        field.performTextClearance()
        field.performTextInput("12")
        field.assertTextEquals("12")

        field.performTextInput("x")
        field.assertTextEquals("12")

        field.performTextInput("3")
        field.assertTextEquals("123")
    }

    @Test
    fun `confirm with valid value invokes callback`() {
        setContent(
            title = "Confirm",
            currentValue = 2,
            minValue = 1,
        )

        val field = composeTestRule.onNode(hasSetTextAction())
        field.performTextClearance()
        field.performTextInput("15")

        composeTestRule
            .onNodeWithText(string(R.string.action_ok))
            .performClick()

        verify(exactly = 1) { onValueConfirm.invoke(15) }
        verify { onDismiss wasNot called }
    }

    @Test
    fun `confirm ignores values below minimum`() {
        setContent(
            title = "Min value",
            currentValue = 5,
            minValue = 5,
        )

        val field = composeTestRule.onNode(hasSetTextAction())
        field.performTextClearance()
        field.performTextInput("4")

        composeTestRule
            .onNodeWithText(string(R.string.action_ok))
            .performClick()

        verify { onValueConfirm wasNot called }
    }

    @Test
    fun `confirm ignores empty input`() {
        setContent(
            title = "Empty",
            currentValue = 3,
        )

        val field = composeTestRule.onNode(hasSetTextAction())
        field.performTextClearance()

        composeTestRule
            .onNodeWithText(string(R.string.action_ok))
            .performClick()

        verify { onValueConfirm wasNot called }
    }

    @Test
    fun `dismiss button invokes onDismiss`() {
        setContent(
            title = "Dismiss",
            currentValue = 4,
        )

        composeTestRule
            .onNodeWithText(string(R.string.action_cancel))
            .performClick()

        verify(exactly = 1) { onDismiss.invoke() }
        verify { onValueConfirm wasNot called }
    }

    private fun setContent(
        title: String,
        currentValue: Int,
        minValue: Int = 1,
    ) {
        composeTestRule.setContent {
            MyApplicationTheme {
                NumberInputDialog(
                    title = title,
                    currentValue = currentValue,
                    onValueConfirm = onValueConfirm,
                    onDismiss = onDismiss,
                    minValue = minValue,
                )
            }
        }
    }

    private fun string(resId: Int): String = context.getString(resId)
}
