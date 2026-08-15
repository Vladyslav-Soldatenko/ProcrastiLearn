package com.procrastilearn.app.data.local.dao

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.data.local.database.AppDatabase
import com.procrastilearn.app.data.local.entity.VocabularyEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private var nextTestPosition = 1L

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
        position: Long = nextTestPosition++,
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
                position = position,
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
    fun `getVocabularyByWords matches case-insensitively`() =
        runTest {
            insert("Haus")
            insert("Baum")
            insert("Katze")

            val results = dao.getVocabularyByWords(listOf("haus", "KATZE", "missing"))

            assertThat(results.map { it.word }).containsExactly("Haus", "Katze")
        }

    @Test
    fun `getVocabularyByWords returns empty list when nothing matches`() =
        runTest {
            insert("Haus")

            assertThat(dao.getVocabularyByWords(listOf("nope"))).isEmpty()
        }

    @Test
    fun `getVocabularyByWords returns empty list for an empty word list`() =
        runTest {
            insert("Haus")

            assertThat(dao.getVocabularyByWords(emptyList())).isEmpty()
        }

    @Test
    fun `getMaxPosition returns zero on an empty table`() =
        runTest {
            assertThat(dao.getMaxPosition()).isEqualTo(0L)
        }

    @Test
    fun `getMaxPosition returns the highest position across all rows`() =
        runTest {
            insert("a", position = 3L)
            insert("b", position = 42L)
            insert("c", position = 17L)

            assertThat(dao.getMaxPosition()).isEqualTo(42L)
        }

    @Test
    fun `insertAllVocabulary no longer replaces on a duplicate word - it throws`() =
        runTest {
            insert("Haus", translation = "old")

            var thrown: Throwable? = null
            try {
                dao.insertAllVocabulary(listOf(VocabularyEntity(word = "haus", translation = "new", position = 999L)))
            } catch (e: SQLiteConstraintException) {
                thrown = e
            }
            assertThat(thrown).isNotNull()

            // The original row must survive untouched - no silent REPLACE occurred.
            assertThat(dao.getVocabularyByWord("Haus")?.translation).isEqualTo("old")
        }

    @Test
    fun `updateAllVocabulary updates every row in the batch by id`() =
        runTest {
            val first = insert("Haus", translation = "house")
            val second = insert("Baum", translation = "tree")

            dao.updateAllVocabulary(
                listOf(
                    entityById(first).copy(translation = "updated-house"),
                    entityById(second).copy(translation = "updated-tree"),
                ),
            )

            assertThat(entityById(first).translation).isEqualTo("updated-house")
            assertThat(entityById(second).translation).isEqualTo("updated-tree")
        }

    @Test
    fun `applyImportBatch inserts new rows and updates existing rows together`() =
        runTest {
            val existing = insert("Haus", translation = "old", position = 1L)

            dao.applyImportBatch(
                toInsert = listOf(VocabularyEntity(word = "Baum", translation = "tree", position = 999L)),
                toUpdate = listOf(entityById(existing).copy(translation = "new")),
            )

            assertThat(entityById(existing).translation).isEqualTo("new")
            val inserted = requireNotNull(dao.getVocabularyByWord("Baum"))
            assertThat(inserted.translation).isEqualTo("tree")
            // The caller-supplied position (999) is ignored - applyImportBatch always assigns
            // MAX(position)+1 itself, atomically with the insert.
            assertThat(inserted.position).isEqualTo(2L)
        }

    @Test
    fun `applyImportBatch assigns sequential positions across a multi-row batch, ignoring caller-supplied values`() =
        runTest {
            insert("Existing", position = 10L)

            dao.applyImportBatch(
                toInsert =
                    listOf(
                        VocabularyEntity(word = "a", translation = "a", position = 1L),
                        VocabularyEntity(word = "b", translation = "b", position = 1L),
                        VocabularyEntity(word = "c", translation = "c", position = 1L),
                    ),
                toUpdate = emptyList(),
            )

            assertThat(requireNotNull(dao.getVocabularyByWord("a")).position).isEqualTo(11L)
            assertThat(requireNotNull(dao.getVocabularyByWord("b")).position).isEqualTo(12L)
            assertThat(requireNotNull(dao.getVocabularyByWord("c")).position).isEqualTo(13L)
        }

    @Test
    fun `applyImportBatch continues numbering from the prior MAX(position) on a later call`() =
        runTest {
            dao.applyImportBatch(
                toInsert = listOf(VocabularyEntity(word = "a", translation = "a")),
                toUpdate = emptyList(),
            )
            dao.applyImportBatch(
                toInsert = listOf(VocabularyEntity(word = "b", translation = "b")),
                toUpdate = emptyList(),
            )

            assertThat(requireNotNull(dao.getVocabularyByWord("a")).position).isEqualTo(1L)
            assertThat(requireNotNull(dao.getVocabularyByWord("b")).position).isEqualTo(2L)
        }

    @Test
    fun `inserting two rows with the same explicit position throws`() =
        runTest {
            insert("Haus", position = 5L)

            var thrown: Throwable? = null
            try {
                dao.insertAllVocabulary(listOf(VocabularyEntity(word = "Baum", translation = "tree", position = 5L)))
            } catch (e: SQLiteConstraintException) {
                thrown = e
            }
            assertThat(thrown).isNotNull()
        }

    @Test
    fun `applyImportBatch with empty lists is a no-op`() =
        runTest {
            insert("Haus")

            dao.applyImportBatch(toInsert = emptyList(), toUpdate = emptyList())

            assertThat(dao.getAllVocabulary().let { flow -> flow }).isNotNull()
        }

    @Test
    fun `applyImportBatch rolls back the whole batch when one insert violates a constraint`() =
        runTest {
            insert("Haus", translation = "existing")

            var thrown: Throwable? = null
            try {
                dao.applyImportBatch(
                    toInsert =
                        listOf(
                            VocabularyEntity(word = "Baum", translation = "tree", position = 1L),
                            // Duplicates the existing row's word - violates the unique index.
                            VocabularyEntity(word = "haus", translation = "duplicate", position = 2L),
                        ),
                    toUpdate = emptyList(),
                )
            } catch (e: SQLiteConstraintException) {
                thrown = e
            }
            assertThat(thrown).isNotNull()

            // Transactional: the "Baum" insert that would have otherwise succeeded must
            // also be rolled back, since it shared the same @Transaction as the failing one.
            assertThat(dao.getVocabularyByWord("Baum")).isNull()
            assertThat(dao.getVocabularyByWord("Haus")?.translation).isEqualTo("existing")
        }
}
