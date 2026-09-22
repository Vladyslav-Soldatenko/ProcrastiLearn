package com.procrastilearn.app.e2e

import android.content.Context
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.procrastilearn.app.MainActivity
import com.procrastilearn.app.R
import com.procrastilearn.app.domain.model.StudyDirectionMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WordEditE2eTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var targetContext: Context

    @Before
    fun beforeEach() {
        targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        resetState()
        composeTestRule.dismissOnboardingIfPresent(targetContext)
    }

    @After
    fun afterEach() {
        resetState()
    }

    @Test
    fun editingWordAndTranslationPersistsChangesToDatabase() {
        val originalWord = "flarnbicket"
        val originalTranslation = "gloomventra"
        targetContext.seedWord(word = originalWord, translation = originalTranslation)

        composeTestRule.navigateToWordList(targetContext)
        openEditDialogFor(originalWord)
        replaceFieldText(R.string.add_word_label_word, "flarnbicket-updated")
        replaceFieldText(R.string.add_word_label_translation, "gloomventra-updated")
        clickAction(R.string.action_save)

        assertNull(targetContext.vocabularyByWord(originalWord))
        val updated = requireNotNull(targetContext.vocabularyByWord("flarnbicket-updated"))
        assertEquals("gloomventra-updated", updated.translation)
    }

    @Test
    fun cancellingEditDialogDiscardsChanges() {
        val word = "prendolack"
        val translation = "ostrivane"
        targetContext.seedWord(word = word, translation = translation)

        composeTestRule.navigateToWordList(targetContext)
        openEditDialogFor(word)
        replaceFieldText(R.string.add_word_label_word, "prendolack-changed")
        clickAction(R.string.action_cancel)

        assertNull(targetContext.vocabularyByWord("prendolack-changed"))
        assertEquals(translation, requireNotNull(targetContext.vocabularyByWord(word)).translation)
    }

    @Test
    fun enablingBidirectionalInEditDialogSetsFlagAndSeedsBackwardDueDateWhenAlreadyReviewed() {
        val word = "quindaloop"
        val translation = "brastanix"
        val forwardDueAt = System.currentTimeMillis() + ONE_DAY_MS
        targetContext.seedWord(word = word, translation = translation, correctCount = 1, fsrsDueAt = forwardDueAt)

        composeTestRule.navigateToWordList(targetContext)
        openEditDialogFor(word)
        composeTestRule.onNode(isToggleable(), useUnmergedTree = true).performClick()
        clickAction(R.string.action_save)

        val updated = requireNotNull(targetContext.vocabularyByWord(word))
        assertTrue(updated.bidirectional)
        assertTrue(updated.backwardFsrsDueAt > 0L)
    }

    @Test
    fun disablingBidirectionalInEditDialogClearsFlag() {
        val word = "trevoskin"
        val translation = "mundacrest"
        targetContext.seedWord(word = word, translation = translation, bidirectional = true)

        composeTestRule.navigateToWordList(targetContext)
        openEditDialogFor(word)
        composeTestRule.onNode(isToggleable(), useUnmergedTree = true).performClick()
        clickAction(R.string.action_save)

        assertFalse(requireNotNull(targetContext.vocabularyByWord(word)).bidirectional)
    }

    @Test
    fun customizingReverseOverridesPersistsPromptAndAnswerText() {
        val word = "shalimquor"
        val translation = "ventrabole"
        targetContext.seedWord(word = word, translation = translation)

        composeTestRule.navigateToWordList(targetContext)
        openEditDialogFor(word)
        composeTestRule.onNode(isToggleable(), useUnmergedTree = true).performClick()
        composeTestRule
            .onNodeWithText(targetContext.string(R.string.add_word_customize_backward_show))
            .performScrollTo()
            .performClick()
        composeTestRule
            .onNodeWithText(targetContext.string(R.string.add_word_backward_prompt_label))
            .performScrollTo()
            .performTextInput("What runs?")
        composeTestRule
            .onNodeWithText(targetContext.string(R.string.add_word_backward_answer_label))
            .performScrollTo()
            .performTextInput(word)
        clickAction(R.string.action_save)

        val updated = requireNotNull(targetContext.vocabularyByWord(word))
        assertTrue(updated.bidirectional)
        assertEquals("What runs?", updated.backwardPromptOverride)
        assertEquals(word, updated.backwardAnswerOverride)
    }

    @Test
    fun clearingReverseOverridesOnSaveResetsThemToNull() {
        val word = "nostrivell"
        val translation = "quenthalor"
        targetContext.seedWord(
            word = word,
            translation = translation,
            bidirectional = true,
            backwardPromptOverride = "Old prompt",
            backwardAnswerOverride = "Old answer",
        )

        composeTestRule.navigateToWordList(targetContext)
        openEditDialogFor(word)
        composeTestRule
            .onNodeWithText(targetContext.string(R.string.add_word_backward_prompt_label))
            .performScrollTo()
            .performTextClearance()
        composeTestRule
            .onNodeWithText(targetContext.string(R.string.add_word_backward_answer_label))
            .performScrollTo()
            .performTextClearance()
        clickAction(R.string.action_save)

        val updated = requireNotNull(targetContext.vocabularyByWord(word))
        assertTrue(updated.bidirectional)
        assertNull(updated.backwardPromptOverride)
        assertNull(updated.backwardAnswerOverride)
    }

    private fun openEditDialogFor(word: String) {
        composeTestRule.waitUntilNodeExists(hasText(word), E2E_TIMEOUT_MS)
        composeTestRule
            .onNodeWithContentDescription(targetContext.string(R.string.word_list_more_actions), useUnmergedTree = true)
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(targetContext.string(R.string.action_edit)).performClick()
        composeTestRule.waitUntilNodeExists(hasText(targetContext.string(R.string.edit_word_title)), E2E_TIMEOUT_MS)
    }

    private fun replaceFieldText(
        labelResId: Int,
        newValue: String,
    ) {
        composeTestRule
            .onNode(hasText(targetContext.string(labelResId)).and(hasSetTextAction()))
            .performTextReplacement(newValue)
    }

    private fun clickAction(actionResId: Int) {
        composeTestRule.onNodeWithText(targetContext.string(actionResId)).performClick()
        composeTestRule.waitForIdle()
    }

    private fun resetState() {
        targetContext.resetE2eDatabase()
        targetContext.setStudyDirectionMode(StudyDirectionMode.BIDIRECTIONAL)
    }

    private companion object {
        const val ONE_DAY_MS = 24 * 60 * 60 * 1000L
    }
}
