package com.procrastilearn.app.e2e

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.procrastilearn.app.MainActivity
import com.procrastilearn.app.R
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end coverage for the Dojo review flow: real Hilt DI, real Room database and real
 * FSRS scheduling wired through [MainActivity], exercising what the mocked-out unit tests
 * ([com.procrastilearn.app.ui.dojo.DojoViewModelTest], [com.procrastilearn.app.ui.dojo.DojoScreenTest])
 * cannot: that rating a card in the running app actually persists, advances the queue, updates
 * the stats header, and that undo actually reverts the database.
 */
@RunWith(AndroidJUnit4::class)
class DojoE2eTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var targetContext: Context

    @Before
    fun beforeEach() {
        targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        targetContext.resetE2eDatabase()
        composeTestRule.dismissOnboardingIfPresent(targetContext)
    }

    @After
    fun afterEach() {
        targetContext.resetE2eDatabase()
    }

    @Test
    fun ratingNewWordUpdatesStatsAndAdvancesToNextCard() {
        val wordA = "flumoxint"
        val wordB = "vintlorae"
        seedNewWord(wordA, "translation-alpha")
        seedNewWord(wordB, "translation-beta")
        composeTestRule.navigateTo(targetContext, R.string.nav_dojo)

        composeTestRule.waitUntilNodeExists(hasText("2", substring = false), E2E_TIMEOUT_MS)
        composeTestRule.onNodeWithText("0", useUnmergedTree = true).assertIsDisplayed()

        composeTestRule
            .onNodeWithContentDescription(targetContext.string(R.string.dojo_undo_content_description))
            .assertIsNotEnabled()

        val firstShown =
            if (composeTestRule.nodeVisibleWithin(hasText(wordA, substring = true), E2E_SHORT_TIMEOUT_MS)) wordA else wordB
        val expectedNext = if (firstShown == wordA) wordB else wordA

        composeTestRule.onNodeWithText(targetContext.string(R.string.learning_show_translation)).performClick()
        composeTestRule.waitForIdle()

        listOf(
            R.string.rating_again,
            R.string.rating_hard,
            R.string.rating_good,
            R.string.rating_easy,
        ).forEach { resId -> composeTestRule.onNodeWithText(targetContext.string(resId)).assertIsDisplayed() }

        composeTestRule.onNodeWithText(targetContext.string(R.string.rating_good)).performClick()

        composeTestRule.waitUntilNodeExists(hasText(expectedNext, substring = true), E2E_TIMEOUT_MS)
        composeTestRule.waitUntilNodeExists(hasText("1", substring = false), E2E_TIMEOUT_MS)
        composeTestRule.onNodeWithText(targetContext.string(R.string.learning_show_translation)).assertIsDisplayed()

        composeTestRule
            .onNodeWithContentDescription(targetContext.string(R.string.dojo_undo_content_description))
            .assertIsDisplayed()
    }

    @Test
    fun undoRestoresPreviousCardAndStats() {
        val word = "quorvanel"
        seedNewWord(word, "restored-translation")
        composeTestRule.navigateTo(targetContext, R.string.nav_dojo)

        composeTestRule.waitUntilNodeExists(hasText(word, substring = true), E2E_TIMEOUT_MS)
        composeTestRule.onNodeWithText(targetContext.string(R.string.learning_show_translation)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(targetContext.string(R.string.rating_again)).performClick()

        composeTestRule.waitUntilNodeExists(hasText(targetContext.string(R.string.dojo_empty_title)), E2E_TIMEOUT_MS)
        // Both the new-remaining and reviews-due counters read 0 now.
        composeTestRule.onAllNodesWithText("0", useUnmergedTree = true).assertCountEquals(2)

        composeTestRule
            .onNodeWithContentDescription(targetContext.string(R.string.dojo_undo_content_description))
            .assertIsDisplayed()
            .performClick()

        val expectedMessage =
            targetContext.getString(
                R.string.dojo_undo_confirmation,
                targetContext.string(R.string.rating_again),
                word,
            )
        composeTestRule.waitUntilNodeExists(hasText(expectedMessage), E2E_TIMEOUT_MS)

        // Undo pins the restored card back on screen with its answer already revealed. The
        // word may currently match twice (the card title and the still-visible snackbar
        // text), so check the first match rather than requiring a single unique node.
        composeTestRule
            .onAllNodesWithText(word, substring = true, useUnmergedTree = true)
            .onFirst()
            .assertIsDisplayed()
        listOf(
            R.string.rating_again,
            R.string.rating_hard,
            R.string.rating_good,
            R.string.rating_easy,
        ).forEach { resId -> composeTestRule.onNodeWithText(targetContext.string(resId)).assertIsDisplayed() }

        composeTestRule.waitUntilNodeExists(hasText("1", substring = false), E2E_TIMEOUT_MS)
    }

    @Test
    fun undoButtonDisabledWhenNothingToUndo() {
        composeTestRule.navigateTo(targetContext, R.string.nav_dojo)

        composeTestRule
            .onNodeWithContentDescription(targetContext.string(R.string.dojo_undo_content_description))
            .assertIsNotEnabled()
    }

    @Test
    fun emptyStateShownWhenNoWordsAvailable() {
        composeTestRule.navigateTo(targetContext, R.string.nav_dojo)

        composeTestRule.onNodeWithText(targetContext.string(R.string.dojo_empty_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(targetContext.string(R.string.dojo_empty_message)).assertIsDisplayed()
        // Both the new-remaining and reviews-due counters read 0.
        composeTestRule.onAllNodesWithText("0", useUnmergedTree = true).assertCountEquals(2)
    }

    @Test
    fun dueReviewCardIsSurfacedAndDecrementsReviewCountThenEmptiesOut() {
        val word = "brastellum"
        seedDueReviewWord(word, "review-translation")
        composeTestRule.navigateTo(targetContext, R.string.nav_dojo)

        composeTestRule.waitUntilNodeExists(hasText(word, substring = true), E2E_TIMEOUT_MS)
        composeTestRule.waitUntilNodeExists(hasText("1", substring = false), E2E_TIMEOUT_MS)

        composeTestRule.onNodeWithText(targetContext.string(R.string.learning_show_translation)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(targetContext.string(R.string.rating_good)).performClick()

        composeTestRule.waitUntilNodeExists(hasText(targetContext.string(R.string.dojo_empty_title)), E2E_TIMEOUT_MS)
        // Both the new-remaining and reviews-due counters read 0 now.
        composeTestRule.onAllNodesWithText("0", useUnmergedTree = true).assertCountEquals(2)
    }

    private fun seedNewWord(
        word: String,
        translation: String,
    ) {
        targetContext.seedWord(word, translation)
    }

    private fun seedDueReviewWord(
        word: String,
        translation: String,
    ) {
        targetContext.seedWord(
            word,
            translation,
            correctCount = 1,
            fsrsDueAt = System.currentTimeMillis() - REVIEW_DUE_OFFSET_MS,
        )
    }

    private companion object {
        const val REVIEW_DUE_OFFSET_MS = 60_000L
    }
}
