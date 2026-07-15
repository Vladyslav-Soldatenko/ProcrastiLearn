package com.procrastilearn.app.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.data.counter.DayCounters
import com.procrastilearn.app.data.local.dao.UndoSnapshotDao
import com.procrastilearn.app.data.local.dao.VocabularyDao
import com.procrastilearn.app.data.local.database.AppDatabase
import com.procrastilearn.app.data.local.entity.UndoSnapshotEntity
import com.procrastilearn.app.data.local.entity.VocabularyEntity
import com.procrastilearn.app.data.local.prefs.DayCountersStore
import com.procrastilearn.app.domain.model.LearningPreferencesConfig
import com.procrastilearn.app.domain.model.MixMode
import com.procrastilearn.app.domain.model.VocabularyItem
import io.github.openspacedrepetition.Card
import io.github.openspacedrepetition.Rating
import io.github.openspacedrepetition.Scheduler
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
class VocabularyRepositoryImplTest {
    private lateinit var database: AppDatabase
    private lateinit var vocabularyDao: VocabularyDao
    private lateinit var dayCountersStore: DayCountersStore
    private lateinit var scheduler: Scheduler
    private lateinit var undoSnapshotDao: UndoSnapshotDao
    private lateinit var repository: VocabularyRepositoryImpl

    @Before
    fun setup() {
        // Setup in-memory database
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    AppDatabase::class.java,
                ).allowMainThreadQueries()
                .build()

        vocabularyDao = database.vocabularyDao()

        // Mock DayCountersStore
        dayCountersStore = mockk(relaxed = true)

        // Setup default scheduler
        scheduler = Scheduler.builder().build()

        // Create repository
        undoSnapshotDao = database.undoSnapshotDao()
        repository =
            VocabularyRepositoryImpl(
                vocabularyDao = vocabularyDao,
                scheduler = scheduler,
                prefs = dayCountersStore,
                undoSnapshotDao = undoSnapshotDao,
                appDatabase = database,
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun todayStamp(): Int = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE).toInt()

    @Suppress("LongParameterList")
    private suspend fun insertTestVocabulary(
        word: String,
        translation: String,
        fsrsCardJson: String = "",
        fsrsDueAt: Long = 0L,
        correctCount: Int = 0,
        incorrectCount: Int = 0,
        lastShownAt: Long = 0L,
    ): Long =
        vocabularyDao.insertVocabulary(
            VocabularyEntity(
                id = 0,
                word = word,
                translation = translation,
                fsrsCardJson = fsrsCardJson,
                fsrsDueAt = fsrsDueAt,
                correctCount = correctCount,
                incorrectCount = incorrectCount,
                lastShownAt = lastShownAt,
            ),
        )

    @Test
    fun `addVocabularyItem persists new vocabulary with fsrs defaults`() =
        runTest {
            val item = VocabularyItem(word = "lernen", translation = "learn", isNew = true)

            repository.addVocabularyItem(item)

            val stored = vocabularyDao.getAllVocabulary().first()
            assertThat(stored).hasSize(1)
            val entity = stored.single()
            assertThat(entity.word).isEqualTo("lernen")
            assertThat(entity.translation).isEqualTo("learn")
            assertThat(entity.correctCount).isEqualTo(0)
            assertThat(entity.incorrectCount).isEqualTo(0)
            assertThat(entity.fsrsDueAt).isEqualTo(0L)
            assertThat(entity.fsrsCardJson).isNotEmpty()
        }

    @Test
    fun `updateVocabularyItem modifies existing record`() =
        runTest {
            val id = insertTestVocabulary("Haus", "House")
            val updated =
                VocabularyItem(
                    id = id,
                    word = "Heim",
                    translation = "Home",
                    isNew = false,
                )

            repository.updateVocabularyItem(updated)

            val entity = vocabularyDao.getVocabularyById(id)
            assertThat(entity?.word).isEqualTo("Heim")
            assertThat(entity?.translation).isEqualTo("Home")
        }

    @Test
    fun `deleteVocabularyItem removes record`() =
        runTest {
            val id = insertTestVocabulary("Baum", "Tree")
            val item =
                VocabularyItem(
                    id = id,
                    word = "Baum",
                    translation = "Tree",
                    isNew = true,
                )

            repository.deleteVocabularyItem(item)

            val remaining = vocabularyDao.getAllVocabulary().first()
            assertThat(remaining).isEmpty()
        }

    @Test
    fun `resetVocabularyProgress clears scheduling state and counters`() =
        runTest {
            val oldCardJson = "old-card"
            val id =
                insertTestVocabulary(
                    word = "lesen",
                    translation = "read",
                    fsrsCardJson = oldCardJson,
                    fsrsDueAt = 1_000L,
                    correctCount = 4,
                    incorrectCount = 2,
                    lastShownAt = 500L,
                )
            val item =
                VocabularyItem(
                    id = id,
                    word = "lesen",
                    translation = "read",
                    isNew = false,
                )

            repository.resetVocabularyProgress(item)

            val entity = vocabularyDao.getVocabularyById(id)
            assertThat(entity?.correctCount).isEqualTo(0)
            assertThat(entity?.incorrectCount).isEqualTo(0)
            assertThat(entity?.fsrsDueAt).isEqualTo(0L)
            assertThat(entity?.lastShownAt).isNull()
            assertThat(entity?.fsrsCardJson).isNotEmpty()
            assertThat(entity?.fsrsCardJson).isNotEqualTo(oldCardJson)
        }

    @Test
    fun `resetVocabularyProgress updates current item when active`() =
        runTest {
            coEvery { dayCountersStore.readPolicy() } returns
                flowOf(
                    LearningPreferencesConfig(
                        newPerDay = 10,
                        reviewPerDay = 10,
                        overlayInterval = 5,
                        mixMode = MixMode.NEW_FIRST,
                    ),
                )
            coEvery { dayCountersStore.read() } returns
                flowOf(
                    DayCounters(
                        yyyymmdd = todayStamp(),
                        newShown = 0,
                        reviewShown = 0,
                        reviewsSinceLastNew = 0,
                    ),
                )

            val id = insertTestVocabulary("schreiben", "write")
            val active = repository.getNextVocabularyItem()

            repository.resetVocabularyProgress(active)

            repository.observeCurrentItem().test {
                val emission = awaitItem()
                assertThat(emission.id).isEqualTo(id)
                assertThat(emission.isNew).isTrue()
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `observeCurrentItem emits latest selected vocabulary`() =
        runTest {
            coEvery { dayCountersStore.readPolicy() } returns
                flowOf(
                    LearningPreferencesConfig(
                        newPerDay = 10,
                        reviewPerDay = 10,
                        overlayInterval = 5,
                        mixMode = MixMode.NEW_FIRST,
                    ),
                )
            coEvery { dayCountersStore.read() } returns
                flowOf(
                    DayCounters(
                        yyyymmdd = todayStamp(),
                        newShown = 0,
                        reviewShown = 0,
                        reviewsSinceLastNew = 0,
                    ),
                )

            val insertedId = insertTestVocabulary("gehen", "go")
            val selected = repository.getNextVocabularyItem()

            assertThat(selected.id).isEqualTo(insertedId)

            repository.observeCurrentItem().test {
                assertThat(awaitItem()).isEqualTo(selected)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `getAllVocabulary emits mapped domain models`() =
        runTest {
            insertTestVocabulary("neu", "new")
            insertTestVocabulary(
                word = "alt",
                translation = "old",
                correctCount = 2,
                incorrectCount = 1,
            )

            repository.getAllVocabulary().test {
                val emission = awaitItem()
                assertThat(emission.map { it.word }).containsExactly("neu", "alt")
                val newItem = emission.first { it.word == "neu" }
                val reviewedItem = emission.first { it.word == "alt" }
                assertThat(newItem.isNew).isTrue()
                assertThat(reviewedItem.isNew).isFalse()
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `reviewVocabularyItem updates fsrs data and marks new shown`() =
        runTest {
            val id = insertTestVocabulary("sehen", "see")
            coEvery { dayCountersStore.markNewShown() } just Runs
            coEvery { dayCountersStore.read() } returns
                flowOf(DayCounters(yyyymmdd = todayStamp(), newShown = 0, reviewShown = 0, reviewsSinceLastNew = 0))

            repository.reviewVocabularyItem(id, Rating.GOOD)

            val entity = vocabularyDao.getVocabularyById(id)
            assertThat(entity?.correctCount).isEqualTo(1)
            assertThat(entity?.incorrectCount).isEqualTo(0)
            assertThat(entity?.fsrsCardJson).isNotEmpty()
            assertThat(entity?.fsrsDueAt ?: 0L).isGreaterThan(0L)
            coVerify(exactly = 1) { dayCountersStore.markNewShown() }
            coVerify(exactly = 0) { dayCountersStore.markReviewShown() }
        }

    @Test
    fun `reviewVocabularyItem updates counters for existing reviews`() =
        runTest {
            val now = System.currentTimeMillis()
            val id =
                insertTestVocabulary(
                    word = "sprechen",
                    translation = "speak",
                    fsrsCardJson = Card.builder().build().toJson(),
                    fsrsDueAt = now - 1_000L,
                    correctCount = 2,
                    incorrectCount = 1,
                )
            coEvery { dayCountersStore.markReviewShown() } just Runs
            coEvery { dayCountersStore.read() } returns
                flowOf(DayCounters(yyyymmdd = todayStamp(), newShown = 0, reviewShown = 2, reviewsSinceLastNew = 1))

            repository.reviewVocabularyItem(id, Rating.GOOD)

            val entity = vocabularyDao.getVocabularyById(id)
            assertThat(entity?.correctCount).isEqualTo(3)
            assertThat(entity?.incorrectCount).isEqualTo(1)
            assertThat(entity?.lastShownAt ?: 0L).isGreaterThan(0L)
            coVerify(exactly = 1) { dayCountersStore.markReviewShown() }
            coVerify(exactly = 0) { dayCountersStore.markNewShown() }
        }

    @Test
    fun `hasAvailableItems returns true when reviews due`() =
        runTest {
            val now = System.currentTimeMillis()
            insertTestVocabulary(
                word = "due review",
                translation = "review",
                fsrsCardJson = Card.builder().build().toJson(),
                fsrsDueAt = now - 1_000L,
                correctCount = 1,
                incorrectCount = 0,
            )

            coEvery { dayCountersStore.read() } returns
                flowOf(
                    DayCounters(
                        yyyymmdd = todayStamp(),
                        newShown = 10,
                        reviewShown = 0,
                        reviewsSinceLastNew = 0,
                    ),
                )
            coEvery { dayCountersStore.readPolicy() } returns
                flowOf(
                    LearningPreferencesConfig(
                        newPerDay = 10,
                        reviewPerDay = 20,
                        overlayInterval = 6,
                        mixMode = MixMode.MIX,
                    ),
                )

            val available = repository.hasAvailableItems()

            assertThat(available).isTrue()
        }

    @Test
    fun `hasAvailableItems returns true when new cards remain`() =
        runTest {
            insertTestVocabulary("new card", "nuevo")

            coEvery { dayCountersStore.read() } returns
                flowOf(
                    DayCounters(
                        yyyymmdd = todayStamp(),
                        newShown = 0,
                        reviewShown = 0,
                        reviewsSinceLastNew = 0,
                    ),
                )
            coEvery { dayCountersStore.readPolicy() } returns
                flowOf(
                    LearningPreferencesConfig(
                        newPerDay = 5,
                        reviewPerDay = 5,
                        overlayInterval = 6,
                        mixMode = MixMode.MIX,
                    ),
                )

            val available = repository.hasAvailableItems()

            assertThat(available).isTrue()
        }

    @Test
    fun `hasAvailableItems returns false when limits reached and nothing due`() =
        runTest {
            coEvery { dayCountersStore.read() } returns
                flowOf(
                    DayCounters(
                        yyyymmdd = todayStamp(),
                        newShown = 5,
                        reviewShown = 0,
                        reviewsSinceLastNew = 0,
                    ),
                )
            coEvery { dayCountersStore.readPolicy() } returns
                flowOf(
                    LearningPreferencesConfig(
                        newPerDay = 5,
                        reviewPerDay = 10,
                        overlayInterval = 6,
                        mixMode = MixMode.MIX,
                    ),
                )

            val available = repository.hasAvailableItems()

            assertThat(available).isFalse()
        }
}
