package com.procrastilearn.app.ui.dojo

import android.content.Context
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.procrastilearn.app.R
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.testing.ComponentActivityRegistrationRule
import io.github.openspacedrepetition.Rating
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
)
class DojoScreenTest {
    private val composeTestRule = createComposeRule()

    @get:Rule
    val rules: TestRule =
        RuleChain
            .outerRule(ComponentActivityRegistrationRule())
            .around(composeTestRule)

    private lateinit var context: Context
    private lateinit var onToggleShowAnswer: () -> Unit
    private lateinit var onDifficultySelected: (Rating) -> Unit
    private lateinit var onUndo: () -> Unit
    private lateinit var onUndoEventShown: () -> Unit

    private val sampleWord =
        VocabularyItem(id = 1L, word = "serendipity", translation = "happy accident", isNew = false)

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        onToggleShowAnswer = mockk(relaxed = true)
        onDifficultySelected = mockk(relaxed = true)
        onUndo = mockk(relaxed = true)
        onUndoEventShown = mockk(relaxed = true)
    }

    private fun string(resId: Int) = context.getString(resId)

    @Test
    fun `shows loading indicator while isLoading is true`() {
        setContent(DojoUiState(isLoading = true))

        composeTestRule
            .onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.dojo_empty_title)).assertDoesNotExist()
    }

    @Test
    fun `shows empty state when there is no word and not loading`() {
        setContent(DojoUiState(vocabularyItem = null, isLoading = false))

        composeTestRule.onNodeWithText(string(R.string.dojo_empty_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.dojo_empty_message)).assertIsDisplayed()
    }

    @Test
    fun `shows word and reveal button when answer hidden`() {
        setContent(DojoUiState(vocabularyItem = sampleWord, showAnswer = false, isLoading = false))

        composeTestRule.onNodeWithText(sampleWord.word).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.learning_show_translation)).assertIsDisplayed()
    }

    @Test
    fun `clicking reveal button invokes onToggleShowAnswer`() {
        setContent(DojoUiState(vocabularyItem = sampleWord, showAnswer = false, isLoading = false))

        composeTestRule.onNodeWithText(string(R.string.learning_show_translation)).performClick()

        verify(exactly = 1) { onToggleShowAnswer.invoke() }
    }

    @Test
    fun `shows translation and rating buttons when answer visible`() {
        setContent(DojoUiState(vocabularyItem = sampleWord, showAnswer = true, isLoading = false))

        composeTestRule.onNodeWithText(sampleWord.translation, useUnmergedTree = true).assertIsDisplayed()
        listOf(
            R.string.rating_again,
            R.string.rating_hard,
            R.string.rating_good,
            R.string.rating_easy,
        ).forEach { resId -> composeTestRule.onNodeWithText(string(resId)).assertIsDisplayed() }
    }

    @Test
    fun `clicking a rating button invokes onDifficultySelected with that rating`() {
        setContent(DojoUiState(vocabularyItem = sampleWord, showAnswer = true, isLoading = false))

        composeTestRule.onNodeWithText(string(R.string.rating_good)).performClick()

        verify(exactly = 1) { onDifficultySelected(Rating.GOOD) }
    }

    @Test
    fun `shows stats header values from state`() {
        setContent(
            DojoUiState(
                vocabularyItem = sampleWord,
                isLoading = false,
                newQuotaRemaining = 12,
                pendingReviewCount = 4,
            ),
        )

        composeTestRule.onNodeWithText("12", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("4", substring = true).assertIsDisplayed()
    }

    @Test
    fun `undo button disabled when canUndo is false`() {
        setContent(DojoUiState(vocabularyItem = sampleWord, isLoading = false, canUndo = false))

        composeTestRule
            .onNodeWithContentDescription(string(R.string.dojo_undo_content_description))
            .assertIsNotEnabled()
            .performClick()

        verify { onUndo wasNot called }
    }

    @Test
    fun `undo button invokes onUndo when canUndo is true`() {
        setContent(DojoUiState(vocabularyItem = sampleWord, isLoading = false, canUndo = true))

        composeTestRule
            .onNodeWithContentDescription(string(R.string.dojo_undo_content_description))
            .performClick()

        verify(exactly = 1) { onUndo.invoke() }
    }

    @Test
    fun `shows undo confirmation snackbar message when an undo event is present`() {
        setContent(
            DojoUiState(
                vocabularyItem = sampleWord,
                isLoading = false,
                undoEvent = UndoEvent(id = 1L, word = "Baum", revertedRating = Rating.GOOD),
            ),
        )

        val expectedMessage =
            context.getString(R.string.dojo_undo_confirmation, string(R.string.rating_good), "Baum")
        composeTestRule.onNodeWithText(expectedMessage).assertIsDisplayed()
    }

    private fun setContent(uiState: DojoUiState) {
        composeTestRule.setContent {
            DojoScreen(
                uiState = uiState,
                onToggleShowAnswer = onToggleShowAnswer,
                onDifficultySelected = onDifficultySelected,
                onUndo = onUndo,
                onUndoEventShown = onUndoEventShown,
            )
        }
        composeTestRule.waitForIdle()
    }
}
