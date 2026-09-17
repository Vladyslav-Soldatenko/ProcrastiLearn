package com.procrastilearn.app.e2e

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.procrastilearn.app.MainActivity
import com.procrastilearn.app.R
import com.procrastilearn.app.data.local.entity.VocabularyEntity
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
        targetContext.resetE2eDatabase()
        composeTestRule.dismissOnboardingIfPresent(targetContext)
    }

    @After
    fun afterEach() {
        targetContext.resetE2eDatabase()
    }

    @Test
    fun wordListDisplaysWordsOrderedByPositionNotInsertionOrder() {
        // Seeded (and thus id-assigned) in the order zeta, alpha, mu, but with position values
        // that put alpha first, mu second, zeta last - neither insertion/id order nor
        // alphabetical order matches the expected position order.
        val zetaId = seedWord(word = "zeta-quorvin", translation = "translation-zeta", position = 3L)
        val alphaId = seedWord(word = "alpha-quorvin", translation = "translation-alpha", position = 1L)
        val muId = seedWord(word = "mu-quorvin", translation = "translation-mu", position = 2L)

        composeTestRule.navigateToWordList(targetContext)
        composeTestRule.waitUntilNodeExists(hasTestTag(wordListItemTag(zetaId)), E2E_TIMEOUT_MS)

        assertEquals(listOf(alphaId, muId, zetaId), displayedWordIdsInOrder())
    }

    @Test
    fun wordListKeepsRemainingWordsInPositionOrderAfterDeletingTheFirstOne() {
        val zetaId = seedWord(word = "zeta-brenlock", translation = "translation-zeta", position = 3L)
        val alphaId = seedWord(word = "alpha-brenlock", translation = "translation-alpha", position = 1L)
        val muId = seedWord(word = "mu-brenlock", translation = "translation-mu", position = 2L)

        composeTestRule.navigateToWordList(targetContext)
        composeTestRule.waitUntilNodeExists(hasTestTag(wordListItemTag(zetaId)), E2E_TIMEOUT_MS)

        composeTestRule.longPressWordListItem(alphaId)
        composeTestRule.openWordListSelectionMenuAndTap(targetContext, R.string.action_delete)
        composeTestRule.confirmWordListBulkDelete(targetContext)

        composeTestRule.waitUntilNodeGone(hasTestTag(wordListItemTag(alphaId)), E2E_TIMEOUT_MS)
        assertEquals(listOf(muId, zetaId), displayedWordIdsInOrder())
    }

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
        targetContext.insertVocabulary(
            VocabularyEntity(word = word, translation = translation, fsrsCardJson = "", position = position),
            assignNextPosition = false,
        )
}
