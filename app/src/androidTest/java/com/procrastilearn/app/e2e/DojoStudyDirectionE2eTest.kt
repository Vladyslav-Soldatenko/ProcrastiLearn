package com.procrastilearn.app.e2e

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
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
import com.procrastilearn.app.di.PreferencesEntryPoint
import com.procrastilearn.app.domain.model.StudyDirectionMode
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DojoStudyDirectionE2eTest {
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
    fun backwardDueBidirectionalCardShowsSwappedWordAndRatingUpdatesBackwardColumnsOnly() {
        val word = "plendarosk"
        val translation = "morvassilk"
        val forwardDueAt = System.currentTimeMillis() + ONE_DAY_MS
        val backwardDueAt = System.currentTimeMillis() - PAST_OFFSET_MS
        seedWord(
            word = word,
            translation = translation,
            bidirectional = true,
            correctCount = 1,
            fsrsDueAt = forwardDueAt,
            backwardFsrsDueAt = backwardDueAt,
        )
        setMode(StudyDirectionMode.BIDIRECTIONAL)
        navigateToDojo()

        composeTestRule.waitUntilNodeExists(hasText(translation, substring = true), DEFAULT_TIMEOUT_MS)
        composeTestRule.onNodeWithText(string(R.string.learning_show_translation)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(word, substring = true).assertIsDisplayed()

        composeTestRule.onNodeWithText(string(R.string.rating_good)).performClick()
        composeTestRule.waitUntilNodeExists(hasText(string(R.string.dojo_empty_title)), DEFAULT_TIMEOUT_MS)

        val updated = vocabularyByWord(word)
        assertEquals(1, updated.backwardCorrectCount)
        assertEquals(0, updated.backwardIncorrectCount)
        assertTrue(updated.backwardFsrsDueAt > System.currentTimeMillis())
        assertEquals(1, updated.correctCount)
        assertEquals(forwardDueAt, updated.fsrsDueAt)
        assertEquals("", updated.fsrsCardJson)
    }

    @Test
    fun newBidirectionalWordInBackwardModeIsIntroducedBackwardFirstAndSeedsForwardDueDate() {
        val word = "quiblenthar"
        val translation = "yornastiv"
        seedWord(word = word, translation = translation, bidirectional = true)
        setMode(StudyDirectionMode.BACKWARD)
        navigateToDojo()

        composeTestRule.waitUntilNodeExists(hasText(translation, substring = true), DEFAULT_TIMEOUT_MS)
        composeTestRule.onNodeWithText(string(R.string.learning_show_translation)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(word, substring = true).assertIsDisplayed()

        composeTestRule.onNodeWithText(string(R.string.rating_good)).performClick()
        composeTestRule.waitUntilNodeExists(hasText(string(R.string.dojo_empty_title)), DEFAULT_TIMEOUT_MS)
        composeTestRule.onAllNodesWithText("0", useUnmergedTree = true).assertCountEquals(2)

        val updated = vocabularyByWord(word)
        assertEquals(1, updated.backwardCorrectCount)
        assertTrue(updated.backwardFsrsDueAt > System.currentTimeMillis())
        assertEquals(0, updated.correctCount)
        assertTrue(updated.fsrsDueAt != 0L)
        assertEquals("", updated.fsrsCardJson)
    }

    @Test
    fun forwardOnlyWordIsHiddenInBackwardModeAndCountedInSkippedBadge() {
        val word = "havrolinet"
        val translation = "eskoralum"
        seedWord(word = word, translation = translation, bidirectional = false)
        setMode(StudyDirectionMode.BACKWARD)
        navigateToDojo()

        composeTestRule.waitUntilNodeExists(hasText(string(R.string.dojo_empty_title)), DEFAULT_TIMEOUT_MS)
        val skippedLabel = string(R.string.dojo_stats_skipped)
        composeTestRule.waitUntilNodeExists(hasText("1 $skippedLabel", substring = true), DEFAULT_TIMEOUT_MS)
    }

    @Test
    fun switchingModeFromBackwardToBidirectionalMidSessionSurfacesPreviouslySkippedForwardWord() {
        val word = "trevonaxil"
        val translation = "quandrelis"
        seedWord(word = word, translation = translation, bidirectional = false)
        setMode(StudyDirectionMode.BACKWARD)
        navigateToDojo()

        val skippedLabel = string(R.string.dojo_stats_skipped)
        composeTestRule.waitUntilNodeExists(hasText(string(R.string.dojo_empty_title)), DEFAULT_TIMEOUT_MS)
        composeTestRule.waitUntilNodeExists(hasText("1 $skippedLabel", substring = true), DEFAULT_TIMEOUT_MS)

        navigateToSettings()
        selectStudyDirectionMode(StudyDirectionMode.BIDIRECTIONAL)
        navigateToDojo()

        composeTestRule.waitUntilNodeExists(hasText(word, substring = true), DEFAULT_TIMEOUT_MS)
        composeTestRule.onAllNodesWithText(skippedLabel, substring = true).assertCountEquals(0)
    }

    @Test
    fun undoAfterBackwardRatingRestoresBackwardColumnsAndReshowsCard() {
        val word = "zelkombrar"
        val translation = "phindorel"
        val forwardDueAt = System.currentTimeMillis() + ONE_DAY_MS
        val backwardDueAt = System.currentTimeMillis() - PAST_OFFSET_MS
        seedWord(
            word = word,
            translation = translation,
            bidirectional = true,
            correctCount = 1,
            fsrsDueAt = forwardDueAt,
            backwardFsrsDueAt = backwardDueAt,
        )
        setMode(StudyDirectionMode.BIDIRECTIONAL)
        navigateToDojo()

        composeTestRule.waitUntilNodeExists(hasText(translation, substring = true), DEFAULT_TIMEOUT_MS)
        composeTestRule.onNodeWithText(string(R.string.learning_show_translation)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.rating_again)).performClick()

        composeTestRule.waitUntilNodeExists(hasText(string(R.string.dojo_empty_title)), DEFAULT_TIMEOUT_MS)
        composeTestRule.onAllNodesWithText("0", useUnmergedTree = true).assertCountEquals(2)

        composeTestRule
            .onNodeWithContentDescription(string(R.string.dojo_undo_content_description))
            .assertIsDisplayed()
            .performClick()

        val expectedMessage =
            targetContext.getString(
                R.string.dojo_undo_confirmation,
                string(R.string.rating_again),
                translation,
            )
        composeTestRule.waitUntilNodeExists(hasText(expectedMessage), DEFAULT_TIMEOUT_MS)

        composeTestRule
            .onAllNodesWithText(translation, substring = true, useUnmergedTree = true)
            .onFirst()
            .assertIsDisplayed()
        listOf(
            R.string.rating_again,
            R.string.rating_hard,
            R.string.rating_good,
            R.string.rating_easy,
        ).forEach { resId -> composeTestRule.onNodeWithText(string(resId)).assertIsDisplayed() }

        val restored = vocabularyByWord(word)
        assertEquals(0, restored.backwardCorrectCount)
        assertEquals(0, restored.backwardIncorrectCount)
        assertEquals(backwardDueAt, restored.backwardFsrsDueAt)
        assertEquals(forwardDueAt, restored.fsrsDueAt)
        assertEquals(1, restored.correctCount)
    }

    @Test
    fun newBidirectionalWordInBidirectionalModeIsIntroducedForwardFirst() {
        val word = "sorqualiven"
        val translation = "abrenthyx"
        seedWord(word = word, translation = translation, bidirectional = true)
        setMode(StudyDirectionMode.BIDIRECTIONAL)
        navigateToDojo()

        composeTestRule.waitUntilNodeExists(hasText(word, substring = true), DEFAULT_TIMEOUT_MS)
        composeTestRule.onNodeWithText(string(R.string.learning_show_translation)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(translation, substring = true).assertIsDisplayed()

        composeTestRule.onNodeWithText(string(R.string.rating_good)).performClick()
        composeTestRule.waitUntilNodeExists(hasText(string(R.string.dojo_empty_title)), DEFAULT_TIMEOUT_MS)

        val updated = vocabularyByWord(word)
        assertEquals(1, updated.correctCount)
        assertTrue(updated.fsrsDueAt > System.currentTimeMillis())
        assertEquals(0, updated.backwardCorrectCount)
        assertTrue(updated.backwardFsrsDueAt > System.currentTimeMillis())
        assertEquals("", updated.backwardFsrsCardJson)
    }

    private fun string(resId: Int) = targetContext.getString(resId)

    private fun navigateToDojo() = navigateTo(R.string.nav_dojo)

    private fun navigateToSettings() = navigateTo(R.string.nav_settings)

    private fun navigateTo(labelResId: Int) {
        val label = targetContext.getString(labelResId)
        composeTestRule.waitUntilNodeExists(hasText(label), DEFAULT_TIMEOUT_MS)
        composeTestRule
            .onNodeWithContentDescription(label, useUnmergedTree = true)
            .performClick()
        composeTestRule.waitForIdle()
    }

    private fun selectStudyDirectionMode(mode: StudyDirectionMode) {
        composeTestRule.onNodeWithText(string(R.string.settings_review_direction_title)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(modeLabel(mode)).performClick()
        composeTestRule.waitForIdle()
    }

    private fun modeLabel(mode: StudyDirectionMode): String =
        when (mode) {
            StudyDirectionMode.FORWARD -> string(R.string.settings_review_direction_forward)
            StudyDirectionMode.BACKWARD -> string(R.string.settings_review_direction_backward)
            StudyDirectionMode.BIDIRECTIONAL -> string(R.string.settings_review_direction_bidirectional)
        }

    private fun seedWord(
        word: String,
        translation: String,
        bidirectional: Boolean = false,
        correctCount: Int = 0,
        fsrsDueAt: Long = 0L,
        backwardFsrsDueAt: Long = 0L,
    ) {
        insertVocabulary(
            VocabularyEntity(
                word = word,
                translation = translation,
                bidirectional = bidirectional,
                correctCount = correctCount,
                fsrsCardJson = "",
                fsrsDueAt = fsrsDueAt,
                backwardFsrsCardJson = "",
                backwardFsrsDueAt = backwardFsrsDueAt,
            ),
        )
    }

    private fun insertVocabulary(entity: VocabularyEntity) {
        runBlocking {
            withContext(Dispatchers.IO) {
                entryPoint().appDatabase().vocabularyDao().insertVocabulary(entity)
            }
        }
    }

    private fun vocabularyByWord(word: String): VocabularyEntity =
        runBlocking {
            withContext(Dispatchers.IO) {
                entryPoint().appDatabase().vocabularyDao().getVocabularyByWord(VocabularyEntity.normalizeWord(word))
            }
        }!!

    private fun setMode(mode: StudyDirectionMode) {
        runBlocking {
            withContext(Dispatchers.IO) {
                preferencesEntryPoint().dayCountersStore().setStudyDirectionMode(mode)
            }
        }
    }

    private fun resetAppState() {
        runBlocking {
            withContext(Dispatchers.IO) {
                val db = entryPoint().appDatabase()
                db.vocabularyDao().deleteAllVocabulary()
                db.undoSnapshotDao().deleteAll()
                preferencesEntryPoint().dayCountersStore().setStudyDirectionMode(StudyDirectionMode.BIDIRECTIONAL)
            }
        }
    }

    private fun entryPoint(): DatabaseEntryPoint =
        EntryPointAccessors.fromApplication(
            targetContext.applicationContext,
            DatabaseEntryPoint::class.java,
        )

    private fun preferencesEntryPoint(): PreferencesEntryPoint =
        EntryPointAccessors.fromApplication(
            targetContext.applicationContext,
            PreferencesEntryPoint::class.java,
        )

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 15_000L
        const val PAST_OFFSET_MS = 60_000L
        const val ONE_DAY_MS = 24 * 60 * 60 * 1000L
    }
}
