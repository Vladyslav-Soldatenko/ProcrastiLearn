package com.procrastilearn.app.e2e

import android.app.Instrumentation
import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.intent.rule.IntentsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.procrastilearn.app.MainActivity
import com.procrastilearn.app.R
import com.procrastilearn.app.data.local.entity.VocabularyEntity
import com.procrastilearn.app.data.local.mapper.toDomain
import com.procrastilearn.app.domain.model.VocabularyItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsAnkiImportE2eTest {
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
    }

    @After
    fun afterEach() {
        targetContext.resetE2eDatabase()
    }

    @Test
    fun importAnkiDeck_addsVocabularyItems() {
        val deckUri = targetContext.stagedAnkiDeckUri(testAssetProviderAuthority, DECK_FILE_NAME)
        prepareAnkiDocumentPickerResponse(instrumentationContext, targetContext, deckUri)

        composeTestRule.dismissOnboardingIfPresent(targetContext)
        composeTestRule.navigateTo(targetContext, R.string.nav_settings, TIMEOUT_IMPORT_MS)
        composeTestRule.openAnkiImport(targetContext, ROW_TIMEOUT_MS)

        composeTestRule.waitUntil(timeoutMillis = TIMEOUT_IMPORT_MS) {
            runBlocking { hasImportedExpectedItems() }
        }
        val actualItems = runBlocking { loadImportedItems() }

        val actualByWord = actualItems.associateBy { it.word }

        expectedVocabularyItems.forEach { expected ->
            val actual = actualByWord[expected.word]
            assertNotNull("Expected word ${expected.word} to be imported", actual)
            assertEquals(
                "Mismatch for imported word ${expected.word}",
                expected.translation,
                actual!!.translation,
            )
            assertEquals(
                "Imported word ${expected.word} should be marked as new",
                true,
                actual.isNew,
            )
        }

        val actualOrder = runBlocking { loadImportedEntitiesOrderedByPosition() }.map { it.word }
        assertEquals(
            "Imported rows should be ordered by Anki's own new-card position (cards.due), " +
                "not arbitrary insertion order",
            expectedWordOrder,
            actualOrder,
        )
    }

    private suspend fun loadImportedItems(): List<VocabularyItem> =
        withContext(Dispatchers.IO) {
            targetContext.databaseEntryPoint()
                .appDatabase()
                .vocabularyDao()
                .getAllVocabulary()
                .first()
                .map { it.toDomain() }
        }

    private suspend fun loadImportedEntitiesOrderedByPosition(): List<VocabularyEntity> =
        withContext(Dispatchers.IO) {
            targetContext.databaseEntryPoint()
                .appDatabase()
                .vocabularyDao()
                .getAllVocabulary()
                .first()
                .sortedBy { it.position }
        }

    private suspend fun hasImportedExpectedItems(): Boolean {
        val actualWords = loadImportedItems().map { it.word }.toSet()
        return expectedVocabularyItems.all { it.word in actualWords }
    }

    private companion object {
        private const val DECK_FILE_NAME = "procrastilearn-test-deck.apkg"
        private const val TIMEOUT_IMPORT_MS = 50_000L
        private const val ROW_TIMEOUT_MS = 10_000L

        // Matches the fixture's cards.due (type=0) values: test2=276, TestTitle=8475,
        // bold...=8475 (ties with TestTitle, loses on note id), agree=8476.
        private val expectedWordOrder =
            listOf(
                "test2",
                "TestTitle",
                "bold italic underline superscript subscript difCollor textHighlight",
                listOf("agree", "əˈɡriː").joinToString(separator = "\n"),
            )

        private val expectedVocabularyItems =
            listOf(
                VocabularyItem(
                    word = "TestTitle",
                    translation = "testBack description",
                    isNew = true,
                ),
                VocabularyItem(
                    word = "test2",
                    translation = "test description2",
                    isNew = true,
                ),
                VocabularyItem(
                    word = "bold italic underline superscript subscript difCollor textHighlight",
                    translation =
                        listOf(
                            "bold italic underline superscript subscript difCollor textHighlight ",
                            "",
                            "ul1",
                            "ul2",
                            "",
                            "ol1",
                            "ol2",
                        ).joinToString(separator = "\n"),
                    isNew = true,
                ),
                VocabularyItem(
                    word = listOf("agree", "əˈɡriː").joinToString(separator = "\n"),
                    translation =
                        listOf(
                            "Meaning: To agree is to have the same opinion or belief as another person.",
                            "Example: The students agree they have too much homework.",
                        ).joinToString(separator = "\n"),
                    isNew = true,
                ),
            )
    }
}
