package com.procrastilearn.app.ui.screens

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.R
import com.procrastilearn.app.domain.model.SearchScope
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.testing.ComponentActivityRegistrationRule
import com.procrastilearn.app.ui.WordListViewModel
import io.mockk.called
import io.mockk.mockk
import io.mockk.verify
import kotlinx.collections.immutable.toImmutableList
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Suppress("LargeClass")
class WordListScreenTest {
    private val composeTestRule = createComposeRule()
    private val dragStepCount = 10
    private val dragOvershootFactor = 2f

    @get:Rule
    val rules: TestRule =
        RuleChain
            .outerRule(ComponentActivityRegistrationRule())
            .around(composeTestRule)

    private lateinit var context: Context
    private lateinit var onDelete: (VocabularyItem) -> Unit
    private lateinit var onEdit: (VocabularyItem) -> Unit
    private lateinit var onReset: (VocabularyItem) -> Unit
    private lateinit var onNavigateBack: () -> Unit
    private lateinit var onEnterSelectionMode: (Long) -> Unit
    private lateinit var onToggleSelection: (Long) -> Unit
    private lateinit var onSelectAll: (List<Long>) -> Unit
    private lateinit var onDeselectAll: () -> Unit
    private lateinit var onExitSelectionMode: () -> Unit
    private lateinit var onDeleteSelected: () -> Unit
    private lateinit var onSetSelectedBidirectional: (Boolean) -> Unit
    private lateinit var onReorder: (List<Long>) -> Unit

    private val words =
        listOf(
            VocabularyItem(id = 1, word = "Serendipity", translation = "Happy accident", isNew = true),
            VocabularyItem(id = 2, word = "Ephemeral", translation = "Short lived", isNew = false),
            VocabularyItem(id = 3, word = "Peregrinate", translation = "To wander", isNew = false),
        )

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        onDelete = mockk(relaxed = true)
        onEdit = mockk(relaxed = true)
        onReset = mockk(relaxed = true)
        onNavigateBack = mockk(relaxed = true)
        onEnterSelectionMode = mockk(relaxed = true)
        onToggleSelection = mockk(relaxed = true)
        onSelectAll = mockk(relaxed = true)
        onDeselectAll = mockk(relaxed = true)
        onExitSelectionMode = mockk(relaxed = true)
        onDeleteSelected = mockk(relaxed = true)
        onSetSelectedBidirectional = mockk(relaxed = true)
        onReorder = mockk(relaxed = true)
    }

    private fun string(resId: Int) = context.getString(resId)

    @Test
    fun `shows empty state when there are no words`() {
        setContent(words = emptyList())

        composeTestRule.onNodeWithText(string(R.string.word_list_empty)).assertIsDisplayed()
    }

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

    @Test
    fun `clicking back button invokes onNavigateBack`() {
        setContent(words = words)

        composeTestRule
            .onNodeWithContentDescription(string(R.string.word_list_navigate_back))
            .performClick()

        verify(exactly = 1) { onNavigateBack.invoke() }
    }

    @Test
    fun `opening the item menu shows edit reset and delete actions`() {
        setContent(words = words.take(1))

        openMenuFor()

        composeTestRule.onNodeWithText(string(R.string.action_edit)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.action_reset)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.action_delete)).assertIsDisplayed()
    }

    @Test
    fun `confirming reset dialog invokes onReset for that item`() {
        setContent(words = words.take(1))
        openMenuFor()
        composeTestRule.onNodeWithText(string(R.string.action_reset)).performClick()

        composeTestRule.onNodeWithText(string(R.string.action_reset)).performClick()

        verify(exactly = 1) { onReset(words[0]) }
    }

    @Test
    fun `cancelling reset dialog does not invoke onReset`() {
        setContent(words = words.take(1))
        openMenuFor()
        composeTestRule.onNodeWithText(string(R.string.action_reset)).performClick()

        composeTestRule.onNodeWithText(string(R.string.action_cancel)).performClick()

        verify { onReset wasNot called }
    }

    @Test
    fun `confirming delete dialog invokes onDelete for that item`() {
        setContent(words = words.take(1))
        openMenuFor()
        composeTestRule.onNodeWithText(string(R.string.action_delete)).performClick()

        composeTestRule.onNodeWithText(string(R.string.action_delete)).performClick()

        verify(exactly = 1) { onDelete(words[0]) }
    }

    @Test
    fun `cancelling delete dialog does not invoke onDelete`() {
        setContent(words = words.take(1))
        openMenuFor()
        composeTestRule.onNodeWithText(string(R.string.action_delete)).performClick()

        composeTestRule.onNodeWithText(string(R.string.action_cancel)).performClick()

        verify { onDelete wasNot called }
    }

    private fun openMenuFor() {
        composeTestRule
            .onNodeWithContentDescription(string(R.string.word_list_more_actions))
            .performClick()
    }

    private fun setContent(
        words: List<VocabularyItem>,
        searchQuery: String = "",
        onSearchQueryChangeOverride: ((String) -> Unit)? = null,
        searchScope: SearchScope = SearchScope(),
        onSearchScopeChangeOverride: ((SearchScope) -> Unit)? = null,
        selectionState: WordListViewModel.SelectionState = WordListViewModel.SelectionState(),
    ) {
        composeTestRule.setContent {
            WordListContent(
                words = words.toImmutableList(),
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChangeOverride ?: {},
                searchScope = searchScope,
                onSearchScopeChange = onSearchScopeChangeOverride ?: {},
                onDelete = onDelete,
                onEdit = onEdit,
                onReset = onReset,
                onNavigateBack = onNavigateBack,
                selectionState = selectionState,
                onEnterSelectionMode = onEnterSelectionMode,
                onToggleSelection = onToggleSelection,
                onSelectAll = onSelectAll,
                onDeselectAll = onDeselectAll,
                onExitSelectionMode = onExitSelectionMode,
                onDeleteSelected = onDeleteSelected,
                onSetSelectedBidirectional = onSetSelectedBidirectional,
                onReorder = onReorder,
            )
        }
    }

    @Test
    fun `long-pressing a word row invokes onEnterSelectionMode with that word's id`() {
        setContent(words = words)

        composeTestRule.onNodeWithTag("word_list_item_${words[0].id}").performTouchInput { longClick() }

        verify(exactly = 1) { onEnterSelectionMode(words[0].id) }
    }

    @Test
    fun `long-pressing a row while already in selection mode does not re-invoke onEnterSelectionMode`() {
        setContent(
            words = words,
            selectionState = WordListViewModel.SelectionState(isActive = true, selectedIds = setOf(words[0].id)),
        )

        composeTestRule.onNodeWithTag("word_list_item_${words[0].id}").performTouchInput { longClick() }

        verify { onEnterSelectionMode wasNot called }
    }

    @Test
    fun `tapping a row while in selection mode invokes onToggleSelection`() {
        setContent(words = words, selectionState = WordListViewModel.SelectionState(isActive = true))

        composeTestRule.onNodeWithTag("word_list_item_${words[0].id}").performClick()

        verify(exactly = 1) { onToggleSelection(words[0].id) }
    }

    @Test
    fun `tapping the checkbox directly invokes onToggleSelection`() {
        setContent(words = words, selectionState = WordListViewModel.SelectionState(isActive = true))

        composeTestRule.onNodeWithTag("word_list_checkbox_${words[0].id}").performClick()

        verify(exactly = 1) { onToggleSelection(words[0].id) }
    }

    @Test
    fun `per-row overflow menu is hidden while in selection mode`() {
        setContent(words = words.take(1), selectionState = WordListViewModel.SelectionState(isActive = true))

        composeTestRule.onNodeWithContentDescription(string(R.string.word_list_more_actions)).assertDoesNotExist()
    }

    @Test
    fun `checkbox is hidden on rows when not in selection mode`() {
        setContent(words = words.take(1))

        composeTestRule.onNodeWithTag("word_list_checkbox_${words[0].id}").assertDoesNotExist()
    }

    @Test
    fun `checkbox is visible on rows in selection mode`() {
        setContent(words = words.take(1), selectionState = WordListViewModel.SelectionState(isActive = true))

        composeTestRule.onNodeWithTag("word_list_checkbox_${words[0].id}").assertIsDisplayed()
    }

    @Test
    fun `checkbox checked state matches per-row selection with a mixed partial selection`() {
        setContent(
            words = words,
            selectionState =
                WordListViewModel.SelectionState(isActive = true, selectedIds = setOf(words[0].id, words[2].id)),
        )

        composeTestRule.onNodeWithTag("word_list_checkbox_${words[0].id}").assertIsOn()
        composeTestRule.onNodeWithTag("word_list_checkbox_${words[1].id}").assertIsOff()
        composeTestRule.onNodeWithTag("word_list_checkbox_${words[2].id}").assertIsOn()
    }

    @Test
    fun `header shows normal title when not in selection mode`() {
        setContent(words = words)

        composeTestRule.onNodeWithText(string(R.string.word_list_title)).assertIsDisplayed()
    }

    @Test
    fun `header shows singular selection count`() {
        setContent(
            words = words,
            selectionState = WordListViewModel.SelectionState(isActive = true, selectedIds = setOf(words[0].id)),
        )

        composeTestRule.onNodeWithText("1 selected").assertIsDisplayed()
    }

    @Test
    fun `header shows plural selection count`() {
        setContent(
            words = words,
            selectionState =
                WordListViewModel.SelectionState(isActive = true, selectedIds = setOf(words[0].id, words[1].id)),
        )

        composeTestRule.onNodeWithText("2 selected").assertIsDisplayed()
    }

    @Test
    fun `header shows zero selected without falling back to the normal title`() {
        setContent(words = words, selectionState = WordListViewModel.SelectionState(isActive = true))

        composeTestRule.onNodeWithText("0 selected").assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.word_list_title)).assertDoesNotExist()
    }

    @Test
    fun `clicking header back arrow while in selection mode invokes onExitSelectionMode not onNavigateBack`() {
        setContent(
            words = words,
            selectionState = WordListViewModel.SelectionState(isActive = true, selectedIds = setOf(words[0].id)),
        )

        composeTestRule
            .onNodeWithContentDescription(string(R.string.word_list_navigate_back))
            .performClick()

        verify(exactly = 1) { onExitSelectionMode() }
        verify { onNavigateBack wasNot called }
    }

    @Test
    fun `clicking header back arrow while not in selection mode invokes onNavigateBack not onExitSelectionMode`() {
        setContent(words = words)

        composeTestRule
            .onNodeWithContentDescription(string(R.string.word_list_navigate_back))
            .performClick()

        verify(exactly = 1) { onNavigateBack() }
        verify { onExitSelectionMode wasNot called }
    }

    @Test
    fun `selection overflow menu icon is absent when not in selection mode`() {
        setContent(words = words)

        composeTestRule
            .onNodeWithContentDescription(string(R.string.word_list_more_actions_selection))
            .assertDoesNotExist()
    }

    @Test
    fun `opening the selection overflow menu shows select all and delete`() {
        setContent(
            words = words,
            selectionState = WordListViewModel.SelectionState(isActive = true, selectedIds = setOf(words[0].id)),
        )

        openSelectionMenu()

        composeTestRule.onNodeWithText(string(R.string.action_select_all)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.action_delete)).assertIsDisplayed()
    }

    @Test
    fun `select all in overflow menu is invoked with only the currently filtered word ids`() {
        setContent(
            words = words,
            searchQuery = "pe",
            selectionState = WordListViewModel.SelectionState(isActive = true),
        )

        openSelectionMenu()
        composeTestRule.onNodeWithText(string(R.string.action_select_all)).performClick()

        verify(exactly = 1) { onSelectAll(listOf(words[2].id)) }
    }

    @Test
    fun `selection menu label is Select all when not all displayed words are selected`() {
        setContent(
            words = words,
            selectionState = WordListViewModel.SelectionState(isActive = true, selectedIds = setOf(words[0].id)),
        )

        openSelectionMenu()

        composeTestRule.onNodeWithText(string(R.string.action_select_all)).assertIsDisplayed()
        composeTestRule.onAllNodesWithText(string(R.string.action_deselect_all)).assertCountEquals(0)
    }

    @Test
    fun `selection menu label flips to Deselect all once all displayed words are selected`() {
        setContent(
            words = words,
            selectionState =
                WordListViewModel.SelectionState(isActive = true, selectedIds = words.map { it.id }.toSet()),
        )

        openSelectionMenu()

        composeTestRule.onNodeWithText(string(R.string.action_deselect_all)).assertIsDisplayed()
    }

    @Test
    fun `selection menu label flips to Deselect all when selection is a superset including hidden ids`() {
        setContent(
            words = words,
            searchQuery = "pe",
            selectionState =
                WordListViewModel.SelectionState(isActive = true, selectedIds = words.map { it.id }.toSet()),
        )

        openSelectionMenu()

        composeTestRule.onNodeWithText(string(R.string.action_deselect_all)).assertIsDisplayed()
    }

    @Test
    fun `selection menu label shows Select all when search matches nothing`() {
        setContent(
            words = words,
            searchQuery = "xyz",
            selectionState = WordListViewModel.SelectionState(isActive = true),
        )

        openSelectionMenu()

        composeTestRule.onNodeWithText(string(R.string.action_select_all)).assertIsDisplayed()
    }

    @Test
    fun `clicking Delete in selection overflow opens bulk delete confirmation with selected count`() {
        setContent(
            words = words,
            selectionState =
                WordListViewModel.SelectionState(isActive = true, selectedIds = setOf(words[0].id, words[1].id)),
        )

        openSelectionMenu()
        composeTestRule.onNodeWithText(string(R.string.action_delete)).performClick()

        composeTestRule.onNodeWithText(string(R.string.word_list_bulk_delete_confirm_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText("Delete 2 words from your list?").assertIsDisplayed()
    }

    @Test
    fun `confirming bulk delete dialog invokes onDeleteSelected`() {
        setContent(
            words = words,
            selectionState = WordListViewModel.SelectionState(isActive = true, selectedIds = setOf(words[0].id)),
        )
        openSelectionMenu()
        composeTestRule.onNodeWithText(string(R.string.action_delete)).performClick()

        composeTestRule.onNodeWithText(string(R.string.action_delete)).performClick()

        verify(exactly = 1) { onDeleteSelected() }
    }

    @Test
    fun `cancelling bulk delete dialog does not invoke onDeleteSelected`() {
        setContent(
            words = words,
            selectionState = WordListViewModel.SelectionState(isActive = true, selectedIds = setOf(words[0].id)),
        )
        openSelectionMenu()
        composeTestRule.onNodeWithText(string(R.string.action_delete)).performClick()

        composeTestRule.onNodeWithText(string(R.string.action_cancel)).performClick()

        verify { onDeleteSelected wasNot called }
        composeTestRule.onNodeWithText(string(R.string.word_list_bulk_delete_confirm_title)).assertDoesNotExist()
    }

    @Test
    fun `search field remains functional while in selection mode`() {
        var query: String? = null
        setContent(
            words = words,
            onSearchQueryChangeOverride = { query = it },
            selectionState = WordListViewModel.SelectionState(isActive = true),
        )

        composeTestRule.onNodeWithText(string(R.string.word_list_search_label)).performTextInput("Ser")

        assertThat(query).isEqualTo("Ser")
    }

    @Test
    fun `selection kebab shows both direction actions in selection mode`() {
        setContent(words = words, selectionState = WordListViewModel.SelectionState(isActive = true))

        openSelectionMenu()

        composeTestRule.onNodeWithText(string(R.string.word_list_bulk_bidirectional_enable)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.word_list_bulk_bidirectional_disable)).assertIsDisplayed()
    }

    @Test
    fun `selection kebab is absent outside selection mode`() {
        setContent(words = words)

        composeTestRule
            .onNodeWithContentDescription(string(R.string.word_list_more_actions_selection))
            .assertDoesNotExist()
    }

    @Test
    fun `tapping test both directions applies immediately without a dialog`() {
        setContent(
            words = words,
            selectionState = WordListViewModel.SelectionState(isActive = true, selectedIds = setOf(words[0].id)),
        )
        openSelectionMenu()

        composeTestRule.onNodeWithText(string(R.string.word_list_bulk_bidirectional_enable)).performClick()

        verify(exactly = 1) { onSetSelectedBidirectional(true) }
        composeTestRule.onNodeWithText(string(R.string.word_list_bulk_forward_only_confirm_title)).assertDoesNotExist()
    }

    @Test
    fun `tapping test forward only opens the confirmation dialog instead of applying`() {
        val bidiWords = words.map { it.copy(bidirectional = true) }
        setContent(
            words = bidiWords,
            selectionState = WordListViewModel.SelectionState(isActive = true, selectedIds = setOf(bidiWords[0].id)),
        )
        openSelectionMenu()

        composeTestRule.onNodeWithText(string(R.string.word_list_bulk_bidirectional_disable)).performClick()

        composeTestRule.onNodeWithText(string(R.string.word_list_bulk_forward_only_confirm_title)).assertIsDisplayed()
        verify { onSetSelectedBidirectional wasNot called }
    }

    @Test
    fun `confirming the forward only dialog invokes onSetSelectedBidirectional with false`() {
        val bidiWords = words.map { it.copy(bidirectional = true) }
        setContent(
            words = bidiWords,
            selectionState = WordListViewModel.SelectionState(isActive = true, selectedIds = setOf(bidiWords[0].id)),
        )
        openSelectionMenu()
        composeTestRule.onNodeWithText(string(R.string.word_list_bulk_bidirectional_disable)).performClick()

        composeTestRule.onNodeWithText(string(R.string.action_continue)).performClick()

        verify(exactly = 1) { onSetSelectedBidirectional(false) }
    }

    @Test
    fun `cancelling the forward only dialog leaves the words unchanged`() {
        val bidiWords = words.map { it.copy(bidirectional = true) }
        setContent(
            words = bidiWords,
            selectionState = WordListViewModel.SelectionState(isActive = true, selectedIds = setOf(bidiWords[0].id)),
        )
        openSelectionMenu()
        composeTestRule.onNodeWithText(string(R.string.word_list_bulk_bidirectional_disable)).performClick()

        composeTestRule.onNodeWithText(string(R.string.action_cancel)).performClick()

        verify { onSetSelectedBidirectional wasNot called }
        composeTestRule.onNodeWithText(string(R.string.word_list_bulk_forward_only_confirm_title)).assertDoesNotExist()
    }

    @Test
    fun `forward only dialog message counts only the selected words that are bidirectional`() {
        val mixed =
            listOf(
                words[0].copy(bidirectional = true),
                words[1].copy(bidirectional = false),
            )
        setContent(
            words = mixed,
            selectionState =
                WordListViewModel.SelectionState(isActive = true, selectedIds = setOf(mixed[0].id, mixed[1].id)),
        )
        openSelectionMenu()

        composeTestRule.onNodeWithText(string(R.string.word_list_bulk_bidirectional_disable)).performClick()

        composeTestRule
            .onNodeWithText(
                "Stop testing 1 word in reverse? Reverse progress is kept and restored if you turn it back on.",
            ).assertIsDisplayed()
    }

    @Test
    fun `direction actions reflect selected words hidden by the active search filter`() {
        val bidiWords = words.map { it.copy(bidirectional = true) }
        setContent(
            words = bidiWords,
            searchQuery = "xyz",
            selectionState = WordListViewModel.SelectionState(isActive = true, selectedIds = setOf(bidiWords[0].id)),
        )

        openSelectionMenu()

        composeTestRule.onNodeWithText(string(R.string.word_list_bulk_bidirectional_disable)).assertIsEnabled()
        composeTestRule.onNodeWithText(string(R.string.word_list_bulk_bidirectional_enable)).assertIsNotEnabled()
    }

    @Test
    fun `delete menu item is greyed out when the selection is empty`() {
        setContent(words = words, selectionState = WordListViewModel.SelectionState(isActive = true))

        openSelectionMenu()

        composeTestRule.onNodeWithText(string(R.string.action_delete)).assertIsNotEnabled()
    }

    private fun openSelectionMenu() {
        composeTestRule
            .onNodeWithContentDescription(string(R.string.word_list_more_actions_selection))
            .performClick()
    }

    @Test
    fun `drag handle is shown and checkbox hidden with no search, no selection, and two or more words`() {
        setContent(words = words)

        words.forEach {
            composeTestRule.onNodeWithTag("word_list_drag_handle_${it.id}").assertIsDisplayed()
            composeTestRule.onNodeWithTag("word_list_checkbox_${it.id}").assertDoesNotExist()
        }
    }

    @Test
    fun `drag handle is hidden for every row when a search query is active`() {
        setContent(words = words, searchQuery = "pe")

        composeTestRule.onNodeWithTag("word_list_drag_handle_${words[2].id}").assertDoesNotExist()
    }

    @Test
    fun `drag handle is hidden for every row when selection mode is active`() {
        setContent(words = words, selectionState = WordListViewModel.SelectionState(isActive = true))

        words.forEach {
            composeTestRule.onNodeWithTag("word_list_drag_handle_${it.id}").assertDoesNotExist()
        }
    }

    @Test
    fun `drag handle is hidden when there is only a single word`() {
        setContent(words = words.take(1))

        composeTestRule.onNodeWithTag("word_list_drag_handle_${words[0].id}").assertDoesNotExist()
    }

    @Test
    fun `dragging the first row's handle past the second row invokes onReorder with them swapped`() {
        setContent(words = words)

        dragHandleToItem(fromWordId = words[0].id, toItemWordId = words[1].id)

        verify(exactly = 1) { onReorder(listOf(words[1].id, words[0].id, words[2].id)) }
    }

    @Test
    fun `a drag that returns to its starting position invokes onReorder with the original unchanged order`() {
        setContent(words = words)
        val handle = composeTestRule.onNodeWithTag("word_list_drag_handle_${words[0].id}")
        val stepDeltaY = stepDeltaYTowardItem(handle, targetItemWordId = words[1].id)

        handle.performTouchInput { down(center) }
        repeat(dragStepCount) {
            handle.performTouchInput { moveBy(Offset(0f, stepDeltaY)) }
            composeTestRule.waitForIdle()
        }
        repeat(dragStepCount) {
            handle.performTouchInput { moveBy(Offset(0f, -stepDeltaY)) }
            composeTestRule.waitForIdle()
        }
        handle.performTouchInput { up() }

        verify(exactly = 1) { onReorder(words.map { it.id }) }
    }

    private fun centerYPx(node: SemanticsNodeInteraction): Float {
        val bounds = node.fetchSemanticsNode().boundsInRoot
        return (bounds.top + bounds.bottom) / 2f
    }

    private fun stepDeltaYTowardItem(
        handleNode: SemanticsNodeInteraction,
        targetItemWordId: Long,
    ): Float {
        val handleCenterY = centerYPx(handleNode)
        val targetCenterY = centerYPx(composeTestRule.onNodeWithTag("word_list_item_$targetItemWordId"))
        return (targetCenterY - handleCenterY) * dragOvershootFactor / dragStepCount
    }

    private fun dragHandleToItem(
        fromWordId: Long,
        toItemWordId: Long,
    ) {
        val handle = composeTestRule.onNodeWithTag("word_list_drag_handle_$fromWordId")
        val stepDeltaY = stepDeltaYTowardItem(handle, toItemWordId)

        handle.performTouchInput { down(center) }
        repeat(dragStepCount) {
            handle.performTouchInput { moveBy(Offset(0f, stepDeltaY)) }
            composeTestRule.waitForIdle()
        }
        handle.performTouchInput { up() }
    }

    @Test
    fun `tapping the drag handle without dragging does not invoke any row action`() {
        setContent(words = words)

        composeTestRule.onNodeWithTag("word_list_drag_handle_${words[0].id}").performClick()

        verify { onToggleSelection wasNot called }
        verify { onEnterSelectionMode wasNot called }
        verify { onDelete wasNot called }
        verify { onEdit wasNot called }
        verify { onReset wasNot called }
    }
}
