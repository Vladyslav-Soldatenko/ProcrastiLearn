package com.procrastilearn.app.e2e

import android.content.Context
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.procrastilearn.app.MainActivity
import com.procrastilearn.app.R
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WordListSearchScopeE2eTest {
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
    fun defaultScopeMatchesBothWordAndTranslationOnFreshInstall() {
        val wordMatchId = targetContext.seedWord(word = "glimmerquat", translation = "shiny-thing")
        val translationMatchId = targetContext.seedWord(word = "sunderpike", translation = "arcanewhisper")

        composeTestRule.navigateToWordList(targetContext)
        composeTestRule.typeInWordListSearch("glimmer")
        composeTestRule.waitUntilNodeExists(hasTestTag(wordListItemTag(wordMatchId)), E2E_TIMEOUT_MS)

        composeTestRule.onNodeWithTag("word_list_search_field").performClick()
        composeTestRule.clearWordListSearch()
        composeTestRule.typeInWordListSearch("arcanewhisper")
        composeTestRule.waitUntilNodeExists(hasTestTag(wordListItemTag(translationMatchId)), E2E_TIMEOUT_MS)
    }

    @Test
    fun openingTheScopeDialogShowsSearchInTitleAndBothOptions() {
        composeTestRule.navigateToWordList(targetContext)

        composeTestRule.openWordListSearchScope(targetContext)

        composeTestRule.onNodeWithText(targetContext.string(R.string.word_list_search_scope_title)).assertExists()
        composeTestRule.onNodeWithText(targetContext.string(R.string.word_list_search_scope_option_word)).assertExists()
        composeTestRule
            .onNodeWithText(targetContext.string(R.string.word_list_search_scope_option_translation))
            .assertExists()
    }

    @Test
    fun applyingWordOnlyScopeExcludesTranslationMatchesFromSearchResults() {
        val translationOnlyMatchId = targetContext.seedWord(word = "brellathorn", translation = "wildberrynectar")
        composeTestRule.navigateToWordList(targetContext)
        composeTestRule.openWordListSearchScope(targetContext)
        composeTestRule.onNodeWithTag("word_list_search_scope_translation_checkbox").performClick()
        composeTestRule.onNodeWithText(targetContext.string(R.string.action_apply)).performClick()

        composeTestRule.typeInWordListSearch("wildberrynectar")

        composeTestRule.onNodeWithTag(wordListItemTag(translationOnlyMatchId)).assertDoesNotExist()
    }

    @Test
    fun applyingTranslationOnlyScopeExcludesWordMatchesFromSearchResults() {
        val wordOnlyMatchId = targetContext.seedWord(word = "molvantree", translation = "unrelated-meaning")
        composeTestRule.navigateToWordList(targetContext)
        composeTestRule.openWordListSearchScope(targetContext)
        composeTestRule.onNodeWithTag("word_list_search_scope_word_checkbox").performClick()
        composeTestRule.onNodeWithText(targetContext.string(R.string.action_apply)).performClick()

        composeTestRule.typeInWordListSearch("molvantree")

        composeTestRule.onNodeWithTag(wordListItemTag(wordOnlyMatchId)).assertDoesNotExist()
    }

    @Test
    fun cancellingTheScopeDialogDoesNotChangeActiveSearchResults() {
        val translationOnlyMatchId = targetContext.seedWord(word = "pikewander", translation = "duskember")
        composeTestRule.navigateToWordList(targetContext)
        composeTestRule.typeInWordListSearch("duskember")
        composeTestRule.waitUntilNodeExists(hasTestTag(wordListItemTag(translationOnlyMatchId)), E2E_TIMEOUT_MS)

        composeTestRule.openWordListSearchScope(targetContext)
        composeTestRule.onNodeWithTag("word_list_search_scope_translation_checkbox").performClick()
        composeTestRule.onNodeWithText(targetContext.string(R.string.action_cancel)).performClick()

        composeTestRule.onNodeWithTag(wordListItemTag(translationOnlyMatchId)).assertExists()
    }

    @Test
    fun reopeningTheDialogAfterCancelShowsTheLastAppliedScopeNotTheDiscardedDraft() {
        composeTestRule.navigateToWordList(targetContext)
        composeTestRule.openWordListSearchScope(targetContext)
        composeTestRule.onNodeWithTag("word_list_search_scope_translation_checkbox").performClick()
        composeTestRule.onNodeWithText(targetContext.string(R.string.action_cancel)).performClick()

        composeTestRule.openWordListSearchScope(targetContext)

        composeTestRule.onNodeWithTag("word_list_search_scope_translation_checkbox").assertIsOn()
    }

    @Test
    fun dismissingTheDialogViaBackPressBehavesTheSameAsCancel() {
        composeTestRule.navigateToWordList(targetContext)
        composeTestRule.openWordListSearchScope(targetContext)
        composeTestRule.onNodeWithTag("word_list_search_scope_word_checkbox").performClick()

        Espresso.pressBack()
        composeTestRule.waitForIdle()

        composeTestRule.openWordListSearchScope(targetContext)
        composeTestRule.onNodeWithTag("word_list_search_scope_word_checkbox").assertIsOn()
    }

    @Test
    fun wordListSearchScopePersistsAcrossActivityRecreation() {
        val translationOnlyMatchId = targetContext.seedWord(word = "corvantiel", translation = "emberfallecho")
        composeTestRule.navigateToWordList(targetContext)
        composeTestRule.openWordListSearchScope(targetContext)
        composeTestRule.onNodeWithTag("word_list_search_scope_translation_checkbox").performClick()
        composeTestRule.onNodeWithText(targetContext.string(R.string.action_apply)).performClick()

        composeTestRule.recreateActivity(composeTestRule.activity)
        composeTestRule.dismissOnboardingIfPresent(targetContext)
        composeTestRule.navigateToWordList(targetContext)
        composeTestRule.typeInWordListSearch("emberfallecho")

        composeTestRule.onNodeWithTag(wordListItemTag(translationOnlyMatchId)).assertDoesNotExist()
    }

    @Test
    fun applyingBothScopeAgainRestoresDefaultBehavior() {
        val translationOnlyMatchId = targetContext.seedWord(word = "sableharrow", translation = "windlornsong")
        composeTestRule.navigateToWordList(targetContext)
        composeTestRule.openWordListSearchScope(targetContext)
        composeTestRule.onNodeWithTag("word_list_search_scope_translation_checkbox").performClick()
        composeTestRule.onNodeWithText(targetContext.string(R.string.action_apply)).performClick()

        composeTestRule.openWordListSearchScope(targetContext)
        composeTestRule.onNodeWithTag("word_list_search_scope_translation_checkbox").performClick()
        composeTestRule.onNodeWithText(targetContext.string(R.string.action_apply)).performClick()

        composeTestRule.typeInWordListSearch("windlornsong")
        composeTestRule.waitUntilNodeExists(hasTestTag(wordListItemTag(translationOnlyMatchId)), E2E_TIMEOUT_MS)
    }

    @Test
    fun existingWordMatchingSearchFlowsAreUnaffectedByTheNewScopeFeature() {
        val matchingId = targetContext.seedWord(word = "quorlinfast", translation = "translation-a")
        val otherId = targetContext.seedWord(word = "thornapple", translation = "translation-b")

        composeTestRule.navigateToWordList(targetContext)
        composeTestRule.waitUntilNodeExists(hasTestTag(wordListItemTag(matchingId)), E2E_TIMEOUT_MS)
        composeTestRule.onNodeWithTag(wordListItemTag(otherId)).assertExists()

        composeTestRule.typeInWordListSearch("quorlin")

        composeTestRule.onNodeWithTag(wordListItemTag(matchingId)).assertExists()
        composeTestRule.waitUntilNodeGone(hasTestTag(wordListItemTag(otherId)), E2E_TIMEOUT_MS)
    }

    private fun resetState() {
        targetContext.resetE2eDatabase()
        targetContext.resetWordListSearchScope()
    }
}
