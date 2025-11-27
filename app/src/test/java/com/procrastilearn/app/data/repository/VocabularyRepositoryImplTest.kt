package com.procrastilearn.app.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.data.counter.DayCounters
import com.procrastilearn.app.data.local.dao.VocabularyDao
import com.procrastilearn.app.data.local.database.AppDatabase
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
        repository =
            VocabularyRepositoryImpl(
                vocabularyDao = vocabularyDao,
                scheduler = scheduler,
                prefs = dayCountersStore,
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

    // Test 1: Empty database should throw exception
    @Test(expected = NoSuchElementException::class)
    fun `getNextVocabularyItem throws when database is empty`() =
        runTest {
            // Setup
            coEvery { dayCountersStore.read() } returns
                flowOf(
                    DayCounters(
                        yyyymmdd = todayStamp(),
                        newShown = 0,
                        reviewShown = 0,
                        reviewsSinceLastNew = 0,
                    ),
                )

            // Execute - should throw
            repository.getNextVocabularyItem()
        }

//    // Test 2: Single new item in database
    @Test
    fun `getNextVocabularyItem returns single new item when only one exists`() =
        runTest {
            // Setup
            val id = insertTestVocabulary("hello", "hola")
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
                        newPerDay = 15,
                        reviewPerDay = 99,
                        overlayInterval = 6,
                        mixMode = MixMode.MIX,
                    ),
                )

            // Execute
            val result = repository.getNextVocabularyItem()

            // Verify
            assertThat(result.word).isEqualTo("hello")
            assertThat(result.translation).isEqualTo("hola")
        }

//
//    // Test 3: Respects daily new limit
    @Test(expected = NoAvailableItemsException::class)
    fun `getNextVocabularyItem respects daily new limit`() =
        runTest {
            coEvery { dayCountersStore.readPolicy() } returns
                flowOf(
                    LearningPreferencesConfig(
                        newPerDay = 20,
                        reviewPerDay = 99,
                        overlayInterval = 6,
                        mixMode = MixMode.REVIEWS_FIRST,
                    ),
                )

            // Setup - insert multiple new items
            insertTestVocabulary("word1", "trans1")
            insertTestVocabulary("word2", "trans2")
            insertTestVocabulary("word3", "trans3")

            // Set counters to show we've hit the new limit (20)
            coEvery { dayCountersStore.read() } returns
                flowOf(
                    DayCounters(
                        yyyymmdd = todayStamp(),
                        newShown = 20, // Hit the limit
                        reviewShown = 0,
                        reviewsSinceLastNew = 0,
                    ),
                )
            repository.getNextVocabularyItem()
        }

//
//    // Test 4: Prefers due reviews over new items
    @Test
    fun `getNextVocabularyItem prefers due reviews over new items in MIX mode`() =
        runTest {
            coEvery { dayCountersStore.readPolicy() } returns
                flowOf(
                    LearningPreferencesConfig(
                        newPerDay = 20,
                        reviewPerDay = 99,
                        overlayInterval = 6,
                        mixMode = MixMode.MIX,
                    ),
                )

            // Setup
            val now = System.currentTimeMillis()
            insertTestVocabulary("new", "nuevo")
            insertTestVocabulary(
                word = "review",
                translation = "revisar",
                fsrsCardJson = Card.builder().build().toJson(),
                fsrsDueAt = now - 1000, // Due in the past
                correctCount = 1,
                incorrectCount = 0,
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

            // Execute
            val result = repository.getNextVocabularyItem()

            // Verify - should get the review item
            assertThat(result.word).isEqualTo("review")
        }

//
//    // Test 5: NEW_FIRST mode prioritizes new items
    @Test
    fun `getNextVocabularyItem prioritizes new in NEW_FIRST mode when available`() =
        runTest {
            // Create custom repository with NEW_FIRST policy
            val customRepo =
                VocabularyRepositoryImpl(
                    vocabularyDao = vocabularyDao,
                    scheduler = scheduler,
                    prefs = dayCountersStore,
                )

            coEvery { dayCountersStore.readPolicy() } returns
                flowOf(
                    LearningPreferencesConfig(
                        newPerDay = 15,
                        reviewPerDay = 99,
                        overlayInterval = 6,
                        mixMode = MixMode.NEW_FIRST,
                    ),
                )

            // Setup
            val now = System.currentTimeMillis()
            insertTestVocabulary("new", "nuevo")
            insertTestVocabulary(
                word = "review",
                translation = "revisar",
                fsrsCardJson = Card.builder().build().toJson(),
                fsrsDueAt = now - 1000,
                correctCount = 1,
                incorrectCount = 0,
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

            // Execute
            val result = customRepo.getNextVocabularyItem()

            // Verify - should get new item despite review being due
            assertThat(result.word).isEqualTo("new")
        }

    @Test
    fun `getNextVocabularyItem does not bypass new limit when repeating last shown`() =
        runTest {
            val now = System.currentTimeMillis()
            val reviewId =
                insertTestVocabulary(
                    word = "review",
                    translation = "rev",
                    fsrsCardJson = Card.builder().build().toJson(),
                    fsrsDueAt = now - 1_000L,
                    correctCount = 1,
                    incorrectCount = 0,
                )
            insertTestVocabulary("brand new", "nuevo")

            coEvery { dayCountersStore.readPolicy() } returns
                flowOf(
                    LearningPreferencesConfig(
                        newPerDay = 1,
                        reviewPerDay = 10,
                        overlayInterval = 6,
                        mixMode = MixMode.REVIEWS_FIRST,
                    ),
                )
            coEvery { dayCountersStore.read() } returns
                flowOf(
                    DayCounters(
                        yyyymmdd = todayStamp(),
                        newShown = 1, // hit new limit
                        reviewShown = 0,
                        reviewsSinceLastNew = 0,
                    ),
                )

            val first = repository.getNextVocabularyItem()
            val second = repository.getNextVocabularyItem()

            assertThat(first.id).isEqualTo(reviewId)
            assertThat(second.id).isEqualTo(reviewId) // should not surface the new card
        }

//    // Test 7: MIX mode interleaving logic
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    @Test
    fun `getNextVocabularyItem in MIX mode interleaves new and reviews properly`() =
        runTest {
            // Setup
            coEvery { dayCountersStore.readPolicy() } returns
                flowOf(
                    LearningPreferencesConfig(
                        newPerDay = 20,
                        reviewPerDay = 99,
                        overlayInterval = 6,
                        mixMode = MixMode.MIX,
                    ),
                )

            val now = System.currentTimeMillis()

            // Add new items
            repeat(5) { i ->
                insertTestVocabulary("new$i", "nuevo$i")
            }

            // Add due reviews
            repeat(10) { i ->
                insertTestVocabulary(
                    word = "review$i",
                    translation = "revisar$i",
                    fsrsCardJson = Card.builder().build().toJson(),
                    fsrsDueAt = now - 1000,
                    correctCount = 1,
                    incorrectCount = 0,
                )
            }

            val results = mutableListOf<VocabularyItem>()

            // Simulate getting items with proper counter updates
            repeat(15) { iteration ->
                val reviewsSinceLastNew =
                    if (iteration == 0) {
                        0
                    } else {
                        results
                            .takeLast(results.size)
                            .indexOfLast {
                                vocabularyDao.getVocabularyById(it.id)?.let {
                                    it.correctCount == 0 && it.incorrectCount == 0
                                } ?: false
                            }.let { if (it == -1) results.size else results.size - it - 1 }
                    }

                coEvery { dayCountersStore.read() } returns
                    flowOf(
                        DayCounters(
                            yyyymmdd = todayStamp(),
                            newShown =
                                results.count {
                                    vocabularyDao.getVocabularyById(it.id)?.let {
                                        it.correctCount == 0 && it.incorrectCount == 0
                                    } ?: false
                                },
                            reviewShown =
                                results.count {
                                    vocabularyDao.getVocabularyById(it.id)?.let {
                                        it.correctCount > 0 || it.incorrectCount > 0
                                    } ?: false
                                },
                            reviewsSinceLastNew = reviewsSinceLastNew,
                        ),
                    )

                results.add(repository.getNextVocabularyItem())
            }
//
            // Verify we got a mix (not all reviews first or all new first)
            val newIndices =
                results.mapIndexedNotNull { index, item ->
                    vocabularyDao.getVocabularyById(item.id)?.let {
                        if (it.correctCount == 0 && it.incorrectCount == 0) index else null
                    }
                }

            // Should have some new items interspersed, not all at beginning or end
            assertThat(newIndices).isNotEmpty()
            assertThat(newIndices.first()).isGreaterThan(0) // Not all new first
        }

//
//
//    // Test 9: Day reset functionality
    @Test
    fun `getNextVocabularyItem resets counters on new day`() =
        runTest {
            // Setup
            coEvery { dayCountersStore.readPolicy() } returns
                flowOf(
                    LearningPreferencesConfig(
                        newPerDay = 20,
                        reviewPerDay = 99,
                        overlayInterval = 6,
                        mixMode = MixMode.NEW_FIRST,
                    ),
                )

            insertTestVocabulary("test", "prueba")

            val yesterday = todayStamp() - 1
            val today = todayStamp()

            // First call with yesterday's date
            coEvery { dayCountersStore.read() } returns
                flowOf(
                    DayCounters(
                        yyyymmdd = yesterday,
                        newShown = 15,
                        reviewShown = 150,
                        reviewsSinceLastNew = 5,
                    ),
                )

            // Expect reset to be called
            coEvery { dayCountersStore.resetFor(today) } just Runs

            // Execute
            repository.getNextVocabularyItem()

            // Verify reset was called
            coVerify { dayCountersStore.resetFor(today) }
        }

//
    @Test(expected = NoAvailableItemsException::class)
    fun `getNextVocabularyItem thows when daily limit reached and nothing is due`() =
        runTest {
            // Setup
            coEvery { dayCountersStore.readPolicy() } returns
                flowOf(
                    LearningPreferencesConfig(
                        newPerDay = 20,
                        reviewPerDay = 99,
                        overlayInterval = 6,
                        mixMode = MixMode.REVIEWS_FIRST,
                    ),
                )

            val future = System.currentTimeMillis() + 3600000 // 1 hour from now
            insertTestVocabulary(
                word = "future",
                translation = "futuro",
                fsrsCardJson = Card.builder().build().toJson(),
                fsrsDueAt = future,
                correctCount = 1,
                incorrectCount = 0,
            )

            coEvery { dayCountersStore.read() } returns
                flowOf(
                    DayCounters(
                        yyyymmdd = todayStamp(),
                        newShown = 20, // Hit new limit
                        reviewShown = 0,
                        reviewsSinceLastNew = 0,
                    ),
                )

            // Execute
            val result = repository.getNextVocabularyItem()

            // Verify - should get the future item since nothing else is available
            assertThat(result.word).isEqualTo("future")
        }

    @Test
    fun `due reviews ignored and new word is shown when review cap reached even if mode is review first`() =
        runTest {
            val now = System.currentTimeMillis()
            val customRepo =
                VocabularyRepositoryImpl(
                    vocabularyDao = vocabularyDao,
                    scheduler = scheduler,
                    prefs = dayCountersStore,
                )
            coEvery { dayCountersStore.readPolicy() } returns
                flowOf(
                    LearningPreferencesConfig(
                        newPerDay = 15,
                        reviewPerDay = 99,
                        overlayInterval = 6,
                        mixMode = MixMode.REVIEWS_FIRST,
                    ),
                )

            insertTestVocabulary(
                "r",
                "t",
                fsrsCardJson = Card.builder().build().toJson(),
                fsrsDueAt = now - 1000,
                correctCount = 1,
            )
            insertTestVocabulary(
                "not seen",
                "not seen",
            )

            coEvery { dayCountersStore.read() } returns
                flowOf(
                    DayCounters(todayStamp(), newShown = 0, reviewShown = 200, reviewsSinceLastNew = 0), // cap hit
                )
            val result = repository.getNextVocabularyItem()
            // Should not be the due review; falls back to new/upcoming/random
            assertThat(result.word).isEqualTo("not seen")
        }
}
