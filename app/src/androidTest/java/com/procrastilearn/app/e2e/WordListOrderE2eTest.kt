package com.procrastilearn.app.e2e

import android.content.Context
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.procrastilearn.app.MainActivity
import com.procrastilearn.app.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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
        val zetaId = targetContext.seedWord(word = "zeta-quorvin", translation = "translation-zeta", position = 3L)
        val alphaId = targetContext.seedWord(word = "alpha-quorvin", translation = "translation-alpha", position = 1L)
        val muId = targetContext.seedWord(word = "mu-quorvin", translation = "translation-mu", position = 2L)

        composeTestRule.navigateToWordList(targetContext)
        composeTestRule.waitUntilNodeExists(hasTestTag(wordListItemTag(zetaId)), E2E_TIMEOUT_MS)

        assertEquals(listOf(alphaId, muId, zetaId), composeTestRule.displayedWordListItemIds())
    }

    @Test
    fun wordListKeepsRemainingWordsInPositionOrderAfterDeletingTheFirstOne() {
        val zetaId = targetContext.seedWord(word = "zeta-brenlock", translation = "translation-zeta", position = 3L)
        val alphaId = targetContext.seedWord(word = "alpha-brenlock", translation = "translation-alpha", position = 1L)
        val muId = targetContext.seedWord(word = "mu-brenlock", translation = "translation-mu", position = 2L)

        composeTestRule.navigateToWordList(targetContext)
        composeTestRule.waitUntilNodeExists(hasTestTag(wordListItemTag(zetaId)), E2E_TIMEOUT_MS)

        composeTestRule.longPressWordListItem(alphaId)
        composeTestRule.openWordListSelectionMenuAndTap(targetContext, R.string.action_delete)
        composeTestRule.confirmWordListBulkDelete(targetContext)

        composeTestRule.waitUntilNodeGone(hasTestTag(wordListItemTag(alphaId)), E2E_TIMEOUT_MS)
        assertEquals(listOf(muId, zetaId), composeTestRule.displayedWordListItemIds())
    }
}
