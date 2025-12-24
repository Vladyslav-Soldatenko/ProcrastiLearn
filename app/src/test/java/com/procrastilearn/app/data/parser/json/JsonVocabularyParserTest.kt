package com.procrastilearn.app.data.parser.json

import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.domain.model.VocabularyExportItem
import org.junit.runner.RunWith
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [33],
    manifest = Config.NONE,
)
class JsonVocabularyParserTest {
    private val parser = JsonVocabularyParser()

    @Test
    fun `parseExport reads full export payload`() {
        val tempFile = File.createTempFile("vocab", ".json")
        tempFile.writeText(
            """
            [
              {
                "id": 1,
                "word": "Haus",
                "translation": "House",
                "createdAt": 10,
                "lastShownAt": null,
                "correctCount": 2,
                "incorrectCount": 1,
                "fsrsCardJson": "{\"c\":1}",
                "fsrsDueAt": 20
              }
            ]
            """.trimIndent(),
        )

        val result = parser.parseExport(tempFile)

        assertThat(result).containsExactly(
            VocabularyExportItem(
                id = 1,
                word = "Haus",
                translation = "House",
                createdAt = 10,
                lastShownAt = null,
                correctCount = 2,
                incorrectCount = 1,
                fsrsCardJson = "{\"c\":1}",
                fsrsDueAt = 20,
            ),
        )
    }
}
