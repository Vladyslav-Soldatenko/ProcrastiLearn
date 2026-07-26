package com.procrastilearn.app.data.export

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

fun interface SchemaMigration {
    fun migrate(root: JsonObject): JsonObject
}

object V1ToV2 : SchemaMigration {
    private const val TARGET_SCHEMA_VERSION = 2
    private const val UNKNOWN_EXPORTED_AT = 0L
    private const val UNKNOWN_APP_VERSION = "unknown"

    override fun migrate(root: JsonObject): JsonObject =
        buildJsonObject {
            put("schemaVersion", JsonPrimitive(TARGET_SCHEMA_VERSION))
            put("exportedAt", JsonPrimitive(UNKNOWN_EXPORTED_AT))
            put("appVersion", JsonPrimitive(UNKNOWN_APP_VERSION))
            put("words", root.getValue("words"))
        }
}

object Migrations {
    private val steps: Map<Int, SchemaMigration> = mapOf(1 to V1ToV2)

    fun migrate(
        root: JsonObject,
        from: Int,
    ): JsonObject =
        (from until CURRENT_SCHEMA_VERSION).fold(root) { acc, v ->
            (steps[v] ?: error("missing migration $v -> ${v + 1}")).migrate(acc)
        }
}
