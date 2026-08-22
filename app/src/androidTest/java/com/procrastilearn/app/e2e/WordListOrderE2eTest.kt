package com.procrastilearn.app.e2e

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
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
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private val WORD_LIST_ITEM_MATCHER =
    SemanticsMatcher("has a test tag starting with word_list_item_") { node ->
        node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("word_list_item_") == true
    }

@RunWith(AndroidJUnit4::class)
class WordListOrderE2eTest {
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
    fun wordListDisplaysWordsOrderedByPositionNotInsertionOrder() {
        // Seeded (and thus id-assigned) in the order zeta, alpha, mu, but with position values
        // that put alpha first, mu second, zeta last - neither insertion/id order nor
        // alphabetical order matches the expected position order.
        val zetaId = seedWord(word = "zeta-quorvin", translation = "translation-zeta", position = 3L)
        val alphaId = seedWord(word = "alpha-quorvin", translation = "translation-alpha", position = 1L)
        val muId = seedWord(word = "mu-quorvin", translation = "translation-mu", position = 2L)

        navigateToWordList()
        composeTestRule.waitUntilNodeExists(hasTestTag(itemTag(zetaId)), DEFAULT_TIMEOUT_MS)

        assertEquals(listOf(alphaId, muId, zetaId), displayedWordIdsInOrder())
    }

    @Test
    fun wordListKeepsRemainingWordsInPositionOrderAfterDeletingTheFirstOne() {
        val zetaId = seedWord(word = "zeta-brenlock", translation = "translation-zeta", position = 3L)
        val alphaId = seedWord(word = "alpha-brenlock", translation = "translation-alpha", position = 1L)
        val muId = seedWord(word = "mu-brenlock", translation = "translation-mu", position = 2L)

        navigateToWordList()
        composeTestRule.waitUntilNodeExists(hasTestTag(itemTag(zetaId)), DEFAULT_TIMEOUT_MS)

        longPressItem(alphaId)
        openSelectionMenuAndTap(R.string.action_delete)
        confirmBulkDeleteDialog()

        composeTestRule.waitUntilNodeGone(hasTestTag(itemTag(alphaId)), DEFAULT_TIMEOUT_MS)
        assertEquals(listOf(muId, zetaId), displayedWordIdsInOrder())
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

    private fun openSelectionMenuAndTap(actionResId: Int) {
        composeTestRule
            .onNodeWithContentDescription(string(R.string.word_list_more_actions_selection))
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(actionResId)).performClick()
        composeTestRule.waitForIdle()
    }

    private fun confirmBulkDeleteDialog() {
        composeTestRule.waitUntilNodeExists(
            hasText(string(R.string.word_list_bulk_delete_confirm_title)),
            DEFAULT_TIMEOUT_MS,
        )
        composeTestRule.onNodeWithText(string(R.string.action_delete)).performClick()
        composeTestRule.waitForIdle()
    }

    private fun itemTag(id: Long) = "word_list_item_$id"

    private fun displayedWordIdsInOrder(): List<Long> =
        composeTestRule
            .onAllNodes(WORD_LIST_ITEM_MATCHER, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .mapNotNull { node -> node.config.getOrNull(SemanticsProperties.TestTag) }
            .map { tag -> tag.removePrefix("word_list_item_").toLong() }

    private fun seedWord(
        word: String,
        translation: String,
        position: Long,
    ): Long =
        runBlocking {
            withContext(Dispatchers.IO) {
                entryPoint().appDatabase().vocabularyDao().insertVocabulary(
                    VocabularyEntity(
                        word = word,
                        translation = translation,
                        fsrsCardJson = "",
                        position = position,
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
