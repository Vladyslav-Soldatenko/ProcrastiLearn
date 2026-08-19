package com.procrastilearn.app.data.local.dao

import android.database.sqlite.SQLiteConstraintException
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
class VocabularyDaoReorderTest {
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
        fsrsCardJson: String = "forward-json",
        backwardFsrsCardJson: String = "backward-json",
        correctCount: Int = 0,
        incorrectCount: Int = 0,
        position: Long = nextTestPosition++,
    ): Long =
        dao.insertVocabulary(
            VocabularyEntity(
                word = word,
                translation = translation,
                bidirectional = bidirectional,
                fsrsDueAt = fsrsDueAt,
                backwardFsrsDueAt = backwardFsrsDueAt,
                fsrsCardJson = fsrsCardJson,
                backwardFsrsCardJson = backwardFsrsCardJson,
                correctCount = correctCount,
                incorrectCount = incorrectCount,
                position = position,
            ),
        )

    private suspend fun entityById(id: Long): VocabularyEntity = requireNotNull(dao.getVocabularyById(id))

    private suspend fun positionOf(id: Long): Long = entityById(id).position

    @Test
    fun `reorderVocabulary with an empty list is a no-op`() =
        runTest {
            val a = insert("a")
            val b = insert("b")

            dao.reorderVocabulary(emptyList())

            assertThat(positionOf(a)).isEqualTo(1L)
            assertThat(positionOf(b)).isEqualTo(2L)
        }

    @Test
    fun `reorderVocabulary on an empty table with an empty list is a no-op`() =
        runTest {
            dao.reorderVocabulary(emptyList())

            assertThat(dao.getAllVocabulary().first()).isEmpty()
        }

    @Test
    fun `reorderVocabulary with a single id sets its position to 1`() =
        runTest {
            val a = insert("a", position = 99L)

            dao.reorderVocabulary(listOf(a))

            assertThat(positionOf(a)).isEqualTo(1L)
        }

    @Test
    fun `reorderVocabulary reversing a two-item list swaps their positions`() =
        runTest {
            val a = insert("a")
            val b = insert("b")

            dao.reorderVocabulary(listOf(b, a))

            assertThat(positionOf(b)).isEqualTo(1L)
            assertThat(positionOf(a)).isEqualTo(2L)
        }

    @Test
    fun `reorderVocabulary is a no-op in effect when the given order already matches current position order`() =
        runTest {
            val a = insert("a")
            val b = insert("b")
            val c = insert("c")

            dao.reorderVocabulary(listOf(a, b, c))

            assertThat(positionOf(a)).isEqualTo(1L)
            assertThat(positionOf(b)).isEqualTo(2L)
            assertThat(positionOf(c)).isEqualTo(3L)
        }

    @Test
    fun `reorderVocabulary moving the first item to the last position renumbers every row contiguously`() =
        runTest {
            val a = insert("a")
            val b = insert("b")
            val c = insert("c")
            val d = insert("d")

            dao.reorderVocabulary(listOf(b, c, d, a))

            assertThat(positionOf(b)).isEqualTo(1L)
            assertThat(positionOf(c)).isEqualTo(2L)
            assertThat(positionOf(d)).isEqualTo(3L)
            assertThat(positionOf(a)).isEqualTo(4L)
        }

    @Test
    fun `reorderVocabulary moving the last item to the first position renumbers every row contiguously`() =
        runTest {
            val a = insert("a")
            val b = insert("b")
            val c = insert("c")
            val d = insert("d")

            dao.reorderVocabulary(listOf(d, a, b, c))

            assertThat(positionOf(d)).isEqualTo(1L)
            assertThat(positionOf(a)).isEqualTo(2L)
            assertThat(positionOf(b)).isEqualTo(3L)
            assertThat(positionOf(c)).isEqualTo(4L)
        }

    @Test
    fun `reorderVocabulary moving a middle item by one slot only changes the two affected rows' relative order`() =
        runTest {
            val a = insert("a")
            val b = insert("b")
            val c = insert("c")

            dao.reorderVocabulary(listOf(a, c, b))

            assertThat(positionOf(a)).isEqualTo(1L)
            assertThat(positionOf(c)).isEqualTo(2L)
            assertThat(positionOf(b)).isEqualTo(3L)
        }

    @Test
    fun `reorderVocabulary handles a large reversed list without constraint violations`() =
        runTest {
            val ids = (1..20).map { insert("word-$it") }

            dao.reorderVocabulary(ids.reversed())

            assertThat(dao.getAllIdsOrderedByPosition()).isEqualTo(ids.reversed())
        }

    @Test
    fun `reorderVocabulary with a duplicate id lands the id at its last occurrence's index`() =
        runTest {
            val a = insert("a")
            val b = insert("b")
            val c = insert("c")

            dao.reorderVocabulary(listOf(a, b, a, c))

            assertThat(positionOf(b)).isEqualTo(2L)
            assertThat(positionOf(a)).isEqualTo(3L)
            assertThat(positionOf(c)).isEqualTo(4L)
            assertThat(dao.getAllIdsOrderedByPosition()).isEqualTo(listOf(b, a, c))
        }

    @Test
    fun `reorderVocabulary with an unknown id does not throw and renumbers existing ids by list index`() =
        runTest {
            val a = insert("a")
            val b = insert("b")
            val missingId = 999_999L

            dao.reorderVocabulary(listOf(a, missingId, b))

            assertThat(positionOf(a)).isEqualTo(1L)
            assertThat(positionOf(b)).isEqualTo(3L)
            assertThat(dao.getVocabularyById(missingId)).isNull()
        }

    @Test
    fun `reorderVocabulary with a partial id list that collides with an untouched row throws and rolls back`() =
        runTest {
            val a = insert("a")
            val b = insert("b")
            val c = insert("c")

            var thrown: Throwable? = null
            try {
                // Omits b entirely. Phase 2 tries to write c->1, a->2, but b still holds the
                // real position 2 - collision.
                dao.reorderVocabulary(listOf(c, a))
            } catch (e: SQLiteConstraintException) {
                thrown = e
            }

            assertThat(thrown).isNotNull()
            assertThat(positionOf(a)).isEqualTo(1L)
            assertThat(positionOf(b)).isEqualTo(2L)
            assertThat(positionOf(c)).isEqualTo(3L)
        }

    @Test
    fun `reorderVocabulary leaves all non-position fields unchanged`() =
        runTest {
            val a =
                insert(
                    "a",
                    translation = "translation-a",
                    bidirectional = true,
                    fsrsDueAt = 500L,
                    backwardFsrsDueAt = 250L,
                    fsrsCardJson = "fwd-json",
                    backwardFsrsCardJson = "bwd-json",
                    correctCount = 3,
                    incorrectCount = 1,
                )
            val b = insert("b")

            dao.reorderVocabulary(listOf(b, a))

            val entity = entityById(a)
            assertThat(entity.translation).isEqualTo("translation-a")
            assertThat(entity.bidirectional).isTrue()
            assertThat(entity.fsrsDueAt).isEqualTo(500L)
            assertThat(entity.backwardFsrsDueAt).isEqualTo(250L)
            assertThat(entity.fsrsCardJson).isEqualTo("fwd-json")
            assertThat(entity.backwardFsrsCardJson).isEqualTo("bwd-json")
            assertThat(entity.correctCount).isEqualTo(3)
            assertThat(entity.incorrectCount).isEqualTo(1)
        }

    @Test
    fun `reorderVocabulary called twice in immediate succession leaves the table in the second call's order`() =
        runTest {
            val a = insert("a")
            val b = insert("b")
            val c = insert("c")

            dao.reorderVocabulary(listOf(c, b, a))
            dao.reorderVocabulary(listOf(a, b, c))

            assertThat(positionOf(a)).isEqualTo(1L)
            assertThat(positionOf(b)).isEqualTo(2L)
            assertThat(positionOf(c)).isEqualTo(3L)
        }

    @Test
    fun `reorderVocabulary never leaves two rows sharing the same position`() =
        runTest {
            val ids = (1..5).map { insert("word-$it") }

            dao.reorderVocabulary(listOf(ids[3], ids[0], ids[4], ids[1], ids[2]))

            val positions = ids.map { positionOf(it) }
            assertThat(positions.distinct()).hasSize(positions.size)
        }

    @Test
    fun `deleteVocabularyAndRenumber with an empty list is a no-op`() =
        runTest {
            val a = insert("a")

            dao.deleteVocabularyAndRenumber(emptyList())

            assertThat(positionOf(a)).isEqualTo(1L)
        }

    @Test
    fun `deleteVocabularyAndRenumber removes the given rows and renumbers the remainder to 1 point N`() =
        runTest {
            val a = insert("a")
            val b = insert("b")
            val c = insert("c")
            val d = insert("d")

            dao.deleteVocabularyAndRenumber(listOf(entityById(b), entityById(d)))

            assertThat(dao.getAllVocabulary().first()).hasSize(2)
            assertThat(positionOf(a)).isEqualTo(1L)
            assertThat(positionOf(c)).isEqualTo(2L)
        }

    @Test
    fun `deleteVocabularyAndRenumber deleting the first row shifts every remaining row's position down by one`() =
        runTest {
            val a = insert("a")
            val b = insert("b")
            val c = insert("c")

            dao.deleteVocabularyAndRenumber(listOf(entityById(a)))

            assertThat(positionOf(b)).isEqualTo(1L)
            assertThat(positionOf(c)).isEqualTo(2L)
        }

    @Test
    fun `deleteVocabularyAndRenumber deleting the last row leaves the remaining rows' positions unchanged`() =
        runTest {
            val a = insert("a")
            val b = insert("b")
            val c = insert("c")

            dao.deleteVocabularyAndRenumber(listOf(entityById(c)))

            assertThat(positionOf(a)).isEqualTo(1L)
            assertThat(positionOf(b)).isEqualTo(2L)
        }

    @Test
    fun `deleteVocabularyAndRenumber deleting a middle row closes the gap and keeps relative order`() =
        runTest {
            val a = insert("a")
            val b = insert("b")
            val c = insert("c")

            dao.deleteVocabularyAndRenumber(listOf(entityById(b)))

            assertThat(positionOf(a)).isEqualTo(1L)
            assertThat(positionOf(c)).isEqualTo(2L)
        }

    @Test
    fun `deleteVocabularyAndRenumber deleting multiple non-contiguous rows renumbers the remainder contiguously`() =
        runTest {
            val a = insert("a")
            val b = insert("b")
            val c = insert("c")
            val d = insert("d")
            val e = insert("e")

            dao.deleteVocabularyAndRenumber(listOf(entityById(b), entityById(d)))

            assertThat(positionOf(a)).isEqualTo(1L)
            assertThat(positionOf(c)).isEqualTo(2L)
            assertThat(positionOf(e)).isEqualTo(3L)
        }

    @Test
    fun `deleteVocabularyAndRenumber deleting all rows leaves an empty table`() =
        runTest {
            val a = insert("a")
            val b = insert("b")
            val c = insert("c")

            dao.deleteVocabularyAndRenumber(listOf(entityById(a), entityById(b), entityById(c)))

            assertThat(dao.getAllVocabulary().first()).isEmpty()
        }

    @Test
    fun `deleteVocabularyAndRenumber on a table with pre-existing gaps also closes those gaps`() =
        runTest {
            val a = insert("a", position = 5L)
            val b = insert("b", position = 12L)
            val c = insert("c", position = 30L)

            dao.deleteVocabularyAndRenumber(listOf(entityById(b)))

            assertThat(positionOf(a)).isEqualTo(1L)
            assertThat(positionOf(c)).isEqualTo(2L)
        }

    @Test
    fun `deleteVocabularyAndRenumber preserves all non-position fields of surviving rows`() =
        runTest {
            val survivor =
                insert(
                    "survivor",
                    translation = "translation-survivor",
                    bidirectional = true,
                    fsrsDueAt = 777L,
                    correctCount = 9,
                )
            val doomed = insert("doomed")

            dao.deleteVocabularyAndRenumber(listOf(entityById(doomed)))

            val entity = entityById(survivor)
            assertThat(entity.translation).isEqualTo("translation-survivor")
            assertThat(entity.bidirectional).isTrue()
            assertThat(entity.fsrsDueAt).isEqualTo(777L)
            assertThat(entity.correctCount).isEqualTo(9)
        }

    @Test
    fun `deleteVocabularyAndRenumber never leaves two remaining rows sharing the same position`() =
        runTest {
            val ids = (1..6).map { insert("word-$it") }

            dao.deleteVocabularyAndRenumber(listOf(entityById(ids[1]), entityById(ids[4])))

            val remainingIds = ids - ids[1] - ids[4]
            val positions = remainingIds.map { positionOf(it) }
            assertThat(positions.distinct()).hasSize(positions.size)
        }
}
