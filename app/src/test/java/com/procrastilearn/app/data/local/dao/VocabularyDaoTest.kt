package com.procrastilearn.app.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.data.local.database.AppDatabase
import com.procrastilearn.app.data.local.entity.VocabularyEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class VocabularyDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: VocabularyDao

    @Before
    fun setup() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    AppDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        dao = database.vocabularyDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun insert(
        word: String,
        bidirectional: Boolean = false,
        fsrsDueAt: Long = 0L,
        backwardFsrsDueAt: Long = 0L,
        translation: String = word,
        backwardPromptOverride: String? = null,
        backwardAnswerOverride: String? = null,
        fsrsCardJson: String = "forward-json",
        backwardFsrsCardJson: String = "backward-json",
        correctCount: Int = 0,
        incorrectCount: Int = 0,
        backwardCorrectCount: Int = 0,
        backwardIncorrectCount: Int = 0,
    ): Long =
        dao.insertVocabulary(
            VocabularyEntity(
                word = word,
                translation = translation,
                bidirectional = bidirectional,
                fsrsDueAt = fsrsDueAt,
                backwardFsrsDueAt = backwardFsrsDueAt,
                backwardPromptOverride = backwardPromptOverride,
                backwardAnswerOverride = backwardAnswerOverride,
                fsrsCardJson = fsrsCardJson,
                backwardFsrsCardJson = backwardFsrsCardJson,
                correctCount = correctCount,
                incorrectCount = incorrectCount,
                backwardCorrectCount = backwardCorrectCount,
                backwardIncorrectCount = backwardIncorrectCount,
            ),
        )

    private suspend fun entityById(id: Long): VocabularyEntity = requireNotNull(dao.getVocabularyById(id))

    @Test
    fun `setBidirectional enables the flag for every id in the list`() =
        runTest {
            val first = insert("Haus")
            val second = insert("Baum")

            dao.setBidirectional(listOf(first, second), bidirectional = true, seedDueAt = 100L)

            assertThat(entityById(first).bidirectional).isTrue()
            assertThat(entityById(second).bidirectional).isTrue()
        }

    @Test
    fun `setBidirectional disables the flag for every id in the list`() =
        runTest {
            val first = insert("Haus", bidirectional = true)
            val second = insert("Baum", bidirectional = true)

            dao.setBidirectional(listOf(first, second), bidirectional = false, seedDueAt = 100L)

            assertThat(entityById(first).bidirectional).isFalse()
            assertThat(entityById(second).bidirectional).isFalse()
        }

    @Test
    fun `setBidirectional leaves rows outside the id list untouched`() =
        runTest {
            val included = insert("Haus")
            val excluded = insert("Baum")

            dao.setBidirectional(listOf(included), bidirectional = true, seedDueAt = 100L)

            assertThat(entityById(included).bidirectional).isTrue()
            assertThat(entityById(excluded).bidirectional).isFalse()
        }

    @Test
    fun `setBidirectional seeds backwardFsrsDueAt when the row was already reviewed forward`() =
        runTest {
            val id = insert("Haus", fsrsDueAt = 500L, backwardFsrsDueAt = 0L)

            dao.setBidirectional(listOf(id), bidirectional = true, seedDueAt = 999L)

            assertThat(entityById(id).backwardFsrsDueAt).isEqualTo(999L)
        }

    @Test
    fun `setBidirectional does not seed a row that has never been reviewed`() =
        runTest {
            val id = insert("Haus", fsrsDueAt = 0L, backwardFsrsDueAt = 0L)

            dao.setBidirectional(listOf(id), bidirectional = true, seedDueAt = 999L)

            assertThat(entityById(id).backwardFsrsDueAt).isEqualTo(0L)
        }

    @Test
    fun `setBidirectional does not overwrite an existing backwardFsrsDueAt`() =
        runTest {
            val id = insert("Haus", fsrsDueAt = 500L, backwardFsrsDueAt = 250L)

            dao.setBidirectional(listOf(id), bidirectional = true, seedDueAt = 999L)

            assertThat(entityById(id).backwardFsrsDueAt).isEqualTo(250L)
        }

    @Test
    fun `setBidirectional never seeds when disabling`() =
        runTest {
            val id = insert("Haus", fsrsDueAt = 500L, backwardFsrsDueAt = 0L)

            dao.setBidirectional(listOf(id), bidirectional = false, seedDueAt = 999L)

            assertThat(entityById(id).backwardFsrsDueAt).isEqualTo(0L)
            assertThat(entityById(id).bidirectional).isFalse()
        }

    @Test
    fun `setBidirectional leaves word translation and both override columns unchanged`() =
        runTest {
            val id =
                insert(
                    "Haus",
                    translation = "house",
                    backwardPromptOverride = "custom prompt",
                    backwardAnswerOverride = "custom answer",
                )

            dao.setBidirectional(listOf(id), bidirectional = true, seedDueAt = 100L)

            val entity = entityById(id)
            assertThat(entity.word).isEqualTo("Haus")
            assertThat(entity.translation).isEqualTo("house")
            assertThat(entity.backwardPromptOverride).isEqualTo("custom prompt")
            assertThat(entity.backwardAnswerOverride).isEqualTo("custom answer")
        }

    @Test
    fun `setBidirectional leaves both fsrs card json blobs and all four counters unchanged`() =
        runTest {
            val id =
                insert(
                    "Haus",
                    fsrsDueAt = 500L,
                    fsrsCardJson = "fwd-json",
                    backwardFsrsCardJson = "bwd-json",
                    correctCount = 3,
                    incorrectCount = 1,
                    backwardCorrectCount = 2,
                    backwardIncorrectCount = 4,
                )

            dao.setBidirectional(listOf(id), bidirectional = true, seedDueAt = 100L)

            val entity = entityById(id)
            assertThat(entity.fsrsCardJson).isEqualTo("fwd-json")
            assertThat(entity.backwardFsrsCardJson).isEqualTo("bwd-json")
            assertThat(entity.correctCount).isEqualTo(3)
            assertThat(entity.incorrectCount).isEqualTo(1)
            assertThat(entity.backwardCorrectCount).isEqualTo(2)
            assertThat(entity.backwardIncorrectCount).isEqualTo(4)
        }

    @Test
    fun `setBidirectional is a no-op for ids that do not exist`() =
        runTest {
            val id = insert("Haus")

            dao.setBidirectional(listOf(999L), bidirectional = true, seedDueAt = 100L)

            assertThat(entityById(id).bidirectional).isFalse()
        }

    @Test
    fun `setBidirectional enabling an already bidirectional row changes nothing`() =
        runTest {
            val id = insert("Haus", bidirectional = true, fsrsDueAt = 500L, backwardFsrsDueAt = 250L)

            dao.setBidirectional(listOf(id), bidirectional = true, seedDueAt = 999L)

            val entity = entityById(id)
            assertThat(entity.bidirectional).isTrue()
            assertThat(entity.backwardFsrsDueAt).isEqualTo(250L)
        }

    @Test
    fun `setBidirectional disabling an already forward-only row changes nothing`() =
        runTest {
            val id = insert("Haus", bidirectional = false, fsrsDueAt = 500L, backwardFsrsDueAt = 0L)

            dao.setBidirectional(listOf(id), bidirectional = false, seedDueAt = 999L)

            val entity = entityById(id)
            assertThat(entity.bidirectional).isFalse()
            assertThat(entity.backwardFsrsDueAt).isEqualTo(0L)
        }

    @Test
    fun `getVocabularyByWord finds an exact match`() =
        runTest {
            insert("Haus")

            val result = dao.getVocabularyByWord(VocabularyEntity.normalizeWord("Haus"))

            assertThat(result?.word).isEqualTo("Haus")
        }

    @Test
    fun `getVocabularyByWord finds an ASCII case-different match`() =
        runTest {
            insert("Haus")

            val result = dao.getVocabularyByWord(VocabularyEntity.normalizeWord("haus"))

            assertThat(result?.word).isEqualTo("Haus")
        }

    @Test
    fun `getVocabularyByWord finds a non-ASCII case-different match`() =
        runTest {
            insert("café")

            val result = dao.getVocabularyByWord(VocabularyEntity.normalizeWord("CAFÉ"))

            assertThat(result?.word).isEqualTo("café")
        }

    @Test
    fun `getVocabularyByWord finds a Cyrillic case-different match`() =
        runTest {
            insert("привет")

            val result = dao.getVocabularyByWord(VocabularyEntity.normalizeWord("ПРИВЕТ"))

            assertThat(result?.word).isEqualTo("привет")
        }

    @Test
    fun `getVocabularyByWord returns null for a genuinely different word`() =
        runTest {
            insert("café")

            val result = dao.getVocabularyByWord(VocabularyEntity.normalizeWord("cafeteria"))

            assertThat(result).isNull()
        }

    @Test
    fun `getVocabularyByWord treats an accent difference as a different word`() =
        runTest {
            insert("café")

            val result = dao.getVocabularyByWord(VocabularyEntity.normalizeWord("cafe"))

            assertThat(result).isNull()
        }

    @Test
    fun `getVocabularyByWord matches regardless of surrounding whitespace`() =
        runTest {
            insert("Haus")

            val result = dao.getVocabularyByWord(VocabularyEntity.normalizeWord("  Haus  "))

            assertThat(result?.word).isEqualTo("Haus")
        }

    @Test
    fun `insertVocabulary replaces an existing row for a non-ASCII case variant`() =
        runTest {
            insert("café", correctCount = 3)

            dao.insertVocabulary(VocabularyEntity(word = "CAFÉ", translation = "coffee"))

            val all = dao.getAllVocabulary().first()
            assertThat(all).hasSize(1)
            assertThat(all.single().word).isEqualTo("CAFÉ")
        }

    @Test
    fun `insertVocabulary keeps distinct rows for words differing only by accent`() =
        runTest {
            insert("café")

            dao.insertVocabulary(VocabularyEntity(word = "cafe", translation = "coffee"))

            val all = dao.getAllVocabulary().first()
            assertThat(all.map { it.word }).containsExactly("café", "cafe")
        }

    @Test
    fun `normalizeWord folds Turkish dotless I the same as any other locale`() {
        assertThat(VocabularyEntity.normalizeWord("I")).isEqualTo("i")
    }
}
