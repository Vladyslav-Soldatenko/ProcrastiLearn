package com.procrastilearn.app.e2e

import android.content.Context
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.procrastilearn.app.MainActivity
import com.procrastilearn.app.R
import com.procrastilearn.app.domain.model.StudyDirectionMode
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WordListBidirectionalToggleE2eTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var targetContext: Context

    @Before
    fun beforeEach() {
        targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        targetContext.resetE2eDatabase()
        targetContext.setStudyDirectionMode(StudyDirectionMode.BIDIRECTIONAL)
        composeTestRule.dismissOnboardingIfPresent(targetContext)
    }

    @After
    fun afterEach() {
        targetContext.resetE2eDatabase()
        targetContext.setStudyDirectionMode(StudyDirectionMode.BIDIRECTIONAL)
    }

    @Test
    fun enablingBidirectionalOnSelectedWordSetsFlagAndSeedsBackwardDueDateWhenAlreadyReviewed() {
        val word = "glimmerquat"
        val translation = "twillendor"
        val forwardDueAt = System.currentTimeMillis() + ONE_DAY_MS
        val id =
            targetContext.seedWord(
                word = word,
                translation = translation,
                correctCount = 1,
                fsrsDueAt = forwardDueAt,
            )

        composeTestRule.navigateToWordList(targetContext)
        composeTestRule.longPressWordListItem(id)
        composeTestRule.openWordListSelectionMenuAndTap(targetContext, R.string.word_list_bulk_bidirectional_enable)

        val updated = targetContext.vocabularyByWord(word)!!
        assertTrue(updated.bidirectional)
        assertTrue(updated.backwardFsrsDueAt > 0L)
    }

    @Test
    fun disablingBidirectionalOnSelectedWordShowsConfirmDialogAndClearsFlagOnConfirm() {
        val word = "sunderpike"
        val translation = "molvantree"
        val id = targetContext.seedWord(word = word, translation = translation, bidirectional = true)

        composeTestRule.navigateToWordList(targetContext)
        composeTestRule.longPressWordListItem(id)
        composeTestRule.openWordListSelectionMenuAndTap(targetContext, R.string.word_list_bulk_bidirectional_disable)

        composeTestRule.waitUntilNodeExists(
            hasText(targetContext.string(R.string.word_list_bulk_forward_only_confirm_title)),
            E2E_TIMEOUT_MS,
        )
        composeTestRule.onNodeWithText(targetContext.string(R.string.action_continue)).performClick()
        composeTestRule.waitForIdle()

        val updated = targetContext.vocabularyByWord(word)!!
        assertFalse(updated.bidirectional)
    }

    @Test
    fun bulkTestBothDirectionsAppliesToMultipleSelectedWords() {
        val wordA = "corvantiel"
        val wordB = "brellathorn"
        val idA = targetContext.seedWord(word = wordA, translation = "translation-a")
        val idB = targetContext.seedWord(word = wordB, translation = "translation-b")

        composeTestRule.navigateToWordList(targetContext)
        composeTestRule.longPressWordListItem(idA)
        composeTestRule.clickWordListItem(idB)
        composeTestRule.openWordListSelectionMenuAndTap(targetContext, R.string.word_list_bulk_bidirectional_enable)

        assertTrue(targetContext.vocabularyByWord(wordA)!!.bidirectional)
        assertTrue(targetContext.vocabularyByWord(wordB)!!.bidirectional)
    }

    private companion object {
        const val ONE_DAY_MS = 24 * 60 * 60 * 1000L
    }
}
