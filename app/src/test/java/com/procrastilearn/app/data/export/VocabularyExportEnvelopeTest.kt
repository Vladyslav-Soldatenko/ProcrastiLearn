package com.procrastilearn.app.data.export

import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.domain.model.VocabularyExportItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test

class VocabularyExportEnvelopeTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
            isLenient = false
            prettyPrint = true
        }

    @Test
    fun `envelope with items round trips through kotlinx serialization`() {
        val envelope =
            VocabularyExportEnvelope(
                schemaVersion = CURRENT_SCHEMA_VERSION,
                exportedAt = 1_785_010_756_357,
                appVersion = "1.3.1",
                words =
                    listOf(
                        VocabularyExportItem(
                            id = 1,
                            word = "test",
                            translation = "test123",
                            createdAt = 1_785_010_756_357,
                            lastShownAt = null,
                            correctCount = 0,
                            incorrectCount = 0,
                            fsrsCardJson = "{}",
                            fsrsDueAt = 0,
                        ),
                    ),
            )

        val encoded = json.encodeToString(envelope)
        val decoded = json.decodeFromString<VocabularyExportEnvelope>(encoded)

        assertThat(decoded).isEqualTo(envelope)
    }

    @Test
    fun `envelope with a bidirectional item round trips every backward field`() {
        val envelope =
            VocabularyExportEnvelope(
                schemaVersion = CURRENT_SCHEMA_VERSION,
                exportedAt = 1_785_010_756_357,
                appVersion = "1.3.1",
                words =
                    listOf(
                        VocabularyExportItem(
                            id = 2,
                            word = "run",
                            translation = "бігати",
                            createdAt = 1_785_010_756_357,
                            lastShownAt = 1_785_010_756_400,
                            correctCount = 2,
                            incorrectCount = 1,
                            fsrsCardJson = "{\"fwd\":1}",
                            fsrsDueAt = 1_785_010_800_000,
                            bidirectional = true,
                            backwardFsrsCardJson = "{\"bwd\":1}",
                            backwardFsrsDueAt = 1_785_010_900_000,
                            backwardCorrectCount = 3,
                            backwardIncorrectCount = 4,
                            backwardPromptOverride = "custom prompt",
                            backwardAnswerOverride = "custom answer",
                        ),
                    ),
            )

        val encoded = json.encodeToString(envelope)
        val decoded = json.decodeFromString<VocabularyExportEnvelope>(encoded)

        assertThat(decoded).isEqualTo(envelope)
    }
}
