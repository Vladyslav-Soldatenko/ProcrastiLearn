package com.procrastilearn.app.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.data.local.dao.PendingWordDao
import com.procrastilearn.app.data.local.database.AppDatabase
import com.procrastilearn.app.domain.model.AiTranslationDirection
import com.procrastilearn.app.domain.model.PendingWord
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class PendingWordRepositoryImplTest {
    private lateinit var database: AppDatabase
    private lateinit var pendingWordDao: PendingWordDao
    private lateinit var repository: PendingWordRepositoryImpl

    @Before
    fun setup() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    AppDatabase::class.java,
                ).allowMainThreadQueries()
                .build()

        pendingWordDao = database.pendingWordDao()
        repository = PendingWordRepositoryImpl(pendingWordDao)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `queuePendingWord persists the word and direction`() =
        runTest {
            repository.queuePendingWord("Haus", AiTranslationDirection.FOREIGN_TO_NATIVE)

            val snapshot = repository.getAllPendingWordsSnapshot()
            assertThat(snapshot).hasSize(1)
            assertThat(snapshot.first().word).isEqualTo("Haus")
            assertThat(snapshot.first().direction).isEqualTo(AiTranslationDirection.FOREIGN_TO_NATIVE)
        }

    @Test
    fun `queuePendingWord replaces existing entry for the same word`() =
        runTest {
            repository.queuePendingWord("Haus", AiTranslationDirection.FOREIGN_TO_NATIVE)
            repository.queuePendingWord("Haus", AiTranslationDirection.NATIVE_TO_FOREIGN)

            val snapshot = repository.getAllPendingWordsSnapshot()
            assertThat(snapshot).hasSize(1)
            assertThat(snapshot.first().direction).isEqualTo(AiTranslationDirection.NATIVE_TO_FOREIGN)
        }

    @Test
    fun `deletePendingWord removes the matching row`() =
        runTest {
            repository.queuePendingWord("Haus", AiTranslationDirection.FOREIGN_TO_NATIVE)
            val stored = repository.getAllPendingWordsSnapshot().first()

            repository.deletePendingWord(stored)

            assertThat(repository.getAllPendingWordsSnapshot()).isEmpty()
        }

    @Test
    fun `deletePendingWordById removes the matching row`() =
        runTest {
            repository.queuePendingWord("Haus", AiTranslationDirection.FOREIGN_TO_NATIVE)
            val stored = repository.getAllPendingWordsSnapshot().first()

            repository.deletePendingWordById(stored.id)

            assertThat(repository.getAllPendingWordsSnapshot()).isEmpty()
        }

    @Test
    fun `observePendingWords emits updates as words are queued and deleted`() =
        runTest {
            repository.observePendingWords().test {
                assertThat(awaitItem()).isEmpty()

                repository.queuePendingWord("Haus", AiTranslationDirection.FOREIGN_TO_NATIVE)
                assertThat(awaitItem().map { it.word }).containsExactly("Haus")

                val stored: PendingWord = repository.getAllPendingWordsSnapshot().first()
                repository.deletePendingWord(stored)
                assertThat(awaitItem()).isEmpty()
            }
        }
}
