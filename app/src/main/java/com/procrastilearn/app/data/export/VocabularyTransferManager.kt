package com.procrastilearn.app.data.export

import android.content.Context
import android.net.Uri
import com.procrastilearn.app.data.local.dao.VocabularyDao
import com.procrastilearn.app.data.local.mapper.toEntity
import com.procrastilearn.app.data.local.mapper.toExportItem
import com.procrastilearn.app.domain.model.VocabularyExportItem
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.domain.parser.VocabularyExportParser
import com.procrastilearn.app.domain.parser.VocabularyImportOption
import com.procrastilearn.app.domain.parser.VocabularyParser
import com.procrastilearn.app.domain.repository.VocabularyCatalogRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import javax.inject.Inject

class VocabularyTransferManager
    @Inject
    constructor(
        private val vocabularyDao: VocabularyDao,
        private val vocabularyRepository: VocabularyCatalogRepository,
        private val parsers: Set<@JvmSuppressWildcards VocabularyParser>,
        private val ioDispatcher: CoroutineDispatcher,
    ) {
        val importOptions: List<VocabularyImportOption> =
            parsers
                .map { parser ->
                    VocabularyImportOption(
                        id = parser.id,
                        titleResId = parser.titleResId,
                        descriptionResId = parser.descriptionResId,
                        mimeTypes = parser.mimeTypes,
                        extensions = parser.supportedExtensions,
                    )
                }.sortedBy { it.titleResId }

        suspend fun exportToUri(
            context: Context,
            uri: Uri,
        ): Result<Unit> =
            withContext(ioDispatcher) {
                runCatching {
                    val items = vocabularyDao.getAllVocabulary().first().map { it.toExportItem() }
                    val encoded = VocabularyExportSerializer.encode(items)

                    val outputStream = context.contentResolver.openOutputStream(uri)
                    checkNotNull(outputStream) { "Failed to open output stream for $uri" }
                    outputStream.use { out ->
                        out.writer(Charsets.UTF_8).use { writer ->
                            writer.write(encoded)
                            writer.flush()
                        }
                    }
                }
            }

        suspend fun importFromUri(
            context: Context,
            optionId: String,
            uri: Uri,
        ): VocabularyImportResult =
            withContext(ioDispatcher) {
                val parser = findParser(optionId)
                if (parser == null) {
                    return@withContext VocabularyImportResult.Failure(VocabularyImportFailureReason.UNSUPPORTED_FORMAT)
                }

                val suffix =
                    parser.supportedExtensions.firstOrNull()?.let { ".$it" }
                        ?: ".tmp"
                val tempFile = File.createTempFile("pl-import-", suffix, context.cacheDir)

                try {
                    performImport(context, parser, uri, tempFile)
                } finally {
                    tempFile.delete()
                }
            }

        private suspend fun performImport(
            context: Context,
            parser: VocabularyParser,
            uri: Uri,
            tempFile: File,
        ): VocabularyImportResult =
            runCatching { importFromStream(context, parser, uri, tempFile) }
                .fold(
                    onSuccess = { it },
                    onFailure = { exception ->
                        when (exception) {
                            is CancellationException -> throw exception
                            is UnsupportedSchemaVersionException ->
                                VocabularyImportResult.Failure(VocabularyImportFailureReason.UNSUPPORTED_SCHEMA_VERSION)
                            is IllegalArgumentException ->
                                VocabularyImportResult.Failure(VocabularyImportFailureReason.PARSE_ERROR)
                            else -> VocabularyImportResult.Failure(VocabularyImportFailureReason.FILE_ERROR)
                        }
                    },
                )

        private suspend fun importFromStream(
            context: Context,
            parser: VocabularyParser,
            uri: Uri,
            tempFile: File,
        ): VocabularyImportResult {
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                return VocabularyImportResult.Failure(VocabularyImportFailureReason.FILE_ERROR)
            }
            copyToTempFile(inputStream, tempFile)
            return parseAndImport(parser, tempFile)
        }

        private fun copyToTempFile(
            inputStream: InputStream,
            tempFile: File,
        ) {
            inputStream.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
        }

        private suspend fun parseAndImport(
            parser: VocabularyParser,
            tempFile: File,
        ): VocabularyImportResult =
            if (parser is VocabularyExportParser) {
                val items = parser.parseExport(tempFile)
                importExportItems(items)
                VocabularyImportResult.Success(items.size)
            } else {
                val items = parser.parse(tempFile)
                importItems(items)
                VocabularyImportResult.Success(items.size)
            }

        private suspend fun importItems(items: List<VocabularyItem>) {
            items.forEach { item ->
                vocabularyRepository.addVocabularyItem(item)
            }
        }

        private suspend fun importExportItems(items: List<VocabularyExportItem>) {
            if (items.isEmpty()) return
            vocabularyDao.insertAllVocabulary(items.map { it.toEntity() })
        }

        private fun findParser(optionId: String): VocabularyParser? =
            parsers.firstOrNull { parser ->
                parser.id.equals(optionId, ignoreCase = true) ||
                    parser.supportedExtensions.any { ext -> ext.equals(optionId, ignoreCase = true) }
            }
    }

sealed interface VocabularyImportResult {
    data class Success(
        val importedCount: Int,
    ) : VocabularyImportResult

    data class Failure(
        val reason: VocabularyImportFailureReason,
    ) : VocabularyImportResult
}

enum class VocabularyImportFailureReason {
    UNSUPPORTED_FORMAT,
    FILE_ERROR,
    PARSE_ERROR,
    UNSUPPORTED_SCHEMA_VERSION,
}
