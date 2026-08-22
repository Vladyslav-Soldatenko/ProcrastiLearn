package com.procrastilearn.app.e2e

import android.app.Activity
import android.app.Instrumentation
import android.content.ClipData
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.rule.IntentsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.procrastilearn.app.MainActivity
import com.procrastilearn.app.R
import com.procrastilearn.app.data.local.mapper.toDomain
import com.procrastilearn.app.data.repository.todayStamp
import com.procrastilearn.app.di.DatabaseEntryPoint
import com.procrastilearn.app.di.PreferencesEntryPoint
import com.procrastilearn.app.domain.model.VocabularyItem
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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
        resetAppState()
    }

    @After
    fun afterEach() {
        resetAppState()
    }

    @Test
    fun importAnkiDeck_addsClozeVocabularyItems() {
        val deckUri = stagedDeckUri()
        prepareDocumentPickerResponse(deckUri)

        composeTestRule.dismissOnboardingIfPresent(targetContext)
        navigateToSettings()
        openImportAndSelectAnki()

        composeTestRule.waitUntil(timeoutMillis = TIMEOUT_IMPORT_MS) {
            runBlocking { importedCount() == EXPECTED_NOTE_COUNT }
        }

        val itemsByPosition = runBlocking { loadImportedItemsOrderedByPosition() }
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
        val deckUri = stagedDeckUri()
        prepareDocumentPickerResponse(deckUri)

        composeTestRule.dismissOnboardingIfPresent(targetContext)
        navigateToSettings()
        openImportAndSelectAnki()

        composeTestRule.waitUntil(timeoutMillis = TIMEOUT_IMPORT_MS) {
            runBlocking { importedCount() == EXPECTED_NOTE_COUNT }
        }

        navigateToWordList()
        composeTestRule.onNodeWithTag("word_list_search_field").performTextInput(RAW_CLOZE_MARKER)
        composeTestRule.waitForIdle()

        composeTestRule.waitUntilNodeExists(
            hasText(targetContext.getString(R.string.word_list_search_no_results)),
            DEFAULT_TIMEOUT_MS,
        )
    }

    @Test
    fun importAnkiDeck_dojoMasksAndRevealsClozeCard() {
        val deckUri = stagedDeckUri()
        prepareDocumentPickerResponse(deckUri)

        composeTestRule.dismissOnboardingIfPresent(targetContext)
        navigateToSettings()
        openImportAndSelectAnki()

        composeTestRule.waitUntil(timeoutMillis = TIMEOUT_IMPORT_MS) {
            runBlocking { importedCount() == EXPECTED_NOTE_COUNT }
        }

        allowExactlyTodaysNewCardQuota(1)
        navigateToDojo()

        val expectedMaskedFragment = "一个[...]"
        composeTestRule.waitUntilNodeExists(hasText(expectedMaskedFragment, substring = true), DEFAULT_TIMEOUT_MS)
        assertFalse(
            "The Dojo front should never render raw cloze deletion syntax",
            composeTestRule.hasNodeWithSubstring(RAW_CLOZE_MARKER),
        )

        val showTranslationLabel = targetContext.getString(R.string.learning_show_translation)
        composeTestRule.onNodeWithText(showTranslationLabel).performClick()
        composeTestRule.waitForIdle()

        val expectedRevealedFragment = "Example 1: 一个 － yīgè － one of"
        composeTestRule.waitUntilNodeExists(hasText(expectedRevealedFragment, substring = true), DEFAULT_TIMEOUT_MS)
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

    private fun resetAppState() {
        val entryPoint = databaseEntryPoint()
        val prefsEntryPoint = preferencesEntryPoint()
        runBlocking {
            withContext(Dispatchers.IO) {
                entryPoint.appDatabase().vocabularyDao().deleteAllVocabulary()
                prefsEntryPoint.dayCountersStore().resetFor(todayStamp())
                prefsEntryPoint.dayCountersStore().setNewPerDay(DEFAULT_NEW_PER_DAY)
            }
        }
    }

    private fun allowExactlyTodaysNewCardQuota(count: Int) {
        runBlocking {
            withContext(Dispatchers.IO) {
                val store = preferencesEntryPoint().dayCountersStore()
                store.resetFor(todayStamp())
                store.setNewPerDay(count)
            }
        }
    }

    private fun stagedDeckUri(): Uri =
        Uri
            .Builder()
            .scheme(ContentResolver.SCHEME_CONTENT)
            .authority(testAssetProviderAuthority)
            .appendPath("import")
            .appendPath("anki")
            .appendPath(DECK_FILE_NAME)
            .build()

    private fun prepareDocumentPickerResponse(uri: Uri) {
        instrumentationContext.grantUriPermission(
            targetContext.packageName,
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        val resultIntent =
            Intent().apply {
                setDataAndType(uri, ANKI_MIME_TYPE)
                clipData = ClipData.newRawUri("anki-deck", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        intending(hasAction(Intent.ACTION_OPEN_DOCUMENT))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, resultIntent))
    }

    private fun navigateToSettings() {
        val settingsLabel = targetContext.getString(R.string.nav_settings)
        composeTestRule.waitUntilNodeExists(hasText(settingsLabel), DEFAULT_TIMEOUT_MS)
        composeTestRule
            .onNodeWithContentDescription(settingsLabel, useUnmergedTree = true)
            .performClick()
        composeTestRule.waitForIdle()
    }

    private fun navigateToDojo() {
        val dojoLabel = targetContext.getString(R.string.nav_dojo)
        composeTestRule.waitUntilNodeExists(hasText(dojoLabel), DEFAULT_TIMEOUT_MS)
        composeTestRule
            .onNodeWithContentDescription(dojoLabel, useUnmergedTree = true)
            .performClick()
        composeTestRule.waitForIdle()
    }

    private fun navigateToWordList() {
        val addWordLabel = targetContext.getString(R.string.nav_add_word)
        composeTestRule.waitUntilNodeExists(hasText(addWordLabel), DEFAULT_TIMEOUT_MS)
        composeTestRule
            .onNodeWithContentDescription(addWordLabel, useUnmergedTree = true)
            .performClick()
        composeTestRule.waitForIdle()

        val viewListLabel = targetContext.getString(R.string.action_view_list)
        composeTestRule.waitUntilNodeExists(hasContentDescription(viewListLabel), DEFAULT_TIMEOUT_MS)
        composeTestRule.onNodeWithContentDescription(viewListLabel).performClick()
        composeTestRule.waitForIdle()
    }

    private fun openImportAndSelectAnki() {
        val importRow = targetContext.getString(R.string.settings_import_row)
        composeTestRule.waitUntilNodeExists(hasText(importRow), ROW_TIMEOUT_MS)
        composeTestRule.onNodeWithText(importRow, useUnmergedTree = true).performScrollTo()
        composeTestRule.onNodeWithText(importRow, useUnmergedTree = true).performClick()

        composeTestRule.waitForIdle()

        val ankiOption = targetContext.getString(R.string.settings_import_option_anki_apkg)
        composeTestRule.waitUntilNodeExists(hasText(ankiOption), ROW_TIMEOUT_MS)
        composeTestRule.onNodeWithText(ankiOption, useUnmergedTree = true).performClick()
    }

    private suspend fun importedCount(): Int =
        withContext(Dispatchers.IO) {
            databaseEntryPoint()
                .appDatabase()
                .vocabularyDao()
                .getAllVocabulary()
                .first()
                .size
        }

    private suspend fun loadImportedItemsOrderedByPosition(): List<VocabularyItem> =
        withContext(Dispatchers.IO) {
            databaseEntryPoint()
                .appDatabase()
                .vocabularyDao()
                .getAllVocabulary()
                .first()
                .sortedBy { it.position }
                .map { it.toDomain() }
        }

    private fun databaseEntryPoint(): DatabaseEntryPoint =
        EntryPointAccessors.fromApplication(targetContext.applicationContext, DatabaseEntryPoint::class.java)

    private fun preferencesEntryPoint(): PreferencesEntryPoint =
        EntryPointAccessors.fromApplication(targetContext.applicationContext, PreferencesEntryPoint::class.java)

    private companion object {
        private const val DECK_FILE_NAME = "anki-cloze-deck.apkg"
        private const val EXPECTED_NOTE_COUNT = 800
        private const val DEFAULT_TIMEOUT_MS = 50_000L
        private const val TIMEOUT_IMPORT_MS = 90_000L
        private const val ROW_TIMEOUT_MS = 10_000L
        private const val ANKI_MIME_TYPE = "application/apkg"
        private const val DEFAULT_NEW_PER_DAY = 15
        private const val RAW_CLOZE_MARKER = "{{c"

        // Ground truth copied verbatim from AnkiApkgVocabularyParserTest's real-fixture
        // assertions, so this E2E test proves the exact same cloze masking/reveal output
        // survives the real device SQLite + zstd + ContentResolver + Room round trip.
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
