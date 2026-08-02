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
class VocabularyStatsDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var vocabularyDao: VocabularyDao
    private lateinit var reviewDao: VocabularyReviewDao
    private lateinit var dao: VocabularyStatsDao

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
        reviewDao = database.vocabularyReviewDao()
        dao = database.vocabularyStatsDao()
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
    ): Long =
        vocabularyDao.insertVocabulary(
            VocabularyEntity(
                word = word,
                translation = word,
                bidirectional = bidirectional,
                fsrsDueAt = fsrsDueAt,
                backwardFsrsDueAt = backwardFsrsDueAt,
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
    fun `countReviewsDue excludes rows not yet due`() =
        runTest {
            val now = System.currentTimeMillis()
            insert("future", fsrsDueAt = now + 100_000)

            assertThat(dao.countReviewsDue(now, includeForward = true, includeBackward = false)).isEqualTo(0)
        }

    @Test
    fun `observeReviewsDueCount emits an updated count after applyBackwardFsrsReview pushes a due date into the future`() =
        runTest {
            val now = System.currentTimeMillis()
            val id = insert("word", bidirectional = true)

            dao.observeReviewsDueCount(now, includeForward = false, includeBackward = true).test {
                assertThat(awaitItem()).isEqualTo(0)

                reviewDao.applyBackwardFsrsReview(
                    id = id,
                    cardJson = "card",
                    dueAt = now - 1000,
                    reviewedAt = now,
                    wasCorrect = true,
                )

                assertThat(awaitItem()).isEqualTo(1)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `observeNewTotalCount emits an updated count after a new row is inserted`() =
        runTest {
            dao.observeNewTotalCount(requireBidirectional = false).test {
                assertThat(awaitItem()).isEqualTo(0)

                insert("word")

                assertThat(awaitItem()).isEqualTo(1)
                cancelAndIgnoreRemainingEvents()
            }
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
