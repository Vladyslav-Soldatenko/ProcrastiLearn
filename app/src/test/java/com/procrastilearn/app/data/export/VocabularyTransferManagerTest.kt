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
import com.procrastilearn.app.domain.repository.VocabularyRepository
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
    private lateinit var vocabularyRepository: VocabularyRepository
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
        vocabularyRepository = mockk(relaxed = true)
        appContext = RuntimeEnvironment.getApplication()
    }

    private fun buildManager(parsers: Set<VocabularyParser> = setOf(apkgParser)): VocabularyTransferManager =
        VocabularyTransferManager(
            vocabularyDao = vocabularyDao,
            vocabularyRepository = vocabularyRepository,
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
    fun `exportToUri writes serialized json and returns true`() =
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

            assertThat(result).isTrue()
            val payload = tempFile.readText()
            assertThat(payload).contains("\"id\": 1")
            assertThat(payload).contains("\"word\": \"Haus\"")
            assertThat(payload).contains("\"translation\": \"House\"")
        }

    @Test
    fun `exportToUri returns false on exception`() =
        runTest {
            val manager = buildManager()
            val tempFile = File.createTempFile("export", ".json")
            val uri = Uri.fromFile(tempFile)
            every { vocabularyDao.getAllVocabulary() } returns flow { throw IllegalStateException("boom") }

            val result = manager.exportToUri(appContext, uri)

            assertThat(result).isFalse()
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

            assertThat(result).isEqualTo(VocabularyImportResult.Failure(VocabularyImportFailureReason.UNSUPPORTED_FORMAT))
            coVerify(exactly = 0) { vocabularyRepository.addVocabularyItem(any()) }
        }

    @Test
    fun `importFromUri delegates non-export parser items to the repository`() =
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
            coEvery { vocabularyRepository.addVocabularyItem(any()) } returns Unit
            val manager = buildManager(parsers = setOf(parser))
            val tempFile = File.createTempFile("deck", ".apkg")
            tempFile.writeText("placeholder")
            val uri = Uri.fromFile(tempFile)

            val result = manager.importFromUri(appContext, parser.id, uri)

            assertThat(result).isEqualTo(VocabularyImportResult.Success(importedCount = 1))
            coVerify { vocabularyRepository.addVocabularyItem(parsedItem) }
        }

    @Test
    fun `importFromUri inserts full entities for an export parser`() =
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
            coEvery { vocabularyDao.insertAllVocabulary(any()) } returns Unit
            val manager = buildManager(parsers = setOf(parser))
            val tempFile = File.createTempFile("deck", ".json")
            tempFile.writeText("placeholder")
            val uri = Uri.fromFile(tempFile)

            val result = manager.importFromUri(appContext, parser.id, uri)

            assertThat(result).isEqualTo(VocabularyImportResult.Success(importedCount = 1))
            coVerify { vocabularyDao.insertAllVocabulary(any()) }
            coVerify(exactly = 0) { vocabularyRepository.addVocabularyItem(any()) }
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
            coVerify(exactly = 0) { vocabularyRepository.addVocabularyItem(any()) }
        }

    @Test
    fun `export then import json preserves all entity fields`() =
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
                )
            every { vocabularyDao.getAllVocabulary() } returns flowOf(listOf(entity))
            coEvery { vocabularyDao.insertAllVocabulary(any()) } returns Unit

            val tempFile = File.createTempFile("export", ".json")
            val uri = Uri.fromFile(tempFile)

            assertThat(manager.exportToUri(appContext, uri)).isTrue()

            val importResult = manager.importFromUri(appContext, parser.id, uri)

            assertThat(importResult).isEqualTo(VocabularyImportResult.Success(importedCount = 1))
            val inserted = slot<List<VocabularyEntity>>()
            coVerify { vocabularyDao.insertAllVocabulary(capture(inserted)) }
            assertThat(inserted.captured).containsExactly(entity)
        }
}
