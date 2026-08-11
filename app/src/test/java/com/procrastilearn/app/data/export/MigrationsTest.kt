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

    private val v2Envelope =
        buildJsonObject {
            put("schemaVersion", JsonPrimitive(2))
            put("exportedAt", JsonPrimitive(1_700_000_000_000L))
            put("appVersion", JsonPrimitive("1.2.0"))
            put("words", bareV1Array)
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

    @Test
    fun `V2ToV3 re-stamps schemaVersion to 3 and preserves words unchanged`() {
        val migrated = V2ToV3.migrate(v2Envelope)

        assertThat(migrated.getValue("schemaVersion").jsonPrimitive.int).isEqualTo(3)
        assertThat(migrated.getValue("words")).isEqualTo(bareV1Array)
    }

    @Test
    fun `V2ToV3 preserves exportedAt and appVersion unchanged`() {
        val migrated = V2ToV3.migrate(v2Envelope)

        assertThat(migrated.getValue("exportedAt")).isEqualTo(v2Envelope.getValue("exportedAt"))
        assertThat(migrated.getValue("appVersion")).isEqualTo(v2Envelope.getValue("appVersion"))
    }

    @Test
    fun `migrate from 2 runs V2ToV3 and lands exactly on CURRENT_SCHEMA_VERSION`() {
        val migrated = Migrations.migrate(v2Envelope, from = 2)

        assertThat(migrated.getValue("schemaVersion").jsonPrimitive.int).isEqualTo(CURRENT_SCHEMA_VERSION)
    }

    @Test
    fun `migrate from 1 chains V1ToV2 then V2ToV3 and lands on CURRENT_SCHEMA_VERSION`() {
        val root = buildJsonObject { put("words", bareV1Array) }

        val migrated = Migrations.migrate(root, from = 1)

        assertThat(migrated.getValue("schemaVersion").jsonPrimitive.int).isEqualTo(CURRENT_SCHEMA_VERSION)
        assertThat(migrated.getValue("words")).isEqualTo(bareV1Array)
    }

    @Test
    fun `V2ToV3 migration decodes a genuine v2 payload with backward fields correctly defaulted`() {
        val rawV2Json =
            """
            {
              "schemaVersion": 2,
              "exportedAt": 1700000000000,
              "appVersion": "1.2.0",
              "words": [
                {
                  "id": 1,
                  "word": "Baum",
                  "translation": "tree",
                  "createdAt": 1000,
                  "lastShownAt": 2000,
                  "correctCount": 3,
                  "incorrectCount": 1,
                  "fsrsCardJson": "{\"cardId\":42}",
                  "fsrsDueAt": 5000
                }
              ]
            }
            """.trimIndent()

        val outcome = VocabularyExportSerializer.decode(rawV2Json)

        check(outcome is ImportOutcome.Success) { "Expected a successful decode, got $outcome" }
        val item = outcome.items.single()
        assertThat(item.word).isEqualTo("Baum")
        assertThat(item.correctCount).isEqualTo(3)
        assertThat(item.bidirectional).isFalse()
        assertThat(item.backwardFsrsCardJson).isEmpty()
        assertThat(item.backwardFsrsDueAt).isEqualTo(0L)
        assertThat(item.backwardCorrectCount).isEqualTo(0)
        assertThat(item.backwardIncorrectCount).isEqualTo(0)
        assertThat(item.backwardPromptOverride).isNull()
        assertThat(item.backwardAnswerOverride).isNull()
    }
}
