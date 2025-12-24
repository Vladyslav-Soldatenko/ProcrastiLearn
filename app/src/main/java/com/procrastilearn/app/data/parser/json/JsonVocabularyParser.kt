package com.procrastilearn.app.data.parser.json

import com.procrastilearn.app.R
import com.procrastilearn.app.domain.model.VocabularyExportItem
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.domain.parser.VocabularyExportParser
import com.procrastilearn.app.domain.parser.VocabularyParser
import org.json.JSONArray
import org.json.JSONException
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
        val jsonArray =
            try {
                JSONArray(raw)
            } catch (exception: JSONException) {
                throw IllegalArgumentException("Invalid JSON export format.", exception)
            }

        return buildList {
            for (i in 0 until jsonArray.length()) {
                val entry = jsonArray.getJSONObject(i)
                val lastShownAt =
                    if (entry.isNull("lastShownAt")) {
                        null
                    } else {
                        entry.getLong("lastShownAt")
                    }
                add(
                    VocabularyExportItem(
                        id = entry.getLong("id"),
                        word = entry.getString("word"),
                        translation = entry.getString("translation"),
                        createdAt = entry.getLong("createdAt"),
                        lastShownAt = lastShownAt,
                        correctCount = entry.getInt("correctCount"),
                        incorrectCount = entry.getInt("incorrectCount"),
                        fsrsCardJson = entry.getString("fsrsCardJson"),
                        fsrsDueAt = entry.getLong("fsrsDueAt"),
                    ),
                )
            }
        }
    }
}
