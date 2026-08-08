package com.procrastilearn.app.ui.screens

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.R
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.testing.ComponentActivityRegistrationRule
import com.procrastilearn.app.ui.WordListViewModel
import io.mockk.called
import io.mockk.mockk
import io.mockk.verify
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

    private val words =
        listOf(
            VocabularyItem(id = 1, word = "Serendipity", translation = "Happy accident", isNew = true),
            VocabularyItem(id = 2, word = "Ephemeral", translation = "Short lived", isNew = false),
            VocabularyItem(id = 3, word = "Peregrinate", translation = "To wander", isNew = false),
        )

    private val bidirectionalWordWithOverrides =
        VocabularyItem(
            id = 4,
            word = "Laufen",
            translation = "to run",
            isNew = false,
            bidirectional = true,
            backwardPromptOverride = "Run!",
            backwardAnswerOverride = "Laufen (verb)",
        )

    private val bidirectionalWordWithoutOverrides =
        VocabularyItem(id = 5, word = "Sprechen", translation = "to speak", isNew = false, bidirectional = true)

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
    fun `confirming edit dialog with changed fields invokes onEdit with updated item`() {
        setContent(words = words.take(1))
        openMenuFor()
        composeTestRule.onNodeWithText(string(R.string.action_edit)).performClick()

        val wordField = composeTestRule.onAllNodes(hasSetTextAction())[1]
        wordField.performTextClearance()
        wordField.performTextInput("Updated")

        composeTestRule.onNodeWithText(string(R.string.action_save)).performClick()

        verify(exactly = 1) {
            onEdit(words[0].copy(word = "Updated"))
        }
    }

    @Test
    fun `edit dialog does not confirm when word is blank`() {
        setContent(words = words.take(1))
        openMenuFor()
        composeTestRule.onNodeWithText(string(R.string.action_edit)).performClick()

        val wordField = composeTestRule.onAllNodes(hasSetTextAction())[1]
        wordField.performTextClearance()

        composeTestRule.onNodeWithText(string(R.string.action_save)).performClick()

        verify { onEdit wasNot called }
        composeTestRule.onNodeWithText(string(R.string.edit_word_title)).assertIsDisplayed()
    }

    @Test
    fun `cancelling edit dialog does not invoke onEdit`() {
        setContent(words = words.take(1))
        openMenuFor()
        composeTestRule.onNodeWithText(string(R.string.action_edit)).performClick()

        composeTestRule.onNodeWithText(string(R.string.action_cancel)).performClick()

        verify { onEdit wasNot called }
        composeTestRule.onNodeWithText(string(R.string.edit_word_title)).assertDoesNotExist()
    }

    @Test
    fun `edit dialog shows bidirectional checkbox unchecked for a non-bidirectional word`() {
        setContent(words = words.take(1))
        openMenuFor()
        composeTestRule.onNodeWithText(string(R.string.action_edit)).performClick()

        composeTestRule.onNode(isToggleable()).assertIsOff()
    }

    @Test
    fun `edit dialog shows bidirectional checkbox checked for a bidirectional word`() {
        setContent(words = listOf(bidirectionalWordWithoutOverrides))
        openMenuFor()
        composeTestRule.onNodeWithText(string(R.string.action_edit)).performClick()

        composeTestRule.onNode(isToggleable()).assertIsOn()
    }

    @Test
    fun `customize action hidden in edit dialog until bidirectional is checked`() {
        setContent(words = words.take(1))
        openMenuFor()
        composeTestRule.onNodeWithText(string(R.string.action_edit)).performClick()

        composeTestRule.onNodeWithText(string(R.string.add_word_customize_backward_show)).assertDoesNotExist()
    }

    @Test
    fun `customize fields start expanded in edit dialog when the word already has reverse overrides`() {
        setContent(words = listOf(bidirectionalWordWithOverrides))
        openMenuFor()
        composeTestRule.onNodeWithText(string(R.string.action_edit)).performClick()

        composeTestRule
            .onNodeWithText(string(R.string.add_word_backward_prompt_label))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(string(R.string.add_word_backward_answer_label))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    @Suppress("ktlint:standard:max-line-length")
    fun `customize fields start collapsed in edit dialog when bidirectional is on but there are no reverse overrides`() {
        setContent(words = listOf(bidirectionalWordWithoutOverrides))
        openMenuFor()
        composeTestRule.onNodeWithText(string(R.string.action_edit)).performClick()

        composeTestRule
            .onNodeWithText(string(R.string.add_word_customize_backward_show))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.add_word_backward_prompt_label)).assertDoesNotExist()
    }

    @Test
    fun `checking bidirectional checkbox in edit dialog reveals the customize action`() {
        setContent(words = words.take(1))
        openMenuFor()
        composeTestRule.onNodeWithText(string(R.string.action_edit)).performClick()

        composeTestRule.onNode(isToggleable()).performClick()

        composeTestRule
            .onNodeWithText(string(R.string.add_word_customize_backward_show))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `unchecking bidirectional checkbox in edit dialog hides the customize action and fields`() {
        setContent(words = listOf(bidirectionalWordWithOverrides))
        openMenuFor()
        composeTestRule.onNodeWithText(string(R.string.action_edit)).performClick()

        composeTestRule.onNode(isToggleable()).performClick()

        composeTestRule.onNodeWithText(string(R.string.add_word_customize_backward_show)).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.add_word_backward_prompt_label)).assertDoesNotExist()
    }

    @Test
    @Suppress("ktlint:standard:max-line-length")
    fun `confirming edit dialog with bidirectional checked and no overrides saves bidirectional true with null overrides`() {
        setContent(words = words.take(1))
        openMenuFor()
        composeTestRule.onNodeWithText(string(R.string.action_edit)).performClick()

        composeTestRule.onNode(isToggleable()).performClick()
        composeTestRule.onNodeWithText(string(R.string.action_save)).performClick()

        verify(exactly = 1) {
            onEdit(words[0].copy(bidirectional = true))
        }
    }

    @Test
    fun `confirming edit dialog with customized reverse fields saves the entered override text`() {
        setContent(words = words.take(1))
        openMenuFor()
        composeTestRule.onNodeWithText(string(R.string.action_edit)).performClick()

        composeTestRule.onNode(isToggleable()).performClick()
        composeTestRule
            .onNodeWithText(string(R.string.add_word_customize_backward_show))
            .performScrollTo()
            .performClick()
        composeTestRule
            .onNodeWithText(string(R.string.add_word_backward_prompt_label))
            .performScrollTo()
            .performTextInput("Run!")
        composeTestRule
            .onNodeWithText(string(R.string.add_word_backward_answer_label))
            .performScrollTo()
            .performTextInput("Laufen")
        composeTestRule.onNodeWithText(string(R.string.action_save)).performClick()

        verify(exactly = 1) {
            onEdit(
                words[0].copy(
                    bidirectional = true,
                    backwardPromptOverride = "Run!",
                    backwardAnswerOverride = "Laufen",
                ),
            )
        }
    }

    @Test
    fun `confirming edit dialog after unchecking bidirectional clears previously saved overrides`() {
        setContent(words = listOf(bidirectionalWordWithOverrides))
        openMenuFor()
        composeTestRule.onNodeWithText(string(R.string.action_edit)).performClick()

        composeTestRule.onNode(isToggleable()).performClick()
        composeTestRule.onNodeWithText(string(R.string.action_save)).performClick()

        verify(exactly = 1) {
            onEdit(
                bidirectionalWordWithOverrides.copy(
                    bidirectional = false,
                    backwardPromptOverride = null,
                    backwardAnswerOverride = null,
                ),
            )
        }
    }

    @Test
    fun `confirming edit dialog with whitespace-only override fields saves null overrides`() {
        setContent(words = words.take(1))
        openMenuFor()
        composeTestRule.onNodeWithText(string(R.string.action_edit)).performClick()

        composeTestRule.onNode(isToggleable()).performClick()
        composeTestRule
            .onNodeWithText(string(R.string.add_word_customize_backward_show))
            .performScrollTo()
            .performClick()
        composeTestRule
            .onNodeWithText(string(R.string.add_word_backward_prompt_label))
            .performScrollTo()
            .performTextInput("   ")
        composeTestRule.onNodeWithText(string(R.string.action_save)).performClick()

        verify(exactly = 1) {
            onEdit(words[0].copy(bidirectional = true))
        }
    }

    @Test
    fun `re-checking bidirectional in edit dialog after unchecking does not restore previously entered overrides`() {
        setContent(words = words.take(1))
        openMenuFor()
        composeTestRule.onNodeWithText(string(R.string.action_edit)).performClick()

        composeTestRule.onNode(isToggleable()).performClick()
        composeTestRule
            .onNodeWithText(string(R.string.add_word_customize_backward_show))
            .performScrollTo()
            .performClick()
        composeTestRule
            .onNodeWithText(string(R.string.add_word_backward_prompt_label))
            .performScrollTo()
            .performTextInput("temp")
        composeTestRule.onNode(isToggleable()).performScrollTo().performClick()
        composeTestRule.onNode(isToggleable()).performScrollTo().performClick()
        composeTestRule.onNodeWithText(string(R.string.action_save)).performClick()

        verify(exactly = 1) {
            onEdit(words[0].copy(bidirectional = true))
        }
    }

    @Test
    fun `cancelling edit dialog after toggling bidirectional does not invoke onEdit`() {
        setContent(words = words.take(1))
        openMenuFor()
        composeTestRule.onNodeWithText(string(R.string.action_edit)).performClick()

        composeTestRule.onNode(isToggleable()).performClick()
        composeTestRule.onNodeWithText(string(R.string.action_cancel)).performClick()

        verify { onEdit wasNot called }
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
        selectionState: WordListViewModel.SelectionState = WordListViewModel.SelectionState(),
    ) {
        composeTestRule.setContent {
            WordListContent(
                words = words,
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChangeOverride ?: {},
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

    private fun openSelectionMenu() {
        composeTestRule
            .onNodeWithContentDescription(string(R.string.word_list_more_actions_selection))
            .performClick()
    }
}
