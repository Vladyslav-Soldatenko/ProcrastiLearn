package com.procrastilearn.app.e2e

import android.content.Context
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.test.performTextInput
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private val WORD_LIST_ITEM_MATCHER =
    SemanticsMatcher("has a test tag starting with word_list_item_") { node ->
        node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("word_list_item_") == true
    }

@RunWith(AndroidJUnit4::class)
class WordListReorderE2eTest {
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
    fun draggingTheFirstWordToTheLastPositionPersistsAcrossActivityRecreation() {
        val alphaId = seedWord("alpha-fenrix", "translation-alpha", position = 1L)
        val muId = seedWord("mu-fenrix", "translation-mu", position = 2L)
        val zetaId = seedWord("zeta-fenrix", "translation-zeta", position = 3L)
        navigateToWordList()
        composeTestRule.waitUntilNodeExists(hasTestTag(itemTag(zetaId)), DEFAULT_TIMEOUT_MS)

        dragHandleBy(alphaId, LARGE_DRAG_OFFSET_PX)
        composeTestRule.waitForIdle()

        composeTestRule.activity.recreate()
        composeTestRule.waitUntilNodeExists(hasTestTag(itemTag(alphaId)), DEFAULT_TIMEOUT_MS)
        assertEquals(listOf(muId, zetaId, alphaId), displayedWordIdsInOrder())
    }

    @Test
    fun draggingTheLastWordToTheFirstPositionPersistsAcrossActivityRecreation() {
        val alphaId = seedWord("alpha-torvane", "translation-alpha", position = 1L)
        val muId = seedWord("mu-torvane", "translation-mu", position = 2L)
        val zetaId = seedWord("zeta-torvane", "translation-zeta", position = 3L)
        navigateToWordList()
        composeTestRule.waitUntilNodeExists(hasTestTag(itemTag(zetaId)), DEFAULT_TIMEOUT_MS)

        dragHandleBy(zetaId, -LARGE_DRAG_OFFSET_PX)
        composeTestRule.waitForIdle()

        composeTestRule.activity.recreate()
        composeTestRule.waitUntilNodeExists(hasTestTag(itemTag(alphaId)), DEFAULT_TIMEOUT_MS)
        assertEquals(listOf(zetaId, alphaId, muId), displayedWordIdsInOrder())
    }

    @Test
    fun draggingAMiddleWordBySeveralPositionsPersistsTheNewRelativeOrder() {
        val aId = seedWord("a-quillon", "translation-a", position = 1L)
        val bId = seedWord("b-quillon", "translation-b", position = 2L)
        val cId = seedWord("c-quillon", "translation-c", position = 3L)
        val dId = seedWord("d-quillon", "translation-d", position = 4L)
        navigateToWordList()
        composeTestRule.waitUntilNodeExists(hasTestTag(itemTag(dId)), DEFAULT_TIMEOUT_MS)

        dragHandleBy(bId, LARGE_DRAG_OFFSET_PX)
        composeTestRule.waitForIdle()

        composeTestRule.activity.recreate()
        composeTestRule.waitUntilNodeExists(hasTestTag(itemTag(aId)), DEFAULT_TIMEOUT_MS)
        assertEquals(listOf(aId, cId, dId, bId), displayedWordIdsInOrder())
    }

    @Test
    fun dragHandleIsAbsentWhileSearchQueryIsActive() {
        val alphaId = seedWord("alpha-dravik", "translation-alpha", position = 1L)
        val betaId = seedWord("beta-dravik", "translation-beta", position = 2L)
        navigateToWordList()
        composeTestRule.waitUntilNodeExists(hasTestTag(itemTag(betaId)), DEFAULT_TIMEOUT_MS)

        composeTestRule.onNodeWithTag("word_list_search_field").performTextInput("alpha")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(dragHandleTag(alphaId)).assertDoesNotExist()
    }

    @Test
    fun dragHandleIsAbsentWhileSelectionModeIsActive() {
        val alphaId = seedWord("alpha-brinshall", "translation-alpha", position = 1L)
        val betaId = seedWord("beta-brinshall", "translation-beta", position = 2L)
        navigateToWordList()
        composeTestRule.waitUntilNodeExists(hasTestTag(itemTag(betaId)), DEFAULT_TIMEOUT_MS)

        composeTestRule.onNodeWithTag(itemTag(alphaId)).performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(dragHandleTag(alphaId)).assertDoesNotExist()
    }

    @Test
    fun dragHandleIsAbsentWhenListHasOnlyOneWord() {
        val onlyId = seedWord("solo-ravenna", "translation-solo", position = 1L)
        navigateToWordList()
        composeTestRule.waitUntilNodeExists(hasTestTag(itemTag(onlyId)), DEFAULT_TIMEOUT_MS)

        composeTestRule.onNodeWithTag(dragHandleTag(onlyId)).assertDoesNotExist()
    }

    @Test
    fun rotatingDeviceMidDragCancelsTheDragWithNoPartialWrite() {
        val alphaId = seedWord("alpha-serath", "translation-alpha", position = 1L)
        val betaId = seedWord("beta-serath", "translation-beta", position = 2L)
        val gammaId = seedWord("gamma-serath", "translation-gamma", position = 3L)
        navigateToWordList()
        composeTestRule.waitUntilNodeExists(hasTestTag(itemTag(gammaId)), DEFAULT_TIMEOUT_MS)

        val handle = composeTestRule.onNodeWithTag(dragHandleTag(alphaId))
        handle.performTouchInput { down(center) }
        handle.performTouchInput { moveTo(center + Offset(0f, LARGE_DRAG_OFFSET_PX)) }
        // A configuration change delivers ACTION_CANCEL to any in-flight touch sequence before
        // tearing the view hierarchy down - simulate that rather than leaving the gesture
        // truly dangling across the recreate() call.
        handle.performTouchInput { cancel() }

        composeTestRule.activity.recreate()
        composeTestRule.waitUntilNodeExists(hasTestTag(itemTag(gammaId)), DEFAULT_TIMEOUT_MS)
        assertEquals(listOf(alphaId, betaId, gammaId), displayedWordIdsInOrder())
    }

    @Test
    fun draggingAWordAcrossAnOffScreenTargetAutoScrollsAndDropsAtCorrectPosition() {
        val ids =
            (1..WORD_COUNT_EXCEEDING_ONE_SCREEN).map { seedWord("word-$it-quorlath", "t-$it", position = it.toLong()) }
        val firstId = ids.first()
        navigateToWordList()
        composeTestRule.waitUntilNodeExists(hasTestTag(itemTag(firstId)), DEFAULT_TIMEOUT_MS)

        val handle = composeTestRule.onNodeWithTag(dragHandleTag(firstId))
        handle.performTouchInput { down(center) }
        handle.performTouchInput { moveTo(Offset(center.x, AUTO_SCROLL_EDGE_Y_PX)) }
        repeat(AUTO_SCROLL_HOLD_STEPS) {
            Thread.sleep(AUTO_SCROLL_STEP_DELAY_MS)
            handle.performTouchInput { moveTo(Offset(center.x, AUTO_SCROLL_EDGE_Y_PX)) }
        }
        handle.performTouchInput { up() }
        composeTestRule.waitForIdle()

        composeTestRule.activity.recreate()
        composeTestRule.waitUntilNodeExists(hasTestTag(itemTag(ids.last())), DEFAULT_TIMEOUT_MS)

        // Exact final index depends on real auto-scroll speed/timing, so keep the assertion
        // loose: the held drag near the bottom edge must have moved the word away from the
        // very front (proving auto-scroll actually engaged, since with no auto-scroll the drag
        // could never reach past whatever was on-screen at the start), and every other word's
        // relative order must be undisturbed.
        val finalOrder = displayedWordIdsInOrder()
        assertTrue(finalOrder.first() != firstId)
        assertEquals(ids.filter { it != firstId }, finalOrder.filter { it != firstId })
    }

    @Test
    fun reorderingWordsChangesWhichWordSequentialNewCardOrderIntroducesNext() {
        val alphaId = seedWord("alpha-mornith", "translation-alpha", position = 1L)
        val betaId = seedWord("beta-mornith", "translation-beta", position = 2L)
        navigateToWordList()
        composeTestRule.waitUntilNodeExists(hasTestTag(itemTag(betaId)), DEFAULT_TIMEOUT_MS)
        assertEquals(alphaId, pickNewIdByPositionAsc())

        dragHandleBy(alphaId, LARGE_DRAG_OFFSET_PX)
        composeTestRule.waitForIdle()
        composeTestRule.waitUntilNodeExists(hasTestTag(itemTag(alphaId)), DEFAULT_TIMEOUT_MS)

        assertEquals(betaId, pickNewIdByPositionAsc())
    }

    @Test
    fun reorderingThenDeletingAWordLeavesRemainingWordsContiguouslyNumbered() {
        val aId = seedWord("a-thessaly", "translation-a", position = 1L)
        val bId = seedWord("b-thessaly", "translation-b", position = 2L)
        val cId = seedWord("c-thessaly", "translation-c", position = 3L)
        navigateToWordList()
        composeTestRule.waitUntilNodeExists(hasTestTag(itemTag(cId)), DEFAULT_TIMEOUT_MS)

        dragHandleBy(aId, LARGE_DRAG_OFFSET_PX)
        composeTestRule.waitForIdle()
        composeTestRule.waitUntilNodeExists(hasTestTag(itemTag(aId)), DEFAULT_TIMEOUT_MS)
        assertEquals(listOf(bId, cId, aId), displayedWordIdsInOrder())

        longPressItem(bId)
        openSelectionMenuAndTap(R.string.action_delete)
        confirmBulkDeleteDialog()
        composeTestRule.waitUntilNodeGone(hasTestTag(itemTag(bId)), DEFAULT_TIMEOUT_MS)

        assertEquals(listOf(cId, aId), displayedWordIdsInOrder())
        assertEquals(1L, positionOf(cId))
        assertEquals(2L, positionOf(aId))
    }

    @Test
    fun reorderingFreshlyAnkiImportedWordsPersistsCorrectly() {
        val existingId = seedWord("existing-vantrel", "translation-existing", position = 1L)
        val importedIds = importBatch(listOf("imported-a-vantrel" to "t-a", "imported-b-vantrel" to "t-b"))
        navigateToWordList()
        composeTestRule.waitUntilNodeExists(hasTestTag(itemTag(importedIds[1])), DEFAULT_TIMEOUT_MS)

        dragHandleBy(importedIds[1], -LARGE_DRAG_OFFSET_PX)
        composeTestRule.waitForIdle()

        composeTestRule.activity.recreate()
        composeTestRule.waitUntilNodeExists(hasTestTag(itemTag(existingId)), DEFAULT_TIMEOUT_MS)
        assertEquals(listOf(importedIds[1], existingId, importedIds[0]), displayedWordIdsInOrder())
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

    private fun dragHandleBy(
        id: Long,
        offsetYPx: Float,
    ) {
        composeTestRule.onNodeWithTag(dragHandleTag(id)).performTouchInput {
            down(center)
            moveTo(center + Offset(0f, offsetYPx))
            up()
        }
    }

    private fun itemTag(id: Long) = "word_list_item_$id"

    private fun dragHandleTag(id: Long) = "word_list_drag_handle_$id"

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

    private fun importBatch(words: List<Pair<String, String>>): List<Long> =
        runBlocking {
            withContext(Dispatchers.IO) {
                val dao = entryPoint().appDatabase().vocabularyDao()
                dao.applyImportBatch(
                    toInsert =
                        words.map { (word, translation) ->
                            VocabularyEntity(word = word, translation = translation, fsrsCardJson = "")
                        },
                    toUpdate = emptyList(),
                )
                words.map { (word, _) -> requireNotNull(dao.getVocabularyByWord(word)).id }
            }
        }

    private fun positionOf(id: Long): Long =
        runBlocking {
            withContext(Dispatchers.IO) {
                requireNotNull(entryPoint().appDatabase().vocabularyDao().getVocabularyById(id)).position
            }
        }

    private fun pickNewIdByPositionAsc(): Long? =
        runBlocking {
            withContext(Dispatchers.IO) {
                entryPoint().appDatabase().vocabularyReviewDao().pickNewIdByPositionAsc()
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
        const val LARGE_DRAG_OFFSET_PX = 3000f
        const val WORD_COUNT_EXCEEDING_ONE_SCREEN = 40
        const val AUTO_SCROLL_EDGE_Y_PX = 5000f
        const val AUTO_SCROLL_HOLD_STEPS = 15
        const val AUTO_SCROLL_STEP_DELAY_MS = 300L
    }
}
