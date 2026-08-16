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
import com.procrastilearn.app.di.PreferencesEntryPoint
import com.procrastilearn.app.domain.model.StudyDirectionMode
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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
        resetAppState()
        composeTestRule.dismissOnboardingIfPresent(targetContext)
    }

    @After
    fun afterEach() {
        resetAppState()
    }

    @Test
    fun enablingBidirectionalOnSelectedWordSetsFlagAndSeedsBackwardDueDateWhenAlreadyReviewed() {
        val word = "glimmerquat"
        val translation = "twillendor"
        val forwardDueAt = System.currentTimeMillis() + ONE_DAY_MS
        val id = seedWord(word = word, translation = translation, correctCount = 1, fsrsDueAt = forwardDueAt)

        navigateToWordList()
        longPressItem(id)
        openSelectionMenuAndTap(R.string.word_list_bulk_bidirectional_enable)

        val updated = vocabularyByWord(word)
        assertTrue(updated.bidirectional)
        assertTrue(updated.backwardFsrsDueAt > 0L)
    }

    @Test
    fun disablingBidirectionalOnSelectedWordShowsConfirmDialogAndClearsFlagOnConfirm() {
        val word = "sunderpike"
        val translation = "molvantree"
        val id = seedWord(word = word, translation = translation, bidirectional = true)

        navigateToWordList()
        longPressItem(id)
        openSelectionMenuAndTap(R.string.word_list_bulk_bidirectional_disable)

        composeTestRule.waitUntilNodeExists(
            hasText(string(R.string.word_list_bulk_forward_only_confirm_title)),
            DEFAULT_TIMEOUT_MS,
        )
        composeTestRule.onNodeWithText(string(R.string.action_continue)).performClick()
        composeTestRule.waitForIdle()

        val updated = vocabularyByWord(word)
        assertFalse(updated.bidirectional)
    }

    @Test
    fun bulkTestBothDirectionsAppliesToMultipleSelectedWords() {
        val wordA = "corvantiel"
        val wordB = "brellathorn"
        val idA = seedWord(word = wordA, translation = "translation-a")
        val idB = seedWord(word = wordB, translation = "translation-b")

        navigateToWordList()
        longPressItem(idA)
        clickItem(idB)
        openSelectionMenuAndTap(R.string.word_list_bulk_bidirectional_enable)

        assertTrue(vocabularyByWord(wordA).bidirectional)
        assertTrue(vocabularyByWord(wordB).bidirectional)
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

    private fun seedWord(
        word: String,
        translation: String,
        bidirectional: Boolean = false,
        correctCount: Int = 0,
        fsrsDueAt: Long = 0L,
    ): Long =
        runBlocking {
            withContext(Dispatchers.IO) {
                val dao = entryPoint().appDatabase().vocabularyDao()
                dao.insertVocabulary(
                    VocabularyEntity(
                        word = word,
                        translation = translation,
                        bidirectional = bidirectional,
                        correctCount = correctCount,
                        fsrsCardJson = "",
                        fsrsDueAt = fsrsDueAt,
                        position = dao.getMaxPosition() + 1,
                    ),
                )
            }
        }

    private fun vocabularyByWord(word: String): VocabularyEntity =
        runBlocking {
            withContext(Dispatchers.IO) {
                entryPoint().appDatabase().vocabularyDao().getVocabularyByWord(word)
            }
        }!!

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
        const val ONE_DAY_MS = 24 * 60 * 60 * 1000L
    }
}
