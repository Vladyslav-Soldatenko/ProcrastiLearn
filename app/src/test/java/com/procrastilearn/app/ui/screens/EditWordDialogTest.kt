package com.procrastilearn.app.ui.screens

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.procrastilearn.app.R
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.testing.ComponentActivityRegistrationRule
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
class EditWordDialogTest {
    private val composeTestRule = createComposeRule()

    @get:Rule
    val rules: TestRule =
        RuleChain
            .outerRule(ComponentActivityRegistrationRule())
            .around(composeTestRule)

    private lateinit var context: Context
    private lateinit var onDelete: (VocabularyItem) -> Unit
    private lateinit var onEdit: (VocabularyItem) -> Unit
    private lateinit var onReset: (VocabularyItem) -> Unit

    private val words =
        listOf(
            VocabularyItem(id = 1, word = "Serendipity", translation = "Happy accident", isNew = true),
            VocabularyItem(id = 2, word = "Ephemeral", translation = "Short lived", isNew = false),
            VocabularyItem(id = 3, word = "Peregrinate", translation = "To wander", isNew = false),
        )

    private val bidirectionalWordWithOverrides =
        VocabularyItem(
            id = 4,
            word = "Laufen",
            translation = "to run",
            isNew = false,
            bidirectional = true,
            backwardPromptOverride = "Run!",
            backwardAnswerOverride = "Laufen (verb)",
        )

    private val bidirectionalWordWithoutOverrides =
        VocabularyItem(id = 5, word = "Sprechen", translation = "to speak", isNew = false, bidirectional = true)

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        onDelete = mockk(relaxed = true)
        onEdit = mockk(relaxed = true)
        onReset = mockk(relaxed = true)
    }

    private fun string(resId: Int) = context.getString(resId)

    @Test
    fun `confirming edit dialog with changed fields invokes onEdit with updated item`() {
        setContent(words = words.take(1))
        openMenuFor()
        composeTestRule.onNodeWithText(string(R.string.action_edit)).performClick()

        val wordField = composeTestRule.onAllNodes(hasSetTextAction())[1]
        wordField.performTextClearance()
        wordField.performTextInput("Updated")

        composeTestRule.onNodeWithText(string(R.string.action_save)).performClick()

        verify(exactly = 1) {
            onEdit(words[0].copy(word = "Updated"))
        }
    }

    @Test
    fun `edit dialog does not confirm when word is blank`() {
        setContent(words = words.take(1))
        openMenuFor()
        composeTestRule.onNodeWithText(string(R.string.action_edit)).performClick()

        val wordField = composeTestRule.onAllNodes(hasSetTextAction())[1]
        wordField.performTextClearance()

        composeTestRule.onNodeWithText(string(R.string.action_save)).performClick()

        verify { onEdit wasNot called }
        composeTestRule.onNodeWithText(string(R.string.edit_word_title)).assertIsDisplayed()
    }

    @Test
    fun `cancelling edit dialog does not invoke onEdit`() {
        setContent(words = words.take(1))
        openMenuFor()
        composeTestRule.onNodeWithText(string(R.string.action_edit)).performClick()

        composeTestRule.onNodeWithText(string(R.string.action_cancel)).performClick()

        verify { onEdit wasNot called }
        composeTestRule.onNodeWithText(string(R.string.edit_word_title)).assertDoesNotExist()
    }

    @Test
    fun `edit dialog shows bidirectional checkbox unchecked for a non-bidirectional word`() {
        setContent(words = words.take(1))
        openMenuFor()
        composeTestRule.onNodeWithText(string(R.string.action_edit)).performClick()

        composeTestRule.onNode(isToggleable()).assertIsOff()
    }

    @Test
    fun `edit dialog shows bidirectional checkbox checked for a bidirectional word`() {
        setContent(words = listOf(bidirectionalWordWithoutOverrides))
        openMenuFor()
        composeTestRule.onNodeWithText(string(R.string.action_edit)).performClick()

        composeTestRule.onNode(isToggleable()).assertIsOn()
    }

    @Test
    fun `customize action hidden in edit dialog until bidirectional is checked`() {
        setContent(words = words.take(1))
        openMenuFor()
        composeTestRule.onNodeWithText(string(R.string.action_edit)).performClick()

        composeTestRule.onNodeWithText(string(R.string.add_word_customize_backward_show)).assertDoesNotExist()
    }

    @Test
    fun `customize fields start expanded in edit dialog when the word already has reverse overrides`() {
        setContent(words = listOf(bidirectionalWordWithOverrides))
        openMenuFor()
        composeTestRule.onNodeWithText(string(R.string.action_edit)).performClick()

        composeTestRule
            .onNodeWithText(string(R.string.add_word_backward_prompt_label))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(string(R.string.add_word_backward_answer_label))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `customize fields start collapsed when bidirectional has no reverse overrides`() {
        setContent(words = listOf(bidirectionalWordWithoutOverrides))
        openMenuFor()
        composeTestRule.onNodeWithText(string(R.string.action_edit)).performClick()

        composeTestRule
            .onNodeWithText(string(R.string.add_word_customize_backward_show))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.add_word_backward_prompt_label)).assertDoesNotExist()
    }

    @Test
    fun `checking bidirectional checkbox in edit dialog reveals the customize action`() {
        setContent(words = words.take(1))
        openMenuFor()
        composeTestRule.onNodeWithText(string(R.string.action_edit)).performClick()

        composeTestRule.onNode(isToggleable()).performClick()

        composeTestRule
            .onNodeWithText(string(R.string.add_word_customize_backward_show))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `unchecking bidirectional checkbox in edit dialog hides the customize action and fields`() {
        setContent(words = listOf(bidirectionalWordWithOverrides))
        openMenuFor()
        composeTestRule.onNodeWithText(string(R.string.action_edit)).performClick()

        composeTestRule.onNode(isToggleable()).performClick()

        composeTestRule.onNodeWithText(string(R.string.add_word_customize_backward_show)).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.add_word_backward_prompt_label)).assertDoesNotExist()
    }

    @Test
    fun `confirming edit dialog with bidirectional checked and no overrides saves null overrides`() {
        setContent(words = words.take(1))
        openMenuFor()
        composeTestRule.onNodeWithText(string(R.string.action_edit)).performClick()

        composeTestRule.onNode(isToggleable()).performClick()
        composeTestRule.onNodeWithText(string(R.string.action_save)).performClick()

        verify(exactly = 1) {
            onEdit(words[0].copy(bidirectional = true))
        }
    }

    @Test
    fun `confirming edit dialog with customized reverse fields saves the entered override text`() {
        setContent(words = words.take(1))
        openMenuFor()
        composeTestRule.onNodeWithText(string(R.string.action_edit)).performClick()

        composeTestRule.onNode(isToggleable()).performClick()
        composeTestRule
            .onNodeWithText(string(R.string.add_word_customize_backward_show))
            .performScrollTo()
            .performClick()
        composeTestRule
            .onNodeWithText(string(R.string.add_word_backward_prompt_label))
            .performScrollTo()
            .performTextInput("Run!")
        composeTestRule
            .onNodeWithText(string(R.string.add_word_backward_answer_label))
            .performScrollTo()
            .performTextInput("Laufen")
        composeTestRule.onNodeWithText(string(R.string.action_save)).performClick()

        verify(exactly = 1) {
            onEdit(
                words[0].copy(
                    bidirectional = true,
                    backwardPromptOverride = "Run!",
                    backwardAnswerOverride = "Laufen",
                ),
            )
        }
    }

    @Test
    fun `confirming edit dialog after unchecking bidirectional keeps previously saved overrides`() {
        setContent(words = listOf(bidirectionalWordWithOverrides))
        openMenuFor()
        composeTestRule.onNodeWithText(string(R.string.action_edit)).performClick()

        composeTestRule.onNode(isToggleable()).performClick()
        composeTestRule.onNodeWithText(string(R.string.action_save)).performClick()

        verify(exactly = 1) {
            onEdit(bidirectionalWordWithOverrides.copy(bidirectional = false))
        }
    }

    @Test
    fun `confirming edit dialog with whitespace-only override fields saves null overrides`() {
        setContent(words = words.take(1))
        openMenuFor()
        composeTestRule.onNodeWithText(string(R.string.action_edit)).performClick()

        composeTestRule.onNode(isToggleable()).performClick()
        composeTestRule
            .onNodeWithText(string(R.string.add_word_customize_backward_show))
            .performScrollTo()
            .performClick()
        composeTestRule
            .onNodeWithText(string(R.string.add_word_backward_prompt_label))
            .performScrollTo()
            .performTextInput("   ")
        composeTestRule.onNodeWithText(string(R.string.action_save)).performClick()

        verify(exactly = 1) {
            onEdit(words[0].copy(bidirectional = true))
        }
    }

    @Test
    fun `re-checking bidirectional in edit dialog after unchecking restores the previously entered overrides`() {
        setContent(words = words.take(1))
        openMenuFor()
        composeTestRule.onNodeWithText(string(R.string.action_edit)).performClick()

        composeTestRule.onNode(isToggleable()).performClick()
        composeTestRule
            .onNodeWithText(string(R.string.add_word_customize_backward_show))
            .performScrollTo()
            .performClick()
        composeTestRule
            .onNodeWithText(string(R.string.add_word_backward_prompt_label))
            .performScrollTo()
            .performTextInput("temp")
        composeTestRule.onNode(isToggleable()).performScrollTo().performClick()
        composeTestRule.onNode(isToggleable()).performScrollTo().performClick()
        composeTestRule.onNodeWithText(string(R.string.action_save)).performClick()

        verify(exactly = 1) {
            onEdit(words[0].copy(bidirectional = true, backwardPromptOverride = "temp"))
        }
    }

    @Test
    fun `cancelling edit dialog after toggling bidirectional does not invoke onEdit`() {
        setContent(words = words.take(1))
        openMenuFor()
        composeTestRule.onNodeWithText(string(R.string.action_edit)).performClick()

        composeTestRule.onNode(isToggleable()).performClick()
        composeTestRule.onNodeWithText(string(R.string.action_cancel)).performClick()

        verify { onEdit wasNot called }
    }

    private fun openMenuFor() {
        composeTestRule
            .onNodeWithContentDescription(string(R.string.word_list_more_actions))
            .performClick()
    }

    private fun setContent(words: List<VocabularyItem>) {
        composeTestRule.setContent {
            WordListContent(
                words = words,
                searchQuery = "",
                onSearchQueryChange = {},
                onDelete = onDelete,
                onEdit = onEdit,
                onReset = onReset,
            )
        }
    }
}
