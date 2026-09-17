package com.procrastilearn.app.e2e

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.procrastilearn.app.MainActivity
import com.procrastilearn.app.R
import com.procrastilearn.app.data.local.entity.VocabularyEntity
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WordListBulkDeleteE2eTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private val targetContext get() = composeTestRule.activity

    @Before
    fun beforeEach() {
        targetContext.resetE2eDatabase()
        composeTestRule.dismissOnboardingIfPresent(targetContext)
    }

    @After
    fun afterEach() {
        targetContext.resetE2eDatabase()
    }

    @Test
    fun deletingSingleSelectedWordRemovesItFromListAndDatabase() {
        val word = "quorvintal"
        val id = seedWord(word = word, translation = "flembercot")

        composeTestRule.navigateToWordList(targetContext)
        composeTestRule.longPressWordListItem(id)
        composeTestRule.openWordListSelectionMenuAndTap(targetContext, R.string.action_delete)
        composeTestRule.confirmWordListBulkDelete(targetContext)

        composeTestRule.waitUntilNodeGone(hasTestTag(wordListItemTag(id)), E2E_TIMEOUT_MS)
        assertNull(targetContext.vocabularyById(id))
    }

    @Test
    fun bulkDeleteRemovesOnlySelectedWordsAndExitsSelectionMode() {
        val wordA = "plindorash"
        val wordB = "castervine"
        val wordKept = "molthingear"
        val idA = seedWord(word = wordA, translation = "translation-a")
        val idB = seedWord(word = wordB, translation = "translation-b")
        val idKept = seedWord(word = wordKept, translation = "translation-kept")

        composeTestRule.navigateToWordList(targetContext)
        composeTestRule.longPressWordListItem(idA)
        composeTestRule.clickWordListItem(idB)
        composeTestRule.openWordListSelectionMenuAndTap(targetContext, R.string.action_delete)
        composeTestRule.confirmWordListBulkDelete(targetContext)

        composeTestRule.waitUntilNodeGone(hasTestTag(wordListItemTag(idA)), E2E_TIMEOUT_MS)

        assertNull(targetContext.vocabularyById(idA))
        assertNull(targetContext.vocabularyById(idB))
        assertNotNull(targetContext.vocabularyById(idKept))

        composeTestRule
            .onNodeWithText(targetContext.string(R.string.word_list_title))
            .assertExists()
        composeTestRule.onNodeWithTag(wordListItemTag(idKept)).assertExists()
    }

    @Test
    fun cancelingBulkDeleteDialogKeepsSelectedWords() {
        val word = "haventrolm"
        val id = seedWord(word = word, translation = "sondrifelt")

        composeTestRule.navigateToWordList(targetContext)
        composeTestRule.longPressWordListItem(id)
        composeTestRule.openWordListSelectionMenuAndTap(targetContext, R.string.action_delete)

        composeTestRule.waitUntilNodeExists(
            hasText(targetContext.string(R.string.word_list_bulk_delete_confirm_title)),
            E2E_TIMEOUT_MS,
        )
        composeTestRule.onNodeWithText(targetContext.string(R.string.action_cancel)).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(wordListItemTag(id)).assertExists()
        assertNotNull(targetContext.vocabularyById(id))
    }

    private fun seedWord(
        word: String,
        translation: String,
    ): Long =
        targetContext.insertVocabulary(
            VocabularyEntity(word = word, translation = translation, fsrsCardJson = ""),
        )
}
