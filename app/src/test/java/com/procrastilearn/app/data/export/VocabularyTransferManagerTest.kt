package com.procrastilearn.app.data.export

import android.content.Context
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.R
import com.procrastilearn.app.data.local.dao.VocabularyDao
import com.procrastilearn.app.data.local.entity.VocabularyEntity
import com.procrastilearn.app.data.parser.json.JsonVocabularyParser
import com.procrastilearn.app.domain.model.VocabularyExportItem
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.domain.parser.VocabularyExportParser
import com.procrastilearn.app.domain.parser.VocabularyParser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class VocabularyTransferManagerTest {
    private lateinit var vocabularyDao: VocabularyDao
    private lateinit var appContext: Context

    private val apkgParser: VocabularyParser =
        object : VocabularyParser {
            override val id: String = "apkg"
            override val titleResId: Int = R.string.settings_import_option_anki_apkg
            override val descriptionResId: Int? = R.string.settings_import_option_anki_apkg_desc
            override val supportedExtensions: Set<String> = setOf("apkg")
            override val mimeTypes: List<String> = listOf("application/apkg")

            override fun parse(file: File): List<VocabularyItem> = emptyList()
        }

    @Before
    fun setUp() {
        vocabularyDao = mockk()
        appContext = RuntimeEnvironment.getApplication()
    }

    private fun buildManager(parsers: Set<VocabularyParser> = setOf(apkgParser)): VocabularyTransferManager =
        VocabularyTransferManager(
            vocabularyDao = vocabularyDao,
            parsers = parsers,
            ioDispatcher = UnconfinedTestDispatcher(),
        )

    @Test
    fun `importOptions surfaces parser metadata sorted by title`() {
        val secondParser =
            object : VocabularyParser {
                override val id: String = "json"
                override val titleResId: Int = R.string.settings_import_option_json
                override val descriptionResId: Int? = R.string.settings_import_option_json_desc
                override val supportedExtensions: Set<String> = setOf("json")
                override val mimeTypes: List<String> = listOf("application/json")

                override fun parse(file: File): List<VocabularyItem> = emptyList()
            }
        val manager = buildManager(parsers = setOf(apkgParser, secondParser))

        val options = manager.importOptions

        assertThat(options).hasSize(2)
        assertThat(options.map { it.id }).containsExactly("apkg", "json")
    }

    @Test
    fun `exportToUri writes serialized json and succeeds`() =
        runTest {
            val manager = buildManager()
            val tempFile = File.createTempFile("export", ".json")
            val uri = Uri.fromFile(tempFile)
            val entity =
                VocabularyEntity(
                    id = 1,
                    word = "Haus",
                    translation = "House",
                    createdAt = 123L,
                    correctCount = 2,
                    incorrectCount = 1,
                    fsrsCardJson = "{\"c\":1}",
                    fsrsDueAt = 456L,
                )
            every { vocabularyDao.getAllVocabulary() } returns flowOf(listOf(entity))

            val result = manager.exportToUri(appContext, uri)

            assertThat(result.isSuccess).isTrue()
            val payload = tempFile.readText()
            assertThat(payload).contains("\"id\": 1")
            assertThat(payload).contains("\"word\": \"Haus\"")
            assertThat(payload).contains("\"translation\": \"House\"")
        }

    @Test
    fun `exportToUri fails and preserves the original exception's message`() =
        runTest {
            val manager = buildManager()
            val tempFile = File.createTempFile("export", ".json")
            val uri = Uri.fromFile(tempFile)
            every { vocabularyDao.getAllVocabulary() } returns flow { throw IllegalStateException("boom") }

            val result = manager.exportToUri(appContext, uri)

            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()?.message).isEqualTo("boom")
            assertThat(tempFile.readText()).isEmpty()
        }

    @Test
    fun `importFromUri reports unsupported format for an unknown option id`() =
        runTest {
            val manager = buildManager(parsers = emptySet())
            val tempFile = File.createTempFile("deck", ".apkg")
            tempFile.writeText("placeholder")
            val uri = Uri.fromFile(tempFile)

            val result = manager.importFromUri(appContext, "unknown", uri)

            assertThat(
                result,
            ).isEqualTo(VocabularyImportResult.Failure(VocabularyImportFailureReason.UNSUPPORTED_FORMAT))
            coVerify(exactly = 0) { vocabularyDao.applyImportBatch(any(), any()) }
        }

    @Test
    fun `importFromUri inserts a genuinely new apkg item with id reset and an appended position`() =
        runTest {
            val parsedItem = VocabularyItem(id = 0, word = "Hallo", translation = "Hello", isNew = true)
            val parser =
                object : VocabularyParser {
                    override val id: String = "apkg"
                    override val titleResId: Int = R.string.settings_import_option_anki_apkg
                    override val descriptionResId: Int? = R.string.settings_import_option_anki_apkg_desc
                    override val supportedExtensions: Set<String> = setOf("apkg")
                    override val mimeTypes: List<String> = listOf("application/apkg")

                    override fun parse(file: File): List<VocabularyItem> = listOf(parsedItem)
                }
            coEvery { vocabularyDao.getMaxPosition() } returns 10L
            coEvery { vocabularyDao.getVocabularyByWords(any()) } returns emptyList()
            coEvery { vocabularyDao.applyImportBatch(any(), any()) } returns Unit
            val manager = buildManager(parsers = setOf(parser))
            val tempFile = File.createTempFile("deck", ".apkg")
            tempFile.writeText("placeholder")
            val uri = Uri.fromFile(tempFile)

            val result = manager.importFromUri(appContext, parser.id, uri)

            assertThat(result).isEqualTo(VocabularyImportResult.Success(importedCount = 1))
            val toInsert = slot<List<VocabularyEntity>>()
            val toUpdate = slot<List<VocabularyEntity>>()
            coVerify { vocabularyDao.applyImportBatch(capture(toInsert), capture(toUpdate)) }
            assertThat(toUpdate.captured).isEmpty()
            assertThat(toInsert.captured).hasSize(1)
            val inserted = toInsert.captured.single()
            assertThat(inserted.id).isEqualTo(0L)
            assertThat(inserted.word).isEqualTo("Hallo")
            assertThat(inserted.translation).isEqualTo("Hello")
            assertThat(inserted.position).isEqualTo(11L)
            assertThat(inserted.fsrsDueAt).isEqualTo(0L)
            assertThat(inserted.fsrsCardJson).isNotEmpty()
        }

    @Test
    fun `importFromUri updates an existing apkg item's translation while preserving id, position and progress`() =
        runTest {
            val parsedItem = VocabularyItem(id = 0, word = "haus", translation = "new translation", isNew = true)
            val parser =
                object : VocabularyParser {
                    override val id: String = "apkg"
                    override val titleResId: Int = R.string.settings_import_option_anki_apkg
                    override val descriptionResId: Int? = R.string.settings_import_option_anki_apkg_desc
                    override val supportedExtensions: Set<String> = setOf("apkg")
                    override val mimeTypes: List<String> = listOf("application/apkg")

                    override fun parse(file: File): List<VocabularyItem> = listOf(parsedItem)
                }
            val existingEntity =
                VocabularyEntity(
                    id = 42L,
                    word = "Haus",
                    translation = "old translation",
                    position = 7L,
                    fsrsDueAt = 999L,
                    fsrsCardJson = "existing-progress-json",
                    correctCount = 5,
                )
            coEvery { vocabularyDao.getMaxPosition() } returns 100L
            coEvery { vocabularyDao.getVocabularyByWords(any()) } returns listOf(existingEntity)
            coEvery { vocabularyDao.applyImportBatch(any(), any()) } returns Unit
            val manager = buildManager(parsers = setOf(parser))
            val tempFile = File.createTempFile("deck", ".apkg")
            tempFile.writeText("placeholder")
            val uri = Uri.fromFile(tempFile)

            manager.importFromUri(appContext, parser.id, uri)

            val toInsert = slot<List<VocabularyEntity>>()
            val toUpdate = slot<List<VocabularyEntity>>()
            coVerify { vocabularyDao.applyImportBatch(capture(toInsert), capture(toUpdate)) }
            assertThat(toInsert.captured).isEmpty()
            assertThat(toUpdate.captured).hasSize(1)
            val updated = toUpdate.captured.single()
            assertThat(updated.id).isEqualTo(42L)
            assertThat(updated.position).isEqualTo(7L)
            assertThat(updated.fsrsDueAt).isEqualTo(999L)
            assertThat(updated.fsrsCardJson).isEqualTo("existing-progress-json")
            assertThat(updated.correctCount).isEqualTo(5)
            assertThat(updated.translation).isEqualTo("new translation")
        }

    @Test
    fun `importFromUri dedupes case-different duplicate words within one apkg batch, last one wins`() =
        runTest {
            val parser =
                object : VocabularyParser {
                    override val id: String = "apkg"
                    override val titleResId: Int = R.string.settings_import_option_anki_apkg
                    override val descriptionResId: Int? = R.string.settings_import_option_anki_apkg_desc
                    override val supportedExtensions: Set<String> = setOf("apkg")
                    override val mimeTypes: List<String> = listOf("application/apkg")

                    override fun parse(file: File): List<VocabularyItem> =
                        listOf(
                            VocabularyItem(word = "Cat", translation = "first", isNew = true),
                            VocabularyItem(word = "cat", translation = "second", isNew = true),
                            VocabularyItem(word = "CAT", translation = "third", isNew = true),
                        )
                }
            coEvery { vocabularyDao.getMaxPosition() } returns 0L
            coEvery { vocabularyDao.getVocabularyByWords(any()) } returns emptyList()
            coEvery { vocabularyDao.applyImportBatch(any(), any()) } returns Unit
            val manager = buildManager(parsers = setOf(parser))
            val tempFile = File.createTempFile("deck", ".apkg")
            tempFile.writeText("placeholder")
            val uri = Uri.fromFile(tempFile)

            val result = manager.importFromUri(appContext, parser.id, uri)

            // importedCount still reflects the parser's raw output size, not the deduped count.
            assertThat(result).isEqualTo(VocabularyImportResult.Success(importedCount = 3))
            val toInsert = slot<List<VocabularyEntity>>()
            coVerify { vocabularyDao.applyImportBatch(capture(toInsert), any()) }
            assertThat(toInsert.captured).hasSize(1)
            assertThat(toInsert.captured.single().translation).isEqualTo("third")
        }

    @Test
    fun `importFromUri looks up existing words in chunks bounded by the sqlite bind-arg limit`() =
        runTest {
            val words = (1..950).map { "word$it" }
            val parser =
                object : VocabularyParser {
                    override val id: String = "apkg"
                    override val titleResId: Int = R.string.settings_import_option_anki_apkg
                    override val descriptionResId: Int? = R.string.settings_import_option_anki_apkg_desc
                    override val supportedExtensions: Set<String> = setOf("apkg")
                    override val mimeTypes: List<String> = listOf("application/apkg")

                    override fun parse(file: File): List<VocabularyItem> =
                        words.map { VocabularyItem(word = it, translation = it, isNew = true) }
                }
            coEvery { vocabularyDao.getMaxPosition() } returns 0L
            coEvery { vocabularyDao.getVocabularyByWords(any()) } returns emptyList()
            coEvery { vocabularyDao.applyImportBatch(any(), any()) } returns Unit
            val manager = buildManager(parsers = setOf(parser))
            val tempFile = File.createTempFile("deck", ".apkg")
            tempFile.writeText("placeholder")
            val uri = Uri.fromFile(tempFile)

            manager.importFromUri(appContext, parser.id, uri)

            val chunks = mutableListOf<List<String>>()
            coVerify { vocabularyDao.getVocabularyByWords(capture(chunks)) }
            assertThat(chunks).hasSize(2)
            assertThat(chunks[0]).hasSize(900)
            assertThat(chunks[1]).hasSize(50)
        }

    @Test
    fun `importFromUri inserts a genuinely new export item with id reset, position carried through verbatim`() =
        runTest {
            val exportItem =
                VocabularyExportItem(
                    id = 4,
                    word = "Wort",
                    translation = "Word",
                    createdAt = 10L,
                    lastShownAt = 20L,
                    correctCount = 1,
                    incorrectCount = 0,
                    fsrsCardJson = "{\"card\":1}",
                    fsrsDueAt = 30L,
                    position = 77L,
                )
            val parser =
                object : VocabularyParser, VocabularyExportParser {
                    override val id: String = "json"
                    override val titleResId: Int = R.string.settings_import_option_json
                    override val descriptionResId: Int? = R.string.settings_import_option_json_desc
                    override val supportedExtensions: Set<String> = setOf("json")
                    override val mimeTypes: List<String> = listOf("application/json")

                    override fun parse(file: File): List<VocabularyItem> = emptyList()

                    override fun parseExport(file: File): List<VocabularyExportItem> = listOf(exportItem)
                }
            coEvery { vocabularyDao.getVocabularyByWords(any()) } returns emptyList()
            coEvery { vocabularyDao.applyImportBatch(any(), any()) } returns Unit
            val manager = buildManager(parsers = setOf(parser))
            val tempFile = File.createTempFile("deck", ".json")
            tempFile.writeText("placeholder")
            val uri = Uri.fromFile(tempFile)

            val result = manager.importFromUri(appContext, parser.id, uri)

            assertThat(result).isEqualTo(VocabularyImportResult.Success(importedCount = 1))
            val toInsert = slot<List<VocabularyEntity>>()
            coVerify { vocabularyDao.applyImportBatch(capture(toInsert), any()) }
            // id is reset to 0 rather than reusing the exported file's id 4 - this is what
            // prevents a coincidental id collision with an unrelated row from aborting the
            // whole import (see VocabularyTransferManager.importExportItems).
            assertThat(toInsert.captured.single().id).isEqualTo(0L)
            assertThat(toInsert.captured.single().position).isEqualTo(77L)
        }

    @Test
    fun `importFromUri json merge updates content but preserves id, position, progress and bidirectional`() =
        runTest {
            val exportItem =
                VocabularyExportItem(
                    id = 999L, // deliberately different from the existing row's id, and must be ignored
                    word = "wort",
                    translation = "new word",
                    createdAt = 10L,
                    lastShownAt = 20L,
                    correctCount = 1,
                    incorrectCount = 0,
                    fsrsCardJson = "{\"card\":1}",
                    fsrsDueAt = 30L,
                    bidirectional = true,
                    backwardPromptOverride = "new prompt",
                    backwardAnswerOverride = "new answer",
                    position = 500L,
                )
            val existingEntity =
                VocabularyEntity(
                    id = 12L,
                    word = "Wort",
                    translation = "old word",
                    position = 3L,
                    fsrsDueAt = 1234L,
                    fsrsCardJson = "existing-progress-json",
                    bidirectional = false,
                    backwardPromptOverride = null,
                    backwardAnswerOverride = null,
                )
            val parser =
                object : VocabularyParser, VocabularyExportParser {
                    override val id: String = "json"
                    override val titleResId: Int = R.string.settings_import_option_json
                    override val descriptionResId: Int? = R.string.settings_import_option_json_desc
                    override val supportedExtensions: Set<String> = setOf("json")
                    override val mimeTypes: List<String> = listOf("application/json")

                    override fun parse(file: File): List<VocabularyItem> = emptyList()

                    override fun parseExport(file: File): List<VocabularyExportItem> = listOf(exportItem)
                }
            coEvery { vocabularyDao.getVocabularyByWords(any()) } returns listOf(existingEntity)
            coEvery { vocabularyDao.applyImportBatch(any(), any()) } returns Unit
            val manager = buildManager(parsers = setOf(parser))
            val tempFile = File.createTempFile("deck", ".json")
            tempFile.writeText("placeholder")
            val uri = Uri.fromFile(tempFile)

            manager.importFromUri(appContext, parser.id, uri)

            val toUpdate = slot<List<VocabularyEntity>>()
            coVerify { vocabularyDao.applyImportBatch(any(), capture(toUpdate)) }
            val updated = toUpdate.captured.single()
            assertThat(updated.id).isEqualTo(12L) // never the incoming file's id (999)
            assertThat(updated.position).isEqualTo(3L)
            assertThat(updated.fsrsDueAt).isEqualTo(1234L)
            assertThat(updated.fsrsCardJson).isEqualTo("existing-progress-json")
            assertThat(updated.translation).isEqualTo("new word")
            assertThat(updated.backwardPromptOverride).isEqualTo("new prompt")
            assertThat(updated.backwardAnswerOverride).isEqualTo("new answer")
            // bidirectional is deliberately NOT taken from the import - flipping it during a
            // routine merge could silently disable a card's configured backward review.
            assertThat(updated.bidirectional).isFalse()
        }

    @Test
    fun `importFromUri reports parser errors as PARSE_ERROR`() =
        runTest {
            val parser =
                object : VocabularyParser {
                    override val id: String = "apkg"
                    override val titleResId: Int = R.string.settings_import_option_anki_apkg
                    override val descriptionResId: Int? = R.string.settings_import_option_anki_apkg_desc
                    override val supportedExtensions: Set<String> = setOf("apkg")
                    override val mimeTypes: List<String> = listOf("application/apkg")

                    override fun parse(file: File): List<VocabularyItem> = throw IllegalArgumentException("bad")
                }
            val manager = buildManager(parsers = setOf(parser))
            val tempFile = File.createTempFile("deck", ".apkg")
            tempFile.writeText("placeholder")
            val uri = Uri.fromFile(tempFile)

            val result = manager.importFromUri(appContext, parser.id, uri)

            assertThat(result).isEqualTo(VocabularyImportResult.Failure(VocabularyImportFailureReason.PARSE_ERROR))
            coVerify(exactly = 0) { vocabularyDao.applyImportBatch(any(), any()) }
        }

    @Test
    fun `export then import json into an empty library re-inserts with a fresh id, same position`() =
        runTest {
            val parser = JsonVocabularyParser()
            val manager = buildManager(parsers = setOf(parser))
            val entity =
                VocabularyEntity(
                    id = 9,
                    word = "schule",
                    translation = "school",
                    createdAt = 111L,
                    lastShownAt = 222L,
                    correctCount = 3,
                    incorrectCount = 4,
                    fsrsCardJson = "{\"card\":2}",
                    fsrsDueAt = 333L,
                    position = 55L,
                )
            every { vocabularyDao.getAllVocabulary() } returns flowOf(listOf(entity))
            coEvery { vocabularyDao.getVocabularyByWords(any()) } returns emptyList()
            coEvery { vocabularyDao.applyImportBatch(any(), any()) } returns Unit

            val tempFile = File.createTempFile("export", ".json")
            val uri = Uri.fromFile(tempFile)

            assertThat(manager.exportToUri(appContext, uri).isSuccess).isTrue()

            val importResult = manager.importFromUri(appContext, parser.id, uri)

            assertThat(importResult).isEqualTo(VocabularyImportResult.Success(importedCount = 1))
            val toInsert = slot<List<VocabularyEntity>>()
            coVerify { vocabularyDao.applyImportBatch(capture(toInsert), any()) }
            val reimported = toInsert.captured.single()
            assertThat(reimported.id).isEqualTo(0L)
            assertThat(reimported.position).isEqualTo(55L)
            assertThat(reimported).isEqualTo(entity.copy(id = 0L))
        }
}
