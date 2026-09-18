package com.procrastilearn.app.e2e

import android.app.Instrumentation
import android.content.Context
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.intent.rule.IntentsRule
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
class AnkiImportOrderE2eTest {
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
    fun importAnkiDeck_preservesOriginalDeckOrder() {
        val deckUri = targetContext.stagedAnkiDeckUri(testAssetProviderAuthority, DECK_FILE_NAME)
        prepareAnkiDocumentPickerResponse(instrumentationContext, targetContext, deckUri)

        composeTestRule.dismissOnboardingIfPresent(targetContext)
        composeTestRule.navigateTo(targetContext, R.string.nav_settings)
        composeTestRule.openAnkiImport(targetContext, E2E_ANKI_IMPORT_ROW_TIMEOUT_MS)

        composeTestRule.waitUntil(timeoutMillis = TIMEOUT_IMPORT_MS) {
            targetContext.allVocabularyEntities().size == EXPECTED_NOTE_COUNT
        }

        val ranksInPositionOrder =
            targetContext
                .allVocabularyEntities()
                .sortedBy { it.position }
                .map { entity ->
                requireNotNull(RANK_REGEX.find(entity.translation)) {
                    "Expected a \"Rank: N\" field in translation but got: ${entity.translation}"
                }.groupValues[1].toInt()
            }
        assertEquals(
            "Imported rows should be ordered by Anki's own new-card position (cards.due), " +
                "not arbitrary insertion order",
            (1..EXPECTED_NOTE_COUNT).toList(),
            ranksInPositionOrder,
        )
    }

    @Test
    fun importAnkiDeck_dojoServesNewCardsInPreservedOrder() {
        val deckUri = targetContext.stagedAnkiDeckUri(testAssetProviderAuthority, DECK_FILE_NAME)
        prepareAnkiDocumentPickerResponse(instrumentationContext, targetContext, deckUri)

        composeTestRule.dismissOnboardingIfPresent(targetContext)
        composeTestRule.navigateTo(targetContext, R.string.nav_settings)
        composeTestRule.openAnkiImport(targetContext, E2E_ANKI_IMPORT_ROW_TIMEOUT_MS)

        composeTestRule.waitUntil(timeoutMillis = TIMEOUT_IMPORT_MS) {
            targetContext.allVocabularyEntities().size == EXPECTED_NOTE_COUNT
        }

        targetContext.setNewCardsPerDay(EXPECTED_NOTE_COUNT)
        composeTestRule.navigateTo(targetContext, R.string.nav_dojo)

        val showTranslationLabel = targetContext.getString(R.string.learning_show_translation)
        val ratingGoodLabel = targetContext.getString(R.string.rating_good)

        expectedWordOrder.forEach { word ->
            composeTestRule.waitUntilNodeExists(hasText(showTranslationLabel), E2E_TIMEOUT_MS)
            composeTestRule.onNodeWithText(showTranslationLabel).performClick()
            composeTestRule.waitForIdle()

            composeTestRule.waitUntilNodeExists(hasText("Word: $word", substring = true), E2E_TIMEOUT_MS)

            composeTestRule.onNodeWithText(ratingGoodLabel).performClick()
        }

        composeTestRule.waitUntilNodeExists(
            hasText(targetContext.getString(R.string.dojo_empty_title)),
            E2E_TIMEOUT_MS,
        )
    }

    private companion object {
        private const val DECK_FILE_NAME = "English-German_Ordered_Deck.apkg"
        private const val EXPECTED_NOTE_COUNT = 20
        private const val TIMEOUT_IMPORT_MS = 50_000L

        private val RANK_REGEX = Regex("""Rank: (\d+)""")

        private val expectedWordOrder =
            listOf(
                "der",
                "und",
                "in",
                "sein, ist, war, ist gewesen",
                "ein",
                "haben, hat, hatte, hat gehabt",
                "sie",
                "werden, wird, wurde, ist geworden",
                "von",
                "ich",
                "nicht",
                "es",
                "mit",
                "sich",
                "er",
                "auf",
                "für",
                "auch",
                "an",
                "dass",
            )
    }
}
