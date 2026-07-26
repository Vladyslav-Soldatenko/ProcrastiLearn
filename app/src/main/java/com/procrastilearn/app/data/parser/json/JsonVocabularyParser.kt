package com.procrastilearn.app.data.parser.json

import com.procrastilearn.app.R
import com.procrastilearn.app.data.export.ImportOutcome
import com.procrastilearn.app.data.export.UnsupportedSchemaVersionException
import com.procrastilearn.app.data.export.VocabularyExportSerializer
import com.procrastilearn.app.domain.model.VocabularyExportItem
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.domain.parser.VocabularyExportParser
import com.procrastilearn.app.domain.parser.VocabularyParser
import kotlinx.serialization.SerializationException
import java.io.File
import javax.inject.Inject

class JsonVocabularyParser @Inject constructor() : VocabularyParser, VocabularyExportParser {
    override val id: String = "json"

    override val titleResId: Int = R.string.settings_import_option_json

    override val descriptionResId: Int = R.string.settings_import_option_json_desc

    override val supportedExtensions: Set<String> = setOf("json")

    override val mimeTypes: List<String> = listOf("application/json", "text/json")

    override fun parse(file: File): List<VocabularyItem> =
        parseExport(file).map { item ->
            VocabularyItem(
                id = item.id,
                word = item.word,
                translation = item.translation,
                isNew = item.correctCount == 0 && item.incorrectCount == 0,
            )
        }

    override fun parseExport(file: File): List<VocabularyExportItem> {
        require(file.exists() && file.isFile) { "Cannot parse from ${file.path}: file does not exist." }

        val raw = file.readText(Charsets.UTF_8)
        val outcome =
            try {
                VocabularyExportSerializer.decode(raw)
            } catch (exception: SerializationException) {
                throw IllegalArgumentException("Invalid JSON export format.", exception)
            }

        return when (outcome) {
            is ImportOutcome.Success -> outcome.items
            is ImportOutcome.SchemaTooNew -> throw UnsupportedSchemaVersionException(outcome.schemaVersion)
        }
    }
}
