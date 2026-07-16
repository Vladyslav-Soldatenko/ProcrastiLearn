package com.procrastilearn.app.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.data.local.database.AppDatabase
import com.procrastilearn.app.data.local.entity.UndoSnapshotEntity
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
class UndoSnapshotDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: UndoSnapshotDao

    @Before
    fun setup() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    AppDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        dao = database.undoSnapshotDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun vocabId(word: String): Long =
        database.vocabularyDao().insertVocabulary(
            VocabularyEntity(id = 0, word = word, translation = word),
        )

    private fun snapshot(
        vocabId: Long,
        createdAt: Long = System.currentTimeMillis(),
    ) = UndoSnapshotEntity(
        vocabId = vocabId,
        createdAt = createdAt,
        snapshotDay = 20260117,
        ratingName = "GOOD",
        fsrsCardJson = "",
        fsrsDueAt = 0L,
        lastShownAt = null,
        correctCount = 0,
        incorrectCount = 0,
        newShown = 0,
        reviewShown = 0,
        reviewsSinceLastNew = 0,
    )

    @Test
    fun `peekLatest returns null when empty`() =
        runTest {
            assertThat(dao.peekLatest()).isNull()
        }

    @Test
    fun `peekLatest returns most recently inserted snapshot`() =
        runTest {
            val id = vocabId("eins")
            dao.insert(snapshot(id, createdAt = 1L))
            dao.insert(snapshot(id, createdAt = 2L))
            val third = dao.insert(snapshot(id, createdAt = 3L))

            val latest = dao.peekLatest()

            assertThat(latest?.id).isEqualTo(third)
        }

    @Test
    fun `trimToLast keeps only the most recent N rows`() =
        runTest {
            val id = vocabId("zwei")
            repeat(5) { dao.insert(snapshot(id, createdAt = it.toLong())) }

            dao.trimToLast(3)

            assertThat(dao.count()).isEqualTo(3)
        }

    @Test
    fun `trimToLast is a no-op when under the cap`() =
        runTest {
            val id = vocabId("drei")
            dao.insert(snapshot(id))
            dao.insert(snapshot(id))

            dao.trimToLast(3)

            assertThat(dao.count()).isEqualTo(2)
        }

    @Test
    fun `trimToLast with zero clears the stack`() =
        runTest {
            val id = vocabId("vier")
            dao.insert(snapshot(id))
            dao.insert(snapshot(id))

            dao.trimToLast(0)

            assertThat(dao.count()).isEqualTo(0)
        }

    @Test
    fun `deleteById removes only the targeted row`() =
        runTest {
            val id = vocabId("fuenf")
            val keep = dao.insert(snapshot(id))
            val remove = dao.insert(snapshot(id))

            dao.deleteById(remove)

            assertThat(dao.count()).isEqualTo(1)
            assertThat(dao.peekLatest()?.id).isEqualTo(keep)
        }

    @Test
    fun `deleteForVocab removes all snapshots for that vocab only`() =
        runTest {
            val a = vocabId("a")
            val b = vocabId("b")
            dao.insert(snapshot(a))
            dao.insert(snapshot(a))
            dao.insert(snapshot(b))

            dao.deleteForVocab(a)

            assertThat(dao.count()).isEqualTo(1)
            assertThat(dao.peekLatest()?.vocabId).isEqualTo(b)
        }

    @Test
    fun `deleteAll clears every snapshot`() =
        runTest {
            val id = vocabId("all")
            dao.insert(snapshot(id))
            dao.insert(snapshot(id))

            dao.deleteAll()

            assertThat(dao.count()).isEqualTo(0)
        }

    @Test
    fun `observeCount emits on every insert and delete`() =
        runTest {
            val id = vocabId("obs")

            dao.observeCount().test {
                assertThat(awaitItem()).isEqualTo(0)

                val inserted = dao.insert(snapshot(id))
                assertThat(awaitItem()).isEqualTo(1)

                dao.deleteById(inserted)
                assertThat(awaitItem()).isEqualTo(0)

                cancelAndConsumeRemainingEvents()
            }
        }
}
