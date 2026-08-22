package com.procrastilearn.app.data.parser.anki

import android.database.sqlite.SQLiteDatabase
import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.R
import com.procrastilearn.app.domain.model.VocabularyItem
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private const val FLDS_SEP = ''

@RunWith(RobolectricTestRunner::class)
class AnkiApkgVocabularyParserTest {
    private val parser = AnkiApkgVocabularyParser()

    @Test
    fun `parses vocabulary items from apkg ordered by Anki's own new-card position`() {
        val deckFile = loadResource("import/anki/procrastilearn-test-deck.apkg")

        val result = parser.parse(deckFile)

        assertThat(result)
            .containsExactly(
                VocabularyItem(
                    word = "test2",
                    translation = "test description2",
                    isNew = true,
                ),
                VocabularyItem(
                    word = "TestTitle",
                    translation = "testBack description",
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
                VocabularyItem(
                    word = listOf("agree", "əˈɡriː").joinToString(separator = "\n"),
                    translation =
                        listOf(
                            "Meaning: To agree is to have the same opinion or belief as another person.",
                            "Example: The students agree they have too much homework.",
                        ).joinToString(separator = "\n"),
                    isNew = true,
                ),
            ).inOrder()
    }

    @Test
    fun `parses the ordered deck fixture with German words in the deck's due order`() {
        val deckFile = loadResource("import/anki/English-German_Ordered_Deck.apkg")

        val result = parser.parse(deckFile)

        val germanWords =
            result.map { item ->
                item.translation
                    .lineSequence()
                    .first { it.startsWith("Word: ") }
                    .removePrefix("Word: ")
            }
        assertThat(germanWords)
            .containsExactly(
                "der",
                "und",
                "in",
                "sein, ist, war, ist gewesen",
                "ein",
                "haben, hat, hatte, hat gehabt",
                "sie",
                "werden, wird, wurde, ist geworden",
                "von",
                "ich",
                "nicht",
                "es",
                "mit",
                "sich",
                "er",
                "auf",
                "für",
                "auch",
                "an",
                "dass",
            ).inOrder()
    }

    @Test
    fun `orders a note with multiple cards by the minimum due among its type=0 cards`() {
        val deckFile =
            buildSyntheticApkg(
                notes =
                    listOf(
                        SyntheticNote(id = 1, mid = 1, flds = "multi-card${FLDS_SEP}translation"),
                        SyntheticNote(id = 2, mid = 1, flds = "single-card${FLDS_SEP}translation"),
                    ),
                cards =
                    listOf(
                        SyntheticCard(id = 10, nid = 1, type = 0, due = 900),
                        SyntheticCard(id = 11, nid = 1, type = 0, due = 50),
                        SyntheticCard(id = 12, nid = 2, type = 0, due = 200),
                    ),
            )

        val result = parser.parse(deckFile)

        assertThat(result.map { it.word }).containsExactly("multi-card", "single-card").inOrder()
    }

    @Test
    fun `sorts notes whose only card is not type 0 after every genuinely new note, tie-broken by note id`() {
        val deckFile =
            buildSyntheticApkg(
                notes =
                    listOf(
                        SyntheticNote(id = 1, mid = 1, flds = "reviewed-early${FLDS_SEP}t"),
                        SyntheticNote(id = 2, mid = 1, flds = "still-new${FLDS_SEP}t"),
                        SyntheticNote(id = 3, mid = 1, flds = "reviewed-late${FLDS_SEP}t"),
                    ),
                cards =
                    listOf(
                        SyntheticCard(id = 10, nid = 1, type = 2, due = 1),
                        SyntheticCard(id = 11, nid = 2, type = 0, due = 500),
                        SyntheticCard(id = 12, nid = 3, type = 1, due = 2),
                    ),
            )

        val result = parser.parse(deckFile)

        assertThat(result.map { it.word })
            .containsExactly("still-new", "reviewed-early", "reviewed-late")
            .inOrder()
    }

    @Test
    fun `sorts a note with no matching card row after every genuinely new note`() {
        val deckFile =
            buildSyntheticApkg(
                notes =
                    listOf(
                        SyntheticNote(id = 1, mid = 1, flds = "orphan-note${FLDS_SEP}t"),
                        SyntheticNote(id = 2, mid = 1, flds = "new-note${FLDS_SEP}t"),
                    ),
                cards =
                    listOf(
                        SyntheticCard(id = 10, nid = 2, type = 0, due = 5),
                    ),
            )

        val result = parser.parse(deckFile)

        assertThat(result.map { it.word }).containsExactly("new-note", "orphan-note").inOrder()
    }

    @Test
    fun `breaks a due tie between two new cards by ascending note id`() {
        val deckFile =
            buildSyntheticApkg(
                notes =
                    listOf(
                        SyntheticNote(id = 5, mid = 1, flds = "higher-id${FLDS_SEP}t"),
                        SyntheticNote(id = 2, mid = 1, flds = "lower-id${FLDS_SEP}t"),
                    ),
                cards =
                    listOf(
                        SyntheticCard(id = 10, nid = 5, type = 0, due = 100),
                        SyntheticCard(id = 11, nid = 2, type = 0, due = 100),
                    ),
            )

        val result = parser.parse(deckFile)

        assertThat(result.map { it.word }).containsExactly("lower-id", "higher-id").inOrder()
    }

    @Test
    fun `imports every note in a real cloze-only deck instead of discarding them all`() {
        val deckFile = loadResource("import/anki/anki-cloze-deck.apkg")

        val result = parser.parse(deckFile)

        assertThat(result).hasSize(800)
    }

    @Test
    fun `masks cloze deletions on the front and reveals them labeled on the back for a real cloze note`() {
        val deckFile = loadResource("import/anki/anki-cloze-deck.apkg")

        val result = parser.parse(deckFile)

        assertThat(result.first())
            .isEqualTo(
                VocabularyItem(
                    word =
                        listOf(
                            "一",
                            "一个[...]",
                            "一本书[...]",
                            "一次[...]",
                            "第一[...]",
                            "一二三。",
                        ).joinToString("\n"),
                    translation =
                        listOf(
                            "Color: 一",
                            "Reading: yī",
                            "Meaning: one",
                            "Example 1: 一个 － yīgè － one of",
                            "Example 2: 一本书 － yīběnshū － a book",
                            "Example 3: 一次 － yīcì － once",
                            "Example 4: 第一 － dìyī － first",
                            "Sentence Translation: One two three.",
                            "Sentence Pinyin: yī èr sān 。",
                        ).joinToString("\n"),
                    isNew = true,
                ),
            )
    }

    @Test
    fun `omits blank example fields from both the front and the back of a real cloze note`() {
        val deckFile = loadResource("import/anki/anki-cloze-deck.apkg")

        val result = parser.parse(deckFile)

        assertThat(result.last())
            .isEqualTo(
                VocabularyItem(
                    word =
                        listOf(
                            "扬",
                            "表扬[...]",
                            "发扬[...]",
                            "这位医生受到所有人的高度赞扬。",
                        ).joinToString("\n"),
                    translation =
                        listOf(
                            "Color: 扬",
                            "Reading: yáng",
                            "Meaning: to raise; to hoist; scattering (in the wind); to flutter; to propagate",
                            "Example 1: 表扬 － biǎoyáng － to praise",
                            "Example 2: 发扬 － fāyáng － to develop; carry forward",
                            "Sentence Translation: This doctor received high praise from everyone.",
                        ).joinToString("\n"),
                    isNew = true,
                ),
            )
    }

    @Test
    fun `never leaks raw cloze deletion syntax into any imported item from a real cloze deck`() {
        val deckFile = loadResource("import/anki/anki-cloze-deck.apkg")

        val result = parser.parse(deckFile)

        assertThat(result.none { it.word.contains("{{c") || it.translation.contains("{{c") }).isTrue()
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

    private data class SyntheticNote(
        val id: Long,
        val mid: Long,
        val flds: String,
    )

    private data class SyntheticCard(
        val id: Long,
        val nid: Long,
        val type: Int,
        val due: Long,
    )

    /**
     * Builds a minimal .apkg (a zip containing a plain, non-zstd `collection.anki21` sqlite db)
     * with just the notes/cards columns AnkiApkgVocabularyParser actually reads, and no
     * `col.models` row - so word/translation fall back to field index 0 / the rest, letting
     * these tests isolate the notes-cards join/ordering logic without needing a real Anki
     * export for every edge case.
     */
    private fun buildSyntheticApkg(
        notes: List<SyntheticNote>,
        cards: List<SyntheticCard>,
    ): File {
        val tempDir = Files.createTempDirectory("synthetic-apkg").toFile()
        val dbFile = File(tempDir, "collection.anki21")
        val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        db.use {
            it.execSQL("CREATE TABLE col (models TEXT)")
            it.execSQL("CREATE TABLE notes (id INTEGER PRIMARY KEY, mid INTEGER, flds TEXT)")
            it.execSQL("CREATE TABLE cards (id INTEGER PRIMARY KEY, nid INTEGER, type INTEGER, due INTEGER)")
            notes.forEach { note ->
                it.execSQL(
                    "INSERT INTO notes (id, mid, flds) VALUES (?, ?, ?)",
                    arrayOf<Any>(note.id, note.mid, note.flds),
                )
            }
            cards.forEach { card ->
                it.execSQL(
                    "INSERT INTO cards (id, nid, type, due) VALUES (?, ?, ?, ?)",
                    arrayOf<Any>(card.id, card.nid, card.type, card.due),
                )
            }
        }

        val apkgFile = File(tempDir, "synthetic.apkg")
        ZipOutputStream(apkgFile.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("collection.anki21"))
            dbFile.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
        return apkgFile
    }
}
