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
import com.procrastilearn.app.domain.model.StudyDirectionMode
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
        resetState()
        composeTestRule.dismissOnboardingIfPresent(targetContext)
    }

    @After
    fun afterEach() {
        resetState()
    }

    @Test
    fun backwardDueBidirectionalCardShowsSwappedWordAndRatingUpdatesBackwardColumnsOnly() {
        val word = "plendarosk"
        val translation = "morvassilk"
        val forwardDueAt = System.currentTimeMillis() + ONE_DAY_MS
        val backwardDueAt = System.currentTimeMillis() - PAST_OFFSET_MS
        targetContext.seedWord(
            word = word,
            translation = translation,
            bidirectional = true,
            correctCount = 1,
            fsrsDueAt = forwardDueAt,
            backwardFsrsDueAt = backwardDueAt,
        )
        targetContext.setStudyDirectionMode(StudyDirectionMode.BIDIRECTIONAL)
        composeTestRule.navigateTo(targetContext, R.string.nav_dojo)

        composeTestRule.waitUntilNodeExists(hasText(translation, substring = true), E2E_TIMEOUT_MS)
        composeTestRule.onNodeWithText(targetContext.string(R.string.learning_show_translation)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(word, substring = true).assertIsDisplayed()

        composeTestRule.onNodeWithText(targetContext.string(R.string.rating_good)).performClick()
        composeTestRule.waitUntilNodeExists(hasText(targetContext.string(R.string.dojo_empty_title)), E2E_TIMEOUT_MS)

        val updated = requireNotNull(targetContext.vocabularyByWord(word))
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
        targetContext.seedWord(word = word, translation = translation, bidirectional = true)
        targetContext.setStudyDirectionMode(StudyDirectionMode.BACKWARD)
        composeTestRule.navigateTo(targetContext, R.string.nav_dojo)

        composeTestRule.waitUntilNodeExists(hasText(translation, substring = true), E2E_TIMEOUT_MS)
        composeTestRule.onNodeWithText(targetContext.string(R.string.learning_show_translation)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(word, substring = true).assertIsDisplayed()

        composeTestRule.onNodeWithText(targetContext.string(R.string.rating_good)).performClick()
        composeTestRule.waitUntilNodeExists(hasText(targetContext.string(R.string.dojo_empty_title)), E2E_TIMEOUT_MS)
        composeTestRule.onAllNodesWithText("0", useUnmergedTree = true).assertCountEquals(2)

        val updated = requireNotNull(targetContext.vocabularyByWord(word))
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
        targetContext.seedWord(word = word, translation = translation, bidirectional = false)
        targetContext.setStudyDirectionMode(StudyDirectionMode.BACKWARD)
        composeTestRule.navigateTo(targetContext, R.string.nav_dojo)

        composeTestRule.waitUntilNodeExists(hasText(targetContext.string(R.string.dojo_empty_title)), E2E_TIMEOUT_MS)
        val skippedLabel = targetContext.string(R.string.dojo_stats_skipped)
        composeTestRule.waitUntilNodeExists(hasText("1 $skippedLabel", substring = true), E2E_TIMEOUT_MS)
    }

    @Test
    fun switchingModeFromBackwardToBidirectionalMidSessionSurfacesPreviouslySkippedForwardWord() {
        val word = "trevonaxil"
        val translation = "quandrelis"
        targetContext.seedWord(word = word, translation = translation, bidirectional = false)
        targetContext.setStudyDirectionMode(StudyDirectionMode.BACKWARD)
        composeTestRule.navigateTo(targetContext, R.string.nav_dojo)

        val skippedLabel = targetContext.string(R.string.dojo_stats_skipped)
        composeTestRule.waitUntilNodeExists(hasText(targetContext.string(R.string.dojo_empty_title)), E2E_TIMEOUT_MS)
        composeTestRule.waitUntilNodeExists(hasText("1 $skippedLabel", substring = true), E2E_TIMEOUT_MS)

        composeTestRule.navigateTo(targetContext, R.string.nav_settings)
        composeTestRule.selectStudyDirectionMode(targetContext, StudyDirectionMode.BIDIRECTIONAL)
        composeTestRule.navigateTo(targetContext, R.string.nav_dojo)

        composeTestRule.waitUntilNodeExists(hasText(word, substring = true), E2E_TIMEOUT_MS)
        composeTestRule.onAllNodesWithText(skippedLabel, substring = true).assertCountEquals(0)
    }

    @Test
    fun undoAfterBackwardRatingRestoresBackwardColumnsAndReshowsCard() {
        val word = "zelkombrar"
        val translation = "phindorel"
        val forwardDueAt = System.currentTimeMillis() + ONE_DAY_MS
        val backwardDueAt = System.currentTimeMillis() - PAST_OFFSET_MS
        targetContext.seedWord(
            word = word,
            translation = translation,
            bidirectional = true,
            correctCount = 1,
            fsrsDueAt = forwardDueAt,
            backwardFsrsDueAt = backwardDueAt,
        )
        targetContext.setStudyDirectionMode(StudyDirectionMode.BIDIRECTIONAL)
        composeTestRule.navigateTo(targetContext, R.string.nav_dojo)

        composeTestRule.waitUntilNodeExists(hasText(translation, substring = true), E2E_TIMEOUT_MS)
        composeTestRule.onNodeWithText(targetContext.string(R.string.learning_show_translation)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(targetContext.string(R.string.rating_again)).performClick()

        composeTestRule.waitUntilNodeExists(hasText(targetContext.string(R.string.dojo_empty_title)), E2E_TIMEOUT_MS)
        composeTestRule.onAllNodesWithText("0", useUnmergedTree = true).assertCountEquals(2)

        composeTestRule
            .onNodeWithContentDescription(targetContext.string(R.string.dojo_undo_content_description))
            .assertIsDisplayed()
            .performClick()

        val expectedMessage =
            targetContext.getString(
                R.string.dojo_undo_confirmation,
                targetContext.string(R.string.rating_again),
                translation,
            )
        composeTestRule.waitUntilNodeExists(hasText(expectedMessage), E2E_TIMEOUT_MS)

        composeTestRule
            .onAllNodesWithText(translation, substring = true, useUnmergedTree = true)
            .onFirst()
            .assertIsDisplayed()
        listOf(
            R.string.rating_again,
            R.string.rating_hard,
            R.string.rating_good,
            R.string.rating_easy,
        ).forEach { resId -> composeTestRule.onNodeWithText(targetContext.string(resId)).assertIsDisplayed() }

        val restored = requireNotNull(targetContext.vocabularyByWord(word))
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
        targetContext.seedWord(word = word, translation = translation, bidirectional = true)
        targetContext.setStudyDirectionMode(StudyDirectionMode.BIDIRECTIONAL)
        composeTestRule.navigateTo(targetContext, R.string.nav_dojo)

        composeTestRule.waitUntilNodeExists(hasText(word, substring = true), E2E_TIMEOUT_MS)
        composeTestRule.onNodeWithText(targetContext.string(R.string.learning_show_translation)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(translation, substring = true).assertIsDisplayed()

        composeTestRule.onNodeWithText(targetContext.string(R.string.rating_good)).performClick()
        composeTestRule.waitUntilNodeExists(hasText(targetContext.string(R.string.dojo_empty_title)), E2E_TIMEOUT_MS)

        val updated = requireNotNull(targetContext.vocabularyByWord(word))
        assertEquals(1, updated.correctCount)
        assertTrue(updated.fsrsDueAt > System.currentTimeMillis())
        assertEquals(0, updated.backwardCorrectCount)
        assertTrue(updated.backwardFsrsDueAt > System.currentTimeMillis())
        assertEquals("", updated.backwardFsrsCardJson)
    }

    private fun resetState() {
        targetContext.resetE2eDatabase()
        targetContext.setStudyDirectionMode(StudyDirectionMode.BIDIRECTIONAL)
    }

    private companion object {
        const val PAST_OFFSET_MS = 60_000L
        const val ONE_DAY_MS = 24 * 60 * 60 * 1000L
    }
}
