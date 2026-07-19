package com.procrastilearn.app.data.parser.anki

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.core.text.HtmlCompat
import com.github.luben.zstd.ZstdInputStream
import com.procrastilearn.app.R
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.domain.parser.VocabularyParser
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.util.zip.ZipInputStream
import javax.inject.Inject

private const val NOTE_FIELDS_SEPARATOR = ''

private val IMG_TAG_REGEX = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE)
private val SOUND_TAG_REGEX = Regex("""\[sound:[^]]*]""", RegexOption.IGNORE_CASE)
private val FIELD_REFERENCE_REGEX = Regex("""\{\{([^{}]+)\}\}""")

class AnkiApkgVocabularyParser @Inject constructor() : VocabularyParser {
    override val id: String = "apkg"

    override val titleResId: Int = R.string.settings_import_option_anki_apkg

    override val descriptionResId: Int = R.string.settings_import_option_anki_apkg_desc

    override val supportedExtensions: Set<String> = setOf("apkg")

    override val mimeTypes: List<String> =
        listOf(
            "application/apkg",
            "application/vnd.anki",
            "application/zip",
            "application/octet-stream",
        )

    override fun parse(file: File): List<VocabularyItem> {
        require(file.exists() && file.isFile) { "Cannot parse from ${file.path}: file does not exist." }

        val tempDir = Files.createTempDirectory("procrastilearn-anki").toFile()
        return try {
            val databaseFile = extractCollectionDatabase(file, tempDir)
            readVocabularyItems(databaseFile)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private class ExtractionState {
        var extractedDb: File? = null
        var extractedPriority: Int = -1
    }

    private fun extractCollectionDatabase(
        source: File,
        tempDir: File,
    ): File {
        val state = ExtractionState()

        ZipInputStream(BufferedInputStream(source.inputStream())).use { zipStream ->
            var entry = zipStream.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    extractZipEntry(entry.name, zipStream, tempDir, state)
                }
                zipStream.closeEntry()
                entry = zipStream.nextEntry
            }
        }

        return requireNotNull(state.extractedDb) {
            "The provided apkg archive does not contain a supported Anki collection database."
        }
    }

    /**
     * apkg archives can carry up to three copies of the collection: the modern zstd-compressed
     * `collection.anki21b`, a plain sqlite `collection.anki21`, and a `collection.anki2` kept only
     * for ancient Anki versions (real decks often ship it as a one-note stub telling old clients to
     * upgrade). Whichever variant is processed *last* in the zip must not win by default, so each is
     * ranked and a lower-ranked entry is never allowed to replace a higher-ranked one.
     */
    private fun extractZipEntry(
        entryName: String,
        zipStream: ZipInputStream,
        tempDir: File,
        state: ExtractionState,
    ) {
        val priority =
            when (entryName) {
                "collection.anki21b" -> 2
                "collection.anki21" -> 1
                "collection.anki2" -> 0
                else -> return
            }
        if (priority <= state.extractedPriority) return

        if (entryName == "collection.anki21b") {
            extractModernDatabase(zipStream, tempDir, state, priority)
        } else {
            extractLegacyDatabase(zipStream, tempDir, state, priority)
        }
    }

    private fun extractModernDatabase(
        zipStream: ZipInputStream,
        tempDir: File,
        state: ExtractionState,
        priority: Int,
    ) {
        val compressedBytes = zipStream.readBytes()
        val target = File(tempDir, "collection-$priority.anki2")
        val decompressed = runCatching { decompressZstd(ByteArrayInputStream(compressedBytes), target) }
        decompressed.onFailure { throwable ->
            target.delete()
            Log.w(
                "AnkiApkgVocabularyParser",
                "Failed to decompress collection.anki21b, will fall back to legacy DB",
                throwable,
            )
        }
        if (decompressed.isSuccess) {
            state.extractedDb = target
            state.extractedPriority = priority
        }
    }

    private fun extractLegacyDatabase(
        zipStream: ZipInputStream,
        tempDir: File,
        state: ExtractionState,
        priority: Int,
    ) {
        state.extractedDb =
            File(tempDir, "collection-$priority.anki2").also { target ->
                target.outputStream().use { output -> zipStream.copyTo(output) }
            }
        state.extractedPriority = priority
    }

    private fun decompressZstd(
        inputStream: InputStream,
        target: File,
    ) {
        ZstdInputStream(inputStream).use { zstdStream ->
            target.outputStream().use { output ->
                zstdStream.copyTo(output)
            }
        }
    }

    private data class NoteModel(
        val fieldNames: List<String>,
        val wordFieldIndices: List<Int>,
        val isCloze: Boolean,
    )

    private fun readVocabularyItems(databaseFile: File): List<VocabularyItem> {
        val database = SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READONLY)
        return database.use { db ->
            val noteModelsByMid = readNoteModelsByMid(db)
            db.rawQuery("SELECT mid, flds FROM notes", null).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val mid = cursor.getLong(0)
                        val rawFields = cursor.getString(1)
                        parseVocabularyItem(rawFields, noteModelsByMid[mid])?.let(::add)
                    }
                }
            }.also { items ->
                Log.d(
                    "AnkiApkgVocabularyParser",
                    "Parsed ${items.size} vocabulary items from ${databaseFile.name}",
                )
            }
        }
    }

    /**
     * Front/back is a property of a note type's card template, not of a field name — a deck's
     * `qfmt` (question template) tells us exactly which field(s) are shown as the prompt,
     * regardless of what language the deck is in or what its fields happen to be called. Every
     * field referenced in `qfmt` becomes part of `word`; every other field becomes part of
     * `translation`. This reads `qfmt` from the legacy `col.models` JSON blob (schema 11, used by
     * "Legacy Support" exports without a `collection.anki21b`). Newer schema versions move this
     * into normalized `notetypes`/`fields`/`templates` tables with protobuf-encoded template
     * config, which isn't handled here — those notes fall back to field index 0, same as before.
     */
    private fun readNoteModelsByMid(db: SQLiteDatabase): Map<Long, NoteModel> =
        runCatching {
            db.rawQuery("SELECT models FROM col", null).use { cursor ->
                if (!cursor.moveToFirst()) return@use emptyMap()
                val modelsJson = JSONObject(cursor.getString(0))
                buildMap {
                    for (mid in modelsJson.keys()) {
                        val model = modelsJson.getJSONObject(mid)
                        val fieldsArray = model.getJSONArray("flds")
                        val fieldNames =
                            (0 until fieldsArray.length()).map { i ->
                                fieldsArray.getJSONObject(i).getString("name")
                            }
                        val qfmt = model.optJSONArray("tmpls")?.optJSONObject(0)?.optString("qfmt").orEmpty()
                        val sortFieldIndex = model.optInt("sortf", 0)
                        val qfmtFieldIndices = extractFieldReferenceOrder(qfmt, fieldNames)
                        // qfmt referencing nothing recognizable (parsing failure, unusual template)
                        // still needs a word field, so fall back to the note type's sort field.
                        val wordFieldIndices = qfmtFieldIndices.ifEmpty { listOf(sortFieldIndex) }
                        val isCloze = model.optInt("type", 0) == 1
                        put(mid.toLong(), NoteModel(fieldNames, wordFieldIndices, isCloze))
                    }
                }
            }
        }.getOrElse { throwable ->
            Log.w("AnkiApkgVocabularyParser", "Failed to read note types from col.models", throwable)
            emptyMap()
        }

    /**
     * Finds `{{FieldName}}` references in a card template, in the order they appear, resolved
     * against this model's actual field names. Conditional markers (`{{#Field}}`, `{{/Field}}`)
     * and filters (`{{type:Field}}`, `{{furigana:Field}}`) are stripped down to the bare field
     * name; anything left that isn't a real field (`{{Tags}}`, `{{FrontSide}}`, ...) is dropped
     * automatically since it won't match a known field name.
     */
    private fun extractFieldReferenceOrder(
        qfmt: String,
        fieldNames: List<String>,
    ): List<Int> {
        val indices = LinkedHashSet<Int>()
        for (match in FIELD_REFERENCE_REGEX.findAll(qfmt)) {
            var token = match.groupValues[1].trim()
            if (token.isNotEmpty() && token[0] in "#^/") token = token.substring(1)
            val candidateName = token.trim().substringAfterLast(':').trim()
            val index = fieldNames.indexOf(candidateName)
            if (index >= 0) indices.add(index)
        }
        return indices.toList()
    }

    private fun parseVocabularyItem(
        rawFields: String,
        noteModel: NoteModel?,
    ): VocabularyItem? {
        if (noteModel?.isCloze == true) return null

        val normalizedFields = rawFields.split(NOTE_FIELDS_SEPARATOR).map(::normalizeField)
        val wordIndices = (noteModel?.wordFieldIndices ?: listOf(0)).toSet()

        val word =
            wordIndices
                .sorted()
                .mapNotNull { index -> normalizedFields.getOrNull(index) }
                .filter { it.isNotBlank() }
                .joinToString("\n")

        val fieldNames = noteModel?.fieldNames
        val translation =
            normalizedFields
                .withIndex()
                .filter { (index, _) -> index !in wordIndices }
                .mapNotNull { (index, value) ->
                    if (value.isBlank()) return@mapNotNull null
                    val label = fieldNames?.getOrNull(index)
                    if (label != null) "$label: $value" else value
                }
                .joinToString("\n")

        if (word.isBlank() || translation.isBlank()) {
            return null
        }

        return VocabularyItem(
            word = word,
            translation = translation,
            isNew = true,
        )
    }

    private fun normalizeField(value: String): String {
        if (value.isBlank()) return ""
        val withoutMedia =
            value
                .replace(IMG_TAG_REGEX, "")
                .replace(SOUND_TAG_REGEX, "")
        val trimmed = withoutMedia.replace("\r\n", "\n").trim()
        if (trimmed.isEmpty()) return ""
        val plainText = HtmlCompat.fromHtml(trimmed, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
        return plainText.replace(' ', ' ').replace('￼', ' ').trim()
    }
}
