package com.procrastilearn.app.e2e

import android.app.Instrumentation
import android.content.Context
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.intent.rule.IntentsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.procrastilearn.app.MainActivity
import com.procrastilearn.app.R
import com.procrastilearn.app.data.local.mapper.toDomain
import com.procrastilearn.app.domain.model.VocabularyItem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnkiClozeImportE2eTest {
    @get:Rule(order = 0)
    val intentsRule = IntentsRule()

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var instrumentation: Instrumentation
    private lateinit var targetContext: Context
    private lateinit var instrumentationContext: Context

    private val testAssetProviderAuthority: String
        get() = "${instrumentationContext.packageName}.test-assets"

    @Before
    fun beforeEach() {
        instrumentation = InstrumentationRegistry.getInstrumentation()
        targetContext = instrumentation.targetContext
        instrumentationContext = instrumentation.context
        targetContext.resetE2eDatabase()
        targetContext.setNewCardsPerDay(E2E_DEFAULT_NEW_CARDS_PER_DAY)
    }

    @After
    fun afterEach() {
        targetContext.resetE2eDatabase()
        targetContext.setNewCardsPerDay(E2E_DEFAULT_NEW_CARDS_PER_DAY)
    }

    @Test
    fun importAnkiDeck_addsClozeVocabularyItems() {
        val deckUri = targetContext.stagedAnkiDeckUri(testAssetProviderAuthority, DECK_FILE_NAME)
        prepareAnkiDocumentPickerResponse(instrumentationContext, targetContext, deckUri)

        composeTestRule.dismissOnboardingIfPresent(targetContext)
        composeTestRule.navigateTo(targetContext, R.string.nav_settings)
        composeTestRule.openAnkiImport(targetContext, E2E_ANKI_IMPORT_ROW_TIMEOUT_MS)

        composeTestRule.waitUntil(timeoutMillis = TIMEOUT_IMPORT_MS) {
            targetContext.allVocabularyEntities().size == EXPECTED_NOTE_COUNT
        }

        val itemsByPosition = targetContext.allVocabularyEntities().sortedBy { it.position }.map { it.toDomain() }
        assertEquals(EXPECTED_NOTE_COUNT, itemsByPosition.size)
        assertTrue("Every imported cloze item should be marked as new", itemsByPosition.all { it.isNew })

        assertEquals(
            "The first parsed cloze note should mask deletions on the front and reveal them on the back",
            expectedFirstItem,
            itemsByPosition.first().copy(id = 0, position = 0L),
        )
        assertEquals(
            "The last parsed cloze note should omit its blank example fields from both sides",
            expectedLastItem,
            itemsByPosition.last().copy(id = 0, position = 0L),
        )
    }

    @Test
    fun importAnkiDeck_neverLeaksRawClozeMarkupIntoWordListSearch() {
        val deckUri = targetContext.stagedAnkiDeckUri(testAssetProviderAuthority, DECK_FILE_NAME)
        prepareAnkiDocumentPickerResponse(instrumentationContext, targetContext, deckUri)

        composeTestRule.dismissOnboardingIfPresent(targetContext)
        composeTestRule.navigateTo(targetContext, R.string.nav_settings)
        composeTestRule.openAnkiImport(targetContext, E2E_ANKI_IMPORT_ROW_TIMEOUT_MS)

        composeTestRule.waitUntil(timeoutMillis = TIMEOUT_IMPORT_MS) {
            targetContext.allVocabularyEntities().size == EXPECTED_NOTE_COUNT
        }

        composeTestRule.navigateToWordList(targetContext)
        composeTestRule.typeInWordListSearch(RAW_CLOZE_MARKER)

        composeTestRule.waitUntilNodeExists(
            hasText(targetContext.getString(R.string.word_list_search_no_results)),
            E2E_TIMEOUT_MS,
        )
    }

    @Test
    fun importAnkiDeck_dojoMasksAndRevealsClozeCard() {
        val deckUri = targetContext.stagedAnkiDeckUri(testAssetProviderAuthority, DECK_FILE_NAME)
        prepareAnkiDocumentPickerResponse(instrumentationContext, targetContext, deckUri)

        composeTestRule.dismissOnboardingIfPresent(targetContext)
        composeTestRule.navigateTo(targetContext, R.string.nav_settings)
        composeTestRule.openAnkiImport(targetContext, E2E_ANKI_IMPORT_ROW_TIMEOUT_MS)

        composeTestRule.waitUntil(timeoutMillis = TIMEOUT_IMPORT_MS) {
            targetContext.allVocabularyEntities().size == EXPECTED_NOTE_COUNT
        }

        targetContext.setNewCardsPerDay(1)
        composeTestRule.navigateTo(targetContext, R.string.nav_dojo)

        val expectedMaskedFragment = "一个[...]"
        composeTestRule.waitUntilNodeExists(hasText(expectedMaskedFragment, substring = true), E2E_TIMEOUT_MS)
        assertFalse(
            "The Dojo front should never render raw cloze deletion syntax",
            composeTestRule.hasNodeWithSubstring(RAW_CLOZE_MARKER),
        )

        val showTranslationLabel = targetContext.getString(R.string.learning_show_translation)
        composeTestRule.onNodeWithText(showTranslationLabel).performClick()
        composeTestRule.waitForIdle()

        val expectedRevealedFragment = "Example 1: 一个 － yīgè － one of"
        composeTestRule.waitUntilNodeExists(hasText(expectedRevealedFragment, substring = true), E2E_TIMEOUT_MS)
        assertFalse(
            "The Dojo back should never render raw cloze deletion syntax",
            composeTestRule.hasNodeWithSubstring(RAW_CLOZE_MARKER),
        )

        val ratingGoodLabel = targetContext.getString(R.string.rating_good)
        composeTestRule.onNodeWithText(ratingGoodLabel).performClick()
    }

    private fun ComposeTestRule.hasNodeWithSubstring(text: String): Boolean =
        onAllNodesWithText(text, substring = true, useUnmergedTree = true)
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .isNotEmpty()

    private companion object {
        private const val DECK_FILE_NAME = "anki-cloze-deck.apkg"
        private const val EXPECTED_NOTE_COUNT = 800
        private const val TIMEOUT_IMPORT_MS = 90_000L
        private const val RAW_CLOZE_MARKER = "{{c"

        private val expectedFirstItem =
            VocabularyItem(
                word =
                    listOf(
                        "一",
                        "一个[...]",
                        "一本书[...]",
                        "一次[...]",
                        "第一[...]",
                        "一二三。",
                    ).joinToString("\n"),
                translation =
                    listOf(
                        "Color: 一",
                        "Reading: yī",
                        "Meaning: one",
                        "Example 1: 一个 － yīgè － one of",
                        "Example 2: 一本书 － yīběnshū － a book",
                        "Example 3: 一次 － yīcì － once",
                        "Example 4: 第一 － dìyī － first",
                        "Sentence Translation: One two three.",
                        "Sentence Pinyin: yī èr sān 。",
                    ).joinToString("\n"),
                isNew = true,
            )

        private val expectedLastItem =
            VocabularyItem(
                word =
                    listOf(
                        "扬",
                        "表扬[...]",
                        "发扬[...]",
                        "这位医生受到所有人的高度赞扬。",
                    ).joinToString("\n"),
                translation =
                    listOf(
                        "Color: 扬",
                        "Reading: yáng",
                        "Meaning: to raise; to hoist; scattering (in the wind); to flutter; to propagate",
                        "Example 1: 表扬 － biǎoyáng － to praise",
                        "Example 2: 发扬 － fāyáng － to develop; carry forward",
                        "Sentence Translation: This doctor received high praise from everyone.",
                    ).joinToString("\n"),
                isNew = true,
            )
    }
}
