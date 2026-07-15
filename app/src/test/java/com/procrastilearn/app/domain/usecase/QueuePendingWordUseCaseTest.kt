package com.procrastilearn.app.domain.usecase

import com.procrastilearn.app.domain.model.AiTranslationDirection
import com.procrastilearn.app.domain.model.PendingWordStatus
import com.procrastilearn.app.domain.repository.PendingWordRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class QueuePendingWordUseCaseTest {
    private val repository: PendingWordRepository = mockk()
    private lateinit var useCase: QueuePendingWordUseCase

    @Before
    fun setUp() {
        useCase = QueuePendingWordUseCase(repository)
    }

    @Test
    fun `invoke trims the word and delegates to repository with default pending status`() =
        runTest {
            coEvery { repository.queuePendingWord(any(), any(), any(), any()) } just Runs

            useCase(" Haus ", AiTranslationDirection.EN_TO_RU)

            coVerify(exactly = 1) {
                repository.queuePendingWord("Haus", AiTranslationDirection.EN_TO_RU, PendingWordStatus.PENDING, null)
            }
        }

    @Test
    fun `invoke forwards an explicit failed status and error message`() =
        runTest {
            coEvery { repository.queuePendingWord(any(), any(), any(), any()) } just Runs

            useCase("Haus", AiTranslationDirection.EN_TO_RU, PendingWordStatus.FAILED, "401: unauthorized")

            coVerify(exactly = 1) {
                repository.queuePendingWord(
                    "Haus",
                    AiTranslationDirection.EN_TO_RU,
                    PendingWordStatus.FAILED,
                    "401: unauthorized",
                )
            }
        }

    @Test
    fun `invoke throws for a blank word without touching the repository`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { useCase("   ", AiTranslationDirection.EN_TO_RU) }
        }

        coVerify(exactly = 0) { repository.queuePendingWord(any(), any(), any(), any()) }
    }
}
