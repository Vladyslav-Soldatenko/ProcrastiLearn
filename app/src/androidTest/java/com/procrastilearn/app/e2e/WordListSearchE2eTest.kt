package com.procrastilearn.app.e2e

import android.content.Context
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
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

@RunWith(AndroidJUnit4::class)
class WordListSearchE2eTest {
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
    fun typingQueryFiltersListToMatchingWordsOnly() {
        val matchingId = seedWord(word = "glimmerquat", translation = "shiny-thing")
        val otherId = seedWord(word = "sunderpike", translation = "broken-spear")

        navigateToWordList()
        composeTestRule.onNodeWithTag(itemTag(matchingId)).assertExists()
        composeTestRule.onNodeWithTag(itemTag(otherId)).assertExists()

        typeInSearchField("glimmer")

        composeTestRule.onNodeWithTag(itemTag(matchingId)).assertExists()
        composeTestRule.waitUntilNodeGone(hasTestTag(itemTag(otherId)), DEFAULT_TIMEOUT_MS)
    }

    @Test
    fun searchIsCaseInsensitiveAndMatchesSubstringAnywhereInWord() {
        val id = seedWord(word = "corvantiel", translation = "translation-a")

        navigateToWordList()
        typeInSearchField("VANTI")

        composeTestRule.onNodeWithTag(itemTag(id)).assertExists()
    }

    @Test
    fun queryWithNoMatchesShowsEmptyStateAndHidesAllWords() {
        val id = seedWord(word = "brellathorn", translation = "translation-b")

        navigateToWordList()
        typeInSearchField("xyznotfound")

        composeTestRule.waitUntilNodeExists(
            hasText(string(R.string.word_list_search_no_results)),
            DEFAULT_TIMEOUT_MS,
        )
        composeTestRule.onNodeWithTag(itemTag(id)).assertDoesNotExist()
    }

    @Test
    fun clearingSearchQueryRestoresFullWordList() {
        val idA = seedWord(word = "molvantree", translation = "translation-a")
        val idB = seedWord(word = "pikewander", translation = "translation-b")

        navigateToWordList()
        typeInSearchField("molvan")
        composeTestRule.waitUntilNodeGone(hasTestTag(itemTag(idB)), DEFAULT_TIMEOUT_MS)

        composeTestRule.onNodeWithTag("word_list_search_field").performTextClearance()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(itemTag(idA)).assertExists()
        composeTestRule.waitUntilNodeExists(hasTestTag(itemTag(idB)), DEFAULT_TIMEOUT_MS)
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

    private fun typeInSearchField(query: String) {
        composeTestRule.waitUntilNodeExists(hasTestTag("word_list_search_field"), DEFAULT_TIMEOUT_MS)
        composeTestRule.onNodeWithTag("word_list_search_field").performTextInput(query)
        composeTestRule.waitForIdle()
    }

    private fun itemTag(id: Long) = "word_list_item_$id"

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
