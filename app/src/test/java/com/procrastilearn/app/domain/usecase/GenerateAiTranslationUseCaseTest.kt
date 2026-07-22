package com.procrastilearn.app.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.data.local.prefs.LanguagePreferencesStore
import com.procrastilearn.app.data.local.prefs.OpenAiPreferencesStore
import com.procrastilearn.app.data.local.prefs.OpenAiPromptDefaults
import com.procrastilearn.app.data.translation.AiTranslationProvider
import com.procrastilearn.app.data.translation.AiTranslationRequest
import com.procrastilearn.app.domain.model.AiTranslationDirection
import com.procrastilearn.app.domain.model.Language
import com.procrastilearn.app.domain.model.LanguagePair
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
    private val languagePreferencesStore: LanguagePreferencesStore = mockk()
    private lateinit var provider: FakeAiTranslationProvider
    private lateinit var useCase: GenerateAiTranslationUseCase

    @Before
    fun setUp() {
        provider = FakeAiTranslationProvider()
        every { openAiStore.readOpenAiApiKey() } returns flowOf("abc")
        every { openAiStore.readOpenAiPrompt() } returns flowOf("forward prompt")
        every { openAiStore.readOpenAiReversePrompt() } returns flowOf("reverse prompt")
        every { languagePreferencesStore.readLanguagePair() } returns
            flowOf(LanguagePair(Language.SPANISH, Language.FRENCH))
        useCase = GenerateAiTranslationUseCase(provider, openAiStore, languagePreferencesStore, UnconfinedTestDispatcher())
    }

    @Test
    fun `invoke uses forward prompt for target to native direction`() =
        runTest {
            provider.nextTranslation = "maison"

            val result = useCase("maison", AiTranslationDirection.TARGET_TO_NATIVE)

            assertThat(result).isEqualTo("maison")
            val request = provider.requests.single()
            assertThat(request.apiKey).isEqualTo("abc")
            assertThat(request.systemPrompt).isEqualTo("forward prompt")
            assertThat(request.userPrompt).contains("HEADWORD: \"maison\"")
        }

    @Test
    fun `invoke uses reverse prompt for native to target direction`() =
        runTest {
            provider.nextTranslation = "maison"

            useCase("casa", AiTranslationDirection.NATIVE_TO_TARGET)

            val request = provider.requests.single()
            assertThat(request.systemPrompt).isEqualTo("reverse prompt")
            assertThat(request.userPrompt).contains("HEADWORD: \"casa\"")
        }

    @Test
    fun `invoke resolves placeholders in the forward prompt to the selected pair`() =
        runTest {
            every { openAiStore.readOpenAiPrompt() } returns flowOf(OpenAiPromptDefaults.translationPrompt)

            useCase("maison", AiTranslationDirection.TARGET_TO_NATIVE)

            val request = provider.requests.single()
            assertThat(request.systemPrompt).contains("Spanish")
            assertThat(request.systemPrompt).contains("French")
            assertThat(request.systemPrompt).doesNotContain("{{")
        }

    @Test
    fun `invoke resolves placeholders in the reverse prompt to the selected pair`() =
        runTest {
            every { openAiStore.readOpenAiReversePrompt() } returns flowOf(OpenAiPromptDefaults.reverseTranslationPrompt)

            useCase("casa", AiTranslationDirection.NATIVE_TO_TARGET)

            val request = provider.requests.single()
            assertThat(request.systemPrompt).contains("Spanish")
            assertThat(request.systemPrompt).contains("French")
            assertThat(request.systemPrompt).doesNotContain("{{")
        }

    @Test
    fun `invoke resolves placeholders in a custom stored prompt too`() =
        runTest {
            every { openAiStore.readOpenAiPrompt() } returns
                flowOf(
                    "Explain in ${OpenAiPromptDefaults.NATIVE_LANGUAGE_PLACEHOLDER}, " +
                        "examples in ${OpenAiPromptDefaults.TARGET_LANGUAGE_PLACEHOLDER}",
                )

            useCase("maison", AiTranslationDirection.TARGET_TO_NATIVE)

            val request = provider.requests.single()
            assertThat(request.systemPrompt).isEqualTo("Explain in Spanish, examples in French")
        }

    @Test
    fun `invoke appends a language reminder to the user prompt for the forward direction`() =
        runTest {
            useCase("maison", AiTranslationDirection.TARGET_TO_NATIVE)

            val request = provider.requests.single()
            assertThat(request.userPrompt).contains("The headword above is in French.")
            assertThat(request.userPrompt).contains("Spanish")
        }

    @Test
    fun `invoke appends a language reminder to the user prompt for the reverse direction`() =
        runTest {
            useCase("casa", AiTranslationDirection.NATIVE_TO_TARGET)

            val request = provider.requests.single()
            assertThat(request.userPrompt).contains("The headword above is in Spanish.")
            assertThat(request.userPrompt).contains("French")
        }

    @Test
    fun `invoke throws when api key is missing`() {
        every { openAiStore.readOpenAiApiKey() } returns flowOf(null)

        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { useCase("Haus", AiTranslationDirection.TARGET_TO_NATIVE) }
        }
        assertThat(provider.requests).isEmpty()
    }

    @Test
    fun `invoke throws when language pair is missing`() {
        every { languagePreferencesStore.readLanguagePair() } returns flowOf(null)

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
