package com.procrastilearn.app.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.data.local.prefs.OpenAiPreferencesStore
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
    private val openAiStore: OpenAiPreferencesStore = mockk()
    private lateinit var provider: FakeAiTranslationProvider
    private lateinit var useCase: GenerateAiTranslationUseCase

    @Before
    fun setUp() {
        provider = FakeAiTranslationProvider()
        every { openAiStore.readOpenAiApiKey() } returns flowOf("abc")
        every { openAiStore.readOpenAiPrompt() } returns flowOf("forward prompt")
        every { openAiStore.readOpenAiReversePrompt() } returns flowOf("reverse prompt")
        useCase = GenerateAiTranslationUseCase(provider, openAiStore, UnconfinedTestDispatcher())
    }

    @Test
    fun `invoke uses forward prompt for EN to RU`() =
        runTest {
            provider.nextTranslation = "House"

            val result = useCase("Haus", AiTranslationDirection.TARGET_TO_NATIVE)

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

            useCase("дом", AiTranslationDirection.NATIVE_TO_TARGET)

            val request = provider.requests.single()
            assertThat(request.systemPrompt).isEqualTo("reverse prompt")
            assertThat(request.userPrompt).contains("HEADWORD: \"дом\"")
        }

    @Test
    fun `invoke throws when api key is missing`() {
        every { openAiStore.readOpenAiApiKey() } returns flowOf(null)

        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { useCase("Haus", AiTranslationDirection.TARGET_TO_NATIVE) }
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
