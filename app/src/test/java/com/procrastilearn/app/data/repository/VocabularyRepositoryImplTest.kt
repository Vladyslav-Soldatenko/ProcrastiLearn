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
import com.procrastilearn.app.domain.model.VocabularyItem
import io.github.openspacedrepetition.Card
import io.github.openspacedrepetition.Scheduler
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
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
                        buryImmediateRepeat = true,
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
                        buryImmediateRepeat = true,
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
                        buryImmediateRepeat = true,
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
                        buryImmediateRepeat = true,
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

//
//    // Test 6: Bury immediate repeat functionality
//    @Test
    fun `getNextVocabularyItem avoids immediate repeat when buryImmediateRepeat is true`() =
        runTest {
            // Setup - insert two items
            insertTestVocabulary("word1", "trans1")
            insertTestVocabulary("word2", "trans2")

            coEvery { dayCountersStore.read() } returns
                flowOf(
                    DayCounters(
                        yyyymmdd = todayStamp(),
                        newShown = 0,
                        reviewShown = 0,
                        reviewsSinceLastNew = 0,
                    ),
                )

            // Get first item
            val first = repository.getNextVocabularyItem()

            // Get second item - should be different
            val second = repository.getNextVocabularyItem()

            // Verify they're different
            assertThat(first.id).isNotEqualTo(second.id)
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
                        buryImmediateRepeat = true,
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
                        buryImmediateRepeat = true,
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
                        buryImmediateRepeat = true,
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
                        buryImmediateRepeat = true,
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
