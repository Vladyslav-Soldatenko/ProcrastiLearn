package com.procrastilearn.app.data.repository

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.data.local.dao.UndoSnapshotDao
import com.procrastilearn.app.data.local.dao.VocabularyDao
import com.procrastilearn.app.data.local.database.AppDatabase
import com.procrastilearn.app.data.local.entity.UndoSnapshotEntity
import com.procrastilearn.app.data.local.entity.VocabularyEntity
import com.procrastilearn.app.data.local.prefs.DayCountersStore
import com.procrastilearn.app.data.local.prefs.StudyPreferencesDataStore
import io.github.openspacedrepetition.Card
import io.github.openspacedrepetition.Rating
import io.github.openspacedrepetition.Scheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class VocabularyRepositoryUndoTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var database: AppDatabase
    private lateinit var vocabularyDao: VocabularyDao
    private lateinit var undoSnapshotDao: UndoSnapshotDao
    private lateinit var dayCountersStore: DayCountersStore
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
        undoSnapshotDao = database.undoSnapshotDao()

        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val filesRoot = temporaryFolder.newFolder("datastore-root")
        val dataStoreContext =
            object : ContextWrapper(baseContext) {
                override fun getFilesDir(): File = filesRoot

                override fun getApplicationContext(): Context = this
            }
        dayCountersStore = DayCountersStore(StudyPreferencesDataStore(dataStoreContext))

        repository =
            VocabularyRepositoryImpl(
                vocabularyDao = vocabularyDao,
                scheduler = Scheduler.builder().build(),
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
    private suspend fun insertVocab(
        word: String,
        translation: String = word,
        fsrsCardJson: String = "",
        fsrsDueAt: Long = 0L,
        correctCount: Int = 0,
        incorrectCount: Int = 0,
        lastShownAt: Long? = null,
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
    fun `undoLastRating returns null when stack is empty`() =
        runTest {
            val result = repository.undoLastRating()

            assertThat(result).isNull()
        }

    @Test
    fun `reviewVocabularyItem pushes a snapshot capturing pre-rating state`() =
        runTest {
            val id = insertVocab("lernen")

            repository.reviewVocabularyItem(id, Rating.GOOD)

            assertThat(undoSnapshotDao.count()).isEqualTo(1)
            val snapshot = undoSnapshotDao.peekLatest()
            assertThat(snapshot?.vocabId).isEqualTo(id)
            assertThat(snapshot?.fsrsCardJson).isEmpty()
            assertThat(snapshot?.fsrsDueAt).isEqualTo(0L)
            assertThat(snapshot?.correctCount).isEqualTo(0)
            assertThat(snapshot?.incorrectCount).isEqualTo(0)
            assertThat(snapshot?.ratingName).isEqualTo("GOOD")
            assertThat(snapshot?.snapshotDay).isEqualTo(todayStamp())
        }

    @Test
    fun `undoLastRating restores fsrs card state exactly for a new card`() =
        runTest {
            val id = insertVocab("lesen")

            repository.reviewVocabularyItem(id, Rating.EASY)
            val ratedEntity = vocabularyDao.getVocabularyById(id)!!
            assertThat(ratedEntity.correctCount).isEqualTo(1)

            val result = repository.undoLastRating()

            val restored = vocabularyDao.getVocabularyById(id)!!
            assertThat(result?.item?.id).isEqualTo(id)
            assertThat(result?.revertedRating).isEqualTo(Rating.EASY)
            assertThat(restored.fsrsCardJson).isEmpty()
            assertThat(restored.fsrsDueAt).isEqualTo(0L)
            assertThat(restored.lastShownAt).isNull()
            assertThat(restored.correctCount).isEqualTo(0)
            assertThat(restored.incorrectCount).isEqualTo(0)
        }

    @Test
    fun `undoLastRating restores fsrs card state exactly for an existing review card`() =
        runTest {
            val now = System.currentTimeMillis()
            val oldCardJson = Card.builder().build().toJson()
            val id =
                insertVocab(
                    word = "sprechen",
                    fsrsCardJson = oldCardJson,
                    fsrsDueAt = now - 1_000L,
                    correctCount = 2,
                    incorrectCount = 1,
                    lastShownAt = now - 100_000L,
                )

            repository.reviewVocabularyItem(id, Rating.AGAIN)
            val ratedEntity = vocabularyDao.getVocabularyById(id)!!
            assertThat(ratedEntity.incorrectCount).isEqualTo(2)

            val result = repository.undoLastRating()

            val restored = vocabularyDao.getVocabularyById(id)!!
            assertThat(result?.revertedRating).isEqualTo(Rating.AGAIN)
            assertThat(restored.fsrsCardJson).isEqualTo(oldCardJson)
            assertThat(restored.fsrsDueAt).isEqualTo(now - 1_000L)
            assertThat(restored.lastShownAt).isEqualTo(now - 100_000L)
            assertThat(restored.correctCount).isEqualTo(2)
            assertThat(restored.incorrectCount).isEqualTo(1)
        }

    @Test
    fun `undoLastRating deletes the consumed snapshot`() =
        runTest {
            val id = insertVocab("gehen")
            repository.reviewVocabularyItem(id, Rating.GOOD)
            assertThat(undoSnapshotDao.count()).isEqualTo(1)

            repository.undoLastRating()

            assertThat(undoSnapshotDao.count()).isEqualTo(0)
        }

    @Test
    fun `undoLastRating sets restored item as current item`() =
        runTest {
            val id = insertVocab("sehen")
            repository.reviewVocabularyItem(id, Rating.GOOD)

            repository.undoLastRating()

            repository.observeCurrentItem().test {
                assertThat(awaitItem().id).isEqualTo(id)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `undoLastRating restores day counters when snapshot is from today`() =
        runTest {
            val id = insertVocab("laufen")

            repository.reviewVocabularyItem(id, Rating.GOOD)
            assertThat(dayCountersStore.read().first().newShown).isEqualTo(1)

            repository.undoLastRating()

            val counters = dayCountersStore.read().first()
            assertThat(counters.newShown).isEqualTo(0)
            assertThat(counters.reviewShown).isEqualTo(0)
            assertThat(counters.reviewsSinceLastNew).isEqualTo(0)
        }

    @Test
    fun `undoLastRating skips day counter restore for a snapshot from a previous day`() =
        runTest {
            val id = insertVocab("schreiben")
            // Simulate a rating made yesterday: insert the snapshot directly rather than
            // going through reviewVocabularyItem (which always stamps "today").
            undoSnapshotDao.insert(
                UndoSnapshotEntity(
                    vocabId = id,
                    createdAt = System.currentTimeMillis() - 86_400_000L,
                    snapshotDay = todayStamp() - 1,
                    ratingName = Rating.EASY.name,
                    fsrsCardJson = "",
                    fsrsDueAt = 0L,
                    lastShownAt = null,
                    correctCount = 0,
                    incorrectCount = 0,
                    newShown = 0,
                    reviewShown = 0,
                    reviewsSinceLastNew = 0,
                ),
            )
            // Today's counters already have real activity that must survive the undo.
            dayCountersStore.resetFor(todayStamp())
            dayCountersStore.markNewShown()
            dayCountersStore.markReviewShown()
            val beforeUndo = dayCountersStore.read().first()

            // Give the card today's state so we can also confirm the FSRS half *is* restored.
            vocabularyDao.applyFsrsReview(
                id = id,
                cardJson = Card.builder().build().toJson(),
                dueAt = System.currentTimeMillis() + 1_000L,
                reviewedAt = System.currentTimeMillis(),
                incCorrect = 0,
                incIncorrect = 1,
            )

            val result = repository.undoLastRating()

            assertThat(result).isNotNull()
            val restoredEntity = vocabularyDao.getVocabularyById(id)!!
            assertThat(restoredEntity.fsrsCardJson).isEmpty()
            assertThat(restoredEntity.incorrectCount).isEqualTo(0)

            val afterUndo = dayCountersStore.read().first()
            assertThat(afterUndo).isEqualTo(beforeUndo)
        }

    @Test
    fun `undoLastRating never touches extraNewToday`() =
        runTest {
            val id = insertVocab("kaufen")
            dayCountersStore.addExtraNewToday(5, availableNew = 100)

            repository.reviewVocabularyItem(id, Rating.GOOD)
            repository.undoLastRating()

            assertThat(dayCountersStore.read().first().extraNewToday).isEqualTo(5)
        }

    @Test
    fun `undo stack keeps only the last three ratings`() =
        runTest {
            val ids = (1..4).map { insertVocab("word$it") }
            ids.forEach { repository.reviewVocabularyItem(it, Rating.GOOD) }

            assertThat(undoSnapshotDao.count()).isEqualTo(3)

            // The oldest (first) rating fell off the stack; only the last three are undoable.
            val undone = mutableListOf<Long>()
            repeat(3) {
                repository.undoLastRating()?.let { undone.add(it.item.id) }
            }
            assertThat(undone).containsExactly(ids[3], ids[2], ids[1]).inOrder()
            assertThat(repository.undoLastRating()).isNull()
        }

    @Test
    fun `undoing the same card rated twice restores state in LIFO order`() =
        runTest {
            val id = insertVocab("machen")

            repository.reviewVocabularyItem(id, Rating.GOOD)
            val afterFirst = vocabularyDao.getVocabularyById(id)!!
            val countersAfterFirst = dayCountersStore.read().first()

            repository.reviewVocabularyItem(id, Rating.EASY)
            assertThat(vocabularyDao.getVocabularyById(id)!!.correctCount).isEqualTo(2)

            // First undo reverts the *second* rating -> back to the post-first-rating state.
            repository.undoLastRating()
            val afterFirstUndo = vocabularyDao.getVocabularyById(id)!!
            assertThat(afterFirstUndo.fsrsCardJson).isEqualTo(afterFirst.fsrsCardJson)
            assertThat(afterFirstUndo.correctCount).isEqualTo(1)
            assertThat(dayCountersStore.read().first()).isEqualTo(countersAfterFirst)

            // Second undo reverts the *first* rating -> back to pristine.
            repository.undoLastRating()
            val afterSecondUndo = vocabularyDao.getVocabularyById(id)!!
            assertThat(afterSecondUndo.fsrsCardJson).isEmpty()
            assertThat(afterSecondUndo.correctCount).isEqualTo(0)
            assertThat(dayCountersStore.read().first().newShown).isEqualTo(0)
        }

    @Test
    fun `editing a word after rating it prunes its undo snapshot`() =
        runTest {
            val id = insertVocab("essen")
            repository.reviewVocabularyItem(id, Rating.GOOD)
            assertThat(undoSnapshotDao.count()).isEqualTo(1)

            repository.updateVocabularyItem(
                com.procrastilearn.app.domain.model.VocabularyItem(
                    id = id,
                    word = "essen (edited)",
                    translation = "eat",
                    isNew = false,
                ),
            )

            assertThat(undoSnapshotDao.count()).isEqualTo(0)
            assertThat(repository.undoLastRating()).isNull()
        }

    @Test
    fun `resetting progress after rating prunes its undo snapshot`() =
        runTest {
            val id = insertVocab("trinken")
            repository.reviewVocabularyItem(id, Rating.GOOD)

            repository.resetVocabularyProgress(
                com.procrastilearn.app.domain.model.VocabularyItem(
                    id = id,
                    word = "trinken",
                    translation = "drink",
                    isNew = false,
                ),
            )

            assertThat(undoSnapshotDao.count()).isEqualTo(0)
        }

    @Test
    fun `deleting a word after rating it prunes its undo snapshot`() =
        runTest {
            val id = insertVocab("fahren")
            repository.reviewVocabularyItem(id, Rating.GOOD)

            repository.deleteVocabularyItem(
                com.procrastilearn.app.domain.model.VocabularyItem(
                    id = id,
                    word = "fahren",
                    translation = "drive",
                    isNew = false,
                ),
            )

            assertThat(undoSnapshotDao.count()).isEqualTo(0)
        }

    @Test
    fun `deleting one word does not prune another word's undo snapshot`() =
        runTest {
            val keepId = insertVocab("bleiben")
            val deleteId = insertVocab("gehen weg")
            repository.reviewVocabularyItem(keepId, Rating.GOOD)
            repository.reviewVocabularyItem(deleteId, Rating.GOOD)
            assertThat(undoSnapshotDao.count()).isEqualTo(2)

            repository.deleteVocabularyItem(
                com.procrastilearn.app.domain.model.VocabularyItem(
                    id = deleteId,
                    word = "gehen weg",
                    translation = "go away",
                    isNew = false,
                ),
            )

            assertThat(undoSnapshotDao.count()).isEqualTo(1)
            assertThat(undoSnapshotDao.peekLatest()?.vocabId).isEqualTo(keepId)
        }

    @Test
    fun `observeUndoCount reflects stack size as ratings and undos happen`() =
        runTest {
            repository.observeUndoCount().test {
                assertThat(awaitItem()).isEqualTo(0)

                val id = insertVocab("wissen")
                repository.reviewVocabularyItem(id, Rating.GOOD)
                assertThat(awaitItem()).isEqualTo(1)

                repository.undoLastRating()
                assertThat(awaitItem()).isEqualTo(0)

                cancelAndConsumeRemainingEvents()
            }
        }
}
