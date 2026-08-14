package com.procrastilearn.app.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.data.local.database.AppDatabase
import com.procrastilearn.app.data.local.entity.VocabularyEntity
import com.procrastilearn.app.data.local.entity.VocabularyFsrsState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class VocabularyReviewDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var vocabularyDao: VocabularyDao
    private lateinit var dao: VocabularyReviewDao

    @Before
    fun setup() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    AppDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        vocabularyDao = database.vocabularyDao()
        dao = database.vocabularyReviewDao()
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
        correctCount: Int = 0,
        incorrectCount: Int = 0,
        backwardCorrectCount: Int = 0,
        backwardIncorrectCount: Int = 0,
        position: Long = 0L,
    ): Long =
        vocabularyDao.insertVocabulary(
            VocabularyEntity(
                word = word,
                translation = word,
                bidirectional = bidirectional,
                fsrsDueAt = fsrsDueAt,
                backwardFsrsDueAt = backwardFsrsDueAt,
                correctCount = correctCount,
                incorrectCount = incorrectCount,
                backwardCorrectCount = backwardCorrectCount,
                backwardIncorrectCount = backwardIncorrectCount,
                position = position,
            ),
        )

    @Test
    fun `pickNewIdByOffset with requireBidirectional true never returns a bidirectional false row`() =
        runTest {
            insert("a", bidirectional = false)
            val b = insert("b", bidirectional = true)

            repeat(5) {
                assertThat(dao.pickNewIdByOffset(0, requireBidirectional = true)).isEqualTo(b)
            }
        }

    @Test
    fun `pickNewIdByOffset with requireBidirectional false can return any never-introduced row`() =
        runTest {
            val a = insert("a", bidirectional = false)

            assertThat(dao.pickNewIdByOffset(0, requireBidirectional = false)).isEqualTo(a)
        }

    @Test
    fun `pickNewIdByOffset returns null when the offset is out of range`() =
        runTest {
            insert("a", bidirectional = false)

            assertThat(dao.pickNewIdByOffset(5, requireBidirectional = false)).isNull()
        }

    @Test
    fun `pickNewIdByPositionAsc returns the lowest-position new row`() =
        runTest {
            insert("late", position = 500L)
            val early = insert("early", position = 10L)
            insert("mid", position = 100L)

            assertThat(dao.pickNewIdByPositionAsc()).isEqualTo(early)
        }

    @Test
    fun `pickNewIdByPositionAsc ties break by ascending id`() =
        runTest {
            val first = insert("first", position = 5L)
            val second = insert("second", position = 5L)
            check(first < second)

            assertThat(dao.pickNewIdByPositionAsc()).isEqualTo(first)
        }

    @Test
    fun `pickNewIdByPositionAsc ignores rows that are no longer new`() =
        runTest {
            insert("reviewed", position = 1L, fsrsDueAt = 1000L)
            val stillNew = insert("new", position = 2L)

            assertThat(dao.pickNewIdByPositionAsc()).isEqualTo(stillNew)
        }

    @Test
    fun `pickNewIdByPositionAsc with requireBidirectional true never returns a bidirectional false row`() =
        runTest {
            insert("a", bidirectional = false, position = 0L)
            val b = insert("b", bidirectional = true, position = 999L)

            assertThat(dao.pickNewIdByPositionAsc(requireBidirectional = true)).isEqualTo(b)
        }

    @Test
    fun `pickNewIdByPositionAsc returns null when there are no new rows`() =
        runTest {
            insert("reviewed", fsrsDueAt = 1000L)

            assertThat(dao.pickNewIdByPositionAsc()).isNull()
        }

    @Test
    fun `pickNextForwardReviewCandidate ignores a backward-only-due row`() =
        runTest {
            val now = System.currentTimeMillis()
            insert("a", bidirectional = true, fsrsDueAt = 0L, backwardFsrsDueAt = now - 1000)

            assertThat(dao.pickNextForwardReviewCandidate(now)).isNull()
        }

    @Test
    fun `pickNextForwardReviewCandidate returns the earliest of several eligible rows`() =
        runTest {
            val now = System.currentTimeMillis()
            insert("late", fsrsDueAt = now - 500)
            val early = insert("early", fsrsDueAt = now - 5000)

            assertThat(dao.pickNextForwardReviewCandidate(now)?.id).isEqualTo(early)
        }

    @Test
    fun `pickNextBackwardReviewCandidate returns null when bidirectional false even if backwardFsrsDueAt is set`() =
        runTest {
            val now = System.currentTimeMillis()
            insert("a", bidirectional = false, backwardFsrsDueAt = now - 1000)

            assertThat(dao.pickNextBackwardReviewCandidate(now)).isNull()
        }

    @Test
    fun `pickNextBackwardReviewCandidate returns the earliest of several eligible rows`() =
        runTest {
            val now = System.currentTimeMillis()
            insert("late", bidirectional = true, backwardFsrsDueAt = now - 500)
            val early = insert("early", bidirectional = true, backwardFsrsDueAt = now - 5000)

            assertThat(dao.pickNextBackwardReviewCandidate(now)?.id).isEqualTo(early)
        }

    @Test
    fun `applyFsrsReview with seedOtherDirection true seeds backwardFsrsDueAt only when it was previously zero`() =
        runTest {
            val now = System.currentTimeMillis()
            val id = insert("word", bidirectional = true)

            dao.applyFsrsReview(
                id = id,
                cardJson = "card",
                dueAt = now + 1000,
                reviewedAt = now,
                wasCorrect = true,
                seedOtherDirection = true,
                seedDueAt = now + 5000,
            )

            assertThat(vocabularyDao.getVocabularyById(id)?.backwardFsrsDueAt).isEqualTo(now + 5000)
        }

    @Test
    fun `applyFsrsReview with seedOtherDirection true does not clobber an already-nonzero backwardFsrsDueAt`() =
        runTest {
            val now = System.currentTimeMillis()
            val id = insert("word", bidirectional = true, backwardFsrsDueAt = now + 999)

            dao.applyFsrsReview(
                id = id,
                cardJson = "card",
                dueAt = now + 1000,
                reviewedAt = now,
                wasCorrect = true,
                seedOtherDirection = true,
                seedDueAt = now + 5000,
            )

            assertThat(vocabularyDao.getVocabularyById(id)?.backwardFsrsDueAt).isEqualTo(now + 999)
        }

    @Test
    fun `applyFsrsReview with seedOtherDirection false never touches backwardFsrsDueAt`() =
        runTest {
            val now = System.currentTimeMillis()
            val id = insert("word", bidirectional = true)

            dao.applyFsrsReview(
                id = id,
                cardJson = "card",
                dueAt = now + 1000,
                reviewedAt = now,
                wasCorrect = true,
                seedOtherDirection = false,
                seedDueAt = now + 5000,
            )

            assertThat(vocabularyDao.getVocabularyById(id)?.backwardFsrsDueAt).isEqualTo(0L)
        }

    @Test
    fun `applyFsrsReview increments correctCount when wasCorrect is true`() =
        runTest {
            val now = System.currentTimeMillis()
            val id = insert("word", correctCount = 1, incorrectCount = 2)

            dao.applyFsrsReview(
                id = id,
                cardJson = "card",
                dueAt = now + 1000,
                reviewedAt = now,
                wasCorrect = true,
            )

            val entity = vocabularyDao.getVocabularyById(id)!!
            assertThat(entity.correctCount).isEqualTo(2)
            assertThat(entity.incorrectCount).isEqualTo(2)
        }

    @Test
    fun `applyFsrsReview increments incorrectCount when wasCorrect is false`() =
        runTest {
            val now = System.currentTimeMillis()
            val id = insert("word", correctCount = 1, incorrectCount = 2)

            dao.applyFsrsReview(
                id = id,
                cardJson = "card",
                dueAt = now + 1000,
                reviewedAt = now,
                wasCorrect = false,
            )

            val entity = vocabularyDao.getVocabularyById(id)!!
            assertThat(entity.correctCount).isEqualTo(1)
            assertThat(entity.incorrectCount).isEqualTo(3)
        }

    @Test
    fun `applyBackwardFsrsReview with seedOtherDirection true seeds fsrsDueAt only when it was previously zero`() =
        runTest {
            val now = System.currentTimeMillis()
            val id = insert("word", bidirectional = true)

            dao.applyBackwardFsrsReview(
                id = id,
                cardJson = "card",
                dueAt = now + 1000,
                reviewedAt = now,
                wasCorrect = true,
                seedOtherDirection = true,
                seedDueAt = now + 5000,
            )

            assertThat(vocabularyDao.getVocabularyById(id)?.fsrsDueAt).isEqualTo(now + 5000)
        }

    @Test
    fun `applyBackwardFsrsReview with seedOtherDirection true does not clobber an already-nonzero fsrsDueAt`() =
        runTest {
            val now = System.currentTimeMillis()
            val id = insert("word", bidirectional = true, fsrsDueAt = now + 999)

            dao.applyBackwardFsrsReview(
                id = id,
                cardJson = "card",
                dueAt = now + 1000,
                reviewedAt = now,
                wasCorrect = true,
                seedOtherDirection = true,
                seedDueAt = now + 5000,
            )

            assertThat(vocabularyDao.getVocabularyById(id)?.fsrsDueAt).isEqualTo(now + 999)
        }

    @Test
    fun `applyBackwardFsrsReview with seedOtherDirection false never touches fsrsDueAt`() =
        runTest {
            val now = System.currentTimeMillis()
            val id = insert("word", bidirectional = true)

            dao.applyBackwardFsrsReview(
                id = id,
                cardJson = "card",
                dueAt = now + 1000,
                reviewedAt = now,
                wasCorrect = true,
                seedOtherDirection = false,
                seedDueAt = now + 5000,
            )

            assertThat(vocabularyDao.getVocabularyById(id)?.fsrsDueAt).isEqualTo(0L)
        }

    @Test
    fun `applyBackwardFsrsReview increments backwardCorrectCount when wasCorrect is true`() =
        runTest {
            val now = System.currentTimeMillis()
            val id = insert("word", backwardCorrectCount = 1, backwardIncorrectCount = 2)

            dao.applyBackwardFsrsReview(
                id = id,
                cardJson = "card",
                dueAt = now + 1000,
                reviewedAt = now,
                wasCorrect = true,
            )

            val entity = vocabularyDao.getVocabularyById(id)!!
            assertThat(entity.backwardCorrectCount).isEqualTo(2)
            assertThat(entity.backwardIncorrectCount).isEqualTo(2)
        }

    @Test
    fun `restoreFsrsState restores both forward and backward columns atomically`() =
        runTest {
            val id = insert("word", bidirectional = true, fsrsDueAt = 500L, backwardFsrsDueAt = 600L)

            dao.restoreFsrsState(
                VocabularyFsrsStateRestore(
                    id = id,
                    fsrsState =
                        VocabularyFsrsState(
                            fsrsCardJson = "restored-fwd",
                            fsrsDueAt = 111L,
                            lastShownAt = null,
                            correctCount = 2,
                            incorrectCount = 3,
                            backwardFsrsCardJson = "restored-bwd",
                            backwardFsrsDueAt = 222L,
                            backwardCorrectCount = 4,
                            backwardIncorrectCount = 5,
                        ),
                ),
            )

            val entity = vocabularyDao.getVocabularyById(id)!!
            assertThat(entity.fsrsCardJson).isEqualTo("restored-fwd")
            assertThat(entity.fsrsDueAt).isEqualTo(111L)
            assertThat(entity.correctCount).isEqualTo(2)
            assertThat(entity.incorrectCount).isEqualTo(3)
            assertThat(entity.backwardFsrsCardJson).isEqualTo("restored-bwd")
            assertThat(entity.backwardFsrsDueAt).isEqualTo(222L)
            assertThat(entity.backwardCorrectCount).isEqualTo(4)
            assertThat(entity.backwardIncorrectCount).isEqualTo(5)
        }
}
