package com.procrastilearn.app.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.data.counter.DayCounters
import com.procrastilearn.app.data.local.dao.UndoSnapshotDao
import com.procrastilearn.app.data.local.dao.VocabularyDao
import com.procrastilearn.app.data.local.dao.VocabularyReviewDao
import com.procrastilearn.app.data.local.dao.VocabularyStatsDao
import com.procrastilearn.app.data.local.database.AppDatabase
import com.procrastilearn.app.data.local.entity.UndoSnapshotEntity
import com.procrastilearn.app.data.local.entity.VocabularyEntity
import com.procrastilearn.app.data.local.prefs.DayCountersStore
import com.procrastilearn.app.domain.model.LearningPreferencesConfig
import com.procrastilearn.app.domain.model.MixMode
import com.procrastilearn.app.domain.model.StudyDirection
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
    private lateinit var vocabularyReviewDao: VocabularyReviewDao
    private lateinit var vocabularyStatsDao: VocabularyStatsDao
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
        vocabularyReviewDao = database.vocabularyReviewDao()
        vocabularyStatsDao = database.vocabularyStatsDao()

        // Mock DayCountersStore
        dayCountersStore = mockk(relaxed = true)

        // Setup default scheduler
        scheduler = Scheduler.builder().build()

        // Create repository
        undoSnapshotDao = database.undoSnapshotDao()
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

    private fun testSnapshot(vocabId: Long) =
        UndoSnapshotEntity(
            vocabId = vocabId,
            createdAt = System.currentTimeMillis(),
            snapshotDay = todayStamp(),
            ratingName = "GOOD",
            direction = "FORWARD",
            fsrsCardJson = "",
            fsrsDueAt = 0L,
            lastShownAt = null,
            correctCount = 0,
            incorrectCount = 0,
            backwardFsrsCardJson = "",
            backwardFsrsDueAt = 0L,
            backwardCorrectCount = 0,
            backwardIncorrectCount = 0,
            newShown = 0,
            reviewShown = 0,
            reviewsSinceLastNew = 0,
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
    fun `deleteVocabularyItem singular still deletes exactly one row and its undo snapshot`() =
        runTest {
            val keepId = insertTestVocabulary("bleiben", "stay")
            val deleteId = insertTestVocabulary("Baum", "Tree")
            undoSnapshotDao.insert(testSnapshot(keepId))
            undoSnapshotDao.insert(testSnapshot(deleteId))
            val item = VocabularyItem(id = deleteId, word = "Baum", translation = "Tree", isNew = true)

            repository.deleteVocabularyItem(item)

            val remaining = vocabularyDao.getAllVocabulary().first()
            assertThat(remaining.map { it.id }).containsExactly(keepId)
            assertThat(undoSnapshotDao.count()).isEqualTo(1)
        }

    @Test
    fun `deleteVocabularyItems deletes given rows and their undo snapshots, leaving others untouched`() =
        runTest {
            val keepId = insertTestVocabulary("bleiben", "stay")
            val deleteId1 = insertTestVocabulary("Baum", "Tree")
            val deleteId2 = insertTestVocabulary("Auto", "Car")
            undoSnapshotDao.insert(testSnapshot(keepId))
            undoSnapshotDao.insert(testSnapshot(deleteId1))
            undoSnapshotDao.insert(testSnapshot(deleteId2))
            val toDelete =
                listOf(
                    VocabularyItem(id = deleteId1, word = "Baum", translation = "Tree", isNew = true),
                    VocabularyItem(id = deleteId2, word = "Auto", translation = "Car", isNew = true),
                )

            repository.deleteVocabularyItems(toDelete)

            val remaining = vocabularyDao.getAllVocabulary().first()
            assertThat(remaining.map { it.id }).containsExactly(keepId)
            assertThat(undoSnapshotDao.count()).isEqualTo(1)
        }

    @Test
    fun `deleteVocabularyItems with an empty list performs no writes`() =
        runTest {
            val keepId = insertTestVocabulary("bleiben", "stay")
            undoSnapshotDao.insert(testSnapshot(keepId))

            repository.deleteVocabularyItems(emptyList())

            val remaining = vocabularyDao.getAllVocabulary().first()
            assertThat(remaining.map { it.id }).containsExactly(keepId)
            assertThat(undoSnapshotDao.count()).isEqualTo(1)
        }

    @Test
    fun `deleteVocabularyItems skips ids that no longer exist without throwing`() =
        runTest {
            val existingId = insertTestVocabulary("bleiben", "stay")
            val missingItem =
                VocabularyItem(id = existingId + 999, word = "ghost", translation = "ghost", isNew = true)
            val existingItem = VocabularyItem(id = existingId, word = "bleiben", translation = "stay", isNew = true)

            repository.deleteVocabularyItems(listOf(missingItem, existingItem))

            val remaining = vocabularyDao.getAllVocabulary().first()
            assertThat(remaining).isEmpty()
        }

    @Test
    fun `deleteVocabularyItems with duplicate entries deletes the row once without throwing`() =
        runTest {
            val id = insertTestVocabulary("Baum", "Tree")
            val item = VocabularyItem(id = id, word = "Baum", translation = "Tree", isNew = true)

            repository.deleteVocabularyItems(listOf(item, item))

            val remaining = vocabularyDao.getAllVocabulary().first()
            assertThat(remaining).isEmpty()
        }

    private suspend fun insertFullVocabulary(
        word: String,
        bidirectional: Boolean = false,
        fsrsDueAt: Long = 0L,
        backwardFsrsDueAt: Long = 0L,
        translation: String = word,
        backwardPromptOverride: String? = null,
        backwardAnswerOverride: String? = null,
        fsrsCardJson: String = "forward-json",
        backwardFsrsCardJson: String = "backward-json",
        correctCount: Int = 0,
        incorrectCount: Int = 0,
        backwardCorrectCount: Int = 0,
        backwardIncorrectCount: Int = 0,
    ): Long =
        vocabularyDao.insertVocabulary(
            VocabularyEntity(
                word = word,
                translation = translation,
                bidirectional = bidirectional,
                fsrsDueAt = fsrsDueAt,
                backwardFsrsDueAt = backwardFsrsDueAt,
                backwardPromptOverride = backwardPromptOverride,
                backwardAnswerOverride = backwardAnswerOverride,
                fsrsCardJson = fsrsCardJson,
                backwardFsrsCardJson = backwardFsrsCardJson,
                correctCount = correctCount,
                incorrectCount = incorrectCount,
                backwardCorrectCount = backwardCorrectCount,
                backwardIncorrectCount = backwardIncorrectCount,
            ),
        )

    @Test
    fun `setBidirectional enables a mixed selection leaving already bidirectional rows unchanged`() =
        runTest {
            val toEnable = insertFullVocabulary("Haus", bidirectional = false)
            val alreadyEnabled = insertFullVocabulary("Baum", bidirectional = true, backwardFsrsDueAt = 42L)

            repository.setBidirectional(setOf(toEnable, alreadyEnabled), bidirectional = true)

            assertThat(vocabularyDao.getVocabularyById(toEnable)?.bidirectional).isTrue()
            val untouched = vocabularyDao.getVocabularyById(alreadyEnabled)
            assertThat(untouched?.bidirectional).isTrue()
            assertThat(untouched?.backwardFsrsDueAt).isEqualTo(42L)
        }

    @Test
    fun `setBidirectional disables a mixed selection leaving already forward-only rows unchanged`() =
        runTest {
            val toDisable = insertFullVocabulary("Haus", bidirectional = true)
            val alreadyForward = insertFullVocabulary("Baum", bidirectional = false)

            repository.setBidirectional(setOf(toDisable, alreadyForward), bidirectional = false)

            assertThat(vocabularyDao.getVocabularyById(toDisable)?.bidirectional).isFalse()
            assertThat(vocabularyDao.getVocabularyById(alreadyForward)?.bidirectional).isFalse()
        }

    @Test
    fun `setBidirectional with an empty id set performs no writes`() =
        runTest {
            val id = insertFullVocabulary("Haus", bidirectional = false)
            undoSnapshotDao.insert(testSnapshot(id))

            repository.setBidirectional(emptySet(), bidirectional = true)

            assertThat(vocabularyDao.getVocabularyById(id)?.bidirectional).isFalse()
            assertThat(undoSnapshotDao.count()).isEqualTo(1)
        }

    @Test
    fun `setBidirectional purges undo snapshots for the affected rows`() =
        runTest {
            val id = insertFullVocabulary("Haus", bidirectional = false)
            undoSnapshotDao.insert(testSnapshot(id))

            repository.setBidirectional(setOf(id), bidirectional = true)

            assertThat(undoSnapshotDao.count()).isEqualTo(0)
        }

    @Test
    fun `setBidirectional leaves undo snapshots of unaffected rows intact`() =
        runTest {
            val changed = insertFullVocabulary("Haus", bidirectional = false)
            val unaffected = insertFullVocabulary("Baum", bidirectional = false)
            undoSnapshotDao.insert(testSnapshot(changed))
            undoSnapshotDao.insert(testSnapshot(unaffected))

            repository.setBidirectional(setOf(changed), bidirectional = true)

            assertThat(undoSnapshotDao.count()).isEqualTo(1)
        }

    @Test
    fun `setBidirectional seeds all rows in one batch to the same instant`() =
        runTest {
            val first = insertFullVocabulary("Haus", fsrsDueAt = 100L, backwardFsrsDueAt = 0L)
            val second = insertFullVocabulary("Baum", fsrsDueAt = 200L, backwardFsrsDueAt = 0L)

            repository.setBidirectional(setOf(first, second), bidirectional = true)

            val firstDue = vocabularyDao.getVocabularyById(first)?.backwardFsrsDueAt
            val secondDue = vocabularyDao.getVocabularyById(second)?.backwardFsrsDueAt
            assertThat(firstDue).isGreaterThan(0L)
            assertThat(firstDue).isEqualTo(secondDue)
        }

    @Test
    fun `setBidirectional chunks id sets larger than the sqlite bind limit`() =
        runTest {
            val ids = (1..1000).map { insertFullVocabulary("word$it", bidirectional = false) }.toSet()

            repository.setBidirectional(ids, bidirectional = true)

            ids.forEach { id ->
                assertThat(vocabularyDao.getVocabularyById(id)?.bidirectional).isTrue()
            }
        }

    @Test
    fun `setBidirectional preserves backward prompt and answer overrides when disabling`() =
        runTest {
            val id =
                insertFullVocabulary(
                    "Haus",
                    bidirectional = true,
                    backwardPromptOverride = "custom prompt",
                    backwardAnswerOverride = "custom answer",
                )

            repository.setBidirectional(setOf(id), bidirectional = false)

            val entity = vocabularyDao.getVocabularyById(id)
            assertThat(entity?.backwardPromptOverride).isEqualTo("custom prompt")
            assertThat(entity?.backwardAnswerOverride).isEqualTo("custom answer")
        }

    @Test
    fun `setBidirectional preserves backward fsrs card json and counters when disabling`() =
        runTest {
            val id =
                insertFullVocabulary(
                    "Haus",
                    bidirectional = true,
                    backwardFsrsCardJson = "bwd-json",
                    backwardCorrectCount = 5,
                    backwardIncorrectCount = 2,
                )

            repository.setBidirectional(setOf(id), bidirectional = false)

            val entity = vocabularyDao.getVocabularyById(id)
            assertThat(entity?.backwardFsrsCardJson).isEqualTo("bwd-json")
            assertThat(entity?.backwardCorrectCount).isEqualTo(5)
            assertThat(entity?.backwardIncorrectCount).isEqualTo(2)
        }

    @Test
    fun `re-enabling after disabling restores the previous backward due date rather than reseeding`() =
        runTest {
            val id = insertFullVocabulary("Haus", bidirectional = true, fsrsDueAt = 500L, backwardFsrsDueAt = 250L)

            repository.setBidirectional(setOf(id), bidirectional = false)
            repository.setBidirectional(setOf(id), bidirectional = true)

            assertThat(vocabularyDao.getVocabularyById(id)?.backwardFsrsDueAt).isEqualTo(250L)
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
    fun `getAllVocabulary emits mapped domain models`() =
        runTest {
            insertTestVocabulary("neu", "new")
            insertTestVocabulary(
                word = "alt",
                translation = "old",
                correctCount = 2,
                incorrectCount = 1,
                fsrsDueAt = 1_000L,
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

            repository.reviewVocabularyItem(id, Rating.GOOD, StudyDirection.FORWARD)

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

            repository.reviewVocabularyItem(id, Rating.GOOD, StudyDirection.FORWARD)

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
