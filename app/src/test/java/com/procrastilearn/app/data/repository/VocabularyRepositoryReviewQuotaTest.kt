package com.procrastilearn.app.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.data.counter.DayCounters
import com.procrastilearn.app.data.local.database.AppDatabase
import com.procrastilearn.app.data.local.entity.VocabularyEntity
import com.procrastilearn.app.data.local.prefs.DayCountersStore
import com.procrastilearn.app.domain.model.LearningPreferencesConfig
import com.procrastilearn.app.domain.model.MixMode
import io.github.openspacedrepetition.Card
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class VocabularyRepositoryReviewQuotaTest {
    private lateinit var database: AppDatabase
    private lateinit var dayCountersStore: DayCountersStore
    private lateinit var repository: VocabularyRepositoryImpl

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    AppDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        dayCountersStore = mockk(relaxed = true)
        repository =
            VocabularyRepositoryImpl(
                appDatabase = database,
                schedulerFactory = FsrsSchedulerFactory(),
                prefs = dayCountersStore,
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `getNextVocabularyItem throws when review quota is exhausted and no new card is eligible`() =
        runTest {
            val now = System.currentTimeMillis()
            coEvery { dayCountersStore.readPolicy() } returns
                flowOf(
                    LearningPreferencesConfig(
                        newPerDay = 1,
                        reviewPerDay = 1,
                        mixMode = MixMode.REVIEWS_FIRST,
                    ),
                )
            database.vocabularyDao().insertVocabulary(
                VocabularyEntity(
                    word = "due review",
                    translation = "repaso pendiente",
                    fsrsCardJson = Card.builder().build().toJson(),
                    fsrsDueAt = now - 1_000L,
                    correctCount = 1,
                ),
            )
            coEvery { dayCountersStore.read() } returns
                flowOf(
                    DayCounters(
                        yyyymmdd = todayStamp(),
                        newShown = 1,
                        reviewShown = 1,
                        reviewsSinceLastNew = 0,
                    ),
                )

            val result = runCatching { repository.getNextVocabularyItem() }

            assertThat(result.exceptionOrNull()).isInstanceOf(NoAvailableItemsException::class.java)
        }

    private fun todayStamp(): Int = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE).toInt()
}
