package com.procrastilearn.app.e2e

import android.app.Activity
import android.app.Instrumentation
import android.content.ClipData
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.rule.IntentsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.procrastilearn.app.MainActivity
import com.procrastilearn.app.R
import com.procrastilearn.app.data.repository.todayStamp
import com.procrastilearn.app.di.DatabaseEntryPoint
import com.procrastilearn.app.di.PreferencesEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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
        resetAppState()
    }

    @After
    fun afterEach() {
        resetAppState()
    }

    @Test
    fun importAnkiDeck_preservesOriginalDeckOrder() {
        val deckUri = stagedDeckUri()
        prepareDocumentPickerResponse(deckUri)

        composeTestRule.dismissOnboardingIfPresent(targetContext)
        navigateToSettings()
        openImportAndSelectAnki()

        composeTestRule.waitUntil(timeoutMillis = TIMEOUT_IMPORT_MS) {
            runBlocking { importedCount() == EXPECTED_NOTE_COUNT }
        }

        val ranksInPositionOrder = runBlocking { loadRanksOrderedByPosition() }
        assertEquals(
            "Imported rows should be ordered by Anki's own new-card position (cards.due), " +
                "not arbitrary insertion order",
            (1..EXPECTED_NOTE_COUNT).toList(),
            ranksInPositionOrder,
        )
    }

    @Test
    fun importAnkiDeck_dojoServesNewCardsInPreservedOrder() {
        val deckUri = stagedDeckUri()
        prepareDocumentPickerResponse(deckUri)

        composeTestRule.dismissOnboardingIfPresent(targetContext)
        navigateToSettings()
        openImportAndSelectAnki()

        composeTestRule.waitUntil(timeoutMillis = TIMEOUT_IMPORT_MS) {
            runBlocking { importedCount() == EXPECTED_NOTE_COUNT }
        }

        allowExactlyTodaysNewCardQuota(EXPECTED_NOTE_COUNT)
        navigateToDojo()

        val showTranslationLabel = targetContext.getString(R.string.learning_show_translation)
        val ratingGoodLabel = targetContext.getString(R.string.rating_good)

        expectedWordOrder.forEach { word ->
            composeTestRule.waitUntilNodeExists(hasText(showTranslationLabel), DEFAULT_TIMEOUT_MS)
            composeTestRule.onNodeWithText(showTranslationLabel).performClick()
            composeTestRule.waitForIdle()

            composeTestRule.waitUntilNodeExists(hasText("Word: $word", substring = true), DEFAULT_TIMEOUT_MS)

            composeTestRule.onNodeWithText(ratingGoodLabel).performClick()
        }

        composeTestRule.waitUntilNodeExists(
            hasText(targetContext.getString(R.string.dojo_empty_title)),
            DEFAULT_TIMEOUT_MS,
        )
    }

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

    private fun navigateToDojo() {
        val dojoLabel = targetContext.getString(R.string.nav_dojo)
        composeTestRule.waitUntilNodeExists(hasText(dojoLabel), DEFAULT_TIMEOUT_MS)
        composeTestRule
            .onNodeWithContentDescription(dojoLabel, useUnmergedTree = true)
            .performClick()
        composeTestRule.waitForIdle()
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

    private suspend fun loadRanksOrderedByPosition(): List<Int> =
        withContext(Dispatchers.IO) {
            databaseEntryPoint()
                .appDatabase()
                .vocabularyDao()
                .getAllVocabulary()
                .first()
                .sortedBy { it.position }
                .map { entity ->
                    requireNotNull(RANK_REGEX.find(entity.translation)) {
                        "Expected a \"Rank: N\" field in translation but got: ${entity.translation}"
                    }.groupValues[1].toInt()
                }
        }

    private fun databaseEntryPoint(): DatabaseEntryPoint =
        EntryPointAccessors.fromApplication(targetContext.applicationContext, DatabaseEntryPoint::class.java)

    private fun preferencesEntryPoint(): PreferencesEntryPoint =
        EntryPointAccessors.fromApplication(targetContext.applicationContext, PreferencesEntryPoint::class.java)

    private companion object {
        private const val DECK_FILE_NAME = "English-German_Ordered_Deck.apkg"
        private const val EXPECTED_NOTE_COUNT = 20
        private const val DEFAULT_TIMEOUT_MS = 50_000L
        private const val TIMEOUT_IMPORT_MS = 50_000L
        private const val ROW_TIMEOUT_MS = 10_000L
        private const val ANKI_MIME_TYPE = "application/apkg"

        private const val DEFAULT_NEW_PER_DAY = 15

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
