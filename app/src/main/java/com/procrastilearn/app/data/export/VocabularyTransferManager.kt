package com.procrastilearn.app.data.export

import android.content.Context
import android.net.Uri
import com.procrastilearn.app.data.local.dao.VocabularyDao
import com.procrastilearn.app.data.local.entity.VocabularyEntity
import com.procrastilearn.app.data.local.mapper.toEntity
import com.procrastilearn.app.data.local.mapper.toExportItem
import com.procrastilearn.app.domain.model.VocabularyExportItem
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.domain.parser.VocabularyExportParser
import com.procrastilearn.app.domain.parser.VocabularyImportOption
import com.procrastilearn.app.domain.parser.VocabularyParser
import io.github.openspacedrepetition.Card
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import javax.inject.Inject

// SQLite's default SQLITE_MAX_VARIABLE_NUMBER is 999 on older Android versions; stay
// comfortably under it when binding word lists for the duplicate lookup below.
private const val MAX_SQLITE_BIND_ARGS = 900

class VocabularyTransferManager
    @Inject
    constructor(
        private val vocabularyDao: VocabularyDao,
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

        // Imports a parsed batch, merging against existing rows by word (the app's real
        // identity key - see the unique NOCASE index on VocabularyEntity.word) rather than by
        // row id, which is per-install and can coincidentally collide across devices/exports.
        // A word that already exists gets its content fields updated in place, preserving its
        // id/position/FSRS progress untouched. A genuinely new word is appended to the end of
        // the position queue and always inserted with id = 0 so Room autogenerates a fresh id -
        // never carrying over an id from the imported file, which could otherwise coincidentally
        // collide with an unrelated existing row (see importExportItems).
        private suspend fun importItems(items: List<VocabularyItem>) {
            if (items.isEmpty()) return
            val deduped = dedupeByWord(items) { it.word }
            val existingByWord = lookupExistingByWord(deduped.values.map { it.word })

            val toInsert = mutableListOf<VocabularyEntity>()
            val toUpdate = mutableListOf<VocabularyEntity>()
            var nextPosition = vocabularyDao.getMaxPosition() + 1
            for (item in deduped.values) {
                val existing = existingByWord[item.word.lowercase()]
                if (existing != null) {
                    toUpdate += existing.copy(translation = item.translation)
                } else {
                    val cardJson = Card.builder().build().toJson()
                    toInsert +=
                        item
                            .toEntity(fsrsCardJson = cardJson, fsrsDueAt = 0L, position = nextPosition++)
                            .copy(id = 0L)
                }
            }
            vocabularyDao.applyImportBatch(toInsert, toUpdate)
        }

        // Merges a JSON backup/export against existing rows the same way importItems does (by
        // word, not by id). Matched words update in place, preserving id/position/progress. New
        // words are inserted with id = 0 (never the id from the export file) so a coincidental
        // id collision with an unrelated row on this device (e.g. restoring a backup from a
        // different install) can never abort the whole batch; their exported `position` carries
        // through verbatim, same as any other export field.
        private suspend fun importExportItems(items: List<VocabularyExportItem>) {
            if (items.isEmpty()) return
            val deduped = dedupeByWord(items) { it.word }
            val existingByWord = lookupExistingByWord(deduped.values.map { it.word })

            val toInsert = mutableListOf<VocabularyEntity>()
            val toUpdate = mutableListOf<VocabularyEntity>()
            for (item in deduped.values) {
                val existing = existingByWord[item.word.lowercase()]
                if (existing != null) {
                    toUpdate +=
                        existing.copy(
                            translation = item.translation,
                            backwardPromptOverride = item.backwardPromptOverride,
                            backwardAnswerOverride = item.backwardAnswerOverride,
                        )
                } else {
                    toInsert += item.toEntity().copy(id = 0L)
                }
            }
            vocabularyDao.applyImportBatch(toInsert, toUpdate)
        }

        // Last-occurrence-wins de-dup by case-insensitive word, preserving encounter order for
        // position assignment. A well-formed deck essentially never has literal duplicate
        // front-text notes, but this keeps the (rare/adversarial) case well-defined.
        private fun <T> dedupeByWord(
            items: List<T>,
            wordOf: (T) -> String,
        ): LinkedHashMap<String, T> {
            val map = LinkedHashMap<String, T>()
            items.forEach { map[wordOf(it).lowercase()] = it }
            return map
        }

        private suspend fun lookupExistingByWord(words: Collection<String>): Map<String, VocabularyEntity> =
            words
                .toList()
                .chunked(MAX_SQLITE_BIND_ARGS)
                .flatMap { vocabularyDao.getVocabularyByWords(it) }
                .associateBy { it.word.lowercase() }

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
