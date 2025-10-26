package com.procrastilearn.app.data.parser.anki

import android.database.sqlite.SQLiteDatabase
import androidx.core.text.HtmlCompat
import com.github.luben.zstd.ZstdInputStream
import com.procrastilearn.app.R
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.domain.parser.VocabularyParser
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.util.zip.ZipInputStream
import javax.inject.Inject

private const val NOTE_FIELDS_SEPARATOR = '\u001f'

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

    private fun extractCollectionDatabase(
        source: File,
        tempDir: File,
    ): File {
        var extractedDb: File? = null
        var extractedFromModernArchive = false

        ZipInputStream(BufferedInputStream(source.inputStream())).use { zipStream ->
            var entry = zipStream.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    when (entry.name) {
                        "collection.anki21b" -> {
                            val compressedBytes = zipStream.readBytes()
                            extractedDb =
                                File(tempDir, "collection.anki2").also { target ->
                                    decompressZstd(ByteArrayInputStream(compressedBytes), target)
                                }
                            // Newer Anki exports include a zstd-compressed sqlite DB; prefer this version.
                            extractedFromModernArchive = true
                        }

                        "collection.anki21" -> {
                            if (!extractedFromModernArchive) {
                                extractedDb =
                                    File(tempDir, "collection.anki2").also { target ->
                                        target.outputStream().use { output -> zipStream.copyTo(output) }
                                    }
                            }
                        }

                        "collection.anki2" -> {
                            if (!extractedFromModernArchive) {
                                extractedDb =
                                    File(tempDir, "collection.anki2").also { target ->
                                        target.outputStream().use { output -> zipStream.copyTo(output) }
                                    }
                            }
                        }
                    }
                }
                zipStream.closeEntry()
                entry = zipStream.nextEntry
            }
        }

        return requireNotNull(extractedDb) {
            "The provided apkg archive does not contain a supported Anki collection database."
        }
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

    private fun readVocabularyItems(databaseFile: File): List<VocabularyItem> {
        val database = SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READONLY)
        return database.use { db ->
            db.rawQuery("SELECT flds FROM notes", null).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val rawFields = cursor.getString(0)
                        parseVocabularyItem(rawFields)?.let(::add)
                    }
                }
            }
        }
    }

    private fun parseVocabularyItem(rawFields: String): VocabularyItem? {
        val fields = rawFields.split(NOTE_FIELDS_SEPARATOR)
        val word = normalizeField(fields.getOrNull(0).orEmpty())
        val translation =
            fields
                .drop(1)
                .map { normalizeField(it) }
                .filter { it.isNotBlank() }
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
        val trimmed = value.replace("\r\n", "\n").trim()
        if (trimmed.isEmpty()) return ""
        val plainText = HtmlCompat.fromHtml(trimmed, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
        return plainText.replace('\u00a0', ' ').trim()
    }
}
