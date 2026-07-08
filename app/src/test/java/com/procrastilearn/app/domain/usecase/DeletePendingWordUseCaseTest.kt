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

class DeletePendingWordUseCaseTest {
    private val repository: PendingWordRepository = mockk()
    private lateinit var useCase: DeletePendingWordUseCase

    @Before
    fun setUp() {
        useCase = DeletePendingWordUseCase(repository)
    }

    @Test
    fun `invoke delegates to repository by id`() =
        runTest {
            coEvery { repository.deletePendingWordById(42L) } just Runs

            useCase(42L)

            coVerify(exactly = 1) { repository.deletePendingWordById(42L) }
        }
}
