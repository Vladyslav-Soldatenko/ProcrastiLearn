package com.procrastilearn.app.data.parser.anki

import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.R
import com.procrastilearn.app.domain.model.VocabularyItem
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class AnkiApkgVocabularyParserTest {
    private val parser = AnkiApkgVocabularyParser()

    @Test
    fun `parses vocabulary items from apkg`() {
        val deckFile = loadResource("import/anki/procrastilearn-test-deck.apkg")

        val result = parser.parse(deckFile)

        assertThat(result)
            .containsExactly(
                VocabularyItem(
                    word = "TestTitle",
                    translation = "testBack description",
                    isNew = true,
                ),
                VocabularyItem(
                    word = "test2",
                    translation = "test description2",
                    isNew = true,
                ),
                VocabularyItem(
                    word = "bold italic underline superscript subscript difCollor textHighlight",
                    translation =
                        listOf(
                            "bold italic underline superscript subscript difCollor textHighlight ",
                            "",
                            "ul1",
                            "ul2",
                            "",
                            "ol1",
                            "ol2",
                        ).joinToString(separator = "\n"),
                    isNew = true,
                ),
            ).inOrder()
    }

    @Test
    fun `provides metadata for ui`() {
        assertThat(parser.id).isEqualTo("apkg")
        assertThat(parser.supportedExtensions).containsExactly("apkg")
        assertThat(parser.mimeTypes).contains("application/apkg")
        assertThat(parser.titleResId).isEqualTo(R.string.settings_import_option_anki_apkg)
        assertThat(parser.descriptionResId).isEqualTo(R.string.settings_import_option_anki_apkg_desc)
    }

    private fun loadResource(path: String): File {
        val url =
            checkNotNull(javaClass.classLoader?.getResource(path)) {
                "Resource at $path was not found in the test resources."
            }
        return File(url.toURI())
    }
}
