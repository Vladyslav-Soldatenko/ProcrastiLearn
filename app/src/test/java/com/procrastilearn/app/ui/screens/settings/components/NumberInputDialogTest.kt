package com.procrastilearn.app.ui.screens.settings.components

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
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
    fun `custom validation uses live other setting and preserves the input`() {
        val interval = mutableIntStateOf(5)
        composeTestRule.setContent {
            MyApplicationTheme {
                NumberInputDialog(
                    title = "Cooldown",
                    currentValue = 6,
                    maxValue = 2000,
                    onValueConfirm = onValueConfirm,
                    onDismiss = onDismiss,
                    validateValue = { if (interval.intValue > 0 && it > interval.intValue) "Too long" else null },
                )
            }
        }
        composeTestRule.onNodeWithText("Too long").assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.action_ok)).assertIsNotEnabled()

        composeTestRule.runOnIdle { interval.intValue = 6 }
        composeTestRule.onNodeWithText(string(R.string.action_ok)).assertIsEnabled()
        composeTestRule.onNode(hasSetTextAction()).assertTextEquals("6")
    }

    @Test
    fun `save failure retains the entered value while retry stays available`() {
        val saving = mutableStateOf(false)
        val error = mutableStateOf<String?>(null)
        composeTestRule.setContent {
            MyApplicationTheme {
                NumberInputDialog(
                    title = "Save",
                    currentValue = 2,
                    onValueConfirm = {
                        saving.value = true
                        onValueConfirm(it)
                    },
                    onDismiss = onDismiss,
                    isSaving = saving.value,
                    saveError = error.value,
                )
            }
        }
        val field = composeTestRule.onNode(hasSetTextAction())
        field.performTextClearance()
        field.performTextInput("8")
        composeTestRule.onNodeWithText(string(R.string.action_ok)).performClick().assertIsNotEnabled()
        composeTestRule.runOnIdle {
            saving.value = false
            error.value = "Could not save"
        }
        field.assertTextContains("8")
        composeTestRule.onNodeWithText("Could not save").assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.action_ok)).assertIsEnabled()
    }

    @Test
    fun `timing input accepts two thousand but rejects overflow and two thousand one`() {
        setContent(title = "Timing", currentValue = 2000, minValue = 0, maxValue = 2000)
        composeTestRule.onNodeWithText(string(R.string.action_ok)).assertIsEnabled()
        val field = composeTestRule.onNode(hasSetTextAction())
        field.performTextClearance()
        field.performTextInput("2001")
        composeTestRule.onNodeWithText(string(R.string.action_ok)).assertIsNotEnabled()
        field.performTextClearance()
        field.performTextInput("999999999999999999999999")
        composeTestRule.onNodeWithText(string(R.string.action_ok)).assertIsNotEnabled()
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
    fun `OK is enabled when value equals minimum boundary`() {
        setContent(
            title = "Boundary",
            currentValue = 0,
            minValue = 1,
        )

        val field = composeTestRule.onNode(hasSetTextAction())
        field.performTextClearance()
        field.performTextInput("1")

        val okButton = composeTestRule.onNodeWithText(string(R.string.action_ok))
        okButton.assertIsEnabled()
        okButton.performClick()

        verify(exactly = 1) { onValueConfirm.invoke(1) }
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

        val okButton = composeTestRule.onNodeWithText(string(R.string.action_ok))
        okButton.assertIsNotEnabled()
        okButton.performClick()

        verify(exactly = 0) { onValueConfirm(any()) }
    }

    @Test
    fun `confirm ignores empty input`() {
        setContent(
            title = "Empty",
            currentValue = 3,
        )

        val field = composeTestRule.onNode(hasSetTextAction())
        field.performTextClearance()

        val okButton = composeTestRule.onNodeWithText(string(R.string.action_ok))
        okButton.assertIsNotEnabled()
        okButton.performClick()

        verify(exactly = 0) { onValueConfirm(any()) }
    }

    @Test
    fun `OK is enabled when value equals maximum boundary`() {
        setContent(
            title = "Max boundary",
            currentValue = 0,
            minValue = 1,
            maxValue = 10,
        )

        val field = composeTestRule.onNode(hasSetTextAction())
        field.performTextClearance()
        field.performTextInput("10")

        val okButton = composeTestRule.onNodeWithText(string(R.string.action_ok))
        okButton.assertIsEnabled()
        okButton.performClick()

        verify(exactly = 1) { onValueConfirm.invoke(10) }
    }

    @Test
    fun `confirm ignores values above maximum`() {
        setContent(
            title = "Max value",
            currentValue = 0,
            minValue = 1,
            maxValue = 10,
        )

        val field = composeTestRule.onNode(hasSetTextAction())
        field.performTextClearance()
        field.performTextInput("11")

        val okButton = composeTestRule.onNodeWithText(string(R.string.action_ok))
        okButton.assertIsNotEnabled()
        okButton.performClick()

        verify(exactly = 0) { onValueConfirm(any()) }
    }

    @Test
    fun `OK stays disabled for any input when maximum is zero`() {
        setContent(
            title = "No capacity",
            currentValue = 0,
            minValue = 1,
            maxValue = 0,
        )

        val field = composeTestRule.onNode(hasSetTextAction())
        field.performTextClearance()
        field.performTextInput("222")

        val okButton = composeTestRule.onNodeWithText(string(R.string.action_ok))
        okButton.assertIsNotEnabled()
        okButton.performClick()

        verify(exactly = 0) { onValueConfirm(any()) }
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
        maxValue: Int = Int.MAX_VALUE,
    ) {
        composeTestRule.setContent {
            MyApplicationTheme {
                NumberInputDialog(
                    title = title,
                    currentValue = currentValue,
                    onValueConfirm = onValueConfirm,
                    onDismiss = onDismiss,
                    minValue = minValue,
                    maxValue = maxValue,
                )
            }
        }
    }

    private fun string(resId: Int): String = context.getString(resId)
}
