package com.procrastilearn.app.data.export

import com.procrastilearn.app.BuildConfig
import com.procrastilearn.app.domain.model.VocabularyExportItem
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

object VocabularyExportSerializer {
    private const val LEGACY_SCHEMA_VERSION = 1

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
            isLenient = false
            prettyPrint = true
        }

    fun encode(items: List<VocabularyExportItem>): String {
        val envelope =
            VocabularyExportEnvelope(
                schemaVersion = CURRENT_SCHEMA_VERSION,
                exportedAt = System.currentTimeMillis(),
                appVersion = BuildConfig.VERSION_NAME,
                words = items,
            )
        return json.encodeToString(envelope)
    }

    fun decode(raw: String): ImportOutcome {
        val root =
            when (val element = json.parseToJsonElement(raw)) {
                is JsonArray -> buildJsonObject { put("words", element) }
                is JsonObject -> element
                else -> throw SerializationException("Export root must be a JSON object or array.")
            }
        val schemaVersion = root["schemaVersion"]?.jsonPrimitive?.intOrNull ?: LEGACY_SCHEMA_VERSION
        if (schemaVersion > CURRENT_SCHEMA_VERSION) {
            return ImportOutcome.SchemaTooNew(schemaVersion)
        }

        val migrated = Migrations.migrate(root, from = schemaVersion)
        val envelope = json.decodeFromJsonElement<VocabularyExportEnvelope>(migrated)
        return ImportOutcome.Success(envelope.words)
    }
}
