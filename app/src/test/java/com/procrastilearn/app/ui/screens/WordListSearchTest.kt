package com.procrastilearn.app.ui.screens

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.R
import com.procrastilearn.app.domain.model.SearchScope
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.testing.ComponentActivityRegistrationRule
import kotlinx.collections.immutable.toImmutableList
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WordListSearchTest {
    private val composeTestRule = createComposeRule()

    @get:Rule
    val rules: TestRule =
        RuleChain
            .outerRule(ComponentActivityRegistrationRule())
            .around(composeTestRule)

    private lateinit var context: Context

    private val words =
        listOf(
            VocabularyItem(id = 1, word = "Serendipity", translation = "Happy accident", isNew = true),
            VocabularyItem(id = 2, word = "Ephemeral", translation = "Short lived", isNew = false),
            VocabularyItem(id = 3, word = "Peregrinate", translation = "To wander", isNew = false),
        )

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun string(resId: Int) = context.getString(resId)

    @Test
    fun `shows all words when search query is empty`() {
        setContent(words = words)

        words.forEach { composeTestRule.onNodeWithText(it.word).assertIsDisplayed() }
    }

    @Test
    fun `filters words by case-insensitive substring match`() {
        setContent(words = words, searchQuery = "PE")

        composeTestRule.onNodeWithText("Peregrinate").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Serendipity").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Ephemeral").assertCountEquals(0)
    }

    @Test
    fun `trims whitespace from search query before filtering`() {
        setContent(words = words, searchQuery = "  ephemeral  ")

        composeTestRule.onNodeWithText("Ephemeral").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Serendipity").assertCountEquals(0)
    }

    @Test
    fun `shows no matches message when search has no results`() {
        setContent(words = words, searchQuery = "xyz")

        composeTestRule.onNodeWithText(string(R.string.word_list_search_no_results)).assertIsDisplayed()
    }

    @Test
    fun `blank search query behaves as if it were empty`() {
        setContent(words = words, searchQuery = "   ")

        words.forEach { composeTestRule.onNodeWithText(it.word).assertIsDisplayed() }
    }

    @Test
    fun `typing in search field invokes onSearchQueryChange`() {
        var query: String? = null
        setContent(words = words, onSearchQueryChangeOverride = { query = it })

        composeTestRule.onNodeWithText(string(R.string.word_list_search_label)).performTextInput("Ser")

        assertThat(query).isEqualTo("Ser")
    }

    @Test
    fun `tune icon is visible in the search field`() {
        setContent(words = words)

        composeTestRule
            .onNodeWithContentDescription(string(R.string.word_list_search_scope_content_description))
            .assertIsDisplayed()
    }

    @Test
    fun `clicking the tune icon opens the search scope dialog`() {
        setContent(words = words)

        composeTestRule
            .onNodeWithContentDescription(string(R.string.word_list_search_scope_content_description))
            .performClick()

        composeTestRule.onNodeWithText(string(R.string.word_list_search_scope_title)).assertIsDisplayed()
    }

    @Test
    fun `search scope dialog shows both checkboxes checked by default`() {
        setContent(words = words, searchScope = SearchScope())

        openSearchScopeDialog()

        composeTestRule.onNodeWithTag("word_list_search_scope_word_checkbox").assertIsOn()
        composeTestRule.onNodeWithTag("word_list_search_scope_translation_checkbox").assertIsOn()
    }

    @Test
    fun `search scope dialog seeds checkboxes from the currently applied scope, not always-default`() {
        setContent(words = words, searchScope = SearchScope(matchWord = true, matchTranslation = false))

        openSearchScopeDialog()

        composeTestRule.onNodeWithTag("word_list_search_scope_word_checkbox").assertIsOn()
        composeTestRule.onNodeWithTag("word_list_search_scope_translation_checkbox").assertIsOff()
    }

    @Test
    fun `unchecking one checkbox leaves Apply enabled and hides the error text`() {
        setContent(words = words)
        openSearchScopeDialog()

        composeTestRule.onNodeWithTag("word_list_search_scope_translation_checkbox").performClick()

        composeTestRule.onNodeWithText(string(R.string.action_apply)).assertIsEnabled()
        composeTestRule.onNodeWithTag("word_list_search_scope_error_text").assertDoesNotExist()
    }

    @Test
    fun `unchecking both checkboxes disables Apply and shows the error text`() {
        setContent(words = words)
        openSearchScopeDialog()

        composeTestRule.onNodeWithTag("word_list_search_scope_word_checkbox").performClick()
        composeTestRule.onNodeWithTag("word_list_search_scope_translation_checkbox").performClick()

        composeTestRule.onNodeWithText(string(R.string.action_apply)).assertIsNotEnabled()
        composeTestRule.onNodeWithText(string(R.string.word_list_search_scope_error)).assertIsDisplayed()
    }

    @Test
    fun `re-checking a box after reaching zero re-enables Apply`() {
        setContent(words = words)
        openSearchScopeDialog()
        composeTestRule.onNodeWithTag("word_list_search_scope_word_checkbox").performClick()
        composeTestRule.onNodeWithTag("word_list_search_scope_translation_checkbox").performClick()

        composeTestRule.onNodeWithTag("word_list_search_scope_word_checkbox").performClick()

        composeTestRule.onNodeWithText(string(R.string.action_apply)).assertIsEnabled()
        composeTestRule.onNodeWithTag("word_list_search_scope_error_text").assertDoesNotExist()
    }

    @Test
    fun `clicking Apply with a modified scope invokes onSearchScopeChange with the new scope and closes the dialog`() {
        var applied: SearchScope? = null
        setContent(words = words, onSearchScopeChangeOverride = { applied = it })
        openSearchScopeDialog()
        composeTestRule.onNodeWithTag("word_list_search_scope_translation_checkbox").performClick()

        composeTestRule.onNodeWithText(string(R.string.action_apply)).performClick()

        assertThat(applied).isEqualTo(SearchScope(matchWord = true, matchTranslation = false))
        composeTestRule.onNodeWithText(string(R.string.word_list_search_scope_title)).assertDoesNotExist()
    }

    @Test
    fun `dismissing the dialog via Cancel does not invoke onSearchScopeChange and reverts unapplied changes`() {
        var invoked = false
        setContent(words = words, onSearchScopeChangeOverride = { invoked = true })
        openSearchScopeDialog()
        composeTestRule.onNodeWithTag("word_list_search_scope_word_checkbox").performClick()

        composeTestRule.onNodeWithText(string(R.string.action_cancel)).performClick()

        assertThat(invoked).isFalse()
        openSearchScopeDialog()
        composeTestRule.onNodeWithTag("word_list_search_scope_word_checkbox").assertIsOn()
    }

    @Test
    fun `Apply button being disabled prevents onSearchScopeChange from being invoked`() {
        var invoked = false
        setContent(words = words, onSearchScopeChangeOverride = { invoked = true })
        openSearchScopeDialog()
        composeTestRule.onNodeWithTag("word_list_search_scope_word_checkbox").performClick()
        composeTestRule.onNodeWithTag("word_list_search_scope_translation_checkbox").performClick()

        composeTestRule.onNodeWithText(string(R.string.action_apply)).performClick()

        assertThat(invoked).isFalse()
    }

    @Test
    fun `filtering with default both-checked scope matches by word (parity with pre-feature behavior)`() {
        setContent(words = words, searchScope = SearchScope(), searchQuery = "PE")

        composeTestRule.onNodeWithText("Peregrinate").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Serendipity").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Ephemeral").assertCountEquals(0)
    }

    @Test
    fun `filtering with word-only scope excludes matches that are only in the translation field`() {
        val extra = VocabularyItem(id = 4, word = "Quixotic", translation = "Wildly impractical scheme", isNew = false)
        setContent(
            words = words + extra,
            searchScope = SearchScope(matchWord = true, matchTranslation = false),
            searchQuery = "wildly",
        )

        composeTestRule.onAllNodesWithText("Quixotic").assertCountEquals(0)
    }

    @Test
    fun `filtering with translation-only scope excludes matches that are only in the word field`() {
        val extra = VocabularyItem(id = 4, word = "Quixotic", translation = "Wildly impractical scheme", isNew = false)
        setContent(
            words = words + extra,
            searchScope = SearchScope(matchWord = false, matchTranslation = true),
            searchQuery = "quixo",
        )

        composeTestRule.onAllNodesWithText("Quixotic").assertCountEquals(0)
    }

    @Test
    fun `filtering with translation-only scope still matches items whose translation contains the query`() {
        val extra = VocabularyItem(id = 4, word = "Quixotic", translation = "Wildly impractical scheme", isNew = false)
        setContent(
            words = words + extra,
            searchScope = SearchScope(matchWord = false, matchTranslation = true),
            searchQuery = "wildly",
        )

        composeTestRule.onNodeWithText("Quixotic").assertIsDisplayed()
    }

    @Test
    fun `filtering with both scopes enabled shows an item only once when it matches in both fields`() {
        val extra = VocabularyItem(id = 4, word = "Quixotic", translation = "Quixotic-like folly", isNew = false)
        setContent(words = words + extra, searchScope = SearchScope(), searchQuery = "quixo")

        composeTestRule.onAllNodesWithText("Quixotic").assertCountEquals(1)
    }

    @Test
    fun `changing scope while a query is already active immediately re-filters the displayed list`() {
        val extra = VocabularyItem(id = 4, word = "Quixotic", translation = "Wildly impractical scheme", isNew = false)
        setContent(
            words = words + extra,
            searchQuery = "wildly",
            searchScope = SearchScope(matchWord = true, matchTranslation = false),
        )

        composeTestRule.onAllNodesWithText("Quixotic").assertCountEquals(0)
    }

    @Test
    fun `search field label is unchanged by the search scope`() {
        setContent(words = words, searchScope = SearchScope(matchWord = false, matchTranslation = true))

        composeTestRule.onNodeWithText(string(R.string.word_list_search_label)).assertIsDisplayed()
    }

    @Test
    fun `no visual indicator differs on the tune icon regardless of active scope`() {
        setContent(words = words, searchScope = SearchScope(matchWord = true, matchTranslation = false))

        composeTestRule
            .onAllNodesWithContentDescription(string(R.string.word_list_search_scope_content_description))
            .assertCountEquals(1)
    }

    private fun openSearchScopeDialog() {
        composeTestRule
            .onNodeWithContentDescription(string(R.string.word_list_search_scope_content_description))
            .performClick()
    }

    private fun setContent(
        words: List<VocabularyItem>,
        searchQuery: String = "",
        onSearchQueryChangeOverride: ((String) -> Unit)? = null,
        searchScope: SearchScope = SearchScope(),
        onSearchScopeChangeOverride: ((SearchScope) -> Unit)? = null,
    ) {
        composeTestRule.setContent {
            WordListContent(
                words = words.toImmutableList(),
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChangeOverride ?: {},
                searchScope = searchScope,
                onSearchScopeChange = onSearchScopeChangeOverride ?: {},
                onDelete = {},
                onEdit = {},
                onReset = {},
            )
        }
    }
}
