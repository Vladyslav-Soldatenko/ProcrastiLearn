package com.procrastilearn.app.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.data.counter.DayCounters
import com.procrastilearn.app.data.local.dao.VocabularyDao
import com.procrastilearn.app.data.local.database.AppDatabase
import com.procrastilearn.app.data.local.entity.VocabularyEntity
import com.procrastilearn.app.data.local.prefs.DayCountersStore
import com.procrastilearn.app.domain.model.LearningPreferencesConfig
import com.procrastilearn.app.domain.model.MixMode
import com.procrastilearn.app.domain.model.NewCardOrder
import com.procrastilearn.app.domain.model.VocabularyItem
import io.github.openspacedrepetition.Rating
import io.github.openspacedrepetition.Scheduler
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

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class VocabularyRepositoryNewCardOrderTest {
    private lateinit var database: AppDatabase
    private lateinit var vocabularyDao: VocabularyDao
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

    private fun todayStamp(): Int = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE).toInt()

    private suspend fun insertTestVocabulary(
        word: String,
        translation: String,
        position: Long = 0L,
    ): Long =
        vocabularyDao.insertVocabulary(
            VocabularyEntity(id = 0, word = word, translation = translation, position = position),
        )

    private fun stubDayCounters() {
        coEvery { dayCountersStore.read() } returns
            flowOf(DayCounters(yyyymmdd = todayStamp(), newShown = 0, reviewShown = 0, reviewsSinceLastNew = 0))
    }

    @Test
    fun `getNextVocabularyItem in SEQUENTIAL mode returns the lowest-position new item first`() =
        runTest {
            coEvery { dayCountersStore.readPolicy() } returns
                flowOf(
                    LearningPreferencesConfig(
                        newPerDay = 20,
                        reviewPerDay = 99,
                        overlayInterval = 6,
                        mixMode = MixMode.NEW_FIRST,
                        newCardOrder = NewCardOrder.SEQUENTIAL,
                    ),
                )
            insertTestVocabulary("late", "trans", position = 500L)
            insertTestVocabulary("early", "trans", position = 10L)
            insertTestVocabulary("mid", "trans", position = 100L)
            stubDayCounters()

            assertThat(repository.getNextVocabularyItem().word).isEqualTo("early")
        }

    @Test
    fun `getNextVocabularyItem in SEQUENTIAL mode advances to the next-lowest position once a card is reviewed`() =
        runTest {
            coEvery { dayCountersStore.readPolicy() } returns
                flowOf(
                    LearningPreferencesConfig(
                        newPerDay = 20,
                        reviewPerDay = 99,
                        overlayInterval = 6,
                        mixMode = MixMode.NEW_FIRST,
                        newCardOrder = NewCardOrder.SEQUENTIAL,
                    ),
                )
            insertTestVocabulary("first", "trans", position = 1L)
            insertTestVocabulary("second", "trans", position = 2L)
            stubDayCounters()

            val picked = repository.getNextVocabularyItem()
            assertThat(picked.word).isEqualTo("first")

            repository.reviewVocabularyItem(picked.id, Rating.GOOD)

            coEvery { dayCountersStore.read() } returns
                flowOf(DayCounters(yyyymmdd = todayStamp(), newShown = 1, reviewShown = 0, reviewsSinceLastNew = 0))

            assertThat(repository.getNextVocabularyItem().word).isEqualTo("second")
        }

    @Test
    fun `getNextVocabularyItem in RANDOM mode still returns a new item when only one exists`() =
        runTest {
            coEvery { dayCountersStore.readPolicy() } returns
                flowOf(
                    LearningPreferencesConfig(
                        newPerDay = 20,
                        reviewPerDay = 99,
                        overlayInterval = 6,
                        mixMode = MixMode.NEW_FIRST,
                        newCardOrder = NewCardOrder.RANDOM,
                    ),
                )
            insertTestVocabulary("only", "trans", position = 999L)
            stubDayCounters()

            assertThat(repository.getNextVocabularyItem().word).isEqualTo("only")
        }

    @Test
    fun `addVocabularyItem assigns position as current max plus one`() =
        runTest {
            insertTestVocabulary("existing", "trans", position = 41L)

            repository.addVocabularyItem(
                VocabularyItem(word = "brand-new", translation = "trans", isNew = true),
            )

            val inserted = requireNotNull(vocabularyDao.getVocabularyByWord("brand-new"))
            assertThat(inserted.position).isEqualTo(42L)
        }

    @Test
    fun `addVocabularyItem assigns position one on an empty library`() =
        runTest {
            repository.addVocabularyItem(
                VocabularyItem(word = "first-ever", translation = "trans", isNew = true),
            )

            val inserted = requireNotNull(vocabularyDao.getVocabularyByWord("first-ever"))
            assertThat(inserted.position).isEqualTo(1L)
        }
}
