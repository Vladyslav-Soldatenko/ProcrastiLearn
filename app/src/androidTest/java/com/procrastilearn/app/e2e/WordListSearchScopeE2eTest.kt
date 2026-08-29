package com.procrastilearn.app.e2e

import android.content.Context
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.procrastilearn.app.MainActivity
import com.procrastilearn.app.R
import com.procrastilearn.app.data.local.entity.VocabularyEntity
import com.procrastilearn.app.di.DatabaseEntryPoint
import com.procrastilearn.app.di.PreferencesEntryPoint
import com.procrastilearn.app.domain.model.SearchScope
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
class WordListSearchScopeE2eTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var targetContext: Context

    private var nextPosition = 1L

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
    fun defaultScopeMatchesBothWordAndTranslationOnFreshInstall() {
        val wordMatchId = seedWord(word = "glimmerquat", translation = "shiny-thing")
        val translationMatchId = seedWord(word = "sunderpike", translation = "arcanewhisper")

        navigateToWordList()
        typeInSearchField("glimmer")
        composeTestRule.waitUntilNodeExists(hasTestTag(itemTag(wordMatchId)), DEFAULT_TIMEOUT_MS)

        composeTestRule.onNodeWithTag("word_list_search_field").performClick()
        clearSearchField()
        typeInSearchField("arcanewhisper")
        composeTestRule.waitUntilNodeExists(hasTestTag(itemTag(translationMatchId)), DEFAULT_TIMEOUT_MS)
    }

    @Test
    fun openingTheScopeDialogShowsSearchInTitleAndBothOptions() {
        navigateToWordList()

        openScopeDialog()

        composeTestRule.onNodeWithText(string(R.string.word_list_search_scope_title)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.word_list_search_scope_option_word)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.word_list_search_scope_option_translation)).assertExists()
    }

    @Test
    fun applyingWordOnlyScopeExcludesTranslationMatchesFromSearchResults() {
        val translationOnlyMatchId = seedWord(word = "brellathorn", translation = "wildberrynectar")
        navigateToWordList()
        openScopeDialog()
        composeTestRule.onNodeWithTag("word_list_search_scope_translation_checkbox").performClick()
        composeTestRule.onNodeWithText(string(R.string.action_apply)).performClick()

        typeInSearchField("wildberrynectar")

        composeTestRule.onNodeWithTag(itemTag(translationOnlyMatchId)).assertDoesNotExist()
    }

    @Test
    fun applyingTranslationOnlyScopeExcludesWordMatchesFromSearchResults() {
        val wordOnlyMatchId = seedWord(word = "molvantree", translation = "unrelated-meaning")
        navigateToWordList()
        openScopeDialog()
        composeTestRule.onNodeWithTag("word_list_search_scope_word_checkbox").performClick()
        composeTestRule.onNodeWithText(string(R.string.action_apply)).performClick()

        typeInSearchField("molvantree")

        composeTestRule.onNodeWithTag(itemTag(wordOnlyMatchId)).assertDoesNotExist()
    }

    @Test
    fun cancellingTheScopeDialogDoesNotChangeActiveSearchResults() {
        val translationOnlyMatchId = seedWord(word = "pikewander", translation = "duskember")
        navigateToWordList()
        typeInSearchField("duskember")
        composeTestRule.waitUntilNodeExists(hasTestTag(itemTag(translationOnlyMatchId)), DEFAULT_TIMEOUT_MS)

        openScopeDialog()
        composeTestRule.onNodeWithTag("word_list_search_scope_translation_checkbox").performClick()
        composeTestRule.onNodeWithText(string(R.string.action_cancel)).performClick()

        composeTestRule.onNodeWithTag(itemTag(translationOnlyMatchId)).assertExists()
    }

    @Test
    fun reopeningTheDialogAfterCancelShowsTheLastAppliedScopeNotTheDiscardedDraft() {
        navigateToWordList()
        openScopeDialog()
        composeTestRule.onNodeWithTag("word_list_search_scope_translation_checkbox").performClick()
        composeTestRule.onNodeWithText(string(R.string.action_cancel)).performClick()

        openScopeDialog()

        composeTestRule.onNodeWithTag("word_list_search_scope_translation_checkbox").assertIsOn()
    }

    @Test
    fun dismissingTheDialogViaBackPressBehavesTheSameAsCancel() {
        navigateToWordList()
        openScopeDialog()
        composeTestRule.onNodeWithTag("word_list_search_scope_word_checkbox").performClick()

        Espresso.pressBack()
        composeTestRule.waitForIdle()

        openScopeDialog()
        composeTestRule.onNodeWithTag("word_list_search_scope_word_checkbox").assertIsOn()
    }

    @Test
    fun wordListSearchScopePersistsAcrossActivityRecreation() {
        val translationOnlyMatchId = seedWord(word = "corvantiel", translation = "emberfallecho")
        navigateToWordList()
        openScopeDialog()
        composeTestRule.onNodeWithTag("word_list_search_scope_translation_checkbox").performClick()
        composeTestRule.onNodeWithText(string(R.string.action_apply)).performClick()

        recreateActivity()
        composeTestRule.dismissOnboardingIfPresent(targetContext)
        navigateToWordList()
        typeInSearchField("emberfallecho")

        composeTestRule.onNodeWithTag(itemTag(translationOnlyMatchId)).assertDoesNotExist()
    }

    @Test
    fun applyingBothScopeAgainRestoresDefaultBehavior() {
        val translationOnlyMatchId = seedWord(word = "sableharrow", translation = "windlornsong")
        navigateToWordList()
        openScopeDialog()
        composeTestRule.onNodeWithTag("word_list_search_scope_translation_checkbox").performClick()
        composeTestRule.onNodeWithText(string(R.string.action_apply)).performClick()

        openScopeDialog()
        composeTestRule.onNodeWithTag("word_list_search_scope_translation_checkbox").performClick()
        composeTestRule.onNodeWithText(string(R.string.action_apply)).performClick()

        typeInSearchField("windlornsong")
        composeTestRule.waitUntilNodeExists(hasTestTag(itemTag(translationOnlyMatchId)), DEFAULT_TIMEOUT_MS)
    }

    @Test
    fun existingWordMatchingSearchFlowsAreUnaffectedByTheNewScopeFeature() {
        val matchingId = seedWord(word = "quorlinfast", translation = "translation-a")
        val otherId = seedWord(word = "thornapple", translation = "translation-b")

        navigateToWordList()
        composeTestRule.waitUntilNodeExists(hasTestTag(itemTag(matchingId)), DEFAULT_TIMEOUT_MS)
        composeTestRule.onNodeWithTag(itemTag(otherId)).assertExists()

        typeInSearchField("quorlin")

        composeTestRule.onNodeWithTag(itemTag(matchingId)).assertExists()
        composeTestRule.waitUntilNodeGone(hasTestTag(itemTag(otherId)), DEFAULT_TIMEOUT_MS)
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

    private fun clearSearchField() {
        composeTestRule.onNodeWithTag("word_list_search_field").performTextClearance()
        composeTestRule.waitForIdle()
    }

    private fun openScopeDialog() {
        composeTestRule.waitUntilNodeExists(
            hasContentDescription(string(R.string.word_list_search_scope_content_description)),
            DEFAULT_TIMEOUT_MS,
        )
        composeTestRule
            .onNodeWithContentDescription(string(R.string.word_list_search_scope_content_description))
            .performClick()
        composeTestRule.waitForIdle()
    }

    private fun recreateActivity() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            composeTestRule.activity.recreate()
        }
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
                        position = nextPosition++,
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
                preferencesEntryPoint().wordListSearchPreferencesStore().setScope(SearchScope())
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
    }
}
