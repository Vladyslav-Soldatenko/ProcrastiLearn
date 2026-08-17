package com.procrastilearn.app.e2e

import android.content.Context
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
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
    fun deletingSingleSelectedWordRemovesItFromListAndDatabase() {
        val word = "quorvintal"
        val id = seedWord(word = word, translation = "flembercot")

        navigateToWordList()
        longPressItem(id)
        openSelectionMenuAndTap(R.string.action_delete)
        confirmBulkDeleteDialog()

        composeTestRule.waitUntilNodeGone(hasTestTag(itemTag(id)), DEFAULT_TIMEOUT_MS)
        assertNull(vocabularyById(id))
    }

    @Test
    fun bulkDeleteRemovesOnlySelectedWordsAndExitsSelectionMode() {
        val wordA = "plindorash"
        val wordB = "castervine"
        val wordKept = "molthingear"
        val idA = seedWord(word = wordA, translation = "translation-a")
        val idB = seedWord(word = wordB, translation = "translation-b")
        val idKept = seedWord(word = wordKept, translation = "translation-kept")

        navigateToWordList()
        longPressItem(idA)
        clickItem(idB)
        openSelectionMenuAndTap(R.string.action_delete)
        confirmBulkDeleteDialog()

        composeTestRule.waitUntilNodeGone(hasTestTag(itemTag(idA)), DEFAULT_TIMEOUT_MS)

        assertNull(vocabularyById(idA))
        assertNull(vocabularyById(idB))
        assertNotNull(vocabularyById(idKept))

        composeTestRule
            .onNodeWithText(string(R.string.word_list_title))
            .assertExists()
        composeTestRule.onNodeWithTag(itemTag(idKept)).assertExists()
    }

    @Test
    fun cancelingBulkDeleteDialogKeepsSelectedWords() {
        val word = "haventrolm"
        val id = seedWord(word = word, translation = "sondrifelt")

        navigateToWordList()
        longPressItem(id)
        openSelectionMenuAndTap(R.string.action_delete)

        composeTestRule.waitUntilNodeExists(
            hasText(string(R.string.word_list_bulk_delete_confirm_title)),
            DEFAULT_TIMEOUT_MS,
        )
        composeTestRule.onNodeWithText(string(R.string.action_cancel)).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(itemTag(id)).assertExists()
        assertNotNull(vocabularyById(id))
    }

    private fun string(resId: Int) = targetContext.getString(resId)

    private fun navigateToWordList() {
        val addWordLabel = string(R.string.nav_add_word)
        composeTestRule.waitUntilNodeExists(hasText(addWordLabel), DEFAULT_TIMEOUT_MS)
        composeTestRule
            .onNodeWithContentDescription(addWordLabel, useUnmergedTree = true)
            .performClick()
        composeTestRule.waitForIdle()

        val viewListLabel = string(R.string.action_view_list)
        composeTestRule.waitUntilNodeExists(hasContentDescription(viewListLabel), DEFAULT_TIMEOUT_MS)
        composeTestRule.onNodeWithContentDescription(viewListLabel).performClick()
        composeTestRule.waitForIdle()
    }

    private fun longPressItem(id: Long) {
        composeTestRule.waitUntilNodeExists(hasTestTag(itemTag(id)), DEFAULT_TIMEOUT_MS)
        composeTestRule.onNodeWithTag(itemTag(id)).performTouchInput { longClick() }
        composeTestRule.waitForIdle()
    }

    private fun clickItem(id: Long) {
        composeTestRule.onNodeWithTag(itemTag(id)).performClick()
        composeTestRule.waitForIdle()
    }

    private fun itemTag(id: Long) = "word_list_item_$id"

    private fun openSelectionMenuAndTap(actionResId: Int) {
        composeTestRule
            .onNodeWithContentDescription(string(R.string.word_list_more_actions_selection))
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(actionResId)).performClick()
        composeTestRule.waitForIdle()
    }

    private fun confirmBulkDeleteDialog() {
        composeTestRule.waitUntilNodeExists(
            hasText(string(R.string.word_list_bulk_delete_confirm_title)),
            DEFAULT_TIMEOUT_MS,
        )
        composeTestRule.onNodeWithText(string(R.string.action_delete)).performClick()
        composeTestRule.waitForIdle()
    }

    private fun seedWord(
        word: String,
        translation: String,
    ): Long =
        runBlocking {
            withContext(Dispatchers.IO) {
                entryPoint().appDatabase().vocabularyDao().insertVocabulary(
                    VocabularyEntity(
                        word = word,
                        translation = translation,
                        fsrsCardJson = "",
                    ),
                )
            }
        }

    private fun vocabularyById(id: Long): VocabularyEntity? =
        runBlocking {
            withContext(Dispatchers.IO) {
                entryPoint().appDatabase().vocabularyDao().getVocabularyById(id)
            }
        }

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
    }
}
