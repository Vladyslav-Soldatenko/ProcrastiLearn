package com.procrastilearn.app.ui.screens

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.R
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.testing.ComponentActivityRegistrationRule
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
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [33],
    manifest = Config.NONE,
)
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

        composeTestRule.onNodeWithText("Save").performClick()

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

        composeTestRule.onNodeWithText("Save").performClick()

        verify { onEdit wasNot called }
        composeTestRule.onNodeWithText(string(R.string.edit_word_title)).assertIsDisplayed()
    }

    @Test
    fun `cancelling edit dialog does not invoke onEdit`() {
        setContent(words = words.take(1))
        openMenuFor()
        composeTestRule.onNodeWithText(string(R.string.action_edit)).performClick()

        composeTestRule.onNodeWithText("Cancel").performClick()

        verify { onEdit wasNot called }
        composeTestRule.onNodeWithText(string(R.string.edit_word_title)).assertDoesNotExist()
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
            )
        }
    }
}
