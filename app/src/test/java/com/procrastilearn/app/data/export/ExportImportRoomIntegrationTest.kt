package com.procrastilearn.app.data.export

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.procrastilearn.app.data.local.database.AppDatabase
import com.procrastilearn.app.data.local.entity.VocabularyEntity
import com.procrastilearn.app.data.local.mapper.toEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ExportImportRoomIntegrationTest {
    private lateinit var database: AppDatabase

    @Before
    fun setup() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    AppDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `every historical fixture imports cleanly into a real database`() =
        runTest {
            val failures = corpusFiles().mapNotNull { checkImport(it) }
            assertWithMessage(failures.joinToString("\n\n")).that(failures).isEmpty()
        }

    // The generic corpus check above compares stored rows against outcome.items.map {
    // it.toEntity() } - the same mapper under test - so it can't independently catch a bug
    // in that mapper or in the decoder that both agree on a wrong value. This test instead
    // imports a genuine schemaVersion=2 payload (predating the bidirectional fields) through
    // the real decode -> map -> insert pipeline and asserts every resulting column against
    // hardcoded expectations, so decoding and enrichment are both checked independently of
    // whatever the production code happens to compute.
    @Test
    fun `importing a genuine v2 export enriches rows with correct default backward values`() =
        runTest {
            val dao = database.vocabularyDao()
            val rawV2Json =
                """
                {
                  "schemaVersion": 2,
                  "exportedAt": 1700000000000,
                  "appVersion": "1.2.0",
                  "words": [
                    {
                      "id": 1,
                      "word": "Baum",
                      "translation": "tree",
                      "createdAt": 1000,
                      "lastShownAt": 2000,
                      "correctCount": 3,
                      "incorrectCount": 1,
                      "fsrsCardJson": "{\"cardId\":42}",
                      "fsrsDueAt": 5000
                    }
                  ]
                }
                """.trimIndent()

            val outcome = VocabularyExportSerializer.decode(rawV2Json)
            check(outcome is ImportOutcome.Success) { "Expected a successful decode, got $outcome" }
            dao.insertAllVocabulary(outcome.items.map { it.toEntity() })

            val stored = dao.getAllVocabulary().first().single()

            // Original (pre-bidirectional) fields carried through unchanged.
            assertThat(stored.word).isEqualTo("Baum")
            assertThat(stored.translation).isEqualTo("tree")
            assertThat(stored.correctCount).isEqualTo(3)
            assertThat(stored.incorrectCount).isEqualTo(1)
            assertThat(stored.fsrsCardJson).isEqualTo("{\"cardId\":42}")
            assertThat(stored.fsrsDueAt).isEqualTo(5000L)

            // New columns enriched with the correct defaults.
            assertThat(stored.bidirectional).isFalse()
            assertThat(stored.backwardFsrsCardJson).isEmpty()
            assertThat(stored.backwardFsrsDueAt).isEqualTo(0L)
            assertThat(stored.backwardCorrectCount).isEqualTo(0)
            assertThat(stored.backwardIncorrectCount).isEqualTo(0)
            assertThat(stored.backwardPromptOverride).isNull()
            assertThat(stored.backwardAnswerOverride).isNull()
        }

    private fun corpusFiles(): List<File> {
        val corpusRoot = File("src/test/resources/exports")
        return corpusRoot
            .walkTopDown()
            .filter { it.isFile && it.extension == "json" }
            .filterNot { it.toRelativeString(corpusRoot).startsWith("malformed") }
            .sortedBy { it.path }
            .toList()
    }

    private suspend fun checkImport(fixture: File): String? {
        val dao = database.vocabularyDao()
        dao.deleteAllVocabulary()

        val outcome = VocabularyExportSerializer.decode(fixture.readText())
        if (outcome !is ImportOutcome.Success) {
            return "${fixture.path}: expected a decodable fixture but got $outcome"
        }

        dao.insertAllVocabulary(outcome.items.map { it.toEntity() })

        val expected = outcome.items.map { it.toEntity() }.sortedBy { it.id }
        val stored = dao.getAllVocabulary().first().sortedBy { it.id }
        return mismatchMessage(fixture, expected, stored)
    }

    private fun mismatchMessage(
        fixture: File,
        expected: List<VocabularyEntity>,
        stored: List<VocabularyEntity>,
    ): String? {
        if (stored == expected) return null
        return "${fixture.path}: stored rows do not match the decoded export.\n" +
            "  expected: $expected\n" +
            "  actual:   $stored"
    }
}
