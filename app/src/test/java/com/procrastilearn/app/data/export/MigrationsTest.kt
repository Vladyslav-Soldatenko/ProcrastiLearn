package com.procrastilearn.app.data.export

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class MigrationsTest {
    private val bareV1Array =
        buildJsonArray {
            addJsonObject {
                put("id", JsonPrimitive(1))
                put("word", JsonPrimitive("test"))
            }
        }

    @Test
    fun `V1ToV2 wraps the bare array in an envelope with a synthesised schemaVersion`() {
        val root = buildJsonObject { put("words", bareV1Array) }

        val migrated = V1ToV2.migrate(root)

        assertThat(migrated.getValue("schemaVersion").jsonPrimitive.int).isEqualTo(2)
        assertThat(migrated.getValue("words")).isEqualTo(bareV1Array)
    }

    @Test
    fun `V1ToV2 preserves every word entry unchanged`() {
        val root = buildJsonObject { put("words", bareV1Array) }

        val migrated = V1ToV2.migrate(root)

        assertThat(migrated.getValue("words")).isEqualTo(bareV1Array)
    }

    @Test
    fun `migrate from 1 runs V1ToV2 and lands exactly on the current schema version`() {
        val root = buildJsonObject { put("words", bareV1Array) }

        val migrated = Migrations.migrate(root, from = 1)

        assertThat(migrated.getValue("schemaVersion").jsonPrimitive.int).isEqualTo(CURRENT_SCHEMA_VERSION)
    }

    @Test
    fun `migrate from the current version is a no-op`() {
        val root =
            buildJsonObject {
                put("schemaVersion", JsonPrimitive(CURRENT_SCHEMA_VERSION))
                put("words", bareV1Array)
            }

        val migrated = Migrations.migrate(root, from = CURRENT_SCHEMA_VERSION)

        assertThat(migrated).isEqualTo(root)
    }
}
