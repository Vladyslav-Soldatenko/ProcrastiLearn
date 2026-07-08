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
            repository.queuePendingWord("Haus", AiTranslationDirection.EN_TO_RU)

            val snapshot = repository.getAllPendingWordsSnapshot()
            assertThat(snapshot).hasSize(1)
            assertThat(snapshot.first().word).isEqualTo("Haus")
            assertThat(snapshot.first().direction).isEqualTo(AiTranslationDirection.EN_TO_RU)
        }

    @Test
    fun `queuePendingWord replaces existing entry for the same word`() =
        runTest {
            repository.queuePendingWord("Haus", AiTranslationDirection.EN_TO_RU)
            repository.queuePendingWord("Haus", AiTranslationDirection.RU_TO_EN)

            val snapshot = repository.getAllPendingWordsSnapshot()
            assertThat(snapshot).hasSize(1)
            assertThat(snapshot.first().direction).isEqualTo(AiTranslationDirection.RU_TO_EN)
        }

    @Test
    fun `deletePendingWord removes the matching row`() =
        runTest {
            repository.queuePendingWord("Haus", AiTranslationDirection.EN_TO_RU)
            val stored = repository.getAllPendingWordsSnapshot().first()

            repository.deletePendingWord(stored)

            assertThat(repository.getAllPendingWordsSnapshot()).isEmpty()
        }

    @Test
    fun `deletePendingWordById removes the matching row`() =
        runTest {
            repository.queuePendingWord("Haus", AiTranslationDirection.EN_TO_RU)
            val stored = repository.getAllPendingWordsSnapshot().first()

            repository.deletePendingWordById(stored.id)

            assertThat(repository.getAllPendingWordsSnapshot()).isEmpty()
        }

    @Test
    fun `observePendingWords emits updates as words are queued and deleted`() =
        runTest {
            repository.observePendingWords().test {
                assertThat(awaitItem()).isEmpty()

                repository.queuePendingWord("Haus", AiTranslationDirection.EN_TO_RU)
                assertThat(awaitItem().map { it.word }).containsExactly("Haus")

                val stored: PendingWord = repository.getAllPendingWordsSnapshot().first()
                repository.deletePendingWord(stored)
                assertThat(awaitItem()).isEmpty()
            }
        }
}
