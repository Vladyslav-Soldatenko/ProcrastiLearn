package com.procrastilearn.app.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.domain.model.AiTranslationDirection
import com.procrastilearn.app.domain.model.PendingWord
import com.procrastilearn.app.domain.repository.PendingWordRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ObservePendingWordsUseCaseTest {
    private val repository: PendingWordRepository = mockk()
    private lateinit var useCase: ObservePendingWordsUseCase

    @Before
    fun setUp() {
        useCase = ObservePendingWordsUseCase(repository)
    }

    @Test
    fun `invoke delegates to repository`() =
        runTest {
            val words = listOf(PendingWord(id = 1, word = "Haus", direction = AiTranslationDirection.TARGET_TO_NATIVE))
            every { repository.observePendingWords() } returns flowOf(words)

            val result = useCase().first()

            assertThat(result).isEqualTo(words)
        }
}
