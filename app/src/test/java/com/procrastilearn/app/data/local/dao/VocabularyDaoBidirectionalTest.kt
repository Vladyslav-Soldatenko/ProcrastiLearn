package com.procrastilearn.app.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
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
class VocabularyDaoBidirectionalTest {
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
        correctCount: Int = 0,
        incorrectCount: Int = 0,
        backwardCorrectCount: Int = 0,
        backwardIncorrectCount: Int = 0,
    ): Long =
        dao.insertVocabulary(
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
            ),
        )

    @Test
    fun `countNewTotal with requireBidirectional false counts all never-introduced rows regardless of flag`() =
        runTest {
            insert("a", bidirectional = false)
            insert("b", bidirectional = true)

            assertThat(dao.countNewTotal(requireBidirectional = false)).isEqualTo(2)
        }

    @Test
    fun `countNewTotal with requireBidirectional true excludes bidirectional false rows`() =
        runTest {
            insert("a", bidirectional = false)
            insert("b", bidirectional = true)

            assertThat(dao.countNewTotal(requireBidirectional = true)).isEqualTo(1)
        }

    @Test
    fun `countNewTotal excludes a row seeded backward-only`() =
        runTest {
            insert("a", fsrsDueAt = 0L, backwardFsrsDueAt = 100L)

            assertThat(dao.countNewTotal()).isEqualTo(0)
        }

    @Test
    fun `countNewTotal excludes a row seeded forward-only`() =
        runTest {
            insert("a", fsrsDueAt = 100L, backwardFsrsDueAt = 0L)

            assertThat(dao.countNewTotal()).isEqualTo(0)
        }

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
    fun `pickNextForwardReviewCandidate ignores a backward-only-due row`() =
        runTest {
            val now = System.currentTimeMillis()
            insert("a", bidirectional = true, fsrsDueAt = 0L, backwardFsrsDueAt = now - 1000)

            assertThat(dao.pickNextForwardReviewCandidate(now)).isNull()
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
    fun `countReviewsDue with includeForward true includeBackward false counts only forward-due rows`() =
        runTest {
            val now = System.currentTimeMillis()
            insert("fwd", fsrsDueAt = now - 1000)
            insert("bwd", bidirectional = true, backwardFsrsDueAt = now - 1000)

            assertThat(dao.countReviewsDue(now, includeForward = true, includeBackward = false)).isEqualTo(1)
        }

    @Test
    fun `countReviewsDue with includeForward false includeBackward true counts only backward-due bidirectional rows`() =
        runTest {
            val now = System.currentTimeMillis()
            insert("fwd", fsrsDueAt = now - 1000)
            insert("bwd", bidirectional = true, backwardFsrsDueAt = now - 1000)

            assertThat(dao.countReviewsDue(now, includeForward = false, includeBackward = true)).isEqualTo(1)
        }

    @Test
    fun `countReviewsDue counts a row due in both directions as 2`() =
        runTest {
            val now = System.currentTimeMillis()
            insert("both", bidirectional = true, fsrsDueAt = now - 1000, backwardFsrsDueAt = now - 1000)
            insert("forwardOnly", fsrsDueAt = now - 1000)

            val total = dao.countReviewsDue(now, includeForward = true, includeBackward = true)

            assertThat(total).isEqualTo(3)
        }

    @Test
    fun `observeReviewsDueCount emits an updated count after applyBackwardFsrsReview pushes a due date into the future`() =
        runTest {
            val now = System.currentTimeMillis()
            val id = insert("word", bidirectional = true)

            dao.observeReviewsDueCount(now, includeForward = false, includeBackward = true).test {
                assertThat(awaitItem()).isEqualTo(0)

                dao.applyBackwardFsrsReview(
                    id = id,
                    cardJson = "card",
                    dueAt = now - 1000,
                    reviewedAt = now,
                    incCorrect = 1,
                    incIncorrect = 0,
                )

                assertThat(awaitItem()).isEqualTo(1)
                cancelAndIgnoreRemainingEvents()
            }
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
                incCorrect = 1,
                incIncorrect = 0,
                seedOtherDirection = true,
                seedDueAt = now + 5000,
            )

            assertThat(dao.getVocabularyById(id)?.backwardFsrsDueAt).isEqualTo(now + 5000)
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
                incCorrect = 1,
                incIncorrect = 0,
                seedOtherDirection = true,
                seedDueAt = now + 5000,
            )

            assertThat(dao.getVocabularyById(id)?.backwardFsrsDueAt).isEqualTo(now + 999)
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
                incCorrect = 1,
                incIncorrect = 0,
                seedOtherDirection = false,
                seedDueAt = now + 5000,
            )

            assertThat(dao.getVocabularyById(id)?.backwardFsrsDueAt).isEqualTo(0L)
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
                incCorrect = 1,
                incIncorrect = 0,
                seedOtherDirection = true,
                seedDueAt = now + 5000,
            )

            assertThat(dao.getVocabularyById(id)?.fsrsDueAt).isEqualTo(now + 5000)
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
                incCorrect = 1,
                incIncorrect = 0,
                seedOtherDirection = true,
                seedDueAt = now + 5000,
            )

            assertThat(dao.getVocabularyById(id)?.fsrsDueAt).isEqualTo(now + 999)
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
                incCorrect = 1,
                incIncorrect = 0,
                seedOtherDirection = false,
                seedDueAt = now + 5000,
            )

            assertThat(dao.getVocabularyById(id)?.fsrsDueAt).isEqualTo(0L)
        }

    @Test
    fun `restoreFsrsState restores both forward and backward columns atomically`() =
        runTest {
            val id = insert("word", bidirectional = true, fsrsDueAt = 500L, backwardFsrsDueAt = 600L)

            dao.restoreFsrsState(
                id = id,
                cardJson = "restored-fwd",
                dueAt = 111L,
                lastShownAt = null,
                correctCount = 2,
                incorrectCount = 3,
                backwardCardJson = "restored-bwd",
                backwardDueAt = 222L,
                backwardCorrectCount = 4,
                backwardIncorrectCount = 5,
            )

            val entity = dao.getVocabularyById(id)!!
            assertThat(entity.fsrsCardJson).isEqualTo("restored-fwd")
            assertThat(entity.fsrsDueAt).isEqualTo(111L)
            assertThat(entity.correctCount).isEqualTo(2)
            assertThat(entity.incorrectCount).isEqualTo(3)
            assertThat(entity.backwardFsrsCardJson).isEqualTo("restored-bwd")
            assertThat(entity.backwardFsrsDueAt).isEqualTo(222L)
            assertThat(entity.backwardCorrectCount).isEqualTo(4)
            assertThat(entity.backwardIncorrectCount).isEqualTo(5)
        }

    @Test
    fun `observeBackwardOnlySkippedCount counts a never-introduced bidirectional false row`() =
        runTest {
            insert("a", bidirectional = false)

            dao.observeBackwardOnlySkippedCount(System.currentTimeMillis()).test {
                assertThat(awaitItem()).isEqualTo(1)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `observeBackwardOnlySkippedCount counts a forward-due bidirectional false row`() =
        runTest {
            val now = System.currentTimeMillis()
            insert("a", bidirectional = false, fsrsDueAt = now - 1000)

            dao.observeBackwardOnlySkippedCount(now).test {
                assertThat(awaitItem()).isEqualTo(1)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `observeBackwardOnlySkippedCount excludes bidirectional true rows regardless of due state`() =
        runTest {
            val now = System.currentTimeMillis()
            insert("a", bidirectional = true, fsrsDueAt = now - 1000)
            insert("b", bidirectional = true)

            dao.observeBackwardOnlySkippedCount(now).test {
                assertThat(awaitItem()).isEqualTo(0)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `observeBackwardOnlySkippedCount excludes rows not yet due or new`() =
        runTest {
            val now = System.currentTimeMillis()
            insert("future", bidirectional = false, fsrsDueAt = now + 100_000)

            dao.observeBackwardOnlySkippedCount(now).test {
                assertThat(awaitItem()).isEqualTo(0)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `observeBackwardOnlySkippedCount emits 0 when the deck has no forward-only rows`() =
        runTest {
            dao.observeBackwardOnlySkippedCount(System.currentTimeMillis()).test {
                assertThat(awaitItem()).isEqualTo(0)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
