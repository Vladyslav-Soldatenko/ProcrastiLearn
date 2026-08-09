package com.procrastilearn.app.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.data.local.dao.VocabularyDao
import com.procrastilearn.app.data.local.dao.VocabularyReviewDao
import com.procrastilearn.app.data.local.dao.VocabularyStatsDao
import com.procrastilearn.app.data.local.database.AppDatabase
import com.procrastilearn.app.data.local.entity.VocabularyEntity
import com.procrastilearn.app.data.local.prefs.DayCountersStore
import io.github.openspacedrepetition.Scheduler
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class VocabularyRepositorySetBidirectionalSchedulingTest {
    private lateinit var database: AppDatabase
    private lateinit var vocabularyDao: VocabularyDao
    private lateinit var vocabularyReviewDao: VocabularyReviewDao
    private lateinit var vocabularyStatsDao: VocabularyStatsDao
    private lateinit var dayCountersStore: DayCountersStore
    private lateinit var scheduler: Scheduler
    private lateinit var repository: VocabularyRepositoryImpl

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
        vocabularyReviewDao = database.vocabularyReviewDao()
        vocabularyStatsDao = database.vocabularyStatsDao()
        dayCountersStore = mockk(relaxed = true)
        scheduler = Scheduler.builder().build()
        repository =
            VocabularyRepositoryImpl(
                appDatabase = database,
                scheduler = scheduler,
                prefs = dayCountersStore,
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun insertVocabulary(
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
    fun `setBidirectional disabling removes the row from backward review candidates`() =
        runTest {
            val id = insertVocabulary("run", bidirectional = true, fsrsDueAt = 500L, backwardFsrsDueAt = 250L)

            repository.setBidirectional(setOf(id), bidirectional = false)

            assertThat(vocabularyReviewDao.pickNextBackwardReviewCandidate(1_000L)).isNull()
        }

    @Test
    fun `setBidirectional re-enabling restores the row as a backward review candidate`() =
        runTest {
            val id = insertVocabulary("run", bidirectional = true, fsrsDueAt = 500L, backwardFsrsDueAt = 250L)
            repository.setBidirectional(setOf(id), bidirectional = false)

            repository.setBidirectional(setOf(id), bidirectional = true)

            assertThat(vocabularyReviewDao.pickNextBackwardReviewCandidate(1_000L)?.id).isEqualTo(id)
        }

    @Test
    fun `setBidirectional enabling makes a reviewed row count as backward-due`() =
        runTest {
            val id = insertVocabulary("run", bidirectional = false, fsrsDueAt = 500L, backwardFsrsDueAt = 0L)

            repository.setBidirectional(setOf(id), bidirectional = true)

            val entity = vocabularyDao.getVocabularyById(id)!!
            val dueCount =
                vocabularyStatsDao.countReviewsDue(
                    now = entity.backwardFsrsDueAt + 1,
                    includeForward = false,
                    includeBackward = true,
                )
            assertThat(dueCount).isEqualTo(1)
        }
}
