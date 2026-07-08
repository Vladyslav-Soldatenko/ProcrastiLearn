package com.procrastilearn.app.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.data.local.prefs.DayCountersStore
import com.procrastilearn.app.data.translation.AiTranslationProvider
import com.procrastilearn.app.data.translation.AiTranslationRequest
import com.procrastilearn.app.domain.model.AiTranslationDirection
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class GenerateAiTranslationUseCaseTest {
    private val prefs: DayCountersStore = mockk()
    private lateinit var provider: FakeAiTranslationProvider
    private lateinit var useCase: GenerateAiTranslationUseCase

    @Before
    fun setUp() {
        provider = FakeAiTranslationProvider()
        every { prefs.readOpenAiApiKey() } returns flowOf("abc")
        every { prefs.readOpenAiPrompt() } returns flowOf("forward prompt")
        every { prefs.readOpenAiReversePrompt() } returns flowOf("reverse prompt")
        useCase = GenerateAiTranslationUseCase(provider, prefs, UnconfinedTestDispatcher())
    }

    @Test
    fun `invoke uses forward prompt for EN to RU`() =
        runTest {
            provider.nextTranslation = "House"

            val result = useCase("Haus", AiTranslationDirection.EN_TO_RU)

            assertThat(result).isEqualTo("House")
            val request = provider.requests.single()
            assertThat(request.apiKey).isEqualTo("abc")
            assertThat(request.systemPrompt).isEqualTo("forward prompt")
            assertThat(request.userPrompt).contains("HEADWORD: \"Haus\"")
        }

    @Test
    fun `invoke uses reverse prompt for RU to EN`() =
        runTest {
            provider.nextTranslation = "House"

            useCase("дом", AiTranslationDirection.RU_TO_EN)

            val request = provider.requests.single()
            assertThat(request.systemPrompt).isEqualTo("reverse prompt")
            assertThat(request.userPrompt).contains("HEADWORD: \"дом\"")
        }

    @Test
    fun `invoke throws when api key is missing`() {
        every { prefs.readOpenAiApiKey() } returns flowOf(null)

        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { useCase("Haus", AiTranslationDirection.EN_TO_RU) }
        }
        assertThat(provider.requests).isEmpty()
    }

    private class FakeAiTranslationProvider : AiTranslationProvider {
        var nextTranslation: String = "House"
        val requests = mutableListOf<AiTranslationRequest>()

        override suspend fun translate(request: AiTranslationRequest): String {
            requests += request
            return nextTranslation
        }
    }
}
