package com.procrastilearn.app.data.export

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

private const val FIELD_SCHEMA_VERSION = "schemaVersion"
private const val FIELD_EXPORTED_AT = "exportedAt"
private const val FIELD_APP_VERSION = "appVersion"
private const val FIELD_WORDS = "words"

fun interface SchemaMigration {
    fun migrate(root: JsonObject): JsonObject
}

object V1ToV2 : SchemaMigration {
    private const val TARGET_SCHEMA_VERSION = 2
    private const val UNKNOWN_EXPORTED_AT = 0L
    private const val UNKNOWN_APP_VERSION = "unknown"

    override fun migrate(root: JsonObject): JsonObject =
        buildJsonObject {
            put(FIELD_SCHEMA_VERSION, JsonPrimitive(TARGET_SCHEMA_VERSION))
            put(FIELD_EXPORTED_AT, JsonPrimitive(UNKNOWN_EXPORTED_AT))
            put(FIELD_APP_VERSION, JsonPrimitive(UNKNOWN_APP_VERSION))
            put(FIELD_WORDS, root.getValue(FIELD_WORDS))
        }
}

object V2ToV3 : SchemaMigration {
    private const val TARGET_SCHEMA_VERSION = 3

    override fun migrate(root: JsonObject): JsonObject =
        buildJsonObject {
            put(FIELD_SCHEMA_VERSION, JsonPrimitive(TARGET_SCHEMA_VERSION))
            put(FIELD_EXPORTED_AT, root.getValue(FIELD_EXPORTED_AT))
            put(FIELD_APP_VERSION, root.getValue(FIELD_APP_VERSION))
            put(FIELD_WORDS, root.getValue(FIELD_WORDS))
        }
}

object V3ToV4 : SchemaMigration {
    private const val TARGET_SCHEMA_VERSION = 4

    override fun migrate(root: JsonObject): JsonObject =
        buildJsonObject {
            put(FIELD_SCHEMA_VERSION, JsonPrimitive(TARGET_SCHEMA_VERSION))
            put(FIELD_EXPORTED_AT, root.getValue(FIELD_EXPORTED_AT))
            put(FIELD_APP_VERSION, root.getValue(FIELD_APP_VERSION))
            put(FIELD_WORDS, root.getValue(FIELD_WORDS))
        }
}

object Migrations {
    private val steps: Map<Int, SchemaMigration> = mapOf(1 to V1ToV2, 2 to V2ToV3, 3 to V3ToV4)

    fun migrate(
        root: JsonObject,
        from: Int,
    ): JsonObject =
        (from until CURRENT_SCHEMA_VERSION).fold(root) { acc, v ->
            (steps[v] ?: error("missing migration $v -> ${v + 1}")).migrate(acc)
        }
}
