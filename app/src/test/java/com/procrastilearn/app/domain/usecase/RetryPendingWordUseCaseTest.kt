package com.procrastilearn.app.domain.usecase

import com.procrastilearn.app.domain.repository.PendingWordRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class RetryPendingWordUseCaseTest {
    private val repository: PendingWordRepository = mockk()
    private lateinit var useCase: RetryPendingWordUseCase

    @Before
    fun setUp() {
        useCase = RetryPendingWordUseCase(repository)
    }

    @Test
    fun `invoke delegates to repository with the given id`() =
        runTest {
            coEvery { repository.retryPendingWord(any()) } just Runs

            useCase(42L)

            coVerify(exactly = 1) { repository.retryPendingWord(42L) }
        }
}
