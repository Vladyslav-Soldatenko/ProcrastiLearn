package com.procrastilearn.app.ui.screens.settings.components

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
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
class StringInputDialogTest {
    private val composeTestRule = createComposeRule()

    @get:Rule
    val rules: TestRule =
        RuleChain
            .outerRule(ComponentActivityRegistrationRule())
            .around(composeTestRule)

    private lateinit var onValueConfirm: (String) -> Unit
    private lateinit var onDismiss: () -> Unit
    private lateinit var context: Context

    @Before
    fun setup() {
        onValueConfirm = mockk(relaxed = true)
        onDismiss = mockk(relaxed = true)
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `shows title and initial value with password semantics`() {
        setContent(
            title = "API Key",
            currentValue = "secret",
        )

        composeTestRule
            .onNodeWithText("API Key")
            .assertIsDisplayed()

        val field = composeTestRule.onNode(hasSetTextAction())
        val semanticsNode = field.fetchSemanticsNode()
        val editableText =
            semanticsNode.config.getOrNull(SemanticsProperties.EditableText)?.text
        assertThat(editableText?.length).isEqualTo("secret".length)

        val semantics = semanticsNode.config.getOrNull(SemanticsProperties.Password)
        assertThat(semantics).isNotNull()

        verify { onValueConfirm wasNot called }
        verify { onDismiss wasNot called }
    }

    @Test
    fun `non password mode exposes text normally`() {
        setContent(
            title = "Prompt",
            currentValue = "default prompt",
            isPassword = false,
            singleLine = false,
            maxLines = 3,
        )

        val field = composeTestRule.onNode(hasSetTextAction())
        field.performTextInput(" more text")
        val semanticsNode = field.fetchSemanticsNode()
        val editableText =
            semanticsNode.config.getOrNull(SemanticsProperties.EditableText)?.text
        assertThat(editableText).isNotNull()
        assertThat(editableText).contains("default prompt")
        assertThat(editableText).contains("more text")

        val semantics = semanticsNode.config.getOrNull(SemanticsProperties.Password)
        assertThat(semantics).isNull()
    }

    @Test
    fun `confirm button returns current text`() {
        setContent(
            title = "Confirm",
            currentValue = "value",
        )

        val field = composeTestRule.onNode(hasSetTextAction())
        field.performTextClearance()
        field.performTextInput("new value")

        composeTestRule
            .onNodeWithText(string(R.string.action_ok))
            .performClick()

        verify(exactly = 1) { onValueConfirm.invoke("new value") }
        verify { onDismiss wasNot called }
    }

    @Test
    fun `dismiss button invokes onDismiss without confirm`() {
        setContent(
            title = "Dismiss",
            currentValue = "value",
        )

        composeTestRule
            .onNodeWithText(string(R.string.action_cancel))
            .performClick()

        verify(exactly = 1) { onDismiss.invoke() }
        verify { onValueConfirm wasNot called }
    }

    private fun setContent(
        title: String,
        currentValue: String,
        isPassword: Boolean = true,
        singleLine: Boolean = true,
        maxLines: Int = if (singleLine) 1 else 4,
    ) {
        composeTestRule.setContent {
            MyApplicationTheme {
                StringInputDialog(
                    title = title,
                    currentValue = currentValue,
                    onValueConfirm = onValueConfirm,
                    onDismiss = onDismiss,
                    isPassword = isPassword,
                    singleLine = singleLine,
                    maxLines = maxLines,
                )
            }
        }
    }

    private fun string(resId: Int): String = context.getString(resId)
}
