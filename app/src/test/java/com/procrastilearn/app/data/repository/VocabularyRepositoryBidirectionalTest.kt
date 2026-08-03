package com.procrastilearn.app.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.data.counter.DayCounters
import com.procrastilearn.app.data.local.dao.VocabularyDao
import com.procrastilearn.app.data.local.dao.VocabularyReviewDao
import com.procrastilearn.app.data.local.dao.VocabularyStatsDao
import com.procrastilearn.app.data.local.database.AppDatabase
import com.procrastilearn.app.data.local.entity.VocabularyEntity
import com.procrastilearn.app.data.local.prefs.DayCountersStore
import com.procrastilearn.app.domain.model.LearningPreferencesConfig
import com.procrastilearn.app.domain.model.MixMode
import com.procrastilearn.app.domain.model.StudyDirection
import com.procrastilearn.app.domain.model.StudyDirectionMode
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
class VocabularyRepositoryBidirectionalTest {
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

    private fun todayStamp(): Int = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE).toInt()

    private suspend fun assertNoAvailableItems() {
        val thrown =
            try {
                repository.getNextVocabularyItem()
                null
            } catch (e: NoAvailableItemsException) {
                e
            }
        assertThat(thrown).isNotNull()
    }

    private suspend fun insertVocabulary(
        word: String,
        bidirectional: Boolean = false,
        fsrsDueAt: Long = 0L,
        backwardFsrsDueAt: Long = 0L,
        correctCount: Int = 0,
        incorrectCount: Int = 0,
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
            ),
        )

    private fun stubCounters(
        newShown: Int = 0,
        reviewShown: Int = 0,
        reviewsSinceLastNew: Int = 0,
        extraNewToday: Int = 0,
    ) {
        coEvery { dayCountersStore.read() } returns
            flowOf(
                DayCounters(
                    yyyymmdd = todayStamp(),
                    newShown = newShown,
                    reviewShown = reviewShown,
                    reviewsSinceLastNew = reviewsSinceLastNew,
                    extraNewToday = extraNewToday,
                ),
            )
    }

    private fun stubPolicy(
        studyDirectionMode: StudyDirectionMode = StudyDirectionMode.FORWARD,
        newPerDay: Int = 20,
        reviewPerDay: Int = 20,
    ) {
        coEvery { dayCountersStore.readPolicy() } returns
            flowOf(
                LearningPreferencesConfig(
                    newPerDay = newPerDay,
                    reviewPerDay = reviewPerDay,
                    overlayInterval = 6,
                    mixMode = MixMode.REVIEWS_FIRST,
                    studyDirectionMode = studyDirectionMode,
                ),
            )
    }

    @Test
    fun `bidirectional card introduced forward-first when mode is BIDIRECTIONAL`() =
        runTest {
            insertVocabulary("run", bidirectional = true)
            stubCounters()
            stubPolicy(studyDirectionMode = StudyDirectionMode.BIDIRECTIONAL)

            val item = repository.getNextVocabularyItem()

            assertThat(item.direction).isEqualTo(StudyDirection.FORWARD)
        }

    @Test
    fun `first-ever forward review of a bidirectional card seeds backwardFsrsDueAt`() =
        runTest {
            val id = insertVocabulary("run", bidirectional = true)
            stubCounters()

            repository.reviewVocabularyItem(id, Rating.GOOD, StudyDirection.FORWARD)

            val entity = vocabularyDao.getVocabularyById(id)!!
            assertThat(entity.backwardFsrsDueAt).isGreaterThan(0L)
        }

    @Test
    fun `bidirectional card introduced backward-first when mode is BACKWARD`() =
        runTest {
            insertVocabulary("run", bidirectional = true)
            stubCounters()
            stubPolicy(studyDirectionMode = StudyDirectionMode.BACKWARD)

            val item = repository.getNextVocabularyItem()

            assertThat(item.direction).isEqualTo(StudyDirection.BACKWARD)
        }

    @Test
    fun `first-ever backward review of a bidirectional card seeds fsrsDueAt`() =
        runTest {
            val id = insertVocabulary("run", bidirectional = true)
            stubCounters()

            repository.reviewVocabularyItem(id, Rating.GOOD, StudyDirection.BACKWARD)

            val entity = vocabularyDao.getVocabularyById(id)!!
            assertThat(entity.fsrsDueAt).isGreaterThan(0L)
        }

    @Test
    fun `seeded backward review surfaces later as a due review not new and does not double-spend the new slot`() =
        runTest {
            val id = insertVocabulary("run", bidirectional = true)
            stubCounters(newShown = 0)
            stubPolicy(studyDirectionMode = StudyDirectionMode.BIDIRECTIONAL)

            val introduced = repository.getNextVocabularyItem()
            assertThat(introduced.direction).isEqualTo(StudyDirection.FORWARD)
            repository.reviewVocabularyItem(id, Rating.GOOD, StudyDirection.FORWARD)

            stubCounters(newShown = 1)
            val entityAfterSeed = vocabularyDao.getVocabularyById(id)!!
            val futureNow = entityAfterSeed.backwardFsrsDueAt + 1

            assertThat(vocabularyReviewDao.pickNextBackwardReviewCandidate(futureNow)?.id).isEqualTo(id)

            val newCount = vocabularyStatsDao.countNewTotal()
            assertThat(newCount).isEqualTo(0)
        }

    @Test
    fun `a non-bidirectional card's first forward review never seeds backwardFsrsDueAt`() =
        runTest {
            val id = insertVocabulary("run", bidirectional = false)
            stubCounters()

            repository.reviewVocabularyItem(id, Rating.GOOD, StudyDirection.FORWARD)

            assertThat(vocabularyDao.getVocabularyById(id)?.backwardFsrsDueAt).isEqualTo(0L)
        }

    @Test
    fun `a forward-only card never produces a backward-due pick even when mode is BIDIRECTIONAL`() =
        runTest {
            val id = insertVocabulary("run", bidirectional = false)
            stubCounters()
            repository.reviewVocabularyItem(id, Rating.GOOD, StudyDirection.FORWARD)

            val now = vocabularyDao.getVocabularyById(id)!!.fsrsDueAt + 1
            assertThat(vocabularyReviewDao.pickNextBackwardReviewCandidate(now)).isNull()
        }

    @Test
    fun `newPerDay=0 blocks introducing a bidirectional card's forward direction`() =
        runTest {
            insertVocabulary("run", bidirectional = true)
            stubCounters()
            stubPolicy(studyDirectionMode = StudyDirectionMode.BIDIRECTIONAL, newPerDay = 0)

            assertNoAvailableItems()
        }

    @Test
    fun `newPerDay=0 blocks introducing the backward direction when mode is BACKWARD`() =
        runTest {
            insertVocabulary("run", bidirectional = true)
            stubCounters()
            stubPolicy(studyDirectionMode = StudyDirectionMode.BACKWARD, newPerDay = 0)

            assertNoAvailableItems()
        }

    @Test
    fun `both directions of the same card due same day each independently consume reviewPerDay`() =
        runTest {
            val id = insertVocabulary("run", bidirectional = true)
            stubCounters()
            repository.reviewVocabularyItem(id, Rating.AGAIN, StudyDirection.FORWARD)
            val entity = vocabularyDao.getVocabularyById(id)!!
            val now = System.currentTimeMillis()
            vocabularyDao.updateVocabulary(
                entity.copy(
                    fsrsDueAt = now - 1000,
                    backwardFsrsDueAt = now - 1000,
                    lastShownAt = now,
                ),
            )

            stubCounters(reviewShown = 0)
            stubPolicy(studyDirectionMode = StudyDirectionMode.BIDIRECTIONAL, reviewPerDay = 2)

            repository.reviewVocabularyItem(id, Rating.GOOD, StudyDirection.FORWARD)
            stubCounters(reviewShown = 1)
            repository.reviewVocabularyItem(id, Rating.GOOD, StudyDirection.BACKWARD)
            stubCounters(reviewShown = 2)

            assertNoAvailableItems()
        }

    @Test
    fun `reviewPerDay exhausted by forward reviews still allows a due backward review of a different card`() =
        runTest {
            val now = System.currentTimeMillis()
            insertVocabulary("forward-due", fsrsDueAt = now - 1000)
            val backwardId = insertVocabulary("backward-due", bidirectional = true, backwardFsrsDueAt = now - 2000)
            stubCounters(reviewShown = 10)
            stubPolicy(studyDirectionMode = StudyDirectionMode.BIDIRECTIONAL, reviewPerDay = 10)

            assertNoAvailableItems()

            stubPolicy(studyDirectionMode = StudyDirectionMode.BIDIRECTIONAL, reviewPerDay = 11)
            val item = repository.getNextVocabularyItem()
            assertThat(item.id).isEqualTo(backwardId)
            assertThat(item.direction).isEqualTo(StudyDirection.BACKWARD)
        }

    @Test
    fun `introducing the two directions on different days still only consumes one new slot total`() =
        runTest {
            val id = insertVocabulary("run", bidirectional = true)
            stubCounters(newShown = 0)
            stubPolicy(studyDirectionMode = StudyDirectionMode.BIDIRECTIONAL)

            repository.getNextVocabularyItem()
            repository.reviewVocabularyItem(id, Rating.GOOD, StudyDirection.FORWARD)

            val seededDueAt = vocabularyDao.getVocabularyById(id)!!.backwardFsrsDueAt
            stubCounters(newShown = 1, reviewShown = 0)
            assertThat(vocabularyReviewDao.pickNextBackwardReviewCandidate(seededDueAt + 1)?.id).isEqualTo(id)
            assertThat(vocabularyStatsDao.countNewTotal()).isEqualTo(0)
        }

    @Test
    fun `extraNewToday bonus applies to bidirectional introduction exactly like a normal new card`() =
        runTest {
            insertVocabulary("run", bidirectional = true)
            stubCounters(newShown = 5, extraNewToday = 3)
            stubPolicy(studyDirectionMode = StudyDirectionMode.BIDIRECTIONAL, newPerDay = 5)

            val item = repository.getNextVocabularyItem()

            assertThat(item.word).isEqualTo("run")
        }

    @Test
    fun `FORWARD mode with flag false is tested forward only baseline`() =
        runTest {
            insertVocabulary("run", bidirectional = false)
            stubCounters()
            stubPolicy(studyDirectionMode = StudyDirectionMode.FORWARD)

            val item = repository.getNextVocabularyItem()

            assertThat(item.direction).isEqualTo(StudyDirection.FORWARD)
        }

    @Test
    fun `FORWARD mode with flag true is still tested forward only`() =
        runTest {
            insertVocabulary("run", bidirectional = true)
            stubCounters()
            stubPolicy(studyDirectionMode = StudyDirectionMode.FORWARD)

            val item = repository.getNextVocabularyItem()

            assertThat(item.direction).isEqualTo(StudyDirection.FORWARD)
        }

    @Test
    fun `BIDIRECTIONAL mode with flag false stays forward-only forever`() =
        runTest {
            val id = insertVocabulary("run", bidirectional = false)
            stubCounters()
            stubPolicy(studyDirectionMode = StudyDirectionMode.BIDIRECTIONAL)

            repository.getNextVocabularyItem()
            repository.reviewVocabularyItem(id, Rating.GOOD, StudyDirection.FORWARD)

            assertThat(vocabularyDao.getVocabularyById(id)?.backwardFsrsDueAt).isEqualTo(0L)
        }

    @Test
    fun `BIDIRECTIONAL mode with flag true makes both directions individually reachable once due`() =
        runTest {
            val now = System.currentTimeMillis()
            val id =
                insertVocabulary("run", bidirectional = true, fsrsDueAt = now - 1000, backwardFsrsDueAt = now - 500)
            stubCounters()
            stubPolicy(studyDirectionMode = StudyDirectionMode.BIDIRECTIONAL)

            val forward = vocabularyReviewDao.pickNextForwardReviewCandidate(now)
            val backward = vocabularyReviewDao.pickNextBackwardReviewCandidate(now)

            assertThat(forward?.id).isEqualTo(id)
            assertThat(backward?.id).isEqualTo(id)
        }

    @Test
    fun `BACKWARD mode with flag false excludes the card from selection entirely`() =
        runTest {
            insertVocabulary("run", bidirectional = false)
            stubCounters()
            stubPolicy(studyDirectionMode = StudyDirectionMode.BACKWARD)

            assertNoAvailableItems()
        }

    @Test
    fun `BACKWARD mode excluded card is never shown forward as a fallback`() =
        runTest {
            val now = System.currentTimeMillis()
            insertVocabulary("run", bidirectional = false, fsrsDueAt = now - 1000)
            stubCounters()
            stubPolicy(studyDirectionMode = StudyDirectionMode.BACKWARD)

            assertNoAvailableItems()
        }

    @Test
    fun `BACKWARD mode with flag true is tested backward only while forward stays dormant`() =
        runTest {
            val id = insertVocabulary("run", bidirectional = true)
            stubCounters()
            stubPolicy(studyDirectionMode = StudyDirectionMode.BACKWARD)

            val item = repository.getNextVocabularyItem()
            assertThat(item.direction).isEqualTo(StudyDirection.BACKWARD)

            repository.reviewVocabularyItem(id, Rating.GOOD, StudyDirection.BACKWARD)
            val seededForwardDueAt = vocabularyDao.getVocabularyById(id)!!.fsrsDueAt
            assertThat(seededForwardDueAt).isGreaterThan(0L)
            assertThat(vocabularyReviewDao.pickNextForwardReviewCandidate(seededForwardDueAt + 1)?.id).isEqualTo(id)
        }

    @Test
    @Suppress("ktlint:standard:max-line-length")
    fun `switching mode from BACKWARD to BIDIRECTIONAL surfaces a dormant forward review without re-consuming a new slot`() =
        runTest {
            val id = insertVocabulary("run", bidirectional = true)
            stubCounters(newShown = 0)
            stubPolicy(studyDirectionMode = StudyDirectionMode.BACKWARD)

            repository.getNextVocabularyItem()
            repository.reviewVocabularyItem(id, Rating.GOOD, StudyDirection.BACKWARD)
            val seededForwardDueAt = vocabularyDao.getVocabularyById(id)!!.fsrsDueAt

            stubCounters(newShown = 1)
            stubPolicy(studyDirectionMode = StudyDirectionMode.BIDIRECTIONAL)
            val newCount = vocabularyStatsDao.countNewTotal()

            assertThat(newCount).isEqualTo(0)
            assertThat(vocabularyReviewDao.pickNextForwardReviewCandidate(seededForwardDueAt + 1)?.id).isEqualTo(id)
        }

    @Test
    fun `observeBackwardOnlySkippedCount reflects forward-only cards right after switching to BACKWARD`() =
        runTest {
            insertVocabulary("a", bidirectional = false)
            insertVocabulary("b", bidirectional = false)

            repository.observeBackwardOnlySkippedCount().test {
                assertThat(awaitItem()).isEqualTo(2)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `observeBackwardOnlySkippedCount is zero once every forward-only card is flagged bidirectional`() =
        runTest {
            val id = insertVocabulary("a", bidirectional = false)
            vocabularyDao.updateVocabulary(vocabularyDao.getVocabularyById(id)!!.copy(bidirectional = true))

            repository.observeBackwardOnlySkippedCount().test {
                assertThat(awaitItem()).isEqualTo(0)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `undoLastRating after a backward review restores all backward progress columns`() =
        runTest {
            val id = insertVocabulary("run", bidirectional = true, correctCount = 1)
            stubCounters()

            repository.reviewVocabularyItem(id, Rating.GOOD, StudyDirection.BACKWARD)
            repository.undoLastRating()

            val restored = vocabularyDao.getVocabularyById(id)!!
            assertThat(restored.backwardFsrsCardJson).isEmpty()
            assertThat(restored.backwardFsrsDueAt).isEqualTo(0L)
            assertThat(restored.backwardCorrectCount).isEqualTo(0)
            assertThat(restored.backwardIncorrectCount).isEqualTo(0)
        }

    @Test
    fun `undoLastRating after a seeding review reverts the seeded due-timestamp on the other direction`() =
        runTest {
            val id = insertVocabulary("run", bidirectional = true)
            stubCounters()

            repository.reviewVocabularyItem(id, Rating.GOOD, StudyDirection.FORWARD)
            assertThat(vocabularyDao.getVocabularyById(id)?.backwardFsrsDueAt).isGreaterThan(0L)

            repository.undoLastRating()

            assertThat(vocabularyDao.getVocabularyById(id)?.backwardFsrsDueAt).isEqualTo(0L)
        }

    @Test
    fun `undoLastRating after a non-seeding review leaves the untouched side's true pre-review values intact`() =
        runTest {
            val now = System.currentTimeMillis()
            val id =
                insertVocabulary(
                    "run",
                    bidirectional = true,
                    fsrsDueAt = now - 1000,
                    correctCount = 3,
                )
            vocabularyReviewDao.applyBackwardFsrsReview(
                id = id,
                cardJson = "existing-backward",
                dueAt = now + 50_000,
                reviewedAt = now,
                wasCorrect = true,
            )
            stubCounters()

            repository.reviewVocabularyItem(id, Rating.GOOD, StudyDirection.FORWARD)
            repository.undoLastRating()

            val restored = vocabularyDao.getVocabularyById(id)!!
            assertThat(restored.backwardFsrsCardJson).isEqualTo("existing-backward")
            assertThat(restored.backwardFsrsDueAt).isEqualTo(now + 50_000)
            assertThat(restored.backwardCorrectCount).isEqualTo(1)
        }

    @Test
    fun `UndoResult item direction reflects the direction that was actually rated and undone`() =
        runTest {
            val id = insertVocabulary("run", bidirectional = true)
            stubCounters()

            repository.reviewVocabularyItem(id, Rating.GOOD, StudyDirection.BACKWARD)
            val result = repository.undoLastRating()

            assertThat(result?.item?.direction).isEqualTo(StudyDirection.BACKWARD)
        }

    @Test
    fun `resetVocabularyProgress clears both forward and backward FSRS progress`() =
        runTest {
            val id =
                insertVocabulary(
                    "run",
                    bidirectional = true,
                    fsrsDueAt = 100L,
                    backwardFsrsDueAt = 200L,
                    correctCount = 2,
                )
            val entity = vocabularyDao.getVocabularyById(id)!!.copy(backwardCorrectCount = 3)
            vocabularyDao.updateVocabulary(entity)

            repository.resetVocabularyProgress(
                com.procrastilearn.app.domain.model.VocabularyItem(
                    id = id,
                    word = "run",
                    translation = "run",
                    isNew = false,
                ),
            )

            val reset = vocabularyDao.getVocabularyById(id)!!
            assertThat(reset.fsrsDueAt).isEqualTo(0L)
            assertThat(reset.backwardFsrsDueAt).isEqualTo(0L)
            assertThat(reset.correctCount).isEqualTo(0)
            assertThat(reset.backwardCorrectCount).isEqualTo(0)
        }

    @Test
    fun `resetVocabularyProgress does not clear the bidirectional flag`() =
        runTest {
            val id = insertVocabulary("run", bidirectional = true)

            repository.resetVocabularyProgress(
                com.procrastilearn.app.domain.model.VocabularyItem(
                    id = id,
                    word = "run",
                    translation = "run",
                    isNew = false,
                ),
            )

            assertThat(vocabularyDao.getVocabularyById(id)?.bidirectional).isTrue()
        }

    @Test
    fun `resetVocabularyProgress does not clear the override fields`() =
        runTest {
            val id = insertVocabulary("run", bidirectional = true)
            val withOverrides =
                vocabularyDao.getVocabularyById(id)!!.copy(
                    backwardPromptOverride = "prompt",
                    backwardAnswerOverride = "answer",
                )
            vocabularyDao.updateVocabulary(withOverrides)

            repository.resetVocabularyProgress(
                com.procrastilearn.app.domain.model.VocabularyItem(
                    id = id,
                    word = "run",
                    translation = "run",
                    isNew = false,
                ),
            )

            val entity = vocabularyDao.getVocabularyById(id)!!
            assertThat(entity.backwardPromptOverride).isEqualTo("prompt")
            assertThat(entity.backwardAnswerOverride).isEqualTo("answer")
        }
}
