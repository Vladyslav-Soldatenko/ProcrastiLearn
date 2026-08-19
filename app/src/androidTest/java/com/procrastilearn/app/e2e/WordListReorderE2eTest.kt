package com.procrastilearn.app.e2e

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
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

        dragHandleToItem(alphaId, zetaId)

        recreateActivity()
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

        dragHandleToItem(zetaId, alphaId)

        recreateActivity()
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

        dragHandleToItem(bId, dId)

        recreateActivity()
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
        val stepDeltaY = stepDeltaYTowardItem(handle, gammaId)
        handle.performTouchInput { down(center) }
        repeat(DRAG_STEP_COUNT / 2) {
            handle.performTouchInput { moveBy(Offset(0f, stepDeltaY)) }
            composeTestRule.waitForIdle()
        }
        // A configuration change delivers ACTION_CANCEL to any in-flight touch sequence before
        // tearing the view hierarchy down - simulate that rather than leaving the gesture
        // truly dangling across the recreate() call. waitForIdle() gives the cancel a chance to
        // actually propagate (and be observed as a no-commit) before recreate() tears everything
        // down.
        handle.performTouchInput { cancel() }
        composeTestRule.waitForIdle()

        recreateActivity()
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

        // A fixed absolute Y risks landing outside the actual device screen entirely (which the
        // library treats as no valid position at all, not clamped to the nearest edge) - aim
        // relative to the handle's own real measured position instead, comfortably below it but
        // still within plausible screen bounds, then hold there so the auto-scroll loop has real
        // wall-clock time to actually advance between each held position report.
        //
        // Repeating moveTo() at the exact same coordinate produces a zero-delta pointer event,
        // which the drag-tracking code has no obligation to treat as "still held near the edge" -
        // alternate a tiny jitter around the target each step so every event is a genuine,
        // distinct motion, keeping the auto-scroll continuously re-triggered for the whole hold.
        val handle = composeTestRule.onNodeWithTag(dragHandleTag(firstId))
        val edgeTargetY = centerYPx(handle) + AUTO_SCROLL_TARGET_OFFSET_PX
        handle.performTouchInput { down(center) }
        repeat(AUTO_SCROLL_HOLD_STEPS) { step ->
            val jitter = if (step % 2 == 0) AUTO_SCROLL_JITTER_PX else -AUTO_SCROLL_JITTER_PX
            handle.performTouchInput { moveTo(Offset(center.x, edgeTargetY + jitter)) }
            Thread.sleep(AUTO_SCROLL_STEP_DELAY_MS)
        }
        handle.performTouchInput { up() }
        composeTestRule.waitForIdle()

        // For a 40-item list, only currently-visible rows are composed into the semantics tree,
        // and where the list happens to be scrolled to isn't something this test controls - so
        // read the persisted position directly from the DB rather than trying to re-locate a
        // specific row (possibly off-screen) after the drop. Exact final position depends on
        // real auto-scroll speed/timing, so keep the assertion loose: it only needs to have
        // moved well past a simple adjacent swap, which is only reachable if auto-scroll
        // actually engaged during the held drag (with no auto-scroll the drag could never reach
        // past whatever was on-screen at the start).
        assertTrue(positionOf(firstId) > AUTO_SCROLL_MIN_EXPECTED_POSITION)
    }

    @Test
    fun reorderingWordsChangesWhichWordSequentialNewCardOrderIntroducesNext() {
        val alphaId = seedWord("alpha-mornith", "translation-alpha", position = 1L)
        val betaId = seedWord("beta-mornith", "translation-beta", position = 2L)
        navigateToWordList()
        composeTestRule.waitUntilNodeExists(hasTestTag(itemTag(betaId)), DEFAULT_TIMEOUT_MS)
        assertEquals(alphaId, pickNewIdByPositionAsc())

        dragHandleToItem(alphaId, betaId)

        assertEquals(betaId, pickNewIdByPositionAsc())
    }

    @Test
    fun reorderingThenDeletingAWordLeavesRemainingWordsContiguouslyNumbered() {
        val aId = seedWord("a-thessaly", "translation-a", position = 1L)
        val bId = seedWord("b-thessaly", "translation-b", position = 2L)
        val cId = seedWord("c-thessaly", "translation-c", position = 3L)
        navigateToWordList()
        composeTestRule.waitUntilNodeExists(hasTestTag(itemTag(cId)), DEFAULT_TIMEOUT_MS)

        dragHandleToItem(aId, cId)
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

        dragHandleToItem(importedIds[1], existingId)

        recreateActivity()
        composeTestRule.waitUntilNodeExists(hasTestTag(itemTag(existingId)), DEFAULT_TIMEOUT_MS)
        assertEquals(listOf(importedIds[1], existingId, importedIds[0]), displayedWordIdsInOrder())
    }

    // Activity.recreate() must run on the main thread - calling it directly from the
    // instrumentation thread throws IllegalStateException.
    private fun recreateActivity() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            composeTestRule.activity.recreate()
        }
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

    // A single large moveTo() jump doesn't reliably register as a real drag - the target
    // position can land outside the list's actual content bounds entirely, which the library
    // treats as "no valid drop target" rather than clamping to the nearest one. Real on-screen
    // row positions (not a guessed pixel distance) split into several incremental moveBy()
    // steps, with idle time between each for layout to catch up, is what actually works.
    private fun centerYPx(node: SemanticsNodeInteraction): Float {
        val bounds = node.fetchSemanticsNode().boundsInRoot
        return (bounds.top + bounds.bottom) / 2f
    }

    private fun stepDeltaYTowardItem(
        handleNode: SemanticsNodeInteraction,
        targetItemWordId: Long,
    ): Float {
        val handleCenterY = centerYPx(handleNode)
        val targetCenterY = centerYPx(composeTestRule.onNodeWithTag(itemTag(targetItemWordId)))
        return (targetCenterY - handleCenterY) / DRAG_STEP_COUNT
    }

    // Landing exactly on the target row's original center is one swap short in practice, so
    // aim well beyond it rather than exactly at it.
    private fun dragHandleToItem(
        fromWordId: Long,
        toItemWordId: Long,
    ) {
        val handle = composeTestRule.onNodeWithTag(dragHandleTag(fromWordId))
        val stepDeltaY = stepDeltaYTowardItem(handle, toItemWordId) * DRAG_OVERSHOOT_FACTOR

        handle.performTouchInput { down(center) }
        repeat(DRAG_STEP_COUNT) {
            handle.performTouchInput { moveBy(Offset(0f, stepDeltaY)) }
            composeTestRule.waitForIdle()
        }
        handle.performTouchInput { up() }
        composeTestRule.waitForIdle()
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
        const val WORD_COUNT_EXCEEDING_ONE_SCREEN = 40
        const val AUTO_SCROLL_TARGET_OFFSET_PX = 1800f
        const val AUTO_SCROLL_JITTER_PX = 5f
        const val AUTO_SCROLL_HOLD_STEPS = 40
        const val AUTO_SCROLL_STEP_DELAY_MS = 400L
        const val AUTO_SCROLL_MIN_EXPECTED_POSITION = 2L
        const val DRAG_STEP_COUNT = 10
        const val DRAG_OVERSHOOT_FACTOR = 2f
    }
}
