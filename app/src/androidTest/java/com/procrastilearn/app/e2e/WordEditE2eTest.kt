package com.procrastilearn.app.e2e

import android.content.Context
import androidx.compose.ui.test.hasContentDescription
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
        resetAppState()
        composeTestRule.dismissOnboardingIfPresent(targetContext)
    }

    @After
    fun afterEach() {
        resetAppState()
    }

    @Test
    fun editingWordAndTranslationPersistsChangesToDatabase() {
        val originalWord = "flarnbicket"
        val originalTranslation = "gloomventra"
        seedWord(word = originalWord, translation = originalTranslation)

        navigateToWordList()
        openEditDialogFor(originalWord)
        replaceFieldText(R.string.add_word_label_word, "flarnbicket-updated")
        replaceFieldText(R.string.add_word_label_translation, "gloomventra-updated")
        clickAction(R.string.action_save)

        assertNull(vocabularyByWord(originalWord))
        val updated = vocabularyByWord("flarnbicket-updated")!!
        assertEquals("gloomventra-updated", updated.translation)
    }

    @Test
    fun cancellingEditDialogDiscardsChanges() {
        val word = "prendolack"
        val translation = "ostrivane"
        seedWord(word = word, translation = translation)

        navigateToWordList()
        openEditDialogFor(word)
        replaceFieldText(R.string.add_word_label_word, "prendolack-changed")
        clickAction(R.string.action_cancel)

        assertNull(vocabularyByWord("prendolack-changed"))
        assertEquals(translation, vocabularyByWord(word)!!.translation)
    }

    @Test
    fun enablingBidirectionalInEditDialogSetsFlagAndSeedsBackwardDueDateWhenAlreadyReviewed() {
        val word = "quindaloop"
        val translation = "brastanix"
        val forwardDueAt = System.currentTimeMillis() + ONE_DAY_MS
        seedWord(word = word, translation = translation, correctCount = 1, fsrsDueAt = forwardDueAt)

        navigateToWordList()
        openEditDialogFor(word)
        composeTestRule.onNode(isToggleable(), useUnmergedTree = true).performClick()
        clickAction(R.string.action_save)

        val updated = vocabularyByWord(word)!!
        assertTrue(updated.bidirectional)
        assertTrue(updated.backwardFsrsDueAt > 0L)
    }

    @Test
    fun disablingBidirectionalInEditDialogClearsFlag() {
        val word = "trevoskin"
        val translation = "mundacrest"
        seedWord(word = word, translation = translation, bidirectional = true)

        navigateToWordList()
        openEditDialogFor(word)
        composeTestRule.onNode(isToggleable(), useUnmergedTree = true).performClick()
        clickAction(R.string.action_save)

        assertFalse(vocabularyByWord(word)!!.bidirectional)
    }

    @Test
    fun customizingReverseOverridesPersistsPromptAndAnswerText() {
        val word = "shalimquor"
        val translation = "ventrabole"
        seedWord(word = word, translation = translation)

        navigateToWordList()
        openEditDialogFor(word)
        composeTestRule.onNode(isToggleable(), useUnmergedTree = true).performClick()
        composeTestRule
            .onNodeWithText(string(R.string.add_word_customize_backward_show))
            .performScrollTo()
            .performClick()
        composeTestRule
            .onNodeWithText(string(R.string.add_word_backward_prompt_label))
            .performScrollTo()
            .performTextInput("What runs?")
        composeTestRule
            .onNodeWithText(string(R.string.add_word_backward_answer_label))
            .performScrollTo()
            .performTextInput(word)
        clickAction(R.string.action_save)

        val updated = vocabularyByWord(word)!!
        assertTrue(updated.bidirectional)
        assertEquals("What runs?", updated.backwardPromptOverride)
        assertEquals(word, updated.backwardAnswerOverride)
    }

    @Test
    fun clearingReverseOverridesOnSaveResetsThemToNull() {
        val word = "nostrivell"
        val translation = "quenthalor"
        seedWord(
            word = word,
            translation = translation,
            bidirectional = true,
            backwardPromptOverride = "Old prompt",
            backwardAnswerOverride = "Old answer",
        )

        navigateToWordList()
        openEditDialogFor(word)
        composeTestRule
            .onNodeWithText(string(R.string.add_word_backward_prompt_label))
            .performScrollTo()
            .performTextClearance()
        composeTestRule
            .onNodeWithText(string(R.string.add_word_backward_answer_label))
            .performScrollTo()
            .performTextClearance()
        clickAction(R.string.action_save)

        val updated = vocabularyByWord(word)!!
        assertTrue(updated.bidirectional)
        assertNull(updated.backwardPromptOverride)
        assertNull(updated.backwardAnswerOverride)
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

    private fun openEditDialogFor(word: String) {
        composeTestRule.waitUntilNodeExists(hasText(word), DEFAULT_TIMEOUT_MS)
        composeTestRule
            .onNodeWithContentDescription(string(R.string.word_list_more_actions), useUnmergedTree = true)
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.action_edit)).performClick()
        composeTestRule.waitUntilNodeExists(hasText(string(R.string.edit_word_title)), DEFAULT_TIMEOUT_MS)
    }

    private fun replaceFieldText(
        labelResId: Int,
        newValue: String,
    ) {
        composeTestRule
            .onNode(hasText(string(labelResId)).and(hasSetTextAction()), useUnmergedTree = true)
            .performTextReplacement(newValue)
    }

    private fun clickAction(actionResId: Int) {
        composeTestRule.onNodeWithText(string(actionResId)).performClick()
        composeTestRule.waitForIdle()
    }

    private fun seedWord(
        word: String,
        translation: String,
        bidirectional: Boolean = false,
        correctCount: Int = 0,
        fsrsDueAt: Long = 0L,
        backwardPromptOverride: String? = null,
        backwardAnswerOverride: String? = null,
    ): Long =
        runBlocking {
            withContext(Dispatchers.IO) {
                entryPoint().appDatabase().vocabularyDao().insertVocabulary(
                    VocabularyEntity(
                        word = word,
                        translation = translation,
                        bidirectional = bidirectional,
                        correctCount = correctCount,
                        fsrsCardJson = "",
                        fsrsDueAt = fsrsDueAt,
                        backwardPromptOverride = backwardPromptOverride,
                        backwardAnswerOverride = backwardAnswerOverride,
                    ),
                )
            }
        }

    private fun vocabularyByWord(word: String): VocabularyEntity? =
        runBlocking {
            withContext(Dispatchers.IO) {
                entryPoint().appDatabase().vocabularyDao().getVocabularyByWord(word)
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
        const val ONE_DAY_MS = 24 * 60 * 60 * 1000L
    }
}
