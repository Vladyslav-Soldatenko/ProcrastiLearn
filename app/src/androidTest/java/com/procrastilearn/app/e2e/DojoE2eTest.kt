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
import com.procrastilearn.app.data.local.entity.VocabularyEntity
import com.procrastilearn.app.di.DatabaseEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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
        resetAppState()
        composeTestRule.dismissOnboardingIfPresent(targetContext)
    }

    @After
    fun afterEach() {
        resetAppState()
    }

    @Test
    fun ratingNewWordUpdatesStatsAndAdvancesToNextCard() {
        val wordA = "flumoxint"
        val wordB = "vintlorae"
        seedNewWord(wordA, "translation-alpha")
        seedNewWord(wordB, "translation-beta")
        navigateToDojo()

        composeTestRule.waitUntilNodeExists(hasText("2", substring = false), DEFAULT_TIMEOUT_MS)
        composeTestRule.onNodeWithText("0", useUnmergedTree = true).assertIsDisplayed()

        composeTestRule
            .onNodeWithContentDescription(string(R.string.dojo_undo_content_description))
            .assertIsNotEnabled()

        val firstShown =
            if (composeTestRule.nodeVisibleWithin(hasText(wordA, substring = true), SHORT_TIMEOUT_MS)) wordA else wordB
        val expectedNext = if (firstShown == wordA) wordB else wordA

        composeTestRule.onNodeWithText(string(R.string.learning_show_translation)).performClick()
        composeTestRule.waitForIdle()

        listOf(
            R.string.rating_again,
            R.string.rating_hard,
            R.string.rating_good,
            R.string.rating_easy,
        ).forEach { resId -> composeTestRule.onNodeWithText(string(resId)).assertIsDisplayed() }

        composeTestRule.onNodeWithText(string(R.string.rating_good)).performClick()

        composeTestRule.waitUntilNodeExists(hasText(expectedNext, substring = true), DEFAULT_TIMEOUT_MS)
        composeTestRule.waitUntilNodeExists(hasText("1", substring = false), DEFAULT_TIMEOUT_MS)
        composeTestRule.onNodeWithText(string(R.string.learning_show_translation)).assertIsDisplayed()

        composeTestRule
            .onNodeWithContentDescription(string(R.string.dojo_undo_content_description))
            .assertIsDisplayed()
    }

    @Test
    fun undoRestoresPreviousCardAndStats() {
        val word = "quorvanel"
        seedNewWord(word, "restored-translation")
        navigateToDojo()

        composeTestRule.waitUntilNodeExists(hasText(word, substring = true), DEFAULT_TIMEOUT_MS)
        composeTestRule.onNodeWithText(string(R.string.learning_show_translation)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.rating_again)).performClick()

        composeTestRule.waitUntilNodeExists(hasText(string(R.string.dojo_empty_title)), DEFAULT_TIMEOUT_MS)
        // Both the new-remaining and reviews-due counters read 0 now.
        composeTestRule.onAllNodesWithText("0", useUnmergedTree = true).assertCountEquals(2)

        composeTestRule
            .onNodeWithContentDescription(string(R.string.dojo_undo_content_description))
            .assertIsDisplayed()
            .performClick()

        val expectedMessage =
            targetContext.getString(
                R.string.dojo_undo_confirmation,
                string(R.string.rating_again),
                word,
            )
        composeTestRule.waitUntilNodeExists(hasText(expectedMessage), DEFAULT_TIMEOUT_MS)

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
        ).forEach { resId -> composeTestRule.onNodeWithText(string(resId)).assertIsDisplayed() }

        composeTestRule.waitUntilNodeExists(hasText("1", substring = false), DEFAULT_TIMEOUT_MS)
    }

    @Test
    fun undoButtonDisabledWhenNothingToUndo() {
        navigateToDojo()

        composeTestRule
            .onNodeWithContentDescription(string(R.string.dojo_undo_content_description))
            .assertIsNotEnabled()
    }

    @Test
    fun emptyStateShownWhenNoWordsAvailable() {
        navigateToDojo()

        composeTestRule.onNodeWithText(string(R.string.dojo_empty_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.dojo_empty_message)).assertIsDisplayed()
        // Both the new-remaining and reviews-due counters read 0.
        composeTestRule.onAllNodesWithText("0", useUnmergedTree = true).assertCountEquals(2)
    }

    @Test
    fun dueReviewCardIsSurfacedAndDecrementsReviewCountThenEmptiesOut() {
        val word = "brastellum"
        seedDueReviewWord(word, "review-translation")
        navigateToDojo()

        composeTestRule.waitUntilNodeExists(hasText(word, substring = true), DEFAULT_TIMEOUT_MS)
        composeTestRule.waitUntilNodeExists(hasText("1", substring = false), DEFAULT_TIMEOUT_MS)

        composeTestRule.onNodeWithText(string(R.string.learning_show_translation)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.rating_good)).performClick()

        composeTestRule.waitUntilNodeExists(hasText(string(R.string.dojo_empty_title)), DEFAULT_TIMEOUT_MS)
        // Both the new-remaining and reviews-due counters read 0 now.
        composeTestRule.onAllNodesWithText("0", useUnmergedTree = true).assertCountEquals(2)
    }

    private fun string(resId: Int) = targetContext.getString(resId)

    private fun navigateToDojo() {
        val dojoLabel = targetContext.getString(R.string.nav_dojo)
        composeTestRule.waitUntilNodeExists(hasText(dojoLabel), DEFAULT_TIMEOUT_MS)
        composeTestRule
            .onNodeWithContentDescription(dojoLabel, useUnmergedTree = true)
            .performClick()
        composeTestRule.waitForIdle()
    }

    private fun seedNewWord(
        word: String,
        translation: String,
    ) {
        insertVocabulary(
            VocabularyEntity(
                word = word,
                translation = translation,
                correctCount = 0,
                incorrectCount = 0,
                fsrsCardJson = "",
                fsrsDueAt = 0L,
            ),
        )
    }

    private fun seedDueReviewWord(
        word: String,
        translation: String,
    ) {
        insertVocabulary(
            VocabularyEntity(
                word = word,
                translation = translation,
                correctCount = 1,
                incorrectCount = 0,
                fsrsCardJson = "",
                fsrsDueAt = System.currentTimeMillis() - REVIEW_DUE_OFFSET_MS,
            ),
        )
    }

    private fun insertVocabulary(entity: VocabularyEntity) {
        runBlocking {
            withContext(Dispatchers.IO) {
                val dao = entryPoint().appDatabase().vocabularyDao()
                dao.insertVocabulary(entity.copy(position = dao.getMaxPosition() + 1))
            }
        }
    }

    // Only vocabulary/undo state is reset here: the day-counters DataStore singleton is
    // already active (opened by MainActivity before this rule's @Before runs), and Hilt has
    // no test-only entry point wired up for it in this codebase, so it isn't touched. The
    // default daily quota (15 new / 99 reviews) resets itself once the calendar day rolls
    // over, and each test seeds well under that quota, so leftover counters from earlier
    // runs the same day don't affect these assertions in practice.
    private fun resetAppState() {
        runBlocking {
            withContext(Dispatchers.IO) {
                val db = entryPoint().appDatabase()
                db.vocabularyDao().deleteAllVocabulary()
                db.undoSnapshotDao().deleteAll()
            }
        }
    }

    private fun entryPoint(): DatabaseEntryPoint =
        EntryPointAccessors.fromApplication(
            targetContext.applicationContext,
            DatabaseEntryPoint::class.java,
        )

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 15_000L
        const val SHORT_TIMEOUT_MS = 5_000L
        const val REVIEW_DUE_OFFSET_MS = 60_000L
    }
}
