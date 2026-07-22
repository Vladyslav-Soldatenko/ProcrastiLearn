package com.procrastilearn.app.ui.screens

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.procrastilearn.app.R
import com.procrastilearn.app.testing.ComponentActivityRegistrationRule
import com.procrastilearn.app.ui.AddWordPreviewContent
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

@RunWith(RobolectricTestRunner::class)
class AddWordDialogsTest {
    private val composeTestRule = createComposeRule()

    @get:Rule
    val rules: TestRule =
        RuleChain
            .outerRule(ComponentActivityRegistrationRule())
            .around(composeTestRule)

    private lateinit var onCancel: () -> Unit
    private lateinit var onConfirm: () -> Unit
    private lateinit var context: Context

    private val previewContent =
        AddWordPreviewContent(
            word = "Haus",
            translation = "House\nHome\nA building",
        )

    @Before
    fun setup() {
        onCancel = mockk(relaxed = true)
        onConfirm = mockk(relaxed = true)
        context = ApplicationProvider.getApplicationContext()
    }

    private fun string(resId: Int) = context.getString(resId)

    @Test
    fun `preview dialog displays word and translation`() {
        setPreviewDialogContent()

        composeTestRule.onNodeWithText(previewContent.word).assertIsDisplayed()
        composeTestRule.onNodeWithText(previewContent.translation).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.add_word_preview_title)).assertIsDisplayed()
    }

    @Test
    fun `preview dialog shows enabled cancel and confirm buttons when not loading`() {
        setPreviewDialogContent(isConfirmLoading = false)

        composeTestRule.onNodeWithText(string(R.string.add_word_preview_cancel)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.add_word_preview_confirm)).assertIsDisplayed()
    }

    @Test
    fun `preview dialog clicking cancel invokes onCancel`() {
        setPreviewDialogContent()

        composeTestRule.onNodeWithText(string(R.string.add_word_preview_cancel)).performClick()

        verify(exactly = 1) { onCancel.invoke() }
        verify { onConfirm wasNot called }
    }

    @Test
    fun `preview dialog clicking confirm invokes onConfirm`() {
        setPreviewDialogContent()

        composeTestRule.onNodeWithText(string(R.string.add_word_preview_confirm)).performClick()

        verify(exactly = 1) { onConfirm.invoke() }
        verify { onCancel wasNot called }
    }

    @Test
    fun `preview dialog hides confirm text and disables buttons while confirm loading`() {
        setPreviewDialogContent(isConfirmLoading = true)

        composeTestRule.onNodeWithText(string(R.string.add_word_preview_confirm)).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.add_word_preview_cancel)).assertIsNotEnabled()
    }

    @Test
    fun `preview dialog clicking disabled cancel while loading does not invoke callback`() {
        setPreviewDialogContent(isConfirmLoading = true)

        composeTestRule.onNodeWithText(string(R.string.add_word_preview_cancel)).performClick()

        verify { onCancel wasNot called }
    }

    private fun setPreviewDialogContent(isConfirmLoading: Boolean = false) {
        composeTestRule.setContent {
            AddWordPreviewDialog(
                previewContent = previewContent,
                isConfirmLoading = isConfirmLoading,
                onCancel = onCancel,
                onConfirm = onConfirm,
            )
        }
    }

    @Test
    fun `existing word dialog shows title and message`() {
        setExistingWordDialogContent(word = "Haus")

        composeTestRule.onNodeWithText(string(R.string.add_word_existing_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.add_word_existing_message)).assertIsDisplayed()
    }

    @Test
    fun `existing word dialog shows word when non-blank`() {
        setExistingWordDialogContent(word = "Haus")

        composeTestRule.onNodeWithText("Haus").assertIsDisplayed()
    }

    @Test
    fun `existing word dialog hides word row when word is null`() {
        setExistingWordDialogContent(word = null)

        composeTestRule.onNodeWithText(string(R.string.add_word_existing_title)).assertIsDisplayed()
    }

    @Test
    fun `existing word dialog hides word row when word is blank`() {
        setExistingWordDialogContent(word = "   ")

        composeTestRule.onNodeWithText("   ").assertDoesNotExist()
    }

    @Test
    fun `existing word dialog clicking cancel invokes onCancel`() {
        setExistingWordDialogContent(word = "Haus")

        composeTestRule.onNodeWithText(string(R.string.add_word_existing_cancel)).performClick()

        verify(exactly = 1) { onCancel.invoke() }
        verify { onConfirm wasNot called }
    }

    @Test
    fun `existing word dialog clicking proceed invokes onProceed`() {
        setExistingWordDialogContent(word = "Haus")

        composeTestRule.onNodeWithText(string(R.string.add_word_existing_proceed)).performClick()

        verify(exactly = 1) { onConfirm.invoke() }
        verify { onCancel wasNot called }
    }

    @Test
    fun `existing word dialog shows disabled buttons and hides proceed text while loading`() {
        setExistingWordDialogContent(word = "Haus", isLoading = true)

        composeTestRule.onNodeWithText(string(R.string.add_word_existing_proceed)).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.add_word_existing_cancel)).assertIsNotEnabled()
    }

    private fun setExistingWordDialogContent(
        word: String?,
        isLoading: Boolean = false,
    ) {
        composeTestRule.setContent {
            ExistingWordDialog(
                word = word,
                isLoading = isLoading,
                onProceed = onConfirm,
                onCancel = onCancel,
            )
        }
    }
}
