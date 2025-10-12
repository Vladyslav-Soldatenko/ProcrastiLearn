package com.procrastilearn.app.data.translation

import com.google.common.truth.Truth.assertThat
import com.openai.client.OpenAIClient
import com.openai.models.chat.completions.ChatCompletion
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.chat.completions.ChatCompletionMessage
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.Optional

@OptIn(ExperimentalCoroutinesApi::class)
class OpenAiTranslationProviderTest {
    private val clientFactory = mockk<OpenAiClientFactory>()
    private val client = mockk<OpenAIClient>(relaxed = true)
    private val provider = OpenAiTranslationProvider(clientFactory)

    @Test
    fun `translate returns trimmed content from first choice`() =
        runTest {
            every { clientFactory.create("test-key") } returns client

            val completion = mockk<ChatCompletion>()
            val choice = mockk<ChatCompletion.Choice>()
            val message = mockk<ChatCompletionMessage>()

            every { client.chat().completions().create(any<ChatCompletionCreateParams>()) } returns completion
            every { completion.choices() } returns listOf(choice)
            every { choice.message() } returns message
            every { message.content() } returns Optional.of("  translated text  ")

            val result =
                provider.translate(
                    AiTranslationRequest(
                        apiKey = "test-key",
                        systemPrompt = "sys",
                        userPrompt = "user prompt",
                    ),
                )

            assertThat(result).isEqualTo("translated text")
            verify { clientFactory.create("test-key") }
            verify { client.chat().completions().create(any()) }
        }

    @Test(expected = IllegalStateException::class)
    fun `translate throws when OpenAI returns blank content`() =
        runTest {
            every { clientFactory.create(any()) } returns client

            val completion = mockk<ChatCompletion>()
            every { client.chat().completions().create(any<ChatCompletionCreateParams>()) } returns completion
            every { completion.choices() } returns emptyList()

            provider.translate(
                AiTranslationRequest(
                    apiKey = "key",
                    systemPrompt = "sys",
                    userPrompt = "user",
                ),
            )
        }
}
