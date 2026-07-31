package com.procrastilearn.app.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.domain.model.StudyDirection
import com.procrastilearn.app.domain.repository.VocabularyStudyRepository
import io.github.openspacedrepetition.Rating
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class SaveDifficultyRatingUseCaseTest {
    private val repository: VocabularyStudyRepository = mockk()

    private lateinit var useCase: SaveDifficultyRatingUseCase

    @Before
    fun setUp() {
        useCase = SaveDifficultyRatingUseCase(repository)
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `invoke returns success when repository succeeds`() =
        runTest {
            coEvery { repository.reviewVocabularyItem(any(), any()) } just Runs

            val result = useCase(42L, Rating.GOOD)

            assertThat(result.isSuccess).isTrue()
            coVerify(exactly = 1) { repository.reviewVocabularyItem(42L, Rating.GOOD) }
        }

    @Test
    fun `invoke forwards the FORWARD direction argument to the repository unchanged`() =
        runTest {
            coEvery { repository.reviewVocabularyItem(any(), any(), any()) } just Runs

            useCase(42L, Rating.GOOD, StudyDirection.FORWARD)

            coVerify(exactly = 1) { repository.reviewVocabularyItem(42L, Rating.GOOD, StudyDirection.FORWARD) }
        }

    @Test
    fun `invoke forwards the BACKWARD direction argument to the repository unchanged`() =
        runTest {
            coEvery { repository.reviewVocabularyItem(any(), any(), any()) } just Runs

            useCase(42L, Rating.GOOD, StudyDirection.BACKWARD)

            coVerify(exactly = 1) { repository.reviewVocabularyItem(42L, Rating.GOOD, StudyDirection.BACKWARD) }
        }

    @Test
    fun `invoke returns failure when repository throws`() =
        runTest {
            val error = IllegalStateException("unable to save")
            coEvery { repository.reviewVocabularyItem(any(), any()) } throws error

            val result = useCase(99L, Rating.HARD)

            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()).isEqualTo(error)
            coVerify(exactly = 1) { repository.reviewVocabularyItem(99L, Rating.HARD) }
        }
}
