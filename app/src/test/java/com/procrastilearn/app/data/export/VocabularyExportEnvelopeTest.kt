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
                exportedAt = 1785010756357,
                appVersion = "1.3.1",
                words =
                    listOf(
                        VocabularyExportItem(
                            id = 1,
                            word = "test",
                            translation = "test123",
                            createdAt = 1785010756357,
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
}
