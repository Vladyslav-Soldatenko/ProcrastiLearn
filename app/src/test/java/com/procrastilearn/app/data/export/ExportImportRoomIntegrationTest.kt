package com.procrastilearn.app.data.export

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
