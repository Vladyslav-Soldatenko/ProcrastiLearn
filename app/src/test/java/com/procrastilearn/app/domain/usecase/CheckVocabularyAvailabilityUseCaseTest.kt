package com.procrastilearn.app.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.domain.repository.VocabularyRepository
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class CheckVocabularyAvailabilityUseCaseTest {
    private val repository: VocabularyRepository = mockk()

    private lateinit var useCase: CheckVocabularyAvailabilityUseCase

    @Before
    fun setUp() {
        useCase = CheckVocabularyAvailabilityUseCase(repository)
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `invoke returns true when repository reports available items`() =
        runTest {
            coEvery { repository.hasAvailableItems() } returns true

            val result = useCase()

            assertThat(result).isTrue()
            coVerify(exactly = 1) { repository.hasAvailableItems() }
        }

    @Test
    fun `invoke returns false when repository reports no available items`() =
        runTest {
            coEvery { repository.hasAvailableItems() } returns false

            val result = useCase()

            assertThat(result).isFalse()
            coVerify(exactly = 1) { repository.hasAvailableItems() }
        }
}
