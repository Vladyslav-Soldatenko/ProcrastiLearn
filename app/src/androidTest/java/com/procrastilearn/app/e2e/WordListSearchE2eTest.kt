package com.procrastilearn.app.e2e

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.procrastilearn.app.MainActivity
import com.procrastilearn.app.R
import com.procrastilearn.app.data.local.entity.VocabularyEntity
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WordListSearchE2eTest {
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
    fun typingQueryFiltersListToMatchingWordsOnly() {
        val matchingId = seedWord(word = "glimmerquat", translation = "shiny-thing")
        val otherId = seedWord(word = "sunderpike", translation = "broken-spear")

        composeTestRule.navigateToWordList(targetContext)
        composeTestRule.waitUntilNodeExists(hasTestTag(wordListItemTag(matchingId)), E2E_TIMEOUT_MS)
        composeTestRule.onNodeWithTag(wordListItemTag(otherId)).assertExists()

        composeTestRule.typeInWordListSearch("glimmer")

        composeTestRule.onNodeWithTag(wordListItemTag(matchingId)).assertExists()
        composeTestRule.waitUntilNodeGone(hasTestTag(wordListItemTag(otherId)), E2E_TIMEOUT_MS)
    }

    @Test
    fun searchIsCaseInsensitiveAndMatchesSubstringAnywhereInWord() {
        val id = seedWord(word = "corvantiel", translation = "translation-a")

        composeTestRule.navigateToWordList(targetContext)
        composeTestRule.typeInWordListSearch("VANTI")

        composeTestRule.onNodeWithTag(wordListItemTag(id)).assertExists()
    }

    @Test
    fun queryWithNoMatchesShowsEmptyStateAndHidesAllWords() {
        val id = seedWord(word = "brellathorn", translation = "translation-b")

        composeTestRule.navigateToWordList(targetContext)
        composeTestRule.typeInWordListSearch("xyznotfound")

        composeTestRule.waitUntilNodeExists(
            hasText(targetContext.string(R.string.word_list_search_no_results)),
            E2E_TIMEOUT_MS,
        )
        composeTestRule.onNodeWithTag(wordListItemTag(id)).assertDoesNotExist()
    }

    @Test
    fun clearingSearchQueryRestoresFullWordList() {
        val idA = seedWord(word = "molvantree", translation = "translation-a")
        val idB = seedWord(word = "pikewander", translation = "translation-b")

        composeTestRule.navigateToWordList(targetContext)
        composeTestRule.typeInWordListSearch("molvan")
        composeTestRule.waitUntilNodeGone(hasTestTag(wordListItemTag(idB)), E2E_TIMEOUT_MS)

        composeTestRule.clearWordListSearch()

        composeTestRule.waitUntilNodeExists(hasTestTag(wordListItemTag(idA)), E2E_TIMEOUT_MS)
        composeTestRule.waitUntilNodeExists(hasTestTag(wordListItemTag(idB)), E2E_TIMEOUT_MS)
    }

    private fun seedWord(
        word: String,
        translation: String,
    ): Long =
        targetContext.insertVocabulary(
            VocabularyEntity(word = word, translation = translation, fsrsCardJson = ""),
        )
}
