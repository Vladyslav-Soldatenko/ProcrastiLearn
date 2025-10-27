package com.procrastilearn.app.e2e

import android.app.Activity
import android.app.Instrumentation
import android.content.ClipData
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
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
import com.procrastilearn.app.data.local.mapper.toDomain
import com.procrastilearn.app.di.DatabaseEntryPoint
import com.procrastilearn.app.domain.model.VocabularyItem
import dagger.hilt.android.EntryPointAccessors
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

    @Before
    fun beforeEach() {
        instrumentation = InstrumentationRegistry.getInstrumentation()
        targetContext = instrumentation.targetContext
        instrumentationContext = instrumentation.context
        resetDatabase()
    }

    @After
    fun afterEach() {
        resetDatabase()
    }

    @Test
    fun importAnkiDeck_addsVocabularyItems() {
        val deckUri = stagedDeckUri()
        Log.d("SettingsAnkiImportE2eTest", "Using deckUri=$deckUri")
        prepareDocumentPickerResponse(deckUri)

        dismissInitialPrompts()
        navigateToSettings()
        openImportAndSelectAnki()

        composeTestRule.waitUntil(timeoutMillis = TIMEOUT_IMPORT_MS) {
            runBlocking { hasImportedExpectedItems() }
        }
        val actualItems = runBlocking { loadImportedItems() }
        Log.i("Fooi", actualItems.toString())

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
    }

    private fun resetDatabase() {
        val entryPoint = databaseEntryPoint()
        runBlocking {
            withContext(Dispatchers.IO) {
                entryPoint.appDatabase().vocabularyDao().deleteAllVocabulary()
            }
        }
    }

    private fun stagedDeckUri(): Uri =
        Uri.Builder()
            .scheme(ContentResolver.SCHEME_CONTENT)
            .authority(testAssetProviderAuthority)
            .appendPath("import")
            .appendPath("anki")
            .appendPath(DECK_FILE_NAME)
            .build()

    private val testAssetProviderAuthority: String
        get() = "${instrumentationContext.packageName}.test-assets"

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

    private fun dismissInitialPrompts() {
        val notNow = targetContext.getString(R.string.action_not_now)
        waitUntilNodeExists(hasText(notNow))
        composeTestRule.onNodeWithText(notNow, useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()

        waitUntilNodeExists(hasText(notNow))
        composeTestRule.onNodeWithText(notNow, useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()
    }

    private fun navigateToSettings() {
        val settingsLabel = targetContext.getString(R.string.nav_settings)
        waitUntilNodeExists(hasText(settingsLabel))
        composeTestRule
            .onNodeWithContentDescription(settingsLabel, useUnmergedTree = true)
            .performClick()
        composeTestRule.waitForIdle()
    }

    private fun openImportAndSelectAnki() {
        val importRow = targetContext.getString(R.string.settings_import_row)
        waitUntilNodeExists(hasText(importRow),10000)
        composeTestRule.onNodeWithText(importRow, useUnmergedTree = true).performScrollTo()
        composeTestRule.onNodeWithText(importRow, useUnmergedTree = true).performClick()

        composeTestRule.waitForIdle()

        val ankiOption = targetContext.getString(R.string.settings_import_option_anki_apkg)
        waitUntilNodeExists(hasText(ankiOption),10000)
        composeTestRule.onNodeWithText(ankiOption, useUnmergedTree = true).performClick()
    }

    private suspend fun loadImportedItems(): List<VocabularyItem> =
        withContext(Dispatchers.IO) {
            databaseEntryPoint()
                .appDatabase()
                .vocabularyDao()
                .getAllVocabulary()
                .first()
                .map { it.toDomain() }
        }

    private suspend fun hasImportedExpectedItems(): Boolean {
        val actualWords = loadImportedItems().map { it.word }.toSet()
        return expectedVocabularyItems.all { it.word in actualWords }
    }

    @OptIn(ExperimentalTestApi::class)
    private fun waitUntilNodeExists(matcher: SemanticsMatcher, timeoutMillis: Long = DEFAULT_TIMEOUT_MS) {
        composeTestRule.waitUntil(timeoutMillis) {
            try {
                composeTestRule.onNode(matcher, useUnmergedTree = true).fetchSemanticsNode()
                true
            } catch (_: AssertionError) {
                false
            } catch (_: IllegalStateException) {
                false
            }
        }
    }

    private fun databaseEntryPoint(): DatabaseEntryPoint =
        EntryPointAccessors.fromApplication(targetContext.applicationContext, DatabaseEntryPoint::class.java)

    private companion object {
        private const val DECK_FILE_NAME = "procrastilearn-test-deck.apkg"
        private const val DEFAULT_TIMEOUT_MS = 5_000L
        private const val TIMEOUT_IMPORT_MS = 10_000L
        private const val ANKI_MIME_TYPE = "application/apkg"

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
            )
    }
}
